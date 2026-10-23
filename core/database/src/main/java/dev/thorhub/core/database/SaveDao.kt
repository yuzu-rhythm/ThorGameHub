package dev.thorhub.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SaveDao {
    @Upsert
    abstract suspend fun upsertProfile(profile: SaveProfileEntity)

    @Upsert
    abstract suspend fun upsertSnapshot(snapshot: SaveSnapshotEntity)

    @Upsert
    protected abstract suspend fun upsertSnapshotFiles(files: List<SaveSnapshotFileEntity>)

    @Query("SELECT * FROM save_profiles WHERE id = :profileId")
    abstract suspend fun getProfile(profileId: String): SaveProfileEntity?

    @Query("SELECT * FROM save_profiles WHERE id = :profileId")
    abstract fun observeProfile(profileId: String): Flow<SaveProfileEntity?>

    @Query(
        "SELECT * FROM save_profiles WHERE game_version_id = :gameVersionId ORDER BY updated_at DESC",
    )
    abstract fun observeProfilesForVersion(gameVersionId: String): Flow<List<SaveProfileEntity>>

    @Query(
        """
        SELECT sp.* FROM save_profiles sp
        INNER JOIN game_versions gv ON gv.id = sp.game_version_id
        WHERE gv.game_id = :gameId
        ORDER BY sp.updated_at DESC
        """,
    )
    abstract fun observeProfilesForGame(gameId: String): Flow<List<SaveProfileEntity>>

    @Query("SELECT * FROM save_snapshots WHERE id = :snapshotId")
    abstract suspend fun getSnapshot(snapshotId: String): SaveSnapshotEntity?

    @Query("SELECT * FROM save_snapshots WHERE id = :snapshotId")
    abstract fun observeSnapshot(snapshotId: String): Flow<SaveSnapshotEntity?>

    @Query(
        """
        SELECT *
        FROM save_snapshots
        WHERE save_profile_id = :profileId
        ORDER BY captured_at DESC
        """,
    )
    abstract fun observeSnapshots(profileId: String): Flow<List<SaveSnapshotEntity>>

    @Query(
        """
        SELECT ss.* FROM save_snapshots ss
        INNER JOIN save_profiles sp ON sp.id = ss.save_profile_id
        INNER JOIN game_versions gv ON gv.id = sp.game_version_id
        WHERE gv.game_id = :gameId
        ORDER BY ss.captured_at DESC
        """,
    )
    abstract fun observeSnapshotsForGame(gameId: String): Flow<List<SaveSnapshotEntity>>

    @Query(
        """
        SELECT * FROM save_snapshots
        WHERE save_profile_id = :profileId AND snapshot_state = 'COMPLETE'
        ORDER BY captured_at DESC LIMIT 1
        """,
    )
    abstract suspend fun latestCompleteSnapshot(profileId: String): SaveSnapshotEntity?

    @Query(
        """
        SELECT *
        FROM save_snapshots
        WHERE parent_snapshot_id = :parentSnapshotId
        ORDER BY captured_at ASC
        """,
    )
    abstract fun observeChildren(parentSnapshotId: String): Flow<List<SaveSnapshotEntity>>

    @Query(
        "SELECT * FROM save_snapshot_files WHERE snapshot_id = :snapshotId ORDER BY relative_path ASC",
    )
    abstract fun observeFiles(snapshotId: String): Flow<List<SaveSnapshotFileEntity>>

    @Query(
        "SELECT * FROM save_snapshot_files WHERE snapshot_id = :snapshotId ORDER BY relative_path ASC",
    )
    abstract suspend fun getFiles(snapshotId: String): List<SaveSnapshotFileEntity>

    @Query("DELETE FROM save_snapshot_files WHERE snapshot_id = :snapshotId")
    protected abstract suspend fun deleteFiles(snapshotId: String)

    /** Stores the snapshot and manifest rows as one durable unit. */
    @Transaction
    open suspend fun saveSnapshotWithFiles(
        snapshot: SaveSnapshotEntity,
        files: List<SaveSnapshotFileEntity>,
    ) {
        upsertSnapshot(snapshot)
        deleteFiles(snapshot.id)
        if (files.isNotEmpty()) {
            upsertSnapshotFiles(files.map { it.copy(snapshotId = snapshot.id) })
        }
    }

    /**
     * Returns the root-to-child lineage for a snapshot.  The traversal is
     * intentionally read-only and cycle-guarded; restore is a separate policy.
     */
    @Transaction
    open suspend fun getLineage(snapshotId: String): List<SaveSnapshotEntity> {
        val lineage = mutableListOf<SaveSnapshotEntity>()
        val visited = mutableSetOf<String>()
        var current = getSnapshot(snapshotId)
        while (current != null && visited.add(current.id)) {
            lineage += current
            current = if (current.parentSnapshotId == null) {
                null
            } else {
                getSnapshot(current.parentSnapshotId)
            }
        }
        return lineage.asReversed()
    }
}
