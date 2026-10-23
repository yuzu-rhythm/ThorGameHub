package dev.thorhub.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import java.time.Instant

class FakeDisplayCoordinator(
    initialTopology: DisplayTopology,
) : DisplayCoordinator {
    private val topologyState = MutableStateFlow(initialTopology)
    override val topology: StateFlow<DisplayTopology> = topologyState
    var nextLaunchResult: DisplayLaunchResult = DisplayLaunchResult(
        requestedDisplayId = null,
        acceptance = DisplayLaunchAcceptance(DisplayLaunchAcceptanceState.UNKNOWN, null),
    )
    var placementTestResult: PlacementTestResult = PlacementTestResult(emptyList())

    override suspend fun refresh(): DisplayTopology = topologyState.value

    override suspend fun launchCompanion(route: CompanionRoute): DisplayLaunchResult = nextLaunchResult

    override suspend fun launchOnUpper(request: UpperLaunchRequest): DisplayLaunchResult = nextLaunchResult

    override suspend fun showHomeOnUpper(route: HomeRoute?): DisplayLaunchResult = nextLaunchResult

    override suspend fun runPlacementTest(): PlacementTestResult = placementTestResult
}

class FakeLibrarySource(
    override val id: LibrarySourceId = LibrarySourceId.of("fake-library"),
) : LibrarySource {
    var sourceAvailability: SourceAvailability = SourceAvailability(SourceAvailabilityState.READY)
    var events: List<ScanEvent> = emptyList()
    var resolution: ResolutionResult = ResolutionResult(ResolutionState.UNKNOWN)

    override suspend fun availability(): SourceAvailability = sourceAvailability

    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        events.forEach { emit(it) }
    }

    override suspend fun resolve(item: DiscoveredItem): ResolutionResult = resolution
}

class FakeLauncherAdapter(
    override val id: LauncherAdapterId = LauncherAdapterId.of("fake-launcher"),
) : LauncherAdapter {
    var probeResult: AdapterProbeResult = AdapterProbeResult(ProbeState.UNKNOWN)
    var validation: LaunchValidation = LaunchValidation(PlayMethodValidity.UNKNOWN)
    var launchIntentResult: LaunchIntentResult = LaunchIntentResult(ProbeState.UNKNOWN)
    var exitResult: ExitRequestResult = ExitRequestResult(ExternalExitRequestState.UNSUPPORTED)
    var diagnostics: AdapterDiagnostics = AdapterDiagnostics(id)

    override suspend fun probe(device: DeviceDescriptor): AdapterProbeResult = probeResult

    override suspend fun validate(method: PlayMethod): LaunchValidation = validation

    override suspend fun buildLaunchIntent(method: PlayMethod): LaunchIntentResult = launchIntentResult

    override suspend fun requestGracefulExit(session: GameSession): ExitRequestResult = exitResult

    override suspend fun collectDiagnostics(method: PlayMethod): AdapterDiagnostics = diagnostics
}

class FakeSaveAdapter(
    override val id: SaveAdapterId = SaveAdapterId.of("fake-save"),
) : SaveAdapter {
    var capability: SaveCapability = SaveCapability(CapabilityStatus.UNKNOWN)
    var manifest: SaveManifestResult = SaveManifestResult(SaveProfileState.UNKNOWN)
    var quiescence: QuiescenceResult = QuiescenceResult(QuiescenceState.UNKNOWN)
    var snapshotResult: SnapshotResult = SnapshotResult(SnapshotResultState.UNKNOWN)
    var restoreResult: RestoreResult = RestoreResult(RestoreResultState.UNKNOWN)

    override suspend fun probe(installation: Installation): SaveCapability = capability

    override suspend fun enumerate(profile: SaveProfile): SaveManifestResult = manifest

    override suspend fun awaitQuiescence(profile: SaveProfile, policy: QuiescencePolicy): QuiescenceResult = quiescence

    override suspend fun snapshot(profile: SaveProfile, reason: SnapshotReason): SnapshotResult = snapshotResult

    override suspend fun restore(snapshotId: SaveSnapshotId): RestoreResult = restoreResult
}

class FakeSyncTransport(
    override val id: SyncTransportId = SyncTransportId.of("fake-sync"),
) : SyncTransport {
    var transportAvailability: TransportAvailability = TransportAvailability(TransportAvailabilityState.READY)
    var currentStatus: SyncStatus = SyncStatus(SyncState.LOCAL_ONLY)
    var pushResult: SyncResult = SyncResult(
        state = SyncResultState.UNKNOWN,
        status = currentStatus,
    )
    var pullResult: SyncResult = pushResult

    override suspend fun availability(): TransportAvailability = transportAvailability

    override suspend fun status(scope: SyncScope): SyncStatus = currentStatus

    override suspend fun push(changeSet: ChangeSet): SyncResult = pushResult

    override suspend fun pull(scope: SyncScope): SyncResult = pullResult
}

class FakeSessionTracker : SessionTracker {
    private val state = MutableStateFlow<GameSession?>(null)
    override val activeSession: StateFlow<GameSession?> = state

    override suspend fun begin(request: BeginSessionRequest): BeginSessionResult {
        val session = GameSession(
            id = DeterministicIds.session(request.gameId.value, request.wallClock.toString()),
            gameId = request.gameId,
            gameVersionId = request.gameVersionId,
            installationId = request.installationId,
            deviceId = request.deviceId,
            playMethodId = request.playMethodId,
            wallStartedAt = request.wallClock,
            timing = SessionDurationPolicy.begin(SessionTiming(), request.monotonic),
        )
        state.value = session
        return BeginSessionResult(BeginSessionState.PENDING, session)
    }

    override suspend fun heartbeat(sessionId: GameSessionId, wallClock: Instant, monotonic: MonotonicMark) {
        val current = state.value ?: return
        if (current.id == sessionId) state.value = SessionPolicy.heartbeat(current, wallClock, monotonic).session
    }

    override suspend fun suspend(sessionId: GameSessionId, reason: SuspendReason) {
        val current = state.value ?: return
        if (current.id == sessionId) {
            val mark = current.timing.lastObserved ?: MonotonicMark("fake", 0L)
            state.value = SessionPolicy.suspend(current, mark).session
        }
    }

    override suspend fun resume(sessionId: GameSessionId, wallClock: Instant, monotonic: MonotonicMark) {
        val current = state.value ?: return
        if (current.id == sessionId) state.value = SessionPolicy.resume(current, monotonic).session
    }

    override suspend fun end(sessionId: GameSessionId, reason: EndReason): EndSessionResult {
        val current = state.value ?: error("No active fake session")
        val closeReason = if (reason == EndReason.RECOVERY) {
            SessionCloseReason.RECOVERED_AND_ENDED
        } else {
            SessionCloseReason.USER_ENDED
        }
        val result = SessionPolicy.end(current, Instant.EPOCH, current.timing.lastObserved, closeReason)
        state.value = result.session
        return EndSessionResult(
            state = if (result.idempotent) SessionEndState.ALREADY_ENDED else SessionEndState.ENDED,
            session = result.session,
        )
    }

    override suspend fun recover(): SessionRecoveryResult {
        val current = state.value ?: return SessionRecoveryResult(emptyList())
        val recovered = SessionPolicy.recover(current).session
        state.value = recovered
        return SessionRecoveryResult(listOf(recovered))
    }
}

class FakeLidSignalProvider : LidSignalProvider {
    private val lidEvents = MutableSharedFlow<LidSignal>(extraBufferCapacity = 16)
    private val lifecycleEvents = MutableSharedFlow<DisplayLifecycleObservation>(extraBufferCapacity = 16)
    override val events: Flow<LidSignal> = lidEvents
    override val displayLifecycle: Flow<DisplayLifecycleObservation> = lifecycleEvents
    var lidCapabilities: LidCapabilities = LidCapabilities(CapabilityStatus.UNKNOWN)

    override suspend fun capabilities(): LidCapabilities = lidCapabilities

    fun emitLid(signal: LidSignal) {
        lidEvents.tryEmit(signal)
    }

    fun emitLifecycle(observation: DisplayLifecycleObservation) {
        lifecycleEvents.tryEmit(observation)
    }
}

class FakePrivilegeBridge : PrivilegeBridge {
    private val status = MutableStateFlow(PrivilegeStatus(PrivilegeState.UNSUPPORTED))
    override val state: StateFlow<PrivilegeStatus> = status
    var nextResult: PrivilegedResult = PrivilegedResult(PrivilegedResultState.UNSUPPORTED)

    override suspend fun moveTask(taskId: Int, displayId: DisplayId): PrivilegedResult = nextResult

    override suspend fun stopPackage(packageName: ExternalAppId): PrivilegedResult = nextResult

    override suspend fun inspectPath(path: ProtectedPathReference): PrivilegedResult = nextResult
}

class FakeMetadataProvider(
    override val id: MetadataProviderId = MetadataProviderId.of("fake-metadata"),
) : MetadataProvider {
    var result: MetadataProviderResult = MetadataProviderResult(MetadataProviderState.UNKNOWN)

    override suspend fun resolve(query: MetadataQuery): MetadataProviderResult = result
}

class FakeArtworkProvider(
    override val id: ArtworkProviderId = ArtworkProviderId.of("fake-artwork"),
) : ArtworkProvider {
    var result: ArtworkProviderResult = ArtworkProviderResult(ArtworkProviderState.UNKNOWN)

    override suspend fun resolve(query: ArtworkQuery): ArtworkProviderResult = result
}
