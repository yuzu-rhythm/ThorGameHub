package dev.thorhub.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class InstallationDao {
    @Upsert
    abstract suspend fun upsert(installation: InstallationEntity)

    @Upsert
    abstract suspend fun upsertAll(installations: List<InstallationEntity>)

    @Query("SELECT * FROM installations WHERE id = :installationId")
    abstract suspend fun getById(installationId: String): InstallationEntity?

    @Query("SELECT * FROM installations WHERE id = :installationId")
    abstract fun observeById(installationId: String): Flow<InstallationEntity?>

    @Query(
        """
        SELECT i.*
        FROM installations i
        INNER JOIN game_versions gv ON gv.id = i.game_version_id
        WHERE gv.game_id = :gameId
        ORDER BY CASE i.availability_state
            WHEN 'AVAILABLE' THEN 0
            WHEN 'NEEDS_RESCAN' THEN 1
            ELSE 2
        END, i.updated_at DESC
        """,
    )
    abstract fun observeForGame(gameId: String): Flow<List<InstallationEntity>>

    @Query("SELECT * FROM installations WHERE library_root_id = :libraryRootId ORDER BY updated_at DESC")
    abstract fun observeForRoot(libraryRootId: String): Flow<List<InstallationEntity>>

    @Query(
        "SELECT * FROM installations WHERE availability_state = 'UNAVAILABLE' ORDER BY missing_since DESC",
    )
    abstract fun observeUnavailable(): Flow<List<InstallationEntity>>

    @Query(
        """
        UPDATE installations
        SET availability_state = 'UNAVAILABLE',
            missing_since = COALESCE(missing_since, :at),
            unavailable_reason = :reason,
            updated_at = :at
        WHERE id = :installationId
        """,
    )
    abstract suspend fun markUnavailable(installationId: String, at: Long, reason: String): Int

    @Query(
        """
        UPDATE installations
        SET availability_state = 'AVAILABLE',
            last_seen_at = :at,
            missing_since = NULL,
            unavailable_reason = NULL,
            updated_at = :at
        WHERE id = :installationId
        """,
    )
    abstract suspend fun markAvailable(installationId: String, at: Long): Int

    @Query(
        """
        UPDATE installations
        SET availability_state = 'UNAVAILABLE',
            missing_since = COALESCE(missing_since, :at),
            unavailable_reason = :reason,
            updated_at = :at
        WHERE library_root_id = :libraryRootId
          AND id NOT IN (:seenInstallationIds)
          AND availability_state != 'UNAVAILABLE'
        """,
    )
    protected abstract suspend fun markMissingExcept(
        libraryRootId: String,
        seenInstallationIds: List<String>,
        at: Long,
        reason: String,
    ): Int

    @Query(
        """
        UPDATE installations
        SET availability_state = 'UNAVAILABLE',
            missing_since = COALESCE(missing_since, :at),
            unavailable_reason = :reason,
            updated_at = :at
        WHERE library_root_id = :libraryRootId
          AND availability_state != 'UNAVAILABLE'
        """,
    )
    protected abstract suspend fun markAllMissing(libraryRootId: String, at: Long, reason: String): Int

    @Transaction
    open suspend fun reconcileScan(
        libraryRootId: String,
        seenInstallationIds: List<String>,
        at: Long,
        reason: String = "MISSING_FROM_SCAN",
    ): Int {
        return if (seenInstallationIds.isEmpty()) {
            markAllMissing(libraryRootId, at, reason)
        } else {
            markMissingExcept(libraryRootId, seenInstallationIds, at, reason)
        }
    }
}
