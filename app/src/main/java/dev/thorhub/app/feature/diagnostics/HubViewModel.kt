package dev.thorhub.app.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.thorhub.app.data.EndSessionSummary
import dev.thorhub.app.data.FixtureSeeder
import dev.thorhub.app.data.GameDetailSummary
import dev.thorhub.app.data.HubRepository
import dev.thorhub.app.data.LibraryGameSummary
import dev.thorhub.app.data.LibraryRootSummary
import dev.thorhub.app.data.NoteSummary
import dev.thorhub.app.data.SessionSummary
import dev.thorhub.app.data.SafLibraryManager
import dev.thorhub.app.data.SaveContinuitySummary
import dev.thorhub.app.data.ResumeCardSummary
import dev.thorhub.app.platform.diagnostics.AdapterProbeSummary
import dev.thorhub.app.platform.diagnostics.DiagnosticEventLog
import dev.thorhub.app.platform.diagnostics.DiagnosticExporter
import dev.thorhub.app.platform.diagnostics.InstalledLaunchableApp
import dev.thorhub.app.platform.diagnostics.LocalDiagnosticEvent
import dev.thorhub.app.platform.diagnostics.PackageProbe
import dev.thorhub.app.platform.display.AndroidDisplayCoordinator
import dev.thorhub.app.platform.display.DisplayLaunchEvidence
import dev.thorhub.app.platform.display.DisplayTopology
import dev.thorhub.app.platform.home.HomeRoleCoordinator
import dev.thorhub.app.platform.home.HomeRoleState
import dev.thorhub.app.platform.input.ControllerMapping
import dev.thorhub.app.platform.input.ControllerMappingStore
import dev.thorhub.app.platform.input.HubControlAction
import dev.thorhub.app.platform.launcher.AndroidLaunchExecutor
import dev.thorhub.app.platform.launcher.GameNativeSource
import dev.thorhub.app.platform.launcher.LauncherAdapterFactory
import dev.thorhub.app.platform.session.RoomSessionTracker
import dev.thorhub.app.platform.session.EndSessionCoordinator
import dev.thorhub.app.platform.session.ResumeThresholdStore
import dev.thorhub.app.platform.storage.LocalSafSaveAdapter
import dev.thorhub.app.platform.storage.LocalSnapshotOutcome
import dev.thorhub.core.model.BeginSessionRequest
import dev.thorhub.core.model.EndReason
import dev.thorhub.core.model.GameId
import dev.thorhub.core.model.GameVersionId
import dev.thorhub.core.model.LaunchExecutionState
import dev.thorhub.core.model.PlayMethodValidity
import dev.thorhub.core.model.SnapshotReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class HubUiState(
    val loading: Boolean = true,
    val topology: DisplayTopology? = null,
    val homeRole: HomeRoleState,
    val adapterProbes: List<AdapterProbeSummary> = emptyList(),
    val launchableApps: List<InstalledLaunchableApp> = emptyList(),
    val recentEvents: List<LocalDiagnosticEvent> = emptyList(),
    val lastLaunch: DisplayLaunchEvidence? = null,
    val library: List<LibraryGameSummary> = emptyList(),
    val libraryPlatforms: List<String> = emptyList(),
    val libraryTotalCount: Int = 0,
    val libraryUnknownCount: Int = 0,
    val libraryPlatformFilter: String = "ALL",
    val libraryLifecycleFilter: String = "ALL",
    val librarySortMode: String = "TITLE",
    val continueGames: List<LibraryGameSummary> = emptyList(),
    val hiddenGames: List<LibraryGameSummary> = emptyList(),
    val selectedGameId: String? = null,
    val selectedGame: GameDetailSummary? = null,
    val searchQuery: String = "",
    val noteSearchQuery: String = "",
    val noteResults: List<NoteSummary> = emptyList(),
    val libraryRoots: List<LibraryRootSummary> = emptyList(),
    val activeSessions: List<SessionSummary> = emptyList(),
    val recoveredSessions: List<SessionSummary> = emptyList(),
    val endSessionSummary: EndSessionSummary? = null,
    val saveContinuity: SaveContinuitySummary = SaveContinuitySummary(),
    val resumeCard: ResumeCardSummary? = null,
    val resumeThresholdHours: Int = 6,
    val controllerMapping: ControllerMapping = ControllerMapping.DEFAULT,
    val userMessage: String? = null,
    val error: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HubViewModel @Inject constructor(
    private val displayCoordinator: AndroidDisplayCoordinator,
    private val packageProbe: PackageProbe,
    private val homeRoleCoordinator: HomeRoleCoordinator,
    private val diagnosticExporter: DiagnosticExporter,
    private val repository: HubRepository,
    private val fixtureSeeder: FixtureSeeder,
    private val sessionTracker: RoomSessionTracker,
    private val endSessionCoordinator: EndSessionCoordinator,
    private val launcherAdapterFactory: LauncherAdapterFactory,
    private val launchExecutor: AndroidLaunchExecutor,
    private val safLibraryManager: SafLibraryManager,
    private val controllerMappingStore: ControllerMappingStore,
    private val localSaveAdapter: LocalSafSaveAdapter,
    private val resumeThresholdStore: ResumeThresholdStore,
) : ViewModel() {
    private val _state = MutableStateFlow(HubUiState(homeRole = homeRoleCoordinator.currentState()))
    val state: StateFlow<HubUiState> = _state.asStateFlow()
    private val searchQuery = MutableStateFlow("")
    private val libraryPlatformFilter = MutableStateFlow("ALL")
    private val libraryLifecycleFilter = MutableStateFlow("ALL")
    private val librarySortMode = MutableStateFlow("TITLE")
    private val noteSearchQuery = MutableStateFlow("")
    private val selectedGameId = MutableStateFlow<String?>(null)
    val pagedLibrary: Flow<PagingData<LibraryGameSummary>> = combine(
        searchQuery,
        libraryPlatformFilter,
        libraryLifecycleFilter,
        librarySortMode,
    ) { query, platform, lifecycle, sortMode ->
        LibraryPagingRequest(query, platform, lifecycle, sortMode)
    }.flatMapLatest { request ->
        repository.pagedLibrary(
            query = request.query,
            platform = request.platform,
            lifecycle = request.lifecycle,
            sortMode = request.sortMode,
        )
    }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            runCatching { fixtureSeeder.seedIfEmpty() }
                .onFailure { recordFailure("fixture_seed_failed", it) }
        }
        observeLibraryState()
        refresh()
    }

    private fun observeLibraryState() {
        viewModelScope.launch {
            repository.libraryPreview().collect { games ->
                _state.update { it.copy(library = games, loading = false) }
            }
        }
        viewModelScope.launch {
            repository.libraryPlatforms().collect { platforms ->
                _state.update { it.copy(libraryPlatforms = platforms) }
            }
        }
        viewModelScope.launch {
            searchQuery.flatMapLatest(repository::libraryCount).collect { count ->
                _state.update { it.copy(libraryTotalCount = count) }
            }
        }
        viewModelScope.launch {
            searchQuery.flatMapLatest(repository::unknownPlatformCount).collect { count ->
                _state.update { it.copy(libraryUnknownCount = count) }
            }
        }
        viewModelScope.launch {
            repository.continueGames().collect { games -> _state.update { it.copy(continueGames = games) } }
        }
        viewModelScope.launch {
            repository.hiddenGames().collect { games -> _state.update { it.copy(hiddenGames = games) } }
        }
        viewModelScope.launch {
            selectedGameId.flatMapLatest { gameId ->
                if (gameId == null) flowOf(null) else repository.detail(gameId)
            }.collect { detail -> _state.update { it.copy(selectedGame = detail) } }
        }
        viewModelScope.launch {
            selectedGameId.flatMapLatest { gameId ->
                if (gameId == null) flowOf(SaveContinuitySummary()) else localSaveAdapter.observeContinuity(gameId)
            }.collect { continuity -> _state.update { it.copy(saveContinuity = continuity) } }
        }
        viewModelScope.launch {
            noteSearchQuery.flatMapLatest(repository::notes).collect { notes ->
                _state.update { it.copy(noteResults = notes) }
            }
        }
        viewModelScope.launch {
            repository.libraryRoots().collect { roots -> _state.update { it.copy(libraryRoots = roots) } }
        }
        viewModelScope.launch {
            repository.activeSessions().collect { sessions -> _state.update { it.copy(activeSessions = sessions) } }
        }
        viewModelScope.launch {
            repository.recoverySessions().collect { sessions -> _state.update { it.copy(recoveredSessions = sessions) } }
        }
        viewModelScope.launch {
            controllerMappingStore.mapping.collect { mapping ->
                _state.update { it.copy(controllerMapping = mapping) }
            }
        }
        viewModelScope.launch {
            resumeThresholdStore.hours.collect { hours ->
                _state.update { it.copy(resumeThresholdHours = hours) }
            }
        }
        viewModelScope.launch {
            combine(
                repository.continueGames(),
                repository.recoverySessions(),
                resumeThresholdStore.hours,
            ) { continued, recovered, thresholdHours ->
                val recoveredSession = recovered.firstOrNull()
                val cutoff = Instant.now().minusSeconds(thresholdHours * 3_600L).toEpochMilli()
                val agedGame = continued.firstOrNull { game ->
                    game.lastSessionAt?.let { it <= cutoff } == true
                }
                ResumeCandidate(
                    gameId = recoveredSession?.gameId ?: agedGame?.id,
                    sessionId = recoveredSession?.id,
                    recovered = recoveredSession != null,
                )
            }.flatMapLatest { candidate ->
                val gameId = candidate.gameId ?: return@flatMapLatest flowOf(null)
                repository.detail(gameId).map { detail ->
                    val session = candidate.sessionId?.let { id -> detail?.sessions?.firstOrNull { it.id == id } }
                        ?: detail?.sessions?.maxByOrNull(SessionSummary::startedAt)
                    if (detail == null || session == null) {
                        null
                    } else {
                        ResumeCardSummary(
                            gameId = detail.id,
                            gameTitle = detail.title,
                            sessionId = session.id,
                            lastPlayedAt = session.endedAt ?: session.startedAt,
                            priorDurationMillis = session.durationMillis,
                            pinnedNote = detail.notes.filter(NoteSummary::pinned).maxByOrNull(NoteSummary::updatedAt)?.body,
                            artworkLocator = detail.artworkLocator,
                            recovered = candidate.recovered,
                        )
                    }
                }
            }.collect { resumeCard -> _state.update { it.copy(resumeCard = resumeCard) } }
        }
    }

    fun cycleControl(action: HubControlAction) {
        viewModelScope.launch {
            controllerMappingStore.cycle(action)
            _state.update { it.copy(userMessage = "Controller mapping updated locally.") }
        }
    }

    fun resetControls() {
        viewModelScope.launch {
            controllerMappingStore.reset()
            _state.update { it.copy(userMessage = "Controller mapping restored to defaults.") }
        }
    }

    fun cycleResumeThreshold() {
        viewModelScope.launch { resumeThresholdStore.cycle() }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val topology = displayCoordinator.refresh()
                val apps = packageProbe.launchableApps()
                Triple(topology, apps, packageProbe.probeAdapters(apps))
            }.onSuccess { (topology, apps, probes) ->
                _state.update {
                    it.copy(
                        loading = false,
                        topology = topology,
                        homeRole = homeRoleCoordinator.currentState(),
                        launchableApps = apps,
                        adapterProbes = probes,
                        recentEvents = DiagnosticEventLog.snapshot(80).reversed(),
                    )
                }
            }.onFailure { error ->
                recordFailure("refresh_failed", error)
                _state.update {
                    it.copy(
                        loading = false,
                        error = "Diagnostics could not refresh: ${error::class.java.simpleName}",
                        recentEvents = DiagnosticEventLog.snapshot(80).reversed(),
                    )
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
        _state.update { it.copy(searchQuery = query) }
    }

    fun setLibraryFilters(platform: String, lifecycle: String, sortMode: String) {
        val normalizedPlatform = platform.takeIf { it == "ALL" || it in _state.value.libraryPlatforms } ?: "ALL"
        val normalizedLifecycle = lifecycle.takeIf { it in LIBRARY_LIFECYCLES } ?: "ALL"
        val normalizedSort = sortMode.takeIf { it in LIBRARY_SORT_MODES } ?: "TITLE"
        libraryPlatformFilter.value = normalizedPlatform
        libraryLifecycleFilter.value = normalizedLifecycle
        librarySortMode.value = normalizedSort
        _state.update {
            it.copy(
                libraryPlatformFilter = normalizedPlatform,
                libraryLifecycleFilter = normalizedLifecycle,
                librarySortMode = normalizedSort,
            )
        }
    }

    fun setNoteSearchQuery(query: String) {
        noteSearchQuery.value = query
        _state.update { it.copy(noteSearchQuery = query) }
    }

    fun selectGame(gameId: String?) {
        selectedGameId.value = gameId
        _state.update { it.copy(selectedGameId = gameId, endSessionSummary = null, userMessage = null) }
    }

    fun toggleSelectedPinned() {
        val game = _state.value.selectedGame ?: return
        viewModelScope.launch { repository.setPinned(game.id, !game.pinned) }
    }

    fun toggleSelectedFavorite() {
        val game = _state.value.selectedGame ?: return
        viewModelScope.launch { repository.setFavorite(game.id, !game.favorite) }
    }

    fun hideSelectedGame() {
        val game = _state.value.selectedGame ?: return
        viewModelScope.launch {
            repository.setHidden(game.id, true)
            selectGame(null)
            _state.update { it.copy(userMessage = "Game hidden. History was preserved.") }
        }
    }

    fun unhideGame(gameId: String) {
        viewModelScope.launch {
            repository.setHidden(gameId, false)
            _state.update { it.copy(userMessage = "Game restored to the local library. History was unchanged.") }
        }
    }

    fun updateSelectedMetadata(title: String, description: String, releaseYear: Int?) {
        val game = _state.value.selectedGame ?: return
        viewModelScope.launch {
            runCatching { repository.updateGameMetadata(game.id, title, description, releaseYear) }
                .onSuccess { _state.update { it.copy(userMessage = "Metadata override saved locally with field provenance.") } }
                .onFailure { error -> setActionError("The metadata override could not be saved.", error) }
        }
    }

    fun setSelectedArtwork(uri: Uri) {
        val game = _state.value.selectedGame ?: return
        viewModelScope.launch {
            runCatching { repository.setArtworkOverride(game.id, uri.toString()) }
                .onSuccess { _state.update { it.copy(userMessage = "Artwork override saved locally.") } }
                .onFailure { error -> setActionError("The artwork override could not be saved.", error) }
        }
    }

    fun importAndroidApp(app: InstalledLaunchableApp) {
        viewModelScope.launch {
            runCatching { repository.importAndroidApp(app) }
                .onSuccess { gameId ->
                    selectGame(gameId)
                    _state.update { it.copy(userMessage = "${app.label} was added to the local library.") }
                }
                .onFailure { error -> setActionError("The app could not be added.", error) }
        }
    }

    fun addGameNativeTitle(title: String, source: GameNativeSource, appId: Int) {
        viewModelScope.launch {
            runCatching { repository.addGameNativeTitle(title, source, appId) }
                .onSuccess { gameId ->
                    selectGame(gameId)
                    _state.update { it.copy(userMessage = "GameNative title added. Test the installed contract before relying on it.") }
                }
                .onFailure { error -> setActionError("The GameNative title could not be added.", error) }
        }
    }

    fun addConfigurableExternalTitle(
        title: String,
        app: InstalledLaunchableApp,
        action: String?,
        data: String?,
        extrasTemplate: String,
    ) {
        viewModelScope.launch {
            runCatching { repository.addConfigurableExternalTitle(title, app, action, data, extrasTemplate) }
                .onSuccess { gameId ->
                    selectGame(gameId)
                    _state.update { it.copy(userMessage = "Editable external launcher profile added. Run a test launch before relying on it.") }
                }
                .onFailure { error -> setActionError("The external launcher profile could not be added.", error) }
        }
    }

    fun configureRetroArch(gameId: String, packageName: String, corePath: String) {
        viewModelScope.launch {
            runCatching { repository.configureRetroArch(gameId, packageName, corePath) }
                .onSuccess {
                    selectGame(gameId)
                    _state.update {
                        it.copy(
                            userMessage = "RetroArch play method saved as UNVERIFIED. Run a test launch for this ROM/core/package combination.",
                        )
                    }
                }
                .onFailure { error -> setActionError("The RetroArch play method could not be configured.", error) }
        }
    }

    fun addLibraryRoot(treeUri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(userMessage = "Scanning the selected SAF root in the background…") }
            runCatching { safLibraryManager.persistAndScan(treeUri) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            userMessage = if (result.completed) {
                                "Library scan completed: ${result.discoveredCount} item(s) discovered."
                            } else {
                                "Library scan needs action: ${result.failureCode ?: "UNKNOWN"}. Existing history was preserved."
                            },
                        )
                    }
                }
                .onFailure { error -> setActionError("The selected library root could not be scanned.", error) }
        }
    }

    fun configureSelectedSaveRoot(treeUri: Uri) {
        val game = _state.value.selectedGame ?: run {
            _state.update { it.copy(userMessage = "Select a game before configuring its save directory.") }
            return
        }
        viewModelScope.launch {
            runCatching { localSaveAdapter.configureForGame(game.id, treeUri) }
                .onSuccess { _state.update { it.copy(userMessage = "Local save profile configured for the granted directory.") } }
                .onFailure { error -> setActionError("The save directory could not be configured.", error) }
        }
    }

    fun snapshotSelectedSaves() {
        val game = _state.value.selectedGame ?: return
        viewModelScope.launch {
            _state.update { it.copy(userMessage = "Waiting briefly for save files to become stable…") }
            val outcome = runCatching {
                localSaveAdapter.snapshotForGame(game.id, SnapshotReason.MANUAL)
            }.getOrElse { error ->
                setActionError("The local snapshot could not be created.", error)
                return@launch
            }
            _state.update { it.copy(userMessage = "${outcome.status}: ${outcome.detail}") }
        }
    }

    fun restoreLatestSnapshot() {
        val game = _state.value.selectedGame ?: return
        viewModelScope.launch {
            val result = runCatching { localSaveAdapter.restoreLatestForGame(game.id) }.getOrElse { error ->
                setActionError("The snapshot could not be restored.", error)
                return@launch
            }
            _state.update { it.copy(userMessage = "${result.state}: ${result.detail ?: "No detail"}") }
        }
    }

    fun endRecoveredSession(sessionId: String) {
        viewModelScope.launch {
            runCatching {
                sessionTracker.end(dev.thorhub.core.model.GameSessionId.of(sessionId), EndReason.RECOVERY)
            }.onSuccess {
                _state.update { state -> state.copy(userMessage = "Recovered local session ended without inferring reboot time.") }
            }.onFailure { error -> setActionError("The recovered session could not be ended.", error) }
        }
    }

    fun saveNote(body: String, checklist: Boolean, pinned: Boolean) {
        val game = _state.value.selectedGame ?: return
        val activeSessionId = _state.value.activeSessions.firstOrNull { it.gameId == game.id }?.id
        viewModelScope.launch {
            val activeVersionId = sessionTracker.activeSession.value
                ?.takeIf { session -> session.gameId.value == game.id }
                ?.gameVersionId
                ?.value
            runCatching {
                repository.saveNote(
                    gameId = game.id,
                    body = body,
                    checklist = checklist,
                    pinned = pinned,
                    sessionId = activeSessionId,
                    gameVersionId = activeVersionId,
                )
            }
                .onSuccess { _state.update { it.copy(userMessage = "Note saved locally.") } }
                .onFailure { error -> setActionError("The note could not be saved.", error) }
        }
    }

    fun updateNote(noteId: String, body: String) {
        viewModelScope.launch {
            runCatching { repository.updateNote(noteId, body) }
                .onFailure { error -> setActionError("The note could not be updated.", error) }
        }
    }

    fun setNotePinned(noteId: String, pinned: Boolean) {
        viewModelScope.launch { repository.setNotePinned(noteId, pinned) }
    }

    fun setNoteCompleted(noteId: String, completed: Boolean) {
        viewModelScope.launch { repository.setNoteCompleted(noteId, completed) }
    }

    fun launchSelectedGame() {
        val detail = _state.value.selectedGame ?: return
        viewModelScope.launch {
            val resolved = runCatching { repository.resolvePlayMethod(detail.id) }.getOrNull()
            if (resolved == null) {
                _state.update { it.copy(userMessage = "No configured play method. Open Setup to choose a launcher.") }
                return@launch
            }
            val adapter = runCatching { launcherAdapterFactory.create(resolved.profile) }.getOrElse { error ->
                setActionError("This launcher profile could not be loaded.", error)
                return@launch
            }
            val validation = adapter.validate(resolved.method)
            if (validation.validity != PlayMethodValidity.VALID) {
                _state.update {
                    it.copy(
                        userMessage = validation.evidence.firstOrNull()
                            ?: "The launcher needs setup before it can be used.",
                    )
                }
                return@launch
            }
            val begin = sessionTracker.begin(
                BeginSessionRequest(
                    gameId = GameId.of(detail.id),
                    gameVersionId = GameVersionId.of(resolved.gameVersionId),
                    installationId = resolved.method.installationId,
                    deviceId = resolved.method.deviceId,
                    playMethodId = resolved.method.id,
                    wallClock = Instant.now(),
                    monotonic = sessionTracker.currentMonotonicMark(),
                ),
            )
            val session = begin.session ?: return@launch
            _state.update { it.copy(userMessage = "Checking the explicit local save snapshot policy before launch…") }
            val prelaunchSnapshot = runCatching {
                localSaveAdapter.snapshotForGame(
                    gameId = resolved.gameId,
                    reason = SnapshotReason.PRELAUNCH,
                    sessionId = session.id.value,
                    gameVersionId = resolved.gameVersionId,
                )
            }.getOrElse { error ->
                LocalSnapshotOutcome(
                    status = "ERROR",
                    detail = "Prelaunch snapshot failed (${error::class.java.simpleName}).",
                )
            }
            DiagnosticEventLog.record(
                "saves",
                "prelaunch_snapshot_checked",
                mapOf(
                    "session" to session.id.value,
                    "status" to prelaunchSnapshot.status,
                    "launch_blocked" to "false",
                ),
            )
            val execution = launchExecutor.launch(resolved.method, _state.value.topology?.upperDisplayId)
            if (execution.result.state == LaunchExecutionState.ACCEPTED) {
                sessionTracker.markLaunchAccepted(session.id)
                _state.value.topology?.lowerDisplayId?.let { lowerDisplayId ->
                    displayCoordinator.launchCompanion(
                        displayId = lowerDisplayId,
                        placementTest = false,
                        sessionId = session.id.value,
                    )
                }
                _state.update {
                    it.copy(
                        lastLaunch = execution.display,
                        userMessage = "Android accepted the launch request. Prelaunch save: ${prelaunchSnapshot.status}. Physical placement remains UNKNOWN until observed.",
                    )
                }
            } else {
                sessionTracker.end(session.id, EndReason.LAUNCH_FAILED)
                _state.update {
                    it.copy(
                        lastLaunch = execution.display,
                        userMessage = "${execution.result.detail ?: "Launch failed."} Prelaunch save: ${prelaunchSnapshot.status}.",
                    )
                }
            }
            refreshEvents()
        }
    }

    fun endActiveSession(optionalNote: String = "") {
        val active = sessionTracker.activeSession.value ?: return
        viewModelScope.launch {
            runCatching { endSessionCoordinator.end(active.id, optionalNote) }
                .onSuccess { summary ->
                    _state.update {
                        it.copy(
                            endSessionSummary = summary,
                            userMessage = "Local session ended; external closure is ${summary.externalClosureStatus}.",
                        )
                    }
                }
                .onFailure { error -> setActionError("The local session could not be finalized.", error) }
            refreshEvents()
        }
    }

    fun launchCompanion(displayId: Int? = _state.value.topology?.lowerDisplayId) {
        if (displayId == null) {
            _state.update {
                it.copy(
                    lastLaunch = DisplayLaunchEvidence(
                        requestedDisplayId = null,
                        requestAccepted = false,
                        resolvedComponent = null,
                        observedOwnActivityDisplayId = null,
                        physicalPlacementConfirmed = null,
                        companionKnownActive = null,
                        message = "No secondary display candidate is available; single-display fallback remains active.",
                    ),
                )
            }
            return
        }
        _state.update { it.copy(lastLaunch = displayCoordinator.launchCompanion(displayId)) }
        refreshEvents()
    }

    fun launchInstalledApp(app: InstalledLaunchableApp) {
        val upper = _state.value.topology?.upperDisplayId
        _state.update { it.copy(lastLaunch = displayCoordinator.launchExternal(app.componentName, upper)) }
        refreshEvents()
    }

    fun swapDisplays() {
        val topology = _state.value.topology ?: return
        viewModelScope.launch {
            _state.update { it.copy(topology = displayCoordinator.swap(topology)) }
            launchCompanion(_state.value.topology?.lowerDisplayId)
        }
    }

    fun confirmPlacement() {
        val topology = _state.value.topology ?: return
        viewModelScope.launch {
            _state.update { it.copy(topology = displayCoordinator.confirm(topology)) }
            refreshEvents()
        }
    }

    fun onHomeRoleResult(systemResultAccepted: Boolean) {
        _state.update { it.copy(homeRole = homeRoleCoordinator.recordRequestResult(systemResultAccepted)) }
        refreshEvents()
    }

    fun refreshHomeRole() {
        _state.update { it.copy(homeRole = homeRoleCoordinator.currentState()) }
    }

    fun exportJson(): String = diagnosticExporter.export(
        topology = _state.value.topology,
        homeRole = _state.value.homeRole,
        probes = _state.value.adapterProbes,
    )

    fun clearMessage() {
        _state.update { it.copy(userMessage = null, error = null) }
    }

    fun refreshEvents() {
        _state.update { it.copy(recentEvents = DiagnosticEventLog.snapshot(80).reversed()) }
    }

    private fun recordFailure(code: String, error: Throwable) {
        DiagnosticEventLog.record("hub", code, mapOf("exception" to error::class.java.simpleName))
    }

    private fun setActionError(message: String, error: Throwable) {
        recordFailure("action_failed", error)
        _state.update { it.copy(userMessage = "$message ${error::class.java.simpleName}") }
    }

    private data class ResumeCandidate(
        val gameId: String?,
        val sessionId: String?,
        val recovered: Boolean,
    )

    private data class LibraryPagingRequest(
        val query: String,
        val platform: String,
        val lifecycle: String,
        val sortMode: String,
    )

    private companion object {
        val LIBRARY_LIFECYCLES = setOf("ALL", "PLAYING", "BACKLOG", "COMPLETED")
        val LIBRARY_SORT_MODES = setOf("TITLE", "PINNED", "RECENT")
    }
}
