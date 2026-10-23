package dev.thorhub.app.platform.session

import dev.thorhub.app.data.EndSessionSummary
import dev.thorhub.app.data.HubRepository
import dev.thorhub.app.platform.diagnostics.DiagnosticEventLog
import dev.thorhub.app.platform.display.AndroidDisplayCoordinator
import dev.thorhub.app.platform.launcher.LauncherAdapterFactory
import dev.thorhub.app.platform.storage.LocalSafSaveAdapter
import dev.thorhub.app.platform.storage.LocalSnapshotOutcome
import dev.thorhub.core.database.ThorHubDatabase
import dev.thorhub.core.database.SessionState as DatabaseSessionState
import dev.thorhub.core.database.SaveState as DatabaseSaveState
import dev.thorhub.core.model.EndReason
import dev.thorhub.core.model.ExitRequestResult
import dev.thorhub.core.model.ExternalExitRequestState
import dev.thorhub.core.model.ExternalProcessClosureState
import dev.thorhub.core.model.GameSessionId
import dev.thorhub.core.model.SnapshotReason
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first

/** One staged, idempotent End Session path shared by Home and Companion. */
@Singleton
class EndSessionCoordinator @Inject constructor(
    private val database: ThorHubDatabase,
    private val repository: HubRepository,
    private val sessionTracker: RoomSessionTracker,
    private val launcherAdapterFactory: LauncherAdapterFactory,
    private val localSaveAdapter: LocalSafSaveAdapter,
    private val displayCoordinator: AndroidDisplayCoordinator,
) {
    private val mutex = Mutex()

    suspend fun end(sessionId: GameSessionId, optionalNote: String = ""): EndSessionSummary = mutex.withLock {
        val persisted = database.sessionDao().getById(sessionId.value)
            ?: error("Cannot end unknown local session ${sessionId.value}")
        if (persisted.state == DatabaseSessionState.ENDED) {
            val homeRouteStatus = routeHomeStatus()
            return@withLock EndSessionSummary(
                sessionId = persisted.id,
                durationMillis = persisted.durationMillis,
                noteSaved = false,
                noteStatus = "NOT_REPEATED_ALREADY_ENDED",
                snapshotStatus = "NOT_REPEATED_ALREADY_ENDED",
                snapshotDetail = "The terminal session is retained; no duplicate note or snapshot was created.",
                syncStatus = syncStatus(persisted.gameId, persisted.gameVersionId),
                externalRequestStatus = ExternalExitRequestState.NOT_REQUESTED.name,
                externalRequestDetail = "A repeated End Session does not repeat external side effects.",
                externalClosureStatus = ExternalProcessClosureState.NOT_VERIFIED.name,
                homeRouteStatus = homeRouteStatus,
            )
        }
        val endingSession = sessionTracker.beginEnding(sessionId)
            ?: error("Cannot transition local session ${sessionId.value} to ENDING")

        val noteStatus = when {
            optionalNote.isBlank() -> NoteStatus.NOT_SUPPLIED
            else -> runCatching {
                repository.saveNote(
                    gameId = endingSession.gameId.value,
                    body = optionalNote,
                    checklist = false,
                    pinned = false,
                    sessionId = endingSession.id.value,
                    gameVersionId = endingSession.gameVersionId.value,
                    deduplicationKey = "end-session:${endingSession.id.value}",
                )
            }.fold(onSuccess = { NoteStatus.SAVED }, onFailure = { NoteStatus.ERROR })
        }

        val snapshot = runCatching {
            localSaveAdapter.snapshotForGame(
                gameId = endingSession.gameId.value,
                reason = SnapshotReason.END_SESSION,
                sessionId = endingSession.id.value,
                gameVersionId = endingSession.gameVersionId.value,
            )
        }.getOrElse { error ->
            LocalSnapshotOutcome(
                status = "ERROR",
                detail = "Snapshot failed (${error::class.java.simpleName}).",
            )
        }

        val endResult = sessionTracker.end(sessionId, EndReason.USER_REQUEST)
        val syncStatus = syncStatus(persisted.gameId, persisted.gameVersionId)

        val homeRouteStatus = routeHomeStatus()

        val exitResult = try {
            val resolved = persisted.playMethodId?.let { repository.resolvePlayMethodById(it) }
            if (resolved == null) {
                ExitRequestResult(
                    requestState = ExternalExitRequestState.NOT_REQUESTED,
                    closureState = ExternalProcessClosureState.NOT_VERIFIED,
                    detail = "The ended session has no resolvable launcher adapter.",
                )
            } else {
                launcherAdapterFactory.create(resolved.profile).requestGracefulExit(endResult.session)
            }
        } catch (error: Throwable) {
            ExitRequestResult(
                requestState = ExternalExitRequestState.FAILED,
                closureState = ExternalProcessClosureState.NOT_VERIFIED,
                detail = "Graceful-exit request failed (${error::class.java.simpleName}).",
            )
        }

        val summary = EndSessionSummary(
            sessionId = endResult.session.id.value,
            durationMillis = endResult.session.timing.accumulatedActive.toMillis(),
            noteSaved = noteStatus == NoteStatus.SAVED,
            noteStatus = noteStatus.name,
            snapshotStatus = snapshot.status,
            snapshotDetail = snapshot.detail,
            syncStatus = syncStatus,
            externalRequestStatus = exitResult.requestState.name,
            externalRequestDetail = exitResult.detail,
            externalClosureStatus = exitResult.closureState.name,
            homeRouteStatus = homeRouteStatus,
        )
        DiagnosticEventLog.record(
            "session",
            "end_session_stages_completed",
            mapOf(
                "session" to sessionId.value,
                "note" to summary.noteStatus,
                "snapshot" to summary.snapshotStatus,
                "sync" to summary.syncStatus,
                "exit_request" to summary.externalRequestStatus,
                "external_closure" to summary.externalClosureStatus,
                "home_route" to summary.homeRouteStatus,
            ),
        )
        summary
    }

    private suspend fun routeHomeStatus(): String {
        val homeRoute = runCatching {
            val topology = displayCoordinator.refresh()
            displayCoordinator.showHomeOnUpper(topology.upperDisplayId)
        }.getOrNull()
        return if (homeRoute?.requestAccepted == true) {
            "REQUEST_ACCEPTED_PHYSICAL_UNKNOWN"
        } else {
            "FAILED"
        }
    }

    private suspend fun syncStatus(gameId: String, gameVersionId: String?): String {
        val profiles = database.saveDao().observeProfilesForGame(gameId).first()
        val profile = profiles.firstOrNull { candidate -> candidate.gameVersionId == gameVersionId }
            ?: profiles.firstOrNull().takeIf { gameVersionId == null }
            ?: return "NOT_CONFIGURED"
        return when (profile.saveState) {
            DatabaseSaveState.MANAGED_EXTERNALLY -> "MANAGED_EXTERNALLY"
            DatabaseSaveState.UNCONFIGURED -> "NOT_CONFIGURED"
            else -> "NO_SYNC_ADAPTER_${profile.saveState.name}"
        }
    }

    private enum class NoteStatus { SAVED, NOT_SUPPLIED, ERROR }
}
