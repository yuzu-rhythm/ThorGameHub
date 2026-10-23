package dev.thorhub.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration
import java.time.Instant

enum class SourceAvailabilityState {
    READY,
    NEEDS_SETUP,
    UNAVAILABLE,
    UNSUPPORTED,
    UNKNOWN,
    ERROR,
}

data class SourceAvailability(
    val state: SourceAvailabilityState,
    val detail: String? = null,
    val checkedAt: Instant = Instant.EPOCH,
)

data class ScanRequest(
    val rootId: LibraryRootId? = null,
    val incremental: Boolean = true,
    val allowFullHash: Boolean = false,
    val requestedAt: Instant = Instant.EPOCH,
)

data class DiscoveredItem(
    val rootId: LibraryRootId?,
    val sourceType: InstallationSourceType,
    val reference: InstallationReference,
    val identity: CheapContentIdentity? = null,
    val titleHint: String? = null,
    val platformHint: GamePlatform = GamePlatform.UNKNOWN,
)

sealed interface ScanEvent {
    val emittedAt: Instant

    data class Started(
        override val emittedAt: Instant = Instant.EPOCH,
    ) : ScanEvent

    data class ItemDiscovered(
        val item: DiscoveredItem,
        override val emittedAt: Instant = Instant.EPOCH,
    ) : ScanEvent

    data class ItemUpdated(
        val installation: Installation,
        override val emittedAt: Instant = Instant.EPOCH,
    ) : ScanEvent

    data class ItemMarkedUnavailable(
        val installationId: InstallationId,
        val reason: InstallationAvailability,
        override val emittedAt: Instant = Instant.EPOCH,
    ) : ScanEvent

    data class Completed(
        val itemCount: Int,
        override val emittedAt: Instant = Instant.EPOCH,
    ) : ScanEvent

    data class Failed(
        val code: String,
        val detail: String? = null,
        override val emittedAt: Instant = Instant.EPOCH,
    ) : ScanEvent
}

enum class ResolutionState {
    RESOLVED,
    NEEDS_REVIEW,
    UNKNOWN,
    UNSUPPORTED,
    FAILED,
}

data class ResolutionResult(
    val state: ResolutionState,
    val game: Game? = null,
    val version: GameVersion? = null,
    val installation: Installation? = null,
    val candidates: List<MetadataCandidate> = emptyList(),
    val detail: String? = null,
)

interface DisplayCoordinator {
    val topology: StateFlow<DisplayTopology>

    suspend fun refresh(): DisplayTopology

    suspend fun launchCompanion(route: CompanionRoute): DisplayLaunchResult

    /** Builds/accepts a platform-independent launch request for the upper display. */
    suspend fun launchOnUpper(request: UpperLaunchRequest): DisplayLaunchResult

    suspend fun showHomeOnUpper(route: HomeRoute? = null): DisplayLaunchResult

    suspend fun runPlacementTest(): PlacementTestResult
}

interface LibrarySource {
    val id: LibrarySourceId

    suspend fun availability(): SourceAvailability

    fun scan(request: ScanRequest): Flow<ScanEvent>

    suspend fun resolve(item: DiscoveredItem): ResolutionResult
}

data class DeviceDescriptor(
    val device: Device,
    val runtimeVersion: String? = null,
    val buildFingerprint: BuildFingerprint? = device.buildFingerprint,
)

enum class ProbeState {
    READY,
    NEEDS_SETUP,
    UNSUPPORTED,
    UNKNOWN,
    ERROR,
}

data class AdapterProbeResult(
    val state: ProbeState,
    val resolvedApp: ExternalAppId? = null,
    val version: String? = null,
    val evidence: List<String> = emptyList(),
    val detail: String? = null,
)

data class LaunchIntentResult(
    val state: ProbeState,
    val request: ExternalLaunchTarget? = null,
    val resolvedComponent: String? = null,
    val evidence: List<String> = emptyList(),
    val detail: String? = null,
)

enum class LaunchExecutionState {
    STARTED,
    ACCEPTED,
    FAILED,
    UNSUPPORTED,
    UNKNOWN,
}

data class LaunchResult(
    val state: LaunchExecutionState,
    val methodId: PlayMethodId,
    val requestedDisplayId: DisplayId? = null,
    val resolvedComponent: String? = null,
    val detail: String? = null,
    val evidence: List<String> = emptyList(),
)

enum class ExternalExitRequestState {
    REQUESTED,
    NOT_REQUESTED,
    UNSUPPORTED,
    FAILED,
    UNKNOWN,
}

enum class ExternalProcessClosureState {
    VERIFIED_CLOSED,
    STILL_RUNNING,
    NOT_VERIFIED,
    UNSUPPORTED,
    UNKNOWN,
}

data class ExitRequestResult(
    val requestState: ExternalExitRequestState,
    val closureState: ExternalProcessClosureState = ExternalProcessClosureState.NOT_VERIFIED,
    val detail: String? = null,
)

data class AdapterDiagnostics(
    val adapterId: LauncherAdapterId,
    val values: Map<String, String> = emptyMap(),
    val redactionRequired: Boolean = true,
)

interface LauncherAdapter {
    val id: LauncherAdapterId

    suspend fun probe(device: DeviceDescriptor): AdapterProbeResult

    suspend fun validate(method: PlayMethod): LaunchValidation

    suspend fun buildLaunchIntent(method: PlayMethod): LaunchIntentResult

    suspend fun requestGracefulExit(session: GameSession): ExitRequestResult

    suspend fun collectDiagnostics(method: PlayMethod): AdapterDiagnostics
}

data class SaveCapability(
    val state: CapabilityStatus,
    val canonicalTypes: Set<SaveCanonicalType> = emptySet(),
    val detail: String? = null,
)

data class SaveManifestEntry(
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedAt: Instant? = null,
    val hash: ContentHash? = null,
)

data class SaveManifestResult(
    val state: SaveProfileState,
    val entries: List<SaveManifestEntry> = emptyList(),
    val detail: String? = null,
)

data class QuiescencePolicy(
    val sampleInterval: Duration = Duration.ofSeconds(2),
    val requiredStableSamples: Int = 2,
    val maxWait: Duration = Duration.ofSeconds(30),
) {
    init {
        require(!sampleInterval.isNegative && !sampleInterval.isZero) { "Sample interval must be positive" }
        require(requiredStableSamples > 0) { "Stable sample count must be positive" }
        require(!maxWait.isNegative && !maxWait.isZero) { "Maximum wait must be positive" }
    }
}

enum class QuiescenceState {
    QUIESCENT,
    CHANGED,
    TIMED_OUT,
    UNAVAILABLE,
    UNKNOWN,
}

data class QuiescenceResult(
    val state: QuiescenceState,
    val samples: Int = 0,
    val detail: String? = null,
)

enum class SnapshotResultState {
    CREATED,
    UNCHANGED,
    UNAVAILABLE,
    UNSUPPORTED,
    ERROR,
    UNKNOWN,
}

data class SnapshotResult(
    val state: SnapshotResultState,
    val snapshot: SaveSnapshot? = null,
    val detail: String? = null,
)

enum class RestoreResultState {
    RESTORED,
    REJECTED,
    UNAVAILABLE,
    UNSUPPORTED,
    ERROR,
    UNKNOWN,
}

data class RestoreResult(
    val state: RestoreResultState,
    val detail: String? = null,
    val preRestoreSnapshotId: SaveSnapshotId? = null,
)

interface SaveAdapter {
    val id: SaveAdapterId

    suspend fun probe(installation: Installation): SaveCapability

    suspend fun enumerate(profile: SaveProfile): SaveManifestResult

    suspend fun awaitQuiescence(profile: SaveProfile, policy: QuiescencePolicy): QuiescenceResult

    suspend fun snapshot(profile: SaveProfile, reason: SnapshotReason): SnapshotResult

    suspend fun restore(snapshotId: SaveSnapshotId): RestoreResult
}

enum class TransportAvailabilityState {
    READY,
    NEEDS_SETUP,
    UNAVAILABLE,
    UNSUPPORTED,
    UNKNOWN,
    ERROR,
}

data class TransportAvailability(
    val state: TransportAvailabilityState,
    val detail: String? = null,
    val checkedAt: Instant = Instant.EPOCH,
)

data class SyncScope(
    val profileId: SaveProfileId,
    val deviceId: DeviceId? = null,
)

data class SyncEvidence(
    val operationId: String,
    val observedAt: Instant,
    val transferredBytes: Long? = null,
    val remoteRevision: String? = null,
    val source: SyncTransportId? = null,
) {
    init {
        require(operationId.isNotBlank()) { "Sync operation ID must not be blank" }
        require(transferredBytes == null || transferredBytes >= 0L) { "Transferred bytes must not be negative" }
    }
}

data class SyncStatus(
    val state: SyncState,
    val evidence: SyncEvidence? = null,
    val detail: String? = null,
)

data class ChangeSet(
    val scope: SyncScope,
    val snapshotIds: List<SaveSnapshotId> = emptyList(),
    val noteIds: List<NoteId> = emptyList(),
    val sessionIds: List<GameSessionId> = emptyList(),
)

enum class SyncResultState {
    COMPLETED,
    NO_CHANGES,
    PENDING,
    CONFLICT,
    UNAVAILABLE,
    UNSUPPORTED,
    ERROR,
    UNKNOWN,
}

data class SyncResult(
    val state: SyncResultState,
    val status: SyncStatus,
    val detail: String? = null,
)

interface SyncTransport {
    val id: SyncTransportId

    suspend fun availability(): TransportAvailability

    suspend fun status(scope: SyncScope): SyncStatus

    suspend fun push(changeSet: ChangeSet): SyncResult

    suspend fun pull(scope: SyncScope): SyncResult
}

data class BeginSessionRequest(
    val gameId: GameId,
    val gameVersionId: GameVersionId,
    val installationId: InstallationId,
    val deviceId: DeviceId,
    val playMethodId: PlayMethodId,
    val wallClock: Instant,
    val monotonic: MonotonicMark,
)

enum class BeginSessionState {
    PENDING,
    REJECTED,
    ERROR,
}

data class BeginSessionResult(
    val state: BeginSessionState,
    val session: GameSession? = null,
    val detail: String? = null,
)

enum class SuspendReason {
    USER_REQUEST,
    SCREEN_OFF,
    LID_CLOSED,
    PROCESS_BACKGROUND,
    UNKNOWN,
}

enum class EndReason {
    USER_REQUEST,
    LAUNCH_FAILED,
    RECOVERY,
    UNKNOWN,
}

enum class SessionEndState {
    ENDED,
    ALREADY_ENDED,
    REJECTED,
    ERROR,
}

data class EndSessionResult(
    val state: SessionEndState,
    val session: GameSession,
    val externalExit: ExitRequestResult? = null,
    val save: SnapshotResult? = null,
    val sync: SyncResult? = null,
    val detail: String? = null,
)

data class SessionRecoveryResult(
    val recovered: List<GameSession>,
    val ambiguous: List<GameSession> = emptyList(),
    val detail: String? = null,
)

interface SessionTracker {
    val activeSession: StateFlow<GameSession?>

    suspend fun begin(request: BeginSessionRequest): BeginSessionResult

    suspend fun heartbeat(sessionId: GameSessionId, wallClock: Instant, monotonic: MonotonicMark)

    suspend fun suspend(sessionId: GameSessionId, reason: SuspendReason)

    suspend fun resume(sessionId: GameSessionId, wallClock: Instant, monotonic: MonotonicMark)

    suspend fun end(sessionId: GameSessionId, reason: EndReason): EndSessionResult

    suspend fun recover(): SessionRecoveryResult
}

interface LidSignalProvider {
    /** Only explicit/OEM lid observations belong in this stream. */
    val events: Flow<LidSignal>

    /** Screen/display lifecycle is exposed separately and must not be relabeled as a lid event. */
    val displayLifecycle: Flow<DisplayLifecycleObservation>

    suspend fun capabilities(): LidCapabilities
}

enum class PrivilegeState {
    UNKNOWN,
    AVAILABLE,
    NEEDS_SETUP,
    UNSUPPORTED,
    DENIED,
    ERROR,
}

data class PrivilegeStatus(
    val state: PrivilegeState,
    val detail: String? = null,
)

@JvmInline
value class ProtectedPathReference(val value: String) {
    init {
        require(value.isNotBlank()) { "Protected path reference must not be blank" }
    }
}

enum class PrivilegedResultState {
    COMPLETED,
    UNSUPPORTED,
    DENIED,
    ERROR,
    UNKNOWN,
}

data class PrivilegedResult(
    val state: PrivilegedResultState,
    val detail: String? = null,
)

interface PrivilegeBridge {
    val state: StateFlow<PrivilegeStatus>

    suspend fun moveTask(taskId: Int, displayId: DisplayId): PrivilegedResult

    suspend fun stopPackage(packageName: ExternalAppId): PrivilegedResult

    suspend fun inspectPath(path: ProtectedPathReference): PrivilegedResult
}

data class MetadataQuery(
    val game: Game?,
    val version: GameVersion?,
    val installation: Installation?,
    val candidates: List<MetadataCandidate> = emptyList(),
)

enum class MetadataProviderState {
    RESOLVED,
    NO_MATCH,
    NEEDS_SETUP,
    UNAVAILABLE,
    UNSUPPORTED,
    ERROR,
    UNKNOWN,
}

data class MetadataProviderResult(
    val state: MetadataProviderState,
    val candidates: List<MetadataCandidate> = emptyList(),
    val detail: String? = null,
)

interface MetadataProvider {
    val id: MetadataProviderId

    suspend fun resolve(query: MetadataQuery): MetadataProviderResult
}

data class ArtworkQuery(
    val game: Game,
    val version: GameVersion? = null,
    val preferredKinds: Set<ArtworkKind> = setOf(ArtworkKind.HERO, ArtworkKind.POSTER, ArtworkKind.CARD),
)

enum class ArtworkProviderState {
    RESOLVED,
    NO_MATCH,
    NEEDS_SETUP,
    UNAVAILABLE,
    UNSUPPORTED,
    ERROR,
    UNKNOWN,
}

data class ArtworkProviderResult(
    val state: ArtworkProviderState,
    val assets: List<ArtworkAsset> = emptyList(),
    val detail: String? = null,
)

interface ArtworkProvider {
    val id: ArtworkProviderId

    suspend fun resolve(query: ArtworkQuery): ArtworkProviderResult
}
