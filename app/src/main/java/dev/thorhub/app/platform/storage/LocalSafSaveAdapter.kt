package dev.thorhub.app.platform.storage

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thorhub.app.data.SaveContinuitySummary
import dev.thorhub.app.data.SaveProfileSummary
import dev.thorhub.app.data.SaveSnapshotSummary
import dev.thorhub.core.database.SaveAccessMethod
import dev.thorhub.core.database.SaveProfileEntity
import dev.thorhub.core.database.SaveSnapshotEntity
import dev.thorhub.core.database.SaveSnapshotFileEntity
import dev.thorhub.core.database.SaveState as DbSaveState
import dev.thorhub.core.database.SaveType as DbSaveType
import dev.thorhub.core.database.SnapshotReason as DbSnapshotReason
import dev.thorhub.core.database.SnapshotState as DbSnapshotState
import dev.thorhub.core.database.ThorHubDatabase
import dev.thorhub.core.model.ContentHash
import dev.thorhub.core.model.DeviceId
import dev.thorhub.core.model.Installation
import dev.thorhub.core.model.InstallationId
import dev.thorhub.core.model.QuiescencePolicy
import dev.thorhub.core.model.QuiescenceResult
import dev.thorhub.core.model.QuiescenceState
import dev.thorhub.core.model.RestoreResult
import dev.thorhub.core.model.RestoreResultState
import dev.thorhub.core.model.SaveAdapter
import dev.thorhub.core.model.SaveAdapterId
import dev.thorhub.core.model.SaveCanonicalType
import dev.thorhub.core.model.SaveCapability
import dev.thorhub.core.model.SaveLocation
import dev.thorhub.core.model.SaveManifestEntry
import dev.thorhub.core.model.SaveManifestResult
import dev.thorhub.core.model.SaveProfile
import dev.thorhub.core.model.SaveProfileId
import dev.thorhub.core.model.SaveProfileState
import dev.thorhub.core.model.SaveSnapshot
import dev.thorhub.core.model.SaveSnapshotFile
import dev.thorhub.core.model.SaveSnapshotId
import dev.thorhub.core.model.SnapshotReason
import dev.thorhub.core.model.SnapshotResult
import dev.thorhub.core.model.SnapshotResultState
import dev.thorhub.core.model.SnapshotState
import dev.thorhub.core.model.CapabilityStatus
import dev.thorhub.core.model.GameSessionId
import dev.thorhub.core.model.GameVersionId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class LocalSnapshotOutcome(
    val status: String,
    val snapshotId: String? = null,
    val detail: String,
)

internal object SnapshotContentPolicy {
    fun unchanged(previousHash: String?, currentHash: String): Boolean =
        !previousHash.isNullOrBlank() && previousHash == currentHash
}

/**
 * Conservative Phase 1B adapter for a user-granted SAF tree.
 *
 * It never discovers or accepts arbitrary filesystem paths. Snapshot files are
 * copied to an app-private temporary directory, hashed, manifested, and then
 * renamed inside the same parent directory before Room records COMPLETE.
 */
@Singleton
class LocalSafSaveAdapter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: ThorHubDatabase,
) : SaveAdapter {
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private val snapshotMutex = Mutex()
    override val id: SaveAdapterId = SaveAdapterId.of(ADAPTER_ID)

    fun observeContinuity(gameId: String): Flow<SaveContinuitySummary> = combine(
        database.saveDao().observeProfilesForGame(gameId),
        database.saveDao().observeSnapshotsForGame(gameId),
    ) { profiles, snapshots ->
        SaveContinuitySummary(
            profile = profiles.firstOrNull()?.let { profile ->
                SaveProfileSummary(
                    id = profile.id,
                    state = profile.saveState.name,
                    type = profile.saveType.name,
                    access = profile.accessMethod.name,
                    configured = profile.rootLocator != null,
                )
            },
            snapshots = snapshots.map { snapshot ->
                SaveSnapshotSummary(
                    id = snapshot.id,
                    capturedAt = snapshot.capturedAt,
                    state = snapshot.snapshotState.name,
                    reason = snapshot.captureReason.name,
                    parentId = snapshot.parentSnapshotId,
                    contentHash = snapshot.contentHash,
                )
            },
        )
    }

    suspend fun configureForGame(gameId: String, treeUri: Uri, type: DbSaveType = DbSaveType.NORMAL_SAVE): String =
        withContext(ioDispatcher) {
            require(treeUri.scheme == "content") { "A Storage Access Framework tree is required" }
            require(hasPersistedGrant(treeUri, write = true)) { "Read/write access was not persisted for this tree" }
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("The selected save tree could not be opened")
            require(root.exists() && root.isDirectory && root.canRead() && root.canWrite()) {
                "The selected save tree must be a readable and writable directory"
            }
            val version = database.gameVersionDao().observeForGame(gameId).first().firstOrNull()
                ?: error("The selected game has no version")
            val installation = database.installationDao().observeForGame(gameId).first().firstOrNull()
                ?: error("The selected game has no installation")
            val deviceId = installation.deviceId ?: error("The installation has no local device")
            val now = Instant.now().toEpochMilli()
            val profileId = dev.thorhub.core.model.DeterministicIds.saveProfile(
                "local-saf-save",
                "$gameId:${treeUri}",
            ).value
            database.saveDao().upsertProfile(
                SaveProfileEntity(
                    id = profileId,
                    gameVersionId = version.id,
                    installationId = installation.id,
                    deviceId = deviceId,
                    adapterId = ADAPTER_ID,
                    accessMethod = SaveAccessMethod.SAF_DIRECTORY,
                    rootLocator = treeUri.toString(),
                    relativeIncludeJson = "[\"**\"]",
                    relativeExcludeJson = "[]",
                    saveType = type,
                    compatibilityFingerprint = null,
                    saveState = DbSaveState.LOCAL_ONLY,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            profileId
        }

    suspend fun snapshotForGame(
        gameId: String,
        reason: SnapshotReason,
        sessionId: String? = null,
        gameVersionId: String? = null,
    ): LocalSnapshotOutcome {
        val profiles = database.saveDao().observeProfilesForGame(gameId).first()
        val profileEntity = profiles.firstOrNull { profile -> profile.gameVersionId == gameVersionId }
            ?: profiles.firstOrNull().takeIf { gameVersionId == null }
            ?: return LocalSnapshotOutcome("UNCONFIGURED", detail = "No explicit SAF save profile is configured.")
        if (profileEntity.saveState == DbSaveState.MANAGED_EXTERNALLY) {
            return LocalSnapshotOutcome("MANAGED_EXTERNALLY", detail = "Saves are managed by the external launcher.")
        }
        val profile = profileEntity.toDomain()
            ?: return LocalSnapshotOutcome("UNAVAILABLE", detail = "The save profile is incomplete.")
        val result = snapshotInternal(profile, reason, sessionId)
        return LocalSnapshotOutcome(
            status = when (result.state) {
                SnapshotResultState.CREATED, SnapshotResultState.UNCHANGED -> "SNAPSHOTTED"
                SnapshotResultState.UNAVAILABLE -> "UNAVAILABLE"
                SnapshotResultState.UNSUPPORTED -> "UNSUPPORTED"
                SnapshotResultState.ERROR -> "ERROR"
                SnapshotResultState.UNKNOWN -> "UNKNOWN"
            },
            snapshotId = result.snapshot?.id?.value,
            detail = result.detail ?: result.state.name,
        )
    }

    suspend fun restoreLatestForGame(gameId: String): RestoreResult {
        val profile = database.saveDao().observeProfilesForGame(gameId).first().firstOrNull()
            ?: return RestoreResult(RestoreResultState.UNAVAILABLE, "No save profile is configured.")
        val snapshot = database.saveDao().latestCompleteSnapshot(profile.id)
            ?: return RestoreResult(RestoreResultState.UNAVAILABLE, "No complete snapshot exists.")
        return restore(SaveSnapshotId.of(snapshot.id))
    }

    override suspend fun probe(installation: Installation): SaveCapability = withContext(ioDispatcher) {
        val profiles = database.saveDao().observeProfilesForVersion(installation.gameVersionId.value).first()
        val configured = profiles.firstOrNull { it.adapterId == ADAPTER_ID && it.rootLocator != null }
        if (configured == null) {
            SaveCapability(CapabilityStatus.NEEDS_ACTION, detail = "Choose an explicit SAF save directory.")
        } else {
            SaveCapability(
                state = if (configured.rootLocator?.toUri()?.let { hasPersistedGrant(it, write = true) } == true) {
                    CapabilityStatus.READY
                } else {
                    CapabilityStatus.NEEDS_ACTION
                },
                canonicalTypes = setOf(configured.saveType.toCanonicalType()),
                detail = "Only the granted save tree is accessible.",
            )
        }
    }

    override suspend fun enumerate(profile: SaveProfile): SaveManifestResult = withContext(ioDispatcher) {
        val root = openRoot(profile, write = false)
            ?: return@withContext SaveManifestResult(SaveProfileState.UNAVAILABLE, detail = "The SAF grant is unavailable.")
        runCatching {
            val files = SnapshotPathPolicy.collect(root)
            SaveManifestResult(
                state = SaveProfileState.AVAILABLE,
                entries = files.map { source ->
                    SaveManifestEntry(
                        relativePath = source.relativePath,
                        sizeBytes = source.document.length().coerceAtLeast(0L),
                        modifiedAt = source.document.lastModified().takeIf { it > 0L }?.let(Instant::ofEpochMilli),
                    )
                },
            )
        }.getOrElse { failure ->
            SaveManifestResult(SaveProfileState.ERROR, detail = "Save enumeration failed (${failure::class.simpleName}).")
        }
    }

    override suspend fun awaitQuiescence(
        profile: SaveProfile,
        policy: QuiescencePolicy,
    ): QuiescenceResult = withContext(ioDispatcher) {
        val root = openRoot(profile, write = false)
            ?: return@withContext QuiescenceResult(QuiescenceState.UNAVAILABLE, detail = "The SAF grant is unavailable.")
        val deadline = System.nanoTime() + policy.maxWait.toNanos()
        var prior: List<FileSignature>? = null
        var stableSamples = 0
        var samples = 0
        while (System.nanoTime() < deadline) {
            val current = runCatching {
                SnapshotPathPolicy.collect(root).map { source ->
                    FileSignature(source.relativePath, source.document.length(), source.document.lastModified())
                }
            }.getOrElse {
                return@withContext QuiescenceResult(QuiescenceState.UNAVAILABLE, samples, "The save tree became unavailable.")
            }
            samples += 1
            if (prior != null && current == prior) stableSamples += 1 else stableSamples = 0
            if (stableSamples >= policy.requiredStableSamples) {
                return@withContext QuiescenceResult(QuiescenceState.QUIESCENT, samples, "The save manifest remained stable.")
            }
            prior = current
            delay(policy.sampleInterval.toMillis())
        }
        QuiescenceResult(QuiescenceState.TIMED_OUT, samples, "Save files did not become stable before the bounded timeout.")
    }

    override suspend fun snapshot(profile: SaveProfile, reason: SnapshotReason): SnapshotResult =
        snapshotInternal(profile, reason, sessionId = null)

    private suspend fun snapshotInternal(
        profile: SaveProfile,
        reason: SnapshotReason,
        sessionId: String?,
    ): SnapshotResult = snapshotMutex.withLock {
        snapshotInternalLocked(profile, reason, sessionId)
    }

    private suspend fun snapshotInternalLocked(
        profile: SaveProfile,
        reason: SnapshotReason,
        sessionId: String?,
    ): SnapshotResult = withContext(ioDispatcher) {
        val profileEntity = database.saveDao().getProfile(profile.id.value)
            ?: return@withContext SnapshotResult(SnapshotResultState.UNAVAILABLE, detail = "The save profile no longer exists.")
        val root = openRoot(profile, write = false)
            ?: return@withContext SnapshotResult(SnapshotResultState.UNAVAILABLE, detail = "The SAF grant is unavailable.")
        val quiescence = awaitQuiescence(
            profile,
            QuiescencePolicy(
                sampleInterval = Duration.ofMillis(500),
                requiredStableSamples = 2,
                maxWait = Duration.ofSeconds(8),
            ),
        )
        if (quiescence.state != QuiescenceState.QUIESCENT) {
            return@withContext SnapshotResult(
                state = if (quiescence.state == QuiescenceState.UNAVAILABLE) {
                    SnapshotResultState.UNAVAILABLE
                } else {
                    SnapshotResultState.UNKNOWN
                },
                detail = quiescence.detail,
            )
        }

        val now = Instant.now()
        val snapshotId = "save-snapshot-${UUID.randomUUID()}"
        val storageRoot = File(context.filesDir, SNAPSHOT_DIRECTORY).apply { mkdirs() }
        val temporary = File(storageRoot, ".tmp-$snapshotId")
        val committed = File(storageRoot, snapshotId)
        require(temporary.canonicalFile.parentFile == storageRoot.canonicalFile)
        require(committed.canonicalFile.parentFile == storageRoot.canonicalFile)
        if (!temporary.mkdir()) {
            return@withContext SnapshotResult(SnapshotResultState.ERROR, detail = "Snapshot temporary storage could not be created.")
        }
        database.saveDao().upsertProfile(profileEntity.copy(saveState = DbSaveState.SNAPSHOTTING, updatedAt = now.toEpochMilli()))

        try {
            val sources = SnapshotPathPolicy.collect(root)
            val manifestFiles = sources.mapIndexed { index, source ->
                val destination = File(temporary, source.relativePath)
                SnapshotPathPolicy.requireInside(temporary, destination)
                destination.parentFile?.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                val input = context.contentResolver.openInputStream(source.document.uri)
                    ?: error("A selected save file could not be opened")
                var copied = 0L
                input.use { stream ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            copied += count
                        }
                        output.fd.sync()
                    }
                }
                val hash = "sha256:${digest.digest().toHex()}"
                SaveSnapshotFileEntity(
                    id = "snapshot-file-$snapshotId-$index",
                    snapshotId = snapshotId,
                    relativePath = source.relativePath,
                    sizeBytes = copied,
                    contentHash = hash,
                    sourceModifiedAt = source.document.lastModified().takeIf { it > 0L },
                    createdAt = now.toEpochMilli(),
                )
            }
            val manifestDigest = SnapshotPathPolicy.manifestDigest(manifestFiles)
            val parent = database.saveDao().latestCompleteSnapshot(profile.id.value)
            if (SnapshotContentPolicy.unchanged(parent?.contentHash, manifestDigest)) {
                check(temporary.deleteRecursively()) { "Unchanged snapshot temporary storage could not be removed" }
                database.saveDao().upsertProfile(
                    profileEntity.copy(saveState = DbSaveState.SNAPSHOTTED, updatedAt = now.toEpochMilli()),
                )
                val unchangedParent = requireNotNull(parent)
                val parentFiles = database.saveDao().getFiles(unchangedParent.id)
                return@withContext SnapshotResult(
                    state = SnapshotResultState.UNCHANGED,
                    snapshot = unchangedParent.toDomain(profile, parentFiles),
                    detail = "Save content is unchanged; the existing verified snapshot was retained.",
                )
            }
            writeManifest(temporary, snapshotId, profile.id.value, reason, manifestDigest, manifestFiles)
            check(temporary.renameTo(committed)) { "Snapshot atomic commit failed" }

            val snapshotEntity = SaveSnapshotEntity(
                id = snapshotId,
                saveProfileId = profile.id.value,
                deviceId = profileEntity.deviceId,
                sessionId = sessionId,
                parentSnapshotId = parent?.id,
                captureReason = reason.toDatabaseReason(),
                snapshotState = DbSnapshotState.COMPLETE,
                compatibilityFingerprint = profileEntity.compatibilityFingerprint,
                manifestSchemaVersion = MANIFEST_VERSION,
                storageToken = "$SNAPSHOT_DIRECTORY/$snapshotId",
                contentHash = manifestDigest,
                capturedAt = now.toEpochMilli(),
                retentionClass = "STANDARD",
            )
            database.saveDao().saveSnapshotWithFiles(snapshotEntity, manifestFiles)
            database.saveDao().upsertProfile(
                profileEntity.copy(saveState = DbSaveState.SNAPSHOTTED, updatedAt = now.toEpochMilli()),
            )
            SnapshotResult(
                state = SnapshotResultState.CREATED,
                snapshot = snapshotEntity.toDomain(profile, manifestFiles),
                detail = "Snapshot committed locally with ${manifestFiles.size} file(s).",
            )
        } catch (failure: Throwable) {
            temporary.deleteRecursively()
            committed.deleteRecursively()
            database.saveDao().upsertProfile(
                profileEntity.copy(saveState = DbSaveState.ERROR, updatedAt = Instant.now().toEpochMilli()),
            )
            SnapshotResult(SnapshotResultState.ERROR, detail = "Snapshot failed (${failure::class.simpleName}). Live saves were not changed.")
        }
    }

    override suspend fun restore(snapshotId: SaveSnapshotId): RestoreResult = withContext(ioDispatcher) {
        val snapshot = database.saveDao().getSnapshot(snapshotId.value)
            ?: return@withContext RestoreResult(RestoreResultState.UNAVAILABLE, "Snapshot not found.")
        val profileEntity = snapshot.saveProfileId?.let { database.saveDao().getProfile(it) }
            ?: return@withContext RestoreResult(RestoreResultState.UNAVAILABLE, "Snapshot profile is unavailable.")
        if (database.sessionDao().countOpenForSaveProfile(profileEntity.id) > 0) {
            return@withContext RestoreResult(RestoreResultState.REJECTED, "Restore is blocked while a session is active.")
        }
        if (profileEntity.saveType == DbSaveType.SAVE_STATE && profileEntity.compatibilityFingerprint == null) {
            return@withContext RestoreResult(
                RestoreResultState.REJECTED,
                "Save-state restore requires an exact compatibility fingerprint.",
            )
        }
        val profile = profileEntity.toDomain()
            ?: return@withContext RestoreResult(RestoreResultState.UNAVAILABLE, "The save profile is incomplete.")
        val root = openRoot(profile, write = true)
            ?: return@withContext RestoreResult(RestoreResultState.UNAVAILABLE, "Writable SAF access is unavailable.")
        val backup = snapshotInternal(profile, SnapshotReason.PRE_RESTORE, sessionId = null)
        if (backup.state != SnapshotResultState.CREATED) {
            return@withContext RestoreResult(
                RestoreResultState.REJECTED,
                "Pre-restore backup failed; the live save was not changed.",
            )
        }

        val snapshotDirectory = snapshot.storageToken?.let { File(context.filesDir, it) }
            ?: return@withContext RestoreResult(RestoreResultState.ERROR, "Snapshot storage token is missing.")
        SnapshotPathPolicy.requireInside(File(context.filesDir, SNAPSHOT_DIRECTORY), snapshotDirectory)
        val files = database.saveDao().getFiles(snapshot.id)
        try {
            files.forEach { manifestFile ->
                SnapshotPathPolicy.validateRelativePath(manifestFile.relativePath)
                val source = File(snapshotDirectory, manifestFile.relativePath)
                SnapshotPathPolicy.requireInside(snapshotDirectory, source)
                check(source.isFile) { "Snapshot file is missing" }
                check("sha256:${source.sha256()}" == manifestFile.contentHash) { "Snapshot hash verification failed" }
                val destination = findOrCreate(root, manifestFile.relativePath)
                val output = context.contentResolver.openOutputStream(destination.uri, "wt")
                    ?: error("The live save destination could not be opened")
                source.inputStream().use { input -> output.use { input.copyTo(it) } }
                val restoredHash = context.contentResolver.openInputStream(destination.uri)?.use { it.sha256() }
                    ?: error("The restored file could not be verified")
                check("sha256:$restoredHash" == manifestFile.contentHash) { "Post-restore hash verification failed" }
            }
            RestoreResult(
                state = RestoreResultState.RESTORED,
                detail = "Snapshot restored after a local pre-restore backup. Unlisted live files were preserved.",
                preRestoreSnapshotId = backup.snapshot?.id,
            )
        } catch (failure: Throwable) {
            RestoreResult(
                RestoreResultState.ERROR,
                "Restore failed (${failure::class.simpleName}). The pre-restore backup was preserved.",
                backup.snapshot?.id,
            )
        }
    }

    private fun openRoot(profile: SaveProfile, write: Boolean): DocumentFile? {
        val locator = profile.location.reference
        val uri = runCatching { locator.toUri() }.getOrNull() ?: return null
        if (!hasPersistedGrant(uri, write)) return null
        return DocumentFile.fromTreeUri(context, uri)?.takeIf {
            it.exists() && it.isDirectory && it.canRead() && (!write || it.canWrite())
        }
    }

    private fun hasPersistedGrant(uri: Uri, write: Boolean): Boolean =
        context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission && (!write || permission.isWritePermission)
        }

    private fun findOrCreate(root: DocumentFile, relativePath: String): DocumentFile {
        val segments = SnapshotPathPolicy.validateRelativePath(relativePath)
        var directory = root
        segments.dropLast(1).forEach { segment ->
            directory = directory.findFile(segment)?.takeIf(DocumentFile::isDirectory)
                ?: directory.createDirectory(segment)
                ?: error("A restore directory could not be created")
        }
        return directory.findFile(segments.last())?.takeIf(DocumentFile::isFile)
            ?: directory.createFile("application/octet-stream", segments.last())
            ?: error("A restore file could not be created")
    }

    private fun writeManifest(
        directory: File,
        snapshotId: String,
        profileId: String,
        reason: SnapshotReason,
        manifestDigest: String,
        files: List<SaveSnapshotFileEntity>,
    ) {
        val entries = JSONArray()
        files.sortedBy { it.relativePath }.forEach { file ->
            entries.put(
                JSONObject()
                    .put("relativePath", file.relativePath)
                    .put("sizeBytes", file.sizeBytes)
                    .put("sha256", file.contentHash),
            )
        }
        val manifest = JSONObject()
            .put("schemaVersion", MANIFEST_VERSION)
            .put("snapshotId", snapshotId)
            .put("profileId", profileId)
            .put("reason", reason.name)
            .put("manifestDigest", manifestDigest)
            .put("files", entries)
            .toString(2)
        FileOutputStream(File(directory, "manifest.json")).use { output ->
            output.write(manifest.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
    }

    private fun SaveProfileEntity.toDomain(): SaveProfile? {
        val versionId = gameVersionId ?: return null
        val installation = installationId ?: return null
        val locator = rootLocator ?: return null
        return SaveProfile(
            id = SaveProfileId.of(id),
            gameVersionId = GameVersionId.of(versionId),
            installationId = InstallationId.of(installation),
            adapterId = SaveAdapterId.of(adapterId ?: ADAPTER_ID),
            location = SaveLocation.GrantedRoot(locator),
            includeRules = listOf("**"),
            canonicalType = saveType.toCanonicalType(),
            compatibilityFingerprint = compatibilityFingerprint?.let(ContentHash::of),
            state = when (saveState) {
                DbSaveState.UNAVAILABLE -> SaveProfileState.UNAVAILABLE
                DbSaveState.MANAGED_EXTERNALLY -> SaveProfileState.MANAGED_EXTERNALLY
                DbSaveState.ERROR -> SaveProfileState.ERROR
                DbSaveState.UNCONFIGURED -> SaveProfileState.UNCONFIGURED
                else -> SaveProfileState.AVAILABLE
            },
            createdAt = Instant.ofEpochMilli(createdAt),
            updatedAt = Instant.ofEpochMilli(updatedAt),
        )
    }

    private fun SaveSnapshotEntity.toDomain(
        profile: SaveProfile,
        files: List<SaveSnapshotFileEntity>,
    ): SaveSnapshot = SaveSnapshot(
        id = SaveSnapshotId.of(id),
        profileId = profile.id,
        deviceId = DeviceId.of(deviceId ?: error("Snapshot device is missing")),
        sessionId = sessionId?.let(GameSessionId::of),
        parentSnapshotId = parentSnapshotId?.let(SaveSnapshotId::of),
        files = files.map { file ->
            SaveSnapshotFile(
                relativePath = file.relativePath,
                sizeBytes = file.sizeBytes,
                hash = ContentHash.of(file.contentHash),
                sourceModifiedAt = file.sourceModifiedAt?.let(Instant::ofEpochMilli),
            )
        },
        manifestDigest = contentHash?.let(ContentHash::of),
        storageReference = storageToken ?: error("Snapshot storage token is missing"),
        reason = when (captureReason) {
            DbSnapshotReason.PRELAUNCH -> SnapshotReason.PRELAUNCH
            DbSnapshotReason.END_SESSION -> SnapshotReason.END_SESSION
            DbSnapshotReason.MANUAL -> SnapshotReason.MANUAL
            DbSnapshotReason.IMPORT -> SnapshotReason.IMPORT
            DbSnapshotReason.PRE_RESTORE -> SnapshotReason.PRE_RESTORE
            DbSnapshotReason.UNKNOWN -> SnapshotReason.MANUAL
        },
        state = SnapshotState.COMPLETE,
        compatibilityFingerprint = compatibilityFingerprint?.let(ContentHash::of),
        capturedAt = Instant.ofEpochMilli(capturedAt),
    )

    private fun SnapshotReason.toDatabaseReason(): DbSnapshotReason = when (this) {
        SnapshotReason.PRELAUNCH -> DbSnapshotReason.PRELAUNCH
        SnapshotReason.END_SESSION -> DbSnapshotReason.END_SESSION
        SnapshotReason.MANUAL -> DbSnapshotReason.MANUAL
        SnapshotReason.IMPORT -> DbSnapshotReason.IMPORT
        SnapshotReason.PRE_RESTORE -> DbSnapshotReason.PRE_RESTORE
    }

    private fun DbSaveType.toCanonicalType(): SaveCanonicalType = when (this) {
        DbSaveType.NORMAL_SAVE -> SaveCanonicalType.NORMAL_SAVE
        DbSaveType.MEMORY_CARD -> SaveCanonicalType.MEMORY_CARD
        DbSaveType.SAVE_STATE -> SaveCanonicalType.SAVE_STATE
        DbSaveType.CONFIG -> SaveCanonicalType.CONFIG
        DbSaveType.UNKNOWN -> SaveCanonicalType.UNKNOWN
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun File.sha256(): String = inputStream().use { it.sha256() }

    private fun java.io.InputStream.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().toHex()
    }

    private data class FileSignature(val path: String, val size: Long, val modifiedAt: Long)

    private companion object {
        const val ADAPTER_ID = "local-saf-snapshot"
        const val SNAPSHOT_DIRECTORY = "save_snapshots"
        const val MANIFEST_VERSION = 1
    }
}

internal object SnapshotPathPolicy {
    data class SourceDocument(val relativePath: String, val document: DocumentFile)

    fun collect(root: DocumentFile): List<SourceDocument> {
        val result = mutableListOf<SourceDocument>()
        fun visit(directory: DocumentFile, prefix: String, depth: Int) {
            require(depth <= 32) { "Save directory nesting exceeds the safety limit" }
            directory.listFiles().sortedBy { it.name.orEmpty() }.forEach { child ->
                val name = child.name ?: error("A save document has no name")
                validateSegment(name)
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                if (child.isDirectory) {
                    visit(child, path, depth + 1)
                } else if (child.isFile) {
                    result += SourceDocument(path, child)
                    require(result.size <= 10_000) { "Save tree exceeds the 10,000-file safety limit" }
                }
            }
        }
        visit(root, "", 0)
        return result.sortedBy(SourceDocument::relativePath)
    }

    fun validateRelativePath(path: String): List<String> {
        require(path.isNotBlank() && !path.startsWith('/')) { "Snapshot paths must be relative" }
        val segments = path.split('/')
        require(segments.isNotEmpty())
        segments.forEach(::validateSegment)
        return segments
    }

    fun requireInside(parent: File, child: File) {
        val parentPath = parent.canonicalFile.toPath()
        val childPath = child.canonicalFile.toPath()
        require(childPath.startsWith(parentPath) && childPath != parentPath) { "Snapshot path escaped its root" }
    }

    fun manifestDigest(files: List<SaveSnapshotFileEntity>): String {
        val canonical = files.sortedBy { it.relativePath }.joinToString("\n") { file ->
            "${file.relativePath.length}:${file.relativePath}:${file.sizeBytes}:${file.contentHash}"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return "sha256:${digest.joinToString("") { "%02x".format(it) }}"
    }

    private fun validateSegment(segment: String) {
        require(segment.isNotBlank() && segment != "." && segment != "..") { "Unsafe snapshot path segment" }
        require('/' !in segment && '\\' !in segment && '\u0000' !in segment) { "Unsafe snapshot path segment" }
    }
}
