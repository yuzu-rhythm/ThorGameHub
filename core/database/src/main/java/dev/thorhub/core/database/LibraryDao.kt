package dev.thorhub.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LibraryDao {
    @Upsert
    protected abstract suspend fun upsertGames(games: List<GameEntity>)

    @Upsert
    protected abstract suspend fun upsertVersions(versions: List<GameVersionEntity>)

    @Upsert
    protected abstract suspend fun upsertDevices(devices: List<DeviceEntity>)

    @Upsert
    protected abstract suspend fun upsertRoots(roots: List<LibraryRootEntity>)

    @Upsert
    protected abstract suspend fun upsertFingerprints(fingerprints: List<ContentFingerprintEntity>)

    @Upsert
    protected abstract suspend fun upsertInstallations(installations: List<InstallationEntity>)

    @Query(
        """
        UPDATE installations
        SET availability_state = 'UNAVAILABLE',
            missing_since = COALESCE(missing_since, :reconciledAt),
            unavailable_reason = :unavailableReason,
            updated_at = :reconciledAt
        WHERE library_root_id = :libraryRootId
          AND id NOT IN (:seenInstallationIds)
          AND availability_state != 'UNAVAILABLE'
        """,
    )
    protected abstract suspend fun markMissingExcept(
        libraryRootId: String,
        seenInstallationIds: List<String>,
        reconciledAt: Long,
        unavailableReason: String,
    ): Int

    @Query(
        """
        UPDATE installations
        SET availability_state = 'UNAVAILABLE',
            missing_since = COALESCE(missing_since, :reconciledAt),
            unavailable_reason = :unavailableReason,
            updated_at = :reconciledAt
        WHERE library_root_id = :libraryRootId
          AND availability_state != 'UNAVAILABLE'
        """,
    )
    protected abstract suspend fun markAllMissing(
        libraryRootId: String,
        reconciledAt: Long,
        unavailableReason: String,
    ): Int

    @Transaction
    open suspend fun importLibrary(
        import: LibraryImport,
        reconciliation: ScanReconciliation? = null,
    ) {
        // Parent rows are written first so foreign keys stay enabled in every
        // environment, including in-memory instrumentation databases.
        upsertDevices(import.devices)
        upsertGames(import.games)
        upsertRoots(import.roots)
        upsertFingerprints(import.fingerprints)
        upsertVersions(import.versions)
        upsertInstallations(import.installations)

        reconciliation?.let { scan ->
            if (scan.seenInstallationIds.isEmpty()) {
                markAllMissing(scan.libraryRootId, scan.reconciledAt, scan.unavailableReason)
            } else {
                markMissingExcept(
                    libraryRootId = scan.libraryRootId,
                    seenInstallationIds = scan.seenInstallationIds,
                    reconciledAt = scan.reconciledAt,
                    unavailableReason = scan.unavailableReason,
                )
            }
        }
    }

    @Query("SELECT * FROM library_roots ORDER BY updated_at DESC")
    abstract fun observeRoots(): Flow<List<LibraryRootEntity>>

    @Query("SELECT * FROM library_roots WHERE id = :rootId")
    abstract fun observeRoot(rootId: String): Flow<LibraryRootEntity?>

    @Query("SELECT * FROM library_roots WHERE id = :rootId")
    abstract suspend fun getRoot(rootId: String): LibraryRootEntity?
}
