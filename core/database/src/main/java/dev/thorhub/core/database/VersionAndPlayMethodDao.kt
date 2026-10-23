package dev.thorhub.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GameVersionDao {
    @Upsert
    abstract suspend fun upsert(version: GameVersionEntity)

    @Upsert
    abstract suspend fun upsertAll(versions: List<GameVersionEntity>)

    @Query("SELECT * FROM game_versions WHERE id = :versionId")
    abstract fun observeById(versionId: String): Flow<GameVersionEntity?>

    @Query("SELECT * FROM game_versions WHERE id = :versionId")
    abstract suspend fun getById(versionId: String): GameVersionEntity?

    @Query("SELECT * FROM game_versions ORDER BY game_id, is_preferred DESC, platform ASC")
    abstract fun observeAll(): Flow<List<GameVersionEntity>>

    @Query(
        "SELECT * FROM game_versions WHERE game_id = :gameId ORDER BY is_preferred DESC, platform ASC, edition ASC",
    )
    abstract fun observeForGame(gameId: String): Flow<List<GameVersionEntity>>
}

@Dao
abstract class PlayMethodDao {
    @Upsert
    abstract suspend fun upsert(playMethod: PlayMethodEntity)

    @Upsert
    abstract suspend fun upsertAll(playMethods: List<PlayMethodEntity>)

    @Query("SELECT * FROM play_methods WHERE id = :playMethodId")
    abstract fun observeById(playMethodId: String): Flow<PlayMethodEntity?>

    @Query("SELECT * FROM play_methods WHERE id = :playMethodId")
    abstract suspend fun getById(playMethodId: String): PlayMethodEntity?

    @Query(
        """
        SELECT pm.*
        FROM play_methods pm
        INNER JOIN installations i ON i.id = pm.installation_id
        INNER JOIN game_versions gv ON gv.id = i.game_version_id
        WHERE gv.game_id = :gameId
        ORDER BY pm.is_preferred DESC, pm.priority DESC, pm.display_name COLLATE NOCASE ASC
        """,
    )
    abstract fun observeForGame(gameId: String): Flow<List<PlayMethodEntity>>

    @Query(
        "SELECT * FROM play_methods WHERE compatibility_state = :state ORDER BY priority DESC, display_name COLLATE NOCASE ASC",
    )
    abstract fun observeByCompatibility(state: CompatibilityState): Flow<List<PlayMethodEntity>>
}
