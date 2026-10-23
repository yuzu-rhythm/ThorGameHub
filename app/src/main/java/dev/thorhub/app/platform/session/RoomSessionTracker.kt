package dev.thorhub.app.platform.session

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thorhub.app.platform.diagnostics.DiagnosticEventLog
import dev.thorhub.core.database.GameSessionEntity
import dev.thorhub.core.database.SessionState as DatabaseSessionState
import dev.thorhub.core.database.ThorHubDatabase
import dev.thorhub.core.model.BeginSessionRequest
import dev.thorhub.core.model.BeginSessionResult
import dev.thorhub.core.model.BeginSessionState
import dev.thorhub.core.model.EndReason
import dev.thorhub.core.model.EndSessionResult
import dev.thorhub.core.model.GameId
import dev.thorhub.core.model.GameSession
import dev.thorhub.core.model.GameSessionId
import dev.thorhub.core.model.GameVersionId
import dev.thorhub.core.model.InstallationId
import dev.thorhub.core.model.MonotonicMark
import dev.thorhub.core.model.PlayMethodId
import dev.thorhub.core.model.SessionCloseReason
import dev.thorhub.core.model.SessionEndState
import dev.thorhub.core.model.SessionRecoveryResult
import dev.thorhub.core.model.SessionState
import dev.thorhub.core.model.SessionPolicy
import dev.thorhub.core.model.SessionTiming
import dev.thorhub.core.model.SessionTracker
import dev.thorhub.core.model.SuspendReason
import dev.thorhub.core.model.DeviceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSessionTracker @Inject constructor(
    private val database: ThorHubDatabase,
    @ApplicationContext context: Context,
) : SessionTracker {
    private val contentResolver = context.contentResolver
    private val _activeSession = MutableStateFlow<GameSession?>(null)
    private val operationMutex = Mutex()
    override val activeSession: StateFlow<GameSession?> = _activeSession.asStateFlow()

    override suspend fun begin(request: BeginSessionRequest): BeginSessionResult = operationMutex.withLock {
        val entity = GameSessionEntity(
            id = deterministicSessionId(request),
            gameId = request.gameId.value,
            gameVersionId = request.gameVersionId.value,
            installationId = request.installationId.value,
            deviceId = request.deviceId.value,
            playMethodId = request.playMethodId.value,
            wallStartedAt = request.wallClock.toEpochMilli(),
            monotonicStartedNanos = request.monotonic.elapsedNanos,
            monotonicBootId = request.monotonic.bootId,
            lastMonotonicNanos = null,
            state = DatabaseSessionState.PENDING,
            createdAt = request.wallClock.toEpochMilli(),
            updatedAt = request.wallClock.toEpochMilli(),
        )
        val persisted = database.sessionDao().createPendingSession(entity)
        val session = persisted.toDomain()
        _activeSession.value = session
        DiagnosticEventLog.record(
            "session",
            "pending_persisted",
            mapOf("session" to session.id.value, "before_external_launch" to "true"),
        )
        BeginSessionResult(BeginSessionState.PENDING, session)
    }

    /** Called only after Android accepts the external launch request. */
    suspend fun markLaunchAccepted(
        sessionId: GameSessionId,
        at: Instant = Instant.now(),
        monotonic: MonotonicMark = currentMonotonicMark(),
    ): GameSession? = operationMutex.withLock {
        val entity = database.sessionDao().markActive(
            sessionId = sessionId.value,
            at = at.toEpochMilli(),
            bootId = monotonic.bootId,
            monotonicNanos = monotonic.elapsedNanos,
        ) ?: return@withLock null
        entity.toDomain().also {
            _activeSession.value = it
            DiagnosticEventLog.record("session", "launch_accepted", mapOf("session" to sessionId.value))
        }
    }

    override suspend fun heartbeat(sessionId: GameSessionId, wallClock: Instant, monotonic: MonotonicMark) {
        operationMutex.withLock {
            val before = database.sessionDao().getById(sessionId.value) ?: return@withLock
            if (
                before.state == DatabaseSessionState.ACTIVE &&
                (before.monotonicBootId == null || before.lastMonotonicNanos == null ||
                    before.monotonicBootId != monotonic.bootId)
            ) {
                database.sessionDao().recoverOne(
                    sessionId = sessionId.value,
                    at = wallClock.toEpochMilli(),
                    reason = "MONOTONIC_CHECKPOINT_UNAVAILABLE",
                )
                if (_activeSession.value?.id == sessionId) _activeSession.value = null
                DiagnosticEventLog.record(
                    "session",
                    "heartbeat_recovered_uncomparable_clock",
                    mapOf("session" to sessionId.value, "duration_inference" to "disabled"),
                )
                return@withLock
            }
            val transition = SessionPolicy.heartbeat(before.toDomain(), wallClock, monotonic)
            if (!transition.applied) return@withLock
            val timing = transition.session.timing
            database.sessionDao().recordHeartbeat(
                sessionId = sessionId.value,
                at = wallClock.toEpochMilli(),
                durationMillis = timing.accumulatedActive.toMillis(),
                bootId = timing.lastObserved?.bootId,
                monotonicNanos = timing.lastObserved?.elapsedNanos,
            )?.toDomain()?.let { _activeSession.value = it }
        }
    }

    override suspend fun suspend(sessionId: GameSessionId, reason: SuspendReason) {
        suspendAt(sessionId, reason, Instant.now(), currentMonotonicMark())
    }

    internal suspend fun suspendAt(
        sessionId: GameSessionId,
        reason: SuspendReason,
        wallClock: Instant,
        monotonic: MonotonicMark,
    ) = operationMutex.withLock {
        val before = database.sessionDao().getById(sessionId.value) ?: return@withLock
        val transition = SessionPolicy.suspend(before.toDomain(), monotonic)
        if (!transition.applied) return@withLock
        database.sessionDao().suspendSession(
            sessionId = sessionId.value,
            at = wallClock.toEpochMilli(),
            durationMillis = transition.session.timing.accumulatedActive.toMillis(),
            reason = reason.name,
        )?.toDomain()?.let { _activeSession.value = it }
    }

    override suspend fun resume(sessionId: GameSessionId, wallClock: Instant, monotonic: MonotonicMark) {
        operationMutex.withLock {
            val before = database.sessionDao().getById(sessionId.value) ?: return@withLock
            val transition = SessionPolicy.resume(before.toDomain(), monotonic)
            if (!transition.applied) return@withLock
            database.sessionDao().resume(
                sessionId = sessionId.value,
                at = wallClock.toEpochMilli(),
                bootId = monotonic.bootId,
                monotonicNanos = monotonic.elapsedNanos,
            )?.toDomain()?.let { _activeSession.value = it }
        }
    }

    suspend fun beginEnding(sessionId: GameSessionId): GameSession? =
        beginEndingAt(sessionId, Instant.now(), currentMonotonicMark())

    internal suspend fun beginEndingAt(
        sessionId: GameSessionId,
        wallClock: Instant,
        monotonic: MonotonicMark,
    ): GameSession? = operationMutex.withLock {
        val before = database.sessionDao().getById(sessionId.value) ?: return@withLock null
        val comparableMark = monotonic.takeIf {
            before.state == DatabaseSessionState.ACTIVE && before.monotonicBootId == it.bootId
        }
        val transition = SessionPolicy.beginEnding(before.toDomain(), comparableMark)
        if (!transition.applied) return@withLock before.toDomain()
        database.sessionDao().beginEnding(
            sessionId = sessionId.value,
            at = wallClock.toEpochMilli(),
            durationMillis = transition.session.timing.accumulatedActive.toMillis(),
        )?.toDomain()?.also { _activeSession.value = it }
    }

    override suspend fun end(sessionId: GameSessionId, reason: EndReason): EndSessionResult {
        return endAt(sessionId, reason, Instant.now(), currentMonotonicMark())
    }

    internal suspend fun endAt(
        sessionId: GameSessionId,
        reason: EndReason,
        endedAt: Instant,
        monotonic: MonotonicMark,
    ): EndSessionResult = operationMutex.withLock {
        val before = database.sessionDao().getById(sessionId.value)
            ?: error("Cannot end unknown local session ${sessionId.value}")
        val wasEnded = before.state == DatabaseSessionState.ENDED
        val comparableMark = monotonic.takeIf {
            before.state == DatabaseSessionState.ACTIVE && before.monotonicBootId == it.bootId
        }
        val transition = SessionPolicy.end(
            session = before.toDomain(),
            wallClock = endedAt,
            at = comparableMark,
            reason = reason.toCloseReason(),
        )
        val durationMillis = transition.session.timing.accumulatedActive.toMillis()
        val persisted = database.sessionDao().endSession(
            sessionId = sessionId.value,
            wallEndedAt = endedAt.toEpochMilli(),
            monotonicEndedNanos = comparableMark?.elapsedNanos,
            durationMillis = durationMillis,
            closeReason = reason.name,
        ) ?: before
        val domain = persisted.toDomain()
        if (_activeSession.value?.id == sessionId) _activeSession.value = null
        DiagnosticEventLog.record(
            "session",
            "local_session_ended",
            mapOf(
                "session" to sessionId.value,
                "external_process_closed" to "NOT_VERIFIED",
                "duration_source" to "persisted_incremental_monotonic",
            ),
        )
        return EndSessionResult(
            state = if (wasEnded) SessionEndState.ALREADY_ENDED else SessionEndState.ENDED,
            session = domain,
            detail = "The local Game Hub session ended. External process closure was not verified.",
        )
    }

    override suspend fun recover(): SessionRecoveryResult = operationMutex.withLock {
        val recoveredEntities = database.sessionDao().recoverAfterProcessRestart(
            at = Instant.now().toEpochMilli(),
            reason = "PROCESS_OR_REBOOT_RECOVERY",
        )
        val recovered = recoveredEntities.mapNotNull { entity -> entity.toDomainOrNull() }
        val incompleteCount = recoveredEntities.size - recovered.size
        _activeSession.value = null
        if (incompleteCount > 0) {
            DiagnosticEventLog.record(
                "session",
                "incomplete_recovery_records_preserved",
                mapOf("count" to incompleteCount.toString(), "converted_to_domain" to "false"),
            )
        }
        if (recovered.isNotEmpty()) {
            DiagnosticEventLog.record(
                "session",
                "unresolved_sessions_recovered",
                mapOf("count" to recovered.size.toString(), "duration_inference" to "disabled"),
            )
        }
        SessionRecoveryResult(
            recovered = recovered,
            ambiguous = recovered,
            detail = if (recovered.isEmpty()) null else "Review recovered sessions; duration across reboot is not inferred.",
        )
    }

    fun currentMonotonicMark(): MonotonicMark = MonotonicMark(currentBootId(), SystemClock.elapsedRealtimeNanos())

    private fun deterministicSessionId(request: BeginSessionRequest): String =
        GameSessionId.deterministic(
            "local-session",
            "${request.gameId.value}:${request.wallClock}:${request.monotonic.elapsedNanos}",
        ).value

    private fun currentBootId(): String {
        val bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val fingerprintHash = MessageDigest.getInstance("SHA-256")
            .digest(Build.FINGERPRINT.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(12)
        return "boot-$bootCount-$fingerprintHash"
    }

    private fun GameSessionEntity.toDomain(): GameSession {
        val storedBootId = monotonicBootId
        val storedCheckpointNanos = lastMonotonicNanos
        val activeCheckpoint = if (
            state == DatabaseSessionState.ACTIVE &&
            storedBootId != null &&
            storedCheckpointNanos != null
        ) {
            MonotonicMark(storedBootId, storedCheckpointNanos)
        } else {
            null
        }
        return GameSession(
        id = GameSessionId.of(id),
        gameId = GameId.of(gameId),
        gameVersionId = GameVersionId.of(requireNotNull(gameVersionId) { "Session version link is missing" }),
        installationId = InstallationId.of(requireNotNull(installationId) { "Session installation link is missing" }),
        deviceId = DeviceId.of(requireNotNull(deviceId) { "Session device link is missing" }),
        playMethodId = PlayMethodId.of(requireNotNull(playMethodId) { "Session play method link is missing" }),
        state = when (state) {
            DatabaseSessionState.PENDING -> SessionState.PENDING
            DatabaseSessionState.ACTIVE -> SessionState.ACTIVE
            DatabaseSessionState.SUSPENDED -> SessionState.SUSPENDED
            DatabaseSessionState.ENDING -> SessionState.ENDING
            DatabaseSessionState.ENDED -> SessionState.ENDED
            DatabaseSessionState.RECOVERED_ACTIVE, DatabaseSessionState.RECOVERED -> SessionState.RECOVERED
            DatabaseSessionState.ABANDONED -> SessionState.ABANDONED
            DatabaseSessionState.INVALID -> SessionState.LAUNCH_FAILED
        },
        wallStartedAt = Instant.ofEpochMilli(wallStartedAt),
        wallEndedAt = wallEndedAt?.let(Instant::ofEpochMilli),
        timing = SessionTiming(
            accumulatedActive = Duration.ofMillis(durationMillis.coerceAtLeast(0L)),
            activeSince = activeCheckpoint,
            lastObserved = activeCheckpoint,
        ),
        closeReason = closeReason?.let {
            when (it) {
                EndReason.USER_REQUEST.name -> SessionCloseReason.USER_ENDED
                EndReason.LAUNCH_FAILED.name -> SessionCloseReason.LAUNCH_FAILED
                EndReason.RECOVERY.name -> SessionCloseReason.RECOVERED_AND_ENDED
                else -> SessionCloseReason.UNKNOWN
            }
        },
        lastHeartbeatAt = lastHeartbeatAt?.let(Instant::ofEpochMilli),
        )
    }

    private fun GameSessionEntity.toDomainOrNull(): GameSession? = runCatching { toDomain() }
        .getOrNull()

    private fun EndReason.toCloseReason(): SessionCloseReason = when (this) {
        EndReason.USER_REQUEST -> SessionCloseReason.USER_ENDED
        EndReason.LAUNCH_FAILED -> SessionCloseReason.LAUNCH_FAILED
        EndReason.RECOVERY -> SessionCloseReason.RECOVERED_AND_ENDED
        EndReason.UNKNOWN -> SessionCloseReason.UNKNOWN
    }
}
