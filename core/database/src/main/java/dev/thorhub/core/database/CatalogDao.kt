package dev.thorhub.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DeviceDao {
    @Upsert
    abstract suspend fun upsert(device: DeviceEntity)

    @Query("SELECT * FROM devices ORDER BY display_name COLLATE NOCASE ASC")
    abstract fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :deviceId")
    abstract fun observeById(deviceId: String): Flow<DeviceEntity?>

    @Query("SELECT * FROM devices WHERE id = :deviceId")
    abstract suspend fun getById(deviceId: String): DeviceEntity?
}

@Dao
abstract class LauncherDao {
    @Upsert
    abstract suspend fun upsert(launcher: LauncherEntity)

    @Query("SELECT * FROM launchers ORDER BY display_name COLLATE NOCASE ASC")
    abstract fun observeAll(): Flow<List<LauncherEntity>>

    @Query("SELECT * FROM launchers WHERE id = :launcherId")
    abstract fun observeById(launcherId: String): Flow<LauncherEntity?>

    @Query("SELECT * FROM launchers WHERE adapter_type = :adapterType ORDER BY display_name COLLATE NOCASE ASC")
    abstract fun observeByAdapter(adapterType: LauncherAdapterType): Flow<List<LauncherEntity>>
}

@Dao
abstract class FingerprintDao {
    @Upsert
    abstract suspend fun upsert(fingerprint: ContentFingerprintEntity)

    @Query("SELECT * FROM content_fingerprints WHERE id = :fingerprintId")
    abstract fun observeById(fingerprintId: String): Flow<ContentFingerprintEntity?>

    @Query(
        "SELECT * FROM content_fingerprints WHERE algorithm = :algorithm AND value = :value LIMIT 1",
    )
    abstract suspend fun find(algorithm: String, value: String): ContentFingerprintEntity?
}

@Dao
abstract class ArtworkDao {
    @Upsert
    abstract suspend fun upsert(asset: ArtworkAssetEntity)

    @Upsert
    abstract suspend fun upsertAll(assets: List<ArtworkAssetEntity>)

    @Query(
        """
        SELECT *
        FROM artwork_assets
        WHERE game_id = :gameId
        ORDER BY CASE artwork_kind
            WHEN 'HERO' THEN 0
            WHEN 'POSTER' THEN 1
            WHEN 'CARD' THEN 2
            WHEN 'THUMBNAIL' THEN 3
            ELSE 4
        END, updated_at DESC
        """,
    )
    abstract fun observeForGame(gameId: String): Flow<List<ArtworkAssetEntity>>

    @Query("SELECT * FROM artwork_assets WHERE id = :assetId")
    abstract fun observeById(assetId: String): Flow<ArtworkAssetEntity?>
}

@Dao
abstract class LibraryRootDao {
    @Upsert
    abstract suspend fun upsert(root: LibraryRootEntity)

    @Query("SELECT * FROM library_roots WHERE id = :rootId")
    abstract suspend fun getById(rootId: String): LibraryRootEntity?

    @Query("SELECT * FROM library_roots ORDER BY updated_at DESC")
    abstract fun observeAll(): Flow<List<LibraryRootEntity>>

    @Query("SELECT * FROM library_roots WHERE id = :rootId")
    abstract fun observeById(rootId: String): Flow<LibraryRootEntity?>

    @Query("UPDATE library_roots SET root_state = :state, updated_at = :updatedAt WHERE id = :rootId")
    abstract suspend fun setState(rootId: String, state: LibraryRootState, updatedAt: Long): Int
}
