package dev.thorhub.core.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import dev.thorhub.core.database.ContentFingerprintEntity
import dev.thorhub.core.database.DeviceEntity
import dev.thorhub.core.database.DeviceAvailabilityState
import dev.thorhub.core.database.GameEntity
import dev.thorhub.core.database.GameLifecycleStatus
import dev.thorhub.core.database.GameVersionClassification
import dev.thorhub.core.database.GameVersionEntity
import dev.thorhub.core.database.InstallationAvailability as DbInstallationAvailability
import dev.thorhub.core.database.InstallationEntity
import dev.thorhub.core.database.InstallationSourceType as DbInstallationSourceType
import dev.thorhub.core.database.LibraryImport
import dev.thorhub.core.database.LibraryRootEntity
import dev.thorhub.core.database.LibraryRootState
import dev.thorhub.core.database.LibraryRootSourceType
import dev.thorhub.core.database.ScanReconciliation
import dev.thorhub.core.database.ThorHubDatabase
import dev.thorhub.core.model.DiscoveredItem
import dev.thorhub.core.model.Game
import dev.thorhub.core.model.GameLifecycle
import dev.thorhub.core.model.GameVersion
import dev.thorhub.core.model.Installation
import dev.thorhub.core.model.InstallationAvailability
import dev.thorhub.core.model.InstallationReference
import dev.thorhub.core.model.InstallationSourceType
import dev.thorhub.core.model.LibraryRootId
import dev.thorhub.core.model.LibrarySource
import dev.thorhub.core.model.ResolutionResult
import dev.thorhub.core.model.ResolutionState
import dev.thorhub.core.model.ScanEvent
import dev.thorhub.core.model.ScanRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Instant

data class LibraryImportSummary(
    val importedInstallationIds: List<String>,
    val reconciled: Boolean,
    val rootId: String?,
)

/**
 * Centralizes the fail-closed rule: a failed or revoked scan must never turn
 * an unavailable provider into a mass missing-item reconciliation.
 */
object ScanReconciliationPolicy {
    fun shouldReconcile(completed: Boolean, failureCode: String?): Boolean =
        completed && failureCode == null

    fun rootStateForFailure(code: String): LibraryRootState = when {
        code == "SAF_PERMISSION_REVOKED" || code == "SAF_NEEDS_SETUP" -> LibraryRootState.REVOKED
        code == "SAF_ROOT_UNAVAILABLE" -> LibraryRootState.STALE
        else -> LibraryRootState.ERROR
    }
}

/** A discovery is not proof that an installation can currently be opened. */
object ResolutionImportPolicy {
    fun availabilityFor(result: ResolutionResult): DbInstallationAvailability {
        if (result.state != ResolutionState.RESOLVED) {
            return DbInstallationAvailability.UNKNOWN
        }
        val installation = result.installation ?: return DbInstallationAvailability.UNKNOWN
        return when (installation.availability) {
            InstallationAvailability.AVAILABLE -> DbInstallationAvailability.AVAILABLE
            InstallationAvailability.ERROR -> DbInstallationAvailability.ERROR
            InstallationAvailability.MISSING,
            InstallationAvailability.UNAVAILABLE,
            InstallationAvailability.REVOKED -> DbInstallationAvailability.UNAVAILABLE

            InstallationAvailability.UNKNOWN -> DbInstallationAvailability.UNKNOWN
        }
    }
}

/**
 * Thin data facade over the already transactional LibraryDao API. It owns no
 * UI state and keeps scanning/import work on an injected background dispatcher.
 */
class LibraryRepository(
    private val database: ThorHubDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Instant = { Instant.now() },
) {
    suspend fun persistSelectedRoot(root: LibraryRootEntity) = withContext(ioDispatcher) {
        requireValidTreeUri(root.treeUri)
        database.libraryRootDao().upsert(root)
    }

    /**
     * Scans and then imports one source in one LibraryDao transaction. Events
     * remain incremental for the caller; the transaction is deliberately
     * deferred until a successful Completed event has been observed.
     */
    fun scanAndImport(
        source: LibrarySource,
        root: LibraryRootEntity? = null,
        device: DeviceEntity? = null,
        request: ScanRequest = ScanRequest(
            rootId = root?.let { LibraryRootId.of(it.id) },
        ),
    ): Flow<ScanEvent> = flow {
        val effectiveRequest = request.copy(
            rootId = request.rootId ?: root?.let { LibraryRootId.of(it.id) },
        )
        val discovered = LinkedHashMap<String, DiscoveredItem>()
        var completed = false
        var failureCode: String? = null
        try {
            source.scan(effectiveRequest).collect { event ->
                emit(event)
                when (event) {
                    is ScanEvent.ItemDiscovered ->
                        discovered[LibraryRecordIds.itemKey(event.item)] = event.item

                    is ScanEvent.Completed -> completed = true
                    is ScanEvent.Failed -> failureCode = event.code
                    else -> Unit
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            failureCode = "SCAN_PIPELINE_FAILED"
            emit(
                ScanEvent.Failed(
                    code = failureCode!!,
                    detail = "Library scan pipeline failed (${failure::class.simpleName ?: "unknown error"}).",
                    emittedAt = clock(),
                ),
            )
        }

        if (!ScanReconciliationPolicy.shouldReconcile(completed, failureCode)) {
            if (root != null && failureCode != null) {
                withContext(ioDispatcher) {
                    database.libraryRootDao().setState(
                        rootId = root.id,
                        state = ScanReconciliationPolicy.rootStateForFailure(failureCode!!),
                        updatedAt = clock().toEpochMilli(),
                    )
                }
            }
            return@flow
        }

        if (discovered.keys.any { key -> key.isBlank() }) {
            emit(ScanEvent.Failed("INVALID_DISCOVERY", "A discovered item had no stable identity", clock()))
            return@flow
        }
        if (discovered.values.any { it.rootId != null } && root == null) {
            emit(
                ScanEvent.Failed(
                    code = "ROOT_REQUIRED",
                    detail = "A rooted source scan requires its selected LibraryRootEntity.",
                    emittedAt = clock(),
                ),
            )
            return@flow
        }
        if (root != null && root.deviceId != null && device?.id != root.deviceId) {
            emit(
                ScanEvent.Failed(
                    code = "DEVICE_REQUIRED",
                    detail = "The selected root references a device that was not supplied to the import.",
                    emittedAt = clock(),
                ),
            )
            return@flow
        }

        runCatching {
            importDiscoveredInternal(
                source = source,
                items = discovered.values.toList(),
                root = root,
                device = device,
                at = clock(),
            )
        }.getOrElse { failure ->
            emit(
                ScanEvent.Failed(
                    code = "IMPORT_FAILED",
                    detail = "Library import failed (${failure::class.simpleName ?: "database error"}).",
                    emittedAt = clock(),
                ),
            )
            return@flow
        }
        // The source already emitted Completed; no synthetic success event is
        // added here, keeping event ordering source-owned and deterministic.
        Unit
    }.flowOn(ioDispatcher)

    suspend fun importDiscovered(
        source: LibrarySource,
        items: List<DiscoveredItem>,
        root: LibraryRootEntity? = null,
        device: DeviceEntity? = null,
        at: Instant = clock(),
    ): LibraryImportSummary = withContext(ioDispatcher) {
        require(items.none { it.rootId != null } || root != null) {
            "A rooted source import requires its selected LibraryRootEntity"
        }
        require(root == null || root.deviceId == null || root.deviceId == device?.id) {
            "The selected root references a device that was not supplied to the import"
        }
        importDiscoveredInternal(source, items, root, device, at)
    }

    suspend fun importFixtureCatalog(
        catalog: DeterministicFixtureCatalog,
        root: LibraryRootEntity,
        device: DeviceEntity? = null,
        at: Instant = clock(),
    ): LibraryImportSummary = withContext(ioDispatcher) {
        importDiscovered(
            source = DeterministicFixtureLibrarySource(
                catalog = catalog,
                deviceId = device?.id?.let(dev.thorhub.core.model.DeviceId::of),
                scanDispatcher = ioDispatcher,
                clock = { at },
            ),
            items = catalog.items,
            root = root,
            device = device,
            at = at,
        )
    }

    private suspend fun importDiscoveredInternal(
        source: LibrarySource,
        items: List<DiscoveredItem>,
        root: LibraryRootEntity?,
        device: DeviceEntity?,
        at: Instant,
    ): LibraryImportSummary {
        val resolutions = items.map { item ->
            item to source.resolve(item)
        }
        val rootedItems = items.filter { it.rootId != null }
        val rootId = root?.id ?: rootedItems.firstOrNull()?.rootId?.value
        if (root != null && rootedItems.any { it.rootId?.value != root.id }) {
            error("Discovered item belongs to a different LibraryRootEntity")
        }
        val import = toLibraryImport(resolutions, root, device, at)
        val seenInstallationIds = resolutions.map { (item, result) ->
            result.installation?.id?.value ?: LibraryRecordIds.installation(item).value
        }.distinct()
        val reconciliation = rootId?.let {
            ScanReconciliation(
                libraryRootId = it,
                seenInstallationIds = seenInstallationIds,
                reconciledAt = at.toEpochMilli(),
                unavailableReason = "MISSING_FROM_SCAN",
            )
        }
        database.libraryDao().importLibrary(import, reconciliation)
        return LibraryImportSummary(
            importedInstallationIds = seenInstallationIds,
            reconciled = reconciliation != null,
            rootId = rootId,
        )
    }

    private fun toLibraryImport(
        resolutions: List<Pair<DiscoveredItem, ResolutionResult>>,
        root: LibraryRootEntity?,
        device: DeviceEntity?,
        at: Instant,
    ): LibraryImport {
        val games = LinkedHashMap<String, GameEntity>()
        val versions = LinkedHashMap<String, GameVersionEntity>()
        val fingerprints = LinkedHashMap<String, ContentFingerprintEntity>()
        val installations = LinkedHashMap<String, InstallationEntity>()
        val rootedDevice = root?.deviceId
        resolutions.forEach { (item, result) ->
            val game = result.game ?: fallbackGame(item, at)
            val version = result.version ?: fallbackVersion(item, game, at)
            val domainInstallation = result.installation
            val installationId = domainInstallation?.id?.value ?: LibraryRecordIds.installation(item).value
            val rootId = item.rootId?.value ?: root?.id
            val importedAvailability = ResolutionImportPolicy.availabilityFor(result)
            val domainFingerprint = domainInstallation?.contentFingerprint
            val fingerprintId = domainFingerprint?.let { fingerprint ->
                val id = LibraryRecordIds.fingerprint(
                    identity = fingerprint.identity,
                    algorithm = fingerprint.algorithm.name,
                    value = fingerprint.hash.value,
                )
                fingerprints[id] = fingerprint.toEntity(id, at)
                id
            }
            games[game.id.value] = game.toEntity(at, filenameFallback = result.game == null)
            versions[version.id.value] = version.toEntity(at)
            installations[installationId] = InstallationEntity(
                id = installationId,
                gameVersionId = version.id.value,
                deviceId = rootedDevice ?: domainInstallation?.deviceId?.value?.takeIf { device?.id == it },
                libraryRootId = rootId,
                sourceType = item.sourceType.toDatabaseSourceType(),
                sourceLocator = item.reference.value,
                contentFingerprintId = fingerprintId,
                availabilityState = importedAvailability,
                discoveredAt = domainInstallation?.discoveredAt?.toEpochMilli() ?: at.toEpochMilli(),
                lastSeenAt = domainInstallation?.lastSeenAt?.toEpochMilli()
                    ?.takeIf { importedAvailability == DbInstallationAvailability.AVAILABLE },
                missingSince = if (importedAvailability == DbInstallationAvailability.UNAVAILABLE) {
                    at.toEpochMilli()
                } else {
                    null
                },
                unavailableReason = domainInstallation?.unavailableReason
                    ?: result.detail?.takeIf { result.state != ResolutionState.RESOLVED },
                updatedAt = at.toEpochMilli(),
            )
        }
        val activeRoot = root?.copy(
            rootState = LibraryRootState.ACTIVE,
            lastSuccessfulScanAt = at.toEpochMilli(),
            updatedAt = at.toEpochMilli(),
        )
        return LibraryImport(
            games = games.values.toList(),
            versions = versions.values.toList(),
            devices = device?.let(::listOf).orEmpty(),
            roots = activeRoot?.let(::listOf).orEmpty(),
            fingerprints = fingerprints.values.toList(),
            installations = installations.values.toList(),
        )
    }

    private fun fallbackGame(item: DiscoveredItem, at: Instant): Game = Game(
        id = LibraryRecordIds.game(item),
        title = item.titleHint?.takeIf(String::isNotBlank)
            ?: ContentNamePolicy.normalize(item.identity?.normalizedName),
        createdAt = at,
        updatedAt = at,
    )

    private fun fallbackVersion(item: DiscoveredItem, game: Game, at: Instant): GameVersion = GameVersion(
        id = LibraryRecordIds.version(item),
        gameId = game.id,
        platform = item.platformHint,
        createdAt = at,
        updatedAt = at,
    )

    private fun Game.toEntity(at: Instant, filenameFallback: Boolean): GameEntity = GameEntity(
        id = id.value,
        canonicalTitle = title,
        sortTitle = sortTitle,
        lifecycleStatus = lifecycle.toDatabaseLifecycle(),
        description = description,
        releaseYear = releaseYear,
        isFavorite = favorite,
        isHidden = hidden,
        isPinned = false,
        userRating = userRating,
        metadataProvenanceJson = if (filenameFallback) {
            "{\"title\":\"FILENAME_FALLBACK\"}"
        } else {
            "{\"title\":\"UNKNOWN\"}"
        },
        createdAt = createdAt.toEpochMilli(),
        updatedAt = at.toEpochMilli(),
    )

    private fun GameVersion.toEntity(at: Instant): GameVersionEntity = GameVersionEntity(
        id = id.value,
        gameId = gameId.value,
        platform = platform.name,
        region = region,
        language = language,
        edition = edition,
        versionClassification = kind.toDatabaseClassification(),
        externalId = externalIdentifiers.values.firstOrNull(),
        isPreferred = false,
        createdAt = createdAt.toEpochMilli(),
        updatedAt = at.toEpochMilli(),
    )

    private fun dev.thorhub.core.model.ContentFingerprint.toEntity(
        id: String,
        at: Instant,
    ): ContentFingerprintEntity = ContentFingerprintEntity(
        id = id,
        algorithm = algorithm.name,
        value = hash.value,
        identityKey = identity.sourceKey,
        sizeBytes = identity.sizeBytes,
        modifiedAt = identity.modifiedAt?.toEpochMilli(),
        createdAt = computedAt.toEpochMilli().takeIf { it != 0L } ?: at.toEpochMilli(),
    )

    private fun GameLifecycle.toDatabaseLifecycle(): GameLifecycleStatus = when (this) {
        GameLifecycle.UNKNOWN -> GameLifecycleStatus.UNKNOWN
        GameLifecycle.BACKLOG -> GameLifecycleStatus.BACKLOG
        GameLifecycle.PLAYING -> GameLifecycleStatus.PLAYING
        GameLifecycle.COMPLETED -> GameLifecycleStatus.COMPLETED
        GameLifecycle.ABANDONED -> GameLifecycleStatus.ABANDONED
    }

    private fun dev.thorhub.core.model.GameVersionKind.toDatabaseClassification(): GameVersionClassification =
        when (this) {
            dev.thorhub.core.model.GameVersionKind.ORIGINAL -> GameVersionClassification.ORIGINAL
            dev.thorhub.core.model.GameVersionKind.HACK -> GameVersionClassification.HACK
            dev.thorhub.core.model.GameVersionKind.FAN_TRANSLATION -> GameVersionClassification.FAN_TRANSLATION
            dev.thorhub.core.model.GameVersionKind.REMASTER -> GameVersionClassification.REMASTER
            dev.thorhub.core.model.GameVersionKind.COLLECTION -> GameVersionClassification.OTHER
            dev.thorhub.core.model.GameVersionKind.DEMO -> GameVersionClassification.OTHER
            dev.thorhub.core.model.GameVersionKind.UNKNOWN -> GameVersionClassification.UNKNOWN
        }

    private fun InstallationSourceType.toDatabaseSourceType(): DbInstallationSourceType = when (this) {
        InstallationSourceType.ROM -> DbInstallationSourceType.ROM
        InstallationSourceType.ANDROID_APP -> DbInstallationSourceType.ANDROID
        InstallationSourceType.GAME_NATIVE -> DbInstallationSourceType.GAMENATIVE
        InstallationSourceType.STEAM -> DbInstallationSourceType.STEAM
        InstallationSourceType.STREAM -> DbInstallationSourceType.STREAM
        InstallationSourceType.MANUAL,
        InstallationSourceType.IMPORTED,
        InstallationSourceType.UNKNOWN,
        -> DbInstallationSourceType.MANUAL
    }

    private fun requireValidTreeUri(value: String) {
        val uri = value.toUri()
        require(uri.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(uri)) {
            "Library root must use a persisted SAF tree URI"
        }
    }
}
