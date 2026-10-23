package dev.thorhub.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SessionDao {
    @Upsert
    protected abstract suspend fun insert(session: GameSessionEntity)

    @Query("SELECT * FROM game_sessions WHERE id = :sessionId")
    abstract suspend fun getById(sessionId: String): GameSessionEntity?

    @Query("SELECT * FROM game_sessions WHERE id = :sessionId")
    abstract fun observeById(sessionId: String): Flow<GameSessionEntity?>

    @Query(
        """
        SELECT *
        FROM game_sessions
        WHERE state = 'PENDING'
        ORDER BY wall_started_at ASC
        """,
    )
    abstract fun observePending(): Flow<List<GameSessionEntity>>

    @Query(
        """
        SELECT *
        FROM game_sessions
        WHERE state IN ('ACTIVE', 'SUSPENDED', 'ENDING', 'RECOVERED_ACTIVE')
        ORDER BY wall_started_at DESC
        """,
    )
    abstract fun observeActive(): Flow<List<GameSessionEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM game_sessions s
        INNER JOIN game_versions gv ON gv.id = s.game_version_id
        INNER JOIN save_profiles sp ON sp.game_version_id = gv.id
        WHERE sp.id = :profileId
          AND s.state IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'ENDING', 'RECOVERED_ACTIVE')
        """,
    )
    abstract suspend fun countOpenForSaveProfile(profileId: String): Int

    @Query(
        """
        SELECT *
        FROM game_sessions
        WHERE state IN ('RECOVERED_ACTIVE', 'RECOVERED', 'ABANDONED')
        ORDER BY updated_at DESC
        """,
    )
    abstract fun observeRecoveryRecords(): Flow<List<GameSessionEntity>>

    @Query(
        """
        SELECT
            s.id AS sessionId,
            s.game_id AS gameId,
            g.canonical_title AS gameTitle,
            s.state AS state,
            s.wall_started_at AS wallStartedAt,
            s.wall_ended_at AS wallEndedAt,
            s.duration_millis AS durationMillis
        FROM game_sessions s
        INNER JOIN games g ON g.id = s.game_id
        WHERE s.game_id = :gameId
        ORDER BY s.wall_started_at DESC
        """,
    )
    abstract fun observeForGame(gameId: String): Flow<List<SessionWithGameRow>>

    @Query(
        """
        UPDATE game_sessions
        SET state = 'ACTIVE',
            monotonic_boot_id = :bootId,
            last_monotonic_nanos = :monotonicNanos,
            updated_at = :at
        WHERE id = :sessionId AND state = 'PENDING'
        """,
    )
    protected abstract suspend fun activatePending(
        sessionId: String,
        at: Long,
        bootId: String?,
        monotonicNanos: Long?,
    ): Int

    @Query(
        """
        UPDATE game_sessions
        SET duration_millis = :durationMillis,
            monotonic_boot_id = :bootId,
            last_monotonic_nanos = :monotonicNanos,
            last_heartbeat_at = :at,
            updated_at = :at
        WHERE id = :sessionId
          AND state = 'ACTIVE'
        """,
    )
    protected abstract suspend fun persistHeartbeat(
        sessionId: String,
        at: Long,
        durationMillis: Long,
        bootId: String?,
        monotonicNanos: Long?,
    ): Int

    @Query(
        """
        UPDATE game_sessions
        SET state = 'SUSPENDED',
            duration_millis = :durationMillis,
            monotonic_boot_id = NULL,
            last_monotonic_nanos = NULL,
            updated_at = :at,
            close_reason = :reason
        WHERE id = :sessionId AND state IN ('ACTIVE', 'RECOVERED_ACTIVE')
        """,
    )
    protected abstract suspend fun persistSuspension(
        sessionId: String,
        at: Long,
        durationMillis: Long,
        reason: String? = null,
    ): Int

    @Query(
        """
        UPDATE game_sessions
        SET state = 'ACTIVE',
            monotonic_boot_id = :bootId,
            last_monotonic_nanos = :monotonicNanos,
            close_reason = NULL,
            updated_at = :at
        WHERE id = :sessionId AND state IN ('SUSPENDED', 'RECOVERED_ACTIVE')
        """,
    )
    protected abstract suspend fun persistResume(
        sessionId: String,
        at: Long,
        bootId: String,
        monotonicNanos: Long,
    ): Int

    @Query(
        """
        UPDATE game_sessions
        SET state = 'ENDING',
            duration_millis = :durationMillis,
            monotonic_boot_id = NULL,
            last_monotonic_nanos = NULL,
            updated_at = :at
        WHERE id = :sessionId
          AND state IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'RECOVERED_ACTIVE', 'RECOVERED')
        """,
    )
    protected abstract suspend fun persistBeginningEnd(
        sessionId: String,
        at: Long,
        durationMillis: Long,
    ): Int

    @Query(
        """
        UPDATE game_sessions
        SET state = 'ENDED',
            wall_ended_at = :wallEndedAt,
            monotonic_ended_nanos = :monotonicEndedNanos,
            duration_millis = :durationMillis,
            monotonic_boot_id = NULL,
            last_monotonic_nanos = NULL,
            close_reason = :closeReason,
            updated_at = :wallEndedAt
        WHERE id = :sessionId
          AND state NOT IN ('ENDED', 'ABANDONED', 'INVALID')
        """,
    )
    protected abstract suspend fun endOpenSession(
        sessionId: String,
        wallEndedAt: Long,
        monotonicEndedNanos: Long?,
        durationMillis: Long,
        closeReason: String?,
    ): Int

    @Query(
        """
        UPDATE game_sessions
        SET state = 'RECOVERED',
            recovery_reason = :reason,
            monotonic_boot_id = NULL,
            last_monotonic_nanos = NULL,
            updated_at = :at
        WHERE state = 'PENDING'
        """,
    )
    protected abstract suspend fun recoverPending(at: Long, reason: String): Int

    @Query(
        """
        UPDATE game_sessions
        SET state = 'RECOVERED_ACTIVE',
            recovery_reason = :reason,
            monotonic_boot_id = NULL,
            last_monotonic_nanos = NULL,
            updated_at = :at
        WHERE state IN ('ACTIVE', 'SUSPENDED', 'ENDING')
        """,
    )
    protected abstract suspend fun recoverActive(at: Long, reason: String): Int

    @Query(
        """
        UPDATE game_sessions
        SET state = CASE WHEN state = 'PENDING' THEN 'RECOVERED' ELSE 'RECOVERED_ACTIVE' END,
            recovery_reason = :reason,
            monotonic_boot_id = NULL,
            last_monotonic_nanos = NULL,
            updated_at = :at
        WHERE id = :sessionId
          AND state IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'ENDING')
        """,
    )
    protected abstract suspend fun recoverOpenSession(
        sessionId: String,
        at: Long,
        reason: String,
    ): Int

    @Query(
        """
        UPDATE game_sessions
        SET state = 'INVALID',
            recovery_reason = :reason,
            monotonic_boot_id = NULL,
            last_monotonic_nanos = NULL,
            updated_at = :at
        WHERE state IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'ENDING')
          AND (
              game_version_id IS NULL OR installation_id IS NULL OR
              device_id IS NULL OR play_method_id IS NULL
          )
        """,
    )
    protected abstract suspend fun invalidateIncompleteOpenSessions(
        at: Long,
        reason: String,
    ): Int

    @Query(
        """
        SELECT *
        FROM game_sessions
        WHERE state IN ('RECOVERED_ACTIVE', 'RECOVERED', 'ABANDONED')
        ORDER BY updated_at DESC
        """,
    )
    protected abstract suspend fun getRecoveryRecords(): List<GameSessionEntity>

    @Query(
        """
        UPDATE game_sessions
        SET state = 'ABANDONED',
            close_reason = :reason,
            updated_at = :at
        WHERE id = :sessionId AND state IN ('RECOVERED_ACTIVE', 'RECOVERED')
        """,
    )
    protected abstract suspend fun abandonRecovered(sessionId: String, at: Long, reason: String): Int

    /**
     * Persists PENDING before an external launch. Reusing the same immutable ID
     * is safe and does not reset an already active or ended record.
     */
    @Transaction
    open suspend fun createPendingSession(session: GameSessionEntity): GameSessionEntity {
        val existing = getById(session.id)
        if (existing == null) {
            insert(session.copy(state = SessionState.PENDING))
        }
        return getById(session.id) ?: error("Session insert did not persist: ${session.id}")
    }

    @Transaction
    open suspend fun markActive(
        sessionId: String,
        at: Long,
        bootId: String? = null,
        monotonicNanos: Long? = null,
    ): GameSessionEntity? {
        activatePending(sessionId, at, bootId, monotonicNanos)
        return getById(sessionId)
    }

    @Transaction
    open suspend fun recordHeartbeat(
        sessionId: String,
        at: Long,
        durationMillis: Long,
        bootId: String?,
        monotonicNanos: Long?,
    ): GameSessionEntity? {
        persistHeartbeat(sessionId, at, durationMillis, bootId, monotonicNanos)
        return getById(sessionId)
    }

    @Transaction
    open suspend fun suspendSession(
        sessionId: String,
        at: Long,
        durationMillis: Long,
        reason: String? = null,
    ): GameSessionEntity? {
        persistSuspension(sessionId, at, durationMillis, reason)
        return getById(sessionId)
    }

    @Transaction
    open suspend fun resume(
        sessionId: String,
        at: Long,
        bootId: String,
        monotonicNanos: Long,
    ): GameSessionEntity? {
        persistResume(sessionId, at, bootId, monotonicNanos)
        return getById(sessionId)
    }

    @Transaction
    open suspend fun beginEnding(
        sessionId: String,
        at: Long,
        durationMillis: Long,
    ): GameSessionEntity? {
        persistBeginningEnd(sessionId, at, durationMillis)
        return getById(sessionId)
    }

    /**
     * Ending is idempotent: a terminal record is returned unchanged on repeat
     * calls, preventing duplicate duration or a later caller from overwriting
     * the original close evidence.
     */
    @Transaction
    open suspend fun endSession(
        sessionId: String,
        wallEndedAt: Long,
        monotonicEndedNanos: Long?,
        durationMillis: Long,
        closeReason: String? = null,
    ): GameSessionEntity? {
        endOpenSession(
            sessionId = sessionId,
            wallEndedAt = wallEndedAt,
            monotonicEndedNanos = monotonicEndedNanos,
            durationMillis = durationMillis,
            closeReason = closeReason,
        )
        return getById(sessionId)
    }

    @Transaction
    open suspend fun recoverAfterProcessRestart(
        at: Long,
        reason: String = "PROCESS_RESTART",
    ): List<GameSessionEntity> {
        invalidateIncompleteOpenSessions(at, "INCOMPLETE_SESSION_LINKS")
        recoverPending(at, reason)
        recoverActive(at, reason)
        return getRecoveryRecords()
    }

    @Transaction
    open suspend fun recoverOne(
        sessionId: String,
        at: Long,
        reason: String,
    ): GameSessionEntity? {
        recoverOpenSession(sessionId, at, reason)
        return getById(sessionId)
    }

    @Transaction
    open suspend fun abandonRecovery(
        sessionId: String,
        at: Long,
        reason: String = "USER_ABANDONED",
    ): GameSessionEntity? {
        abandonRecovered(sessionId, at, reason)
        return getById(sessionId)
    }
}
