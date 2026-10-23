package dev.thorhub.core.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dev.thorhub.core.model.CheapContentIdentity
import dev.thorhub.core.model.ContentFingerprint
import dev.thorhub.core.model.DeviceId
import dev.thorhub.core.model.DiscoveredItem
import dev.thorhub.core.model.Game
import dev.thorhub.core.model.GameLifecycle
import dev.thorhub.core.model.GamePlatform
import dev.thorhub.core.model.GameVersion
import dev.thorhub.core.model.GameVersionKind
import dev.thorhub.core.model.Installation
import dev.thorhub.core.model.InstallationAvailability
import dev.thorhub.core.model.InstallationReference
import dev.thorhub.core.model.InstallationSourceType
import dev.thorhub.core.model.LibraryRoot
import dev.thorhub.core.model.LibraryRootAccessState
import dev.thorhub.core.model.LibraryRootId
import dev.thorhub.core.model.LibrarySource
import dev.thorhub.core.model.LibrarySourceId
import dev.thorhub.core.model.ResolutionResult
import dev.thorhub.core.model.ResolutionState
import dev.thorhub.core.model.ScanEvent
import dev.thorhub.core.model.ScanRequest
import dev.thorhub.core.model.SourceAvailability
import dev.thorhub.core.model.SourceAvailabilityState
import dev.thorhub.core.database.ThorHubDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Instant

/** A provider-neutral view of a SAF document, used for deterministic tests. */
interface SafDocument {
    val uri: Uri
    val name: String?
    val lengthBytes: Long
    val lastModifiedMillis: Long?
    val isDirectory: Boolean
    val exists: Boolean

    fun listFiles(): List<SafDocument>
}

interface SafDocumentProvider {
    fun fromTreeUri(treeUri: Uri): SafDocument?
}

/** Production SAF bridge: DocumentFile performs provider enumeration, resolver performs content I/O. */
class DocumentFileSafDocumentProvider(
    context: Context,
) : SafDocumentProvider {
    private val applicationContext = context.applicationContext

    override fun fromTreeUri(treeUri: Uri): SafDocument? =
        DocumentFile.fromTreeUri(applicationContext, treeUri)?.let(::DocumentFileSafDocument)
}

private class DocumentFileSafDocument(
    private val document: DocumentFile,
) : SafDocument {
    override val uri: Uri
        get() = document.uri
    override val name: String?
        get() = document.name
    override val lengthBytes: Long
        get() = document.length().coerceAtLeast(0L)
    override val lastModifiedMillis: Long?
        get() = document.lastModified().takeIf { it > 0L }
    override val isDirectory: Boolean
        get() = document.isDirectory
    override val exists: Boolean
        get() = document.exists()

    override fun listFiles(): List<SafDocument> = document.listFiles()
        .asSequence()
        .map(::DocumentFileSafDocument)
        .toList()
}

/** Helpers used by the activity result handler when a tree is selected. */
object SafUriGrants {
    fun persistReadGrant(contentResolver: ContentResolver, treeUri: Uri): Int {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        contentResolver.takePersistableUriPermission(treeUri, flags)
        return flags
    }

    fun persistReadWriteGrant(contentResolver: ContentResolver, treeUri: Uri): Int {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(treeUri, flags)
        return flags
    }

    fun hasReadGrant(contentResolver: ContentResolver, treeUri: Uri): Boolean = runCatching {
        contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == treeUri && permission.isReadPermission
        }
    }.getOrDefault(false)
}

/**
 * SAF ROM source. It only accepts a persisted content tree URI and never turns
 * a document into a private filesystem path.
 */
class SafRomLibrarySource(
    private val contentResolver: ContentResolver,
    private val documentProvider: SafDocumentProvider,
    private val root: LibraryRoot,
    private val deviceId: DeviceId? = null,
    private val fingerprintCache: FingerprintCache = InMemoryFingerprintCache(),
    private val scanDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Instant = { Instant.now() },
    private val hasReadGrant: (ContentResolver, Uri) -> Boolean = SafUriGrants::hasReadGrant,
) : LibrarySource {
    constructor(
        context: Context,
        root: LibraryRoot,
        deviceId: DeviceId? = null,
        fingerprintCache: FingerprintCache = InMemoryFingerprintCache(),
        scanDispatcher: CoroutineDispatcher = Dispatchers.IO,
        clock: () -> Instant = { Instant.now() },
    ) : this(
        contentResolver = context.contentResolver,
        documentProvider = DocumentFileSafDocumentProvider(context),
        root = root,
        deviceId = deviceId,
        fingerprintCache = fingerprintCache,
        scanDispatcher = scanDispatcher,
        clock = clock,
    )

    constructor(
        context: Context,
        root: LibraryRoot,
        database: ThorHubDatabase,
        deviceId: DeviceId? = null,
        scanDispatcher: CoroutineDispatcher = Dispatchers.IO,
        clock: () -> Instant = { Instant.now() },
    ) : this(
        contentResolver = context.contentResolver,
        documentProvider = DocumentFileSafDocumentProvider(context),
        root = root,
        deviceId = deviceId,
        fingerprintCache = DatabaseFingerprintCache(database, root.id.value),
        scanDispatcher = scanDispatcher,
        clock = clock,
    )

    override val id: LibrarySourceId = LibrarySourceId.of("saf-rom")

    override suspend fun availability(): SourceAvailability = withContext(scanDispatcher) {
        val treeUri = validatedTreeUri()
            ?: return@withContext SourceAvailability(
                state = SourceAvailabilityState.NEEDS_SETUP,
                detail = "Choose a ROM folder with ACTION_OPEN_DOCUMENT_TREE.",
                checkedAt = clock(),
            )
        if (root.accessState == LibraryRootAccessState.REVOKED) {
            return@withContext SourceAvailability(
                state = SourceAvailabilityState.NEEDS_SETUP,
                detail = "SAF permission was revoked; select this ROM folder again.",
                checkedAt = clock(),
            )
        }
        if (!hasReadGrant(contentResolver, treeUri)) {
            return@withContext SourceAvailability(
                state = SourceAvailabilityState.NEEDS_SETUP,
                detail = "SAF read permission is missing or revoked; select the ROM folder again.",
                checkedAt = clock(),
            )
        }
        try {
            val tree = documentProvider.fromTreeUri(treeUri)
            when {
                tree == null || !tree.exists -> SourceAvailability(
                    state = SourceAvailabilityState.UNAVAILABLE,
                    detail = "The selected SAF root is unavailable; choose it again if it moved.",
                    checkedAt = clock(),
                )

                !tree.isDirectory -> SourceAvailability(
                    state = SourceAvailabilityState.UNSUPPORTED,
                    detail = "A ROM library root must be a directory.",
                    checkedAt = clock(),
                )

                else -> SourceAvailability(SourceAvailabilityState.READY, checkedAt = clock())
            }
        } catch (security: SecurityException) {
            SourceAvailability(
                state = SourceAvailabilityState.NEEDS_SETUP,
                detail = "SAF read permission was revoked; select the ROM folder again.",
                checkedAt = clock(),
            )
        } catch (error: Throwable) {
            SourceAvailability(
                state = SourceAvailabilityState.ERROR,
                detail = "SAF root could not be opened (${error::class.simpleName ?: "provider error"}).",
                checkedAt = clock(),
            )
        }
    }

    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        emit(ScanEvent.Started(clock()))
        if (request.rootId != null && request.rootId != root.id) {
            emit(
                ScanEvent.Failed(
                    code = "ROOT_MISMATCH",
                    detail = "The requested scan root does not match this source.",
                    emittedAt = clock(),
                ),
            )
            return@flow
        }

        val sourceAvailability = availability()
        if (sourceAvailability.state != SourceAvailabilityState.READY) {
            emit(
                ScanEvent.Failed(
                    code = failureCode(sourceAvailability),
                    detail = sourceAvailability.detail,
                    emittedAt = clock(),
                ),
            )
            return@flow
        }

        val treeUri = validatedTreeUri() ?: return@flow
        var itemCount = 0
        try {
            val tree = documentProvider.fromTreeUri(treeUri)
                ?: throw SafScanFailure("SAF root disappeared while scanning")
            itemCount = enumerate(
                document = tree,
                request = request,
                emit = { item ->
                    emit(ScanEvent.ItemDiscovered(item, clock()))
                    val resolved = resolveInternal(item, allowFullHash = request.allowFullHash)
                    if (resolved.state == ResolutionState.FAILED) {
                        throw SafScanFailure(resolved.detail ?: "Unable to resolve SAF item")
                    }
                    resolved.installation?.let { installation ->
                        emit(ScanEvent.ItemUpdated(installation, clock()))
                    }
                },
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (security: SecurityException) {
            throw SafScanFailure("SAF permission was revoked during enumeration")
        } catch (failure: SafScanFailure) {
            emit(
                ScanEvent.Failed(
                    code = if (failure.message?.contains("permission", ignoreCase = true) == true) {
                        "SAF_PERMISSION_REVOKED"
                    } else {
                        "SAF_SCAN_FAILED"
                    },
                    detail = failure.message,
                    emittedAt = clock(),
                ),
            )
            return@flow
        } catch (failure: Throwable) {
            emit(
                ScanEvent.Failed(
                    code = "SAF_SCAN_FAILED",
                    detail = "SAF enumeration failed (${failure::class.simpleName ?: "provider error"}).",
                    emittedAt = clock(),
                ),
            )
            return@flow
        }
        emit(ScanEvent.Completed(itemCount, clock()))
    }.flowOn(scanDispatcher)

    override suspend fun resolve(item: DiscoveredItem): ResolutionResult = withContext(scanDispatcher) {
        resolveInternal(item, allowFullHash = false)
    }

    private suspend fun resolveInternal(
        item: DiscoveredItem,
        allowFullHash: Boolean,
    ): ResolutionResult {
        if (item.rootId != root.id || item.sourceType != InstallationSourceType.ROM) {
            return ResolutionResult(ResolutionState.UNSUPPORTED, detail = "Item does not belong to this SAF ROM root")
        }
        val reference = item.reference as? InstallationReference.GrantedDocument
            ?: return ResolutionResult(ResolutionState.UNSUPPORTED, detail = "SAF item has no granted document reference")
        val uri = reference.value.toUri()
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return ResolutionResult(ResolutionState.FAILED, detail = "SAF item is not a content URI")
        }
        // Enumerated items already carry their provider identity. Reusing it
        // keeps a scan O(number of documents); only callers that construct an
        // item without identity need the defensive provider lookup.
        val document = if (item.identity == null) {
            documentProvider.fromTreeUri(validatedTreeUri() ?: uri)
                ?.find(uri)
                ?: return ResolutionResult(ResolutionState.FAILED, detail = "SAF document is no longer available")
        } else {
            null
        }
        val identity = item.identity ?: identityOf(document ?: error("Missing SAF document identity"))
        val fingerprint = if (deviceId == null) {
            null
        } else {
            runCatching {
                StagedFingerprintHelper(
                    contentResolver = contentResolver,
                    cache = fingerprintCache,
                    clock = clock,
                ).compute(
                    uri = uri,
                    identity = identity,
                    allowFullHash = allowFullHash,
                ).fingerprint
            }.getOrElse { error ->
                return ResolutionResult(
                    state = ResolutionState.FAILED,
                    detail = "Fingerprinting failed (${error::class.simpleName ?: "provider error"}).",
                )
            }
        }
        val title = item.titleHint?.takeIf(String::isNotBlank)
            ?: ContentNamePolicy.normalize(document?.name)
        val game = Game(
            id = LibraryRecordIds.game(item),
            title = title,
            sortTitle = title,
            lifecycle = GameLifecycle.UNKNOWN,
            createdAt = clock(),
            updatedAt = clock(),
        )
        val version = GameVersion(
            id = LibraryRecordIds.version(item),
            gameId = game.id,
            platform = item.platformHint,
            kind = GameVersionKind.ORIGINAL,
            createdAt = game.createdAt,
            updatedAt = game.updatedAt,
        )
        if (deviceId == null) {
            return ResolutionResult(
                state = ResolutionState.NEEDS_REVIEW,
                game = game,
                version = version,
                detail = "A local device identity is required before an installation can be fully resolved.",
            )
        }
        val installation = Installation(
            id = LibraryRecordIds.installation(item),
            gameVersionId = version.id,
            deviceId = deviceId,
            sourceType = InstallationSourceType.ROM,
            reference = reference,
            availability = InstallationAvailability.AVAILABLE,
            contentFingerprint = fingerprint,
            discoveredAt = game.createdAt,
            lastSeenAt = game.updatedAt,
        )
        return ResolutionResult(
            state = ResolutionState.RESOLVED,
            game = game,
            version = version,
            installation = installation,
        )
    }

    private suspend fun enumerate(
        document: SafDocument,
        request: ScanRequest,
        emit: suspend (DiscoveredItem) -> Unit,
    ): Int {
        currentCoroutineContext().ensureActive()
        if (!document.exists) throw SafScanFailure("SAF document is unavailable")
        if (document.isDirectory) {
            var count = 0
            document.listFiles()
                .sortedBy { it.uri.toString() }
                .forEach { child ->
                    count += enumerate(child, request, emit)
                }
            return count
        }
        if (!RomExtensionPolicy.accepts(document.name, root.platformHint, root.supportedExtensions)) return 0
        val item = DiscoveredItem(
            rootId = root.id,
            sourceType = InstallationSourceType.ROM,
            reference = InstallationReference.GrantedDocument(document.uri.toString()),
            identity = identityOf(document),
            titleHint = ContentNamePolicy.normalize(document.name),
            platformHint = root.platformHint,
        )
        emit(item)
        return 1
    }

    private fun identityOf(document: SafDocument): CheapContentIdentity = CheapContentIdentity(
        sourceKey = document.uri.toString(),
        rootId = root.id,
        sizeBytes = document.lengthBytes.coerceAtLeast(0L),
        modifiedAt = document.lastModifiedMillis?.takeIf { it > 0L }?.let(Instant::ofEpochMilli),
        extension = RomExtensionPolicy.extensionOf(document.name),
        normalizedName = ContentNamePolicy.normalize(document.name),
    )

    private fun validatedTreeUri(): Uri? {
        if (root.sourceType != InstallationSourceType.ROM) return null
        val reference = root.reference as? InstallationReference.GrantedDocument ?: return null
        val uri = reference.value.toUri()
        return uri.takeIf {
            it.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(it)
        }
    }

    private fun failureCode(availability: SourceAvailability): String = when {
        availability.detail?.contains("permission", ignoreCase = true) == true -> "SAF_PERMISSION_REVOKED"
        availability.state == SourceAvailabilityState.NEEDS_SETUP -> "SAF_NEEDS_SETUP"
        availability.state == SourceAvailabilityState.UNAVAILABLE -> "SAF_ROOT_UNAVAILABLE"
        else -> "SAF_SOURCE_UNAVAILABLE"
    }

    private fun SafDocument.find(targetUri: Uri): SafDocument? {
        if (uri == targetUri) return this.takeIf { exists }
        if (!isDirectory) return null
        return listFiles()
            .asSequence()
            .mapNotNull { child -> child.find(targetUri) }
            .firstOrNull()
    }

    private class SafScanFailure(message: String) : IllegalStateException(message)
}
