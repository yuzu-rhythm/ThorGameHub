package dev.thorhub.app.feature.companion

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thorhub.app.data.GameDetailSummary
import dev.thorhub.app.data.EndSessionSummary
import dev.thorhub.app.data.HubRepository
import dev.thorhub.app.data.NoteSummary
import dev.thorhub.app.data.SaveContinuitySummary
import dev.thorhub.app.platform.session.EndSessionCoordinator
import dev.thorhub.app.platform.session.RoomSessionTracker
import dev.thorhub.app.platform.input.ControllerMapping
import dev.thorhub.app.platform.input.ControllerMappingStore
import dev.thorhub.app.platform.storage.LocalSafSaveAdapter
import dev.thorhub.core.model.GameSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private val Context.companionPreferences by preferencesDataStore(name = "companion_preferences")

enum class CompanionTab { HOME, NOTES, GAME, TOOLS, SYSTEM }

data class CompanionUiState(
    val activeSession: GameSession? = null,
    val game: GameDetailSummary? = null,
    val pinnedNote: NoteSummary? = null,
    val selectedTab: CompanionTab = CompanionTab.HOME,
    val homeCards: List<String> = listOf("SESSION", "PINNED_NOTE", "SAVE", "SYSTEM"),
    val endCompleted: Boolean = false,
    val endSummary: EndSessionSummary? = null,
    val controllerMapping: ControllerMapping = ControllerMapping.DEFAULT,
    val saveContinuity: SaveContinuitySummary = SaveContinuitySummary(),
)

@Singleton
class CompanionPreferenceStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.companionPreferences

    suspend fun lastTab(gameId: String): CompanionTab {
        val value = dataStore.data.first()[stringPreferencesKey("tab_${hash(gameId)}")]
        return runCatching { CompanionTab.valueOf(value.orEmpty()) }.getOrDefault(CompanionTab.HOME)
    }

    suspend fun saveLastTab(gameId: String, tab: CompanionTab) {
        dataStore.edit { it[stringPreferencesKey("tab_${hash(gameId)}")] = tab.name }
    }

    suspend fun homeCards(): List<String> = dataStore.data.first()[HOME_CARDS]
        ?.split(',')
        ?.filter(String::isNotBlank)
        ?.ifEmpty { null }
        ?: listOf("SESSION", "PINNED_NOTE", "SAVE", "SYSTEM")

    suspend fun saveHomeCards(cards: List<String>) {
        dataStore.edit { it[HOME_CARDS] = cards.distinct().joinToString(",") }
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(16)

    private companion object {
        val HOME_CARDS = stringPreferencesKey("home_cards")
    }
}

@HiltViewModel
class CompanionViewModel @Inject constructor(
    private val repository: HubRepository,
    private val sessionTracker: RoomSessionTracker,
    private val endSessionCoordinator: EndSessionCoordinator,
    private val preferences: CompanionPreferenceStore,
    private val controllerMappingStore: ControllerMappingStore,
    private val localSaveAdapter: LocalSafSaveAdapter,
) : ViewModel() {
    private val _state = MutableStateFlow(CompanionUiState())
    val state: StateFlow<CompanionUiState> = _state.asStateFlow()
    private var gameCollector: Job? = null
    private var heartbeat: Job? = null
    private var saveCollector: Job? = null

    init {
        viewModelScope.launch {
            _state.update { it.copy(homeCards = preferences.homeCards()) }
            sessionTracker.activeSession.collectLatest(::onSessionChanged)
        }
        viewModelScope.launch {
            controllerMappingStore.mapping.collectLatest { mapping ->
                _state.update { it.copy(controllerMapping = mapping) }
            }
        }
    }

    private suspend fun onSessionChanged(session: GameSession?) {
        _state.update {
            it.copy(
                activeSession = session,
                endCompleted = if (session == null) it.endCompleted else false,
                endSummary = if (session == null) it.endSummary else null,
            )
        }
        gameCollector?.cancel()
        heartbeat?.cancel()
        saveCollector?.cancel()
        if (session == null) {
            _state.update { it.copy(game = null, pinnedNote = null, saveContinuity = SaveContinuitySummary()) }
            return
        }
        _state.update { it.copy(selectedTab = preferences.lastTab(session.gameId.value)) }
        gameCollector = viewModelScope.launch {
            repository.detail(session.gameId.value).collect { game ->
                _state.update {
                    it.copy(
                        game = game,
                        pinnedNote = game?.notes?.filter(NoteSummary::pinned)?.maxByOrNull(NoteSummary::updatedAt),
                    )
                }
            }
        }
        saveCollector = viewModelScope.launch {
            localSaveAdapter.observeContinuity(session.gameId.value).collect { continuity ->
                _state.update { it.copy(saveContinuity = continuity) }
            }
        }
        heartbeat = viewModelScope.launch {
            while (true) {
                sessionTracker.heartbeat(session.id, Instant.now(), sessionTracker.currentMonotonicMark())
                delay(30_000)
            }
        }
    }

    fun selectTab(tab: CompanionTab) {
        _state.update { it.copy(selectedTab = tab) }
        val gameId = _state.value.activeSession?.gameId?.value ?: return
        viewModelScope.launch { preferences.saveLastTab(gameId, tab) }
    }

    fun saveQuickNote(body: String, checklist: Boolean, pinned: Boolean) {
        val session = _state.value.activeSession ?: return
        viewModelScope.launch {
            repository.saveNote(
                gameId = session.gameId.value,
                body = body,
                checklist = checklist,
                pinned = pinned,
                sessionId = session.id.value,
                gameVersionId = session.gameVersionId.value,
            )
        }
    }

    fun setNotePinned(noteId: String, pinned: Boolean) {
        viewModelScope.launch { repository.setNotePinned(noteId, pinned) }
    }

    fun endLocalSession(note: String = "") {
        val session = _state.value.activeSession ?: return
        viewModelScope.launch {
            runCatching { endSessionCoordinator.end(session.id, note) }
                .onSuccess { summary ->
                    _state.update { it.copy(endCompleted = true, endSummary = summary) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            endCompleted = false,
                            endSummary = null,
                        )
                    }
                    dev.thorhub.app.platform.diagnostics.DiagnosticEventLog.record(
                        "session",
                        "companion_end_session_failed",
                        mapOf("exception" to error::class.java.simpleName),
                    )
                }
        }
    }
}
