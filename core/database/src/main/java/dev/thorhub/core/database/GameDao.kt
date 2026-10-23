package dev.thorhub.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
abstract class GameDao {
    @Upsert
    abstract suspend fun upsert(game: GameEntity)

    @Upsert
    abstract suspend fun upsertAll(games: List<GameEntity>)

    @Query("SELECT * FROM games WHERE id = :gameId")
    abstract suspend fun getById(gameId: String): GameEntity?

    @Query("SELECT * FROM games WHERE id = :gameId")
    abstract fun observeById(gameId: String): Flow<GameEntity?>

    @Query(
        """
        SELECT *
        FROM games
        WHERE (:query = '' OR canonical_title LIKE '%' || :query || '%' COLLATE NOCASE
            OR sort_title LIKE '%' || :query || '%' COLLATE NOCASE)
          AND (:includeHidden = 1 OR is_hidden = 0)
        ORDER BY is_pinned DESC, is_favorite DESC, sort_title COLLATE NOCASE ASC
        """,
    )
    abstract fun observeLibrary(query: String = "", includeHidden: Boolean = false): Flow<List<GameEntity>>

    @Query(
        """
        SELECT
            g.id AS gameId,
            g.canonical_title AS canonicalTitle,
            g.sort_title AS sortTitle,
            g.lifecycle_status AS lifecycleStatus,
            g.is_favorite AS isFavorite,
            g.is_hidden AS isHidden,
            COUNT(DISTINCT CASE
                WHEN i.availability_state = 'AVAILABLE' THEN i.id
            END) AS availableInstallationCount
        FROM games g
        LEFT JOIN game_versions gv ON gv.game_id = g.id
        LEFT JOIN installations i ON i.game_version_id = gv.id
        WHERE (:query = '' OR g.canonical_title LIKE '%' || :query || '%' COLLATE NOCASE
            OR g.sort_title LIKE '%' || :query || '%' COLLATE NOCASE)
          AND (:includeHidden = 1 OR g.is_hidden = 0)
        GROUP BY g.id, g.canonical_title, g.sort_title, g.lifecycle_status,
            g.is_favorite, g.is_hidden
        ORDER BY g.is_pinned DESC, g.sort_title COLLATE NOCASE ASC
        """,
    )
    abstract fun observeSearch(
        query: String,
        includeHidden: Boolean = false,
    ): Flow<List<SearchGameRow>>

    @Query(
        """
        SELECT
            g.id AS gameId,
            g.canonical_title AS canonicalTitle,
            g.sort_title AS sortTitle,
            g.is_pinned AS isPinned,
            g.is_favorite AS isFavorite,
            g.is_hidden AS isHidden,
            (
                SELECT COUNT(*)
                FROM installations i
                INNER JOIN game_versions iv ON iv.id = i.game_version_id
                WHERE iv.game_id = g.id AND i.availability_state = 'AVAILABLE'
            ) AS availableInstallationCount,
            COALESCE(
                (
                    SELECT pv.platform
                    FROM game_versions pv
                    WHERE pv.game_id = g.id
                    ORDER BY pv.is_preferred DESC, pv.created_at ASC, pv.id ASC
                    LIMIT 1
                ),
                'UNKNOWN'
            ) AS platform,
            g.lifecycle_status AS lifecycleStatus,
            (
                SELECT COALESCE(gs.wall_ended_at, gs.wall_started_at)
                FROM game_sessions gs
                WHERE gs.game_id = g.id
                  AND gs.state IN ('ENDED', 'ACTIVE', 'SUSPENDED', 'RECOVERED_ACTIVE', 'RECOVERED')
                ORDER BY COALESCE(gs.wall_ended_at, gs.wall_started_at) DESC
                LIMIT 1
            ) AS lastSessionAt,
            (
                SELECT COUNT(*)
                FROM game_sessions cs
                WHERE cs.game_id = g.id
                  AND cs.state IN ('ENDED', 'ACTIVE', 'SUSPENDED', 'RECOVERED_ACTIVE', 'RECOVERED')
            ) AS sessionCount
        FROM games g
        WHERE (:query = '' OR g.canonical_title LIKE '%' || :query || '%' COLLATE NOCASE
            OR g.sort_title LIKE '%' || :query || '%' COLLATE NOCASE)
          AND (:includeHidden = 1 OR g.is_hidden = 0)
          AND (
              :platform = 'ALL' OR
              COALESCE(
                  (
                      SELECT fp.platform
                      FROM game_versions fp
                      WHERE fp.game_id = g.id
                      ORDER BY fp.is_preferred DESC, fp.created_at ASC, fp.id ASC
                      LIMIT 1
                  ),
                  'UNKNOWN'
              ) = :platform
          )
          AND (:lifecycle = 'ALL' OR g.lifecycle_status = :lifecycle)
        ORDER BY
            CASE WHEN :sortMode = 'RECENT' THEN lastSessionAt END DESC,
            CASE WHEN :sortMode = 'PINNED' THEN g.is_pinned END DESC,
            CASE WHEN :sortMode = 'PINNED' THEN g.is_favorite END DESC,
            g.sort_title COLLATE NOCASE ASC,
            g.id ASC
        """,
    )
    abstract fun pagingLibrary(
        query: String = "",
        platform: String = "ALL",
        lifecycle: String = "ALL",
        sortMode: String = "TITLE",
        includeHidden: Boolean = false,
    ): PagingSource<Int, LibraryGameRow>

    @Query(
        """
        SELECT
            g.id AS gameId,
            g.canonical_title AS canonicalTitle,
            g.sort_title AS sortTitle,
            g.is_pinned AS isPinned,
            g.is_favorite AS isFavorite,
            g.is_hidden AS isHidden,
            (
                SELECT COUNT(*)
                FROM installations i
                INNER JOIN game_versions iv ON iv.id = i.game_version_id
                WHERE iv.game_id = g.id AND i.availability_state = 'AVAILABLE'
            ) AS availableInstallationCount,
            COALESCE(
                (
                    SELECT pv.platform
                    FROM game_versions pv
                    WHERE pv.game_id = g.id
                    ORDER BY pv.is_preferred DESC, pv.created_at ASC, pv.id ASC
                    LIMIT 1
                ),
                'UNKNOWN'
            ) AS platform,
            g.lifecycle_status AS lifecycleStatus,
            (
                SELECT COALESCE(gs.wall_ended_at, gs.wall_started_at)
                FROM game_sessions gs
                WHERE gs.game_id = g.id
                  AND gs.state IN ('ENDED', 'ACTIVE', 'SUSPENDED', 'RECOVERED_ACTIVE', 'RECOVERED')
                ORDER BY COALESCE(gs.wall_ended_at, gs.wall_started_at) DESC
                LIMIT 1
            ) AS lastSessionAt,
            (
                SELECT COUNT(*)
                FROM game_sessions cs
                WHERE cs.game_id = g.id
                  AND cs.state IN ('ENDED', 'ACTIVE', 'SUSPENDED', 'RECOVERED_ACTIVE', 'RECOVERED')
            ) AS sessionCount
        FROM games g
        WHERE g.is_hidden = 0
        ORDER BY g.is_pinned DESC, g.is_favorite DESC, g.sort_title COLLATE NOCASE ASC, g.id ASC
        LIMIT :limit
        """,
    )
    abstract fun observeLibraryPreview(limit: Int = 24): Flow<List<LibraryGameRow>>

    @Query(
        """
        SELECT COUNT(*)
        FROM games g
        WHERE (:query = '' OR g.canonical_title LIKE '%' || :query || '%' COLLATE NOCASE
            OR g.sort_title LIKE '%' || :query || '%' COLLATE NOCASE)
          AND g.is_hidden = 0
        """,
    )
    abstract fun observeVisibleLibraryCount(query: String = ""): Flow<Int>

    @Query(
        """
        SELECT COUNT(*)
        FROM games g
        WHERE (:query = '' OR g.canonical_title LIKE '%' || :query || '%' COLLATE NOCASE
            OR g.sort_title LIKE '%' || :query || '%' COLLATE NOCASE)
          AND g.is_hidden = 0
          AND COALESCE(
              (
                  SELECT gv.platform
                  FROM game_versions gv
                  WHERE gv.game_id = g.id
                  ORDER BY gv.is_preferred DESC, gv.created_at ASC, gv.id ASC
                  LIMIT 1
              ),
              'UNKNOWN'
          ) = 'UNKNOWN'
        """,
    )
    abstract fun observeUnknownPlatformCount(query: String = ""): Flow<Int>

    @Query("SELECT DISTINCT platform FROM game_versions ORDER BY platform COLLATE NOCASE ASC")
    abstract fun observePlatforms(): Flow<List<String>>

    @Query(
        """
        SELECT
            g.id AS gameId,
            g.canonical_title AS canonicalTitle,
            g.sort_title AS sortTitle,
            g.is_pinned AS isPinned,
            (
                SELECT gs.id
                FROM game_sessions gs
                WHERE gs.game_id = g.id
                  AND gs.state IN ('ENDED', 'ACTIVE', 'SUSPENDED', 'RECOVERED_ACTIVE', 'RECOVERED')
                ORDER BY COALESCE(gs.wall_ended_at, gs.wall_started_at) DESC
                LIMIT 1
            ) AS lastSessionId,
            (
                SELECT COALESCE(gs.wall_ended_at, gs.wall_started_at)
                FROM game_sessions gs
                WHERE gs.game_id = g.id
                  AND gs.state IN ('ENDED', 'ACTIVE', 'SUSPENDED', 'RECOVERED_ACTIVE', 'RECOVERED')
                ORDER BY COALESCE(gs.wall_ended_at, gs.wall_started_at) DESC
                LIMIT 1
            ) AS lastSessionAt,
            COUNT(s.id) AS sessionCount
        FROM games g
        LEFT JOIN game_sessions s
            ON s.game_id = g.id
           AND s.state IN ('ENDED', 'ACTIVE', 'SUSPENDED', 'RECOVERED_ACTIVE', 'RECOVERED')
        WHERE g.is_hidden = 0
          AND (g.is_pinned = 1 OR s.id IS NOT NULL)
        GROUP BY g.id, g.canonical_title, g.sort_title, g.is_pinned
        ORDER BY g.is_pinned DESC, lastSessionAt DESC, g.sort_title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    abstract fun observeContinue(limit: Int = 20): Flow<List<ContinueGameRow>>

    @Query("UPDATE games SET is_pinned = :isPinned, updated_at = :updatedAt WHERE id = :gameId")
    abstract suspend fun setPinned(gameId: String, isPinned: Boolean, updatedAt: Long): Int

    @Query("UPDATE games SET is_favorite = :isFavorite, updated_at = :updatedAt WHERE id = :gameId")
    abstract suspend fun setFavorite(gameId: String, isFavorite: Boolean, updatedAt: Long): Int

    @Query("UPDATE games SET is_hidden = :isHidden, updated_at = :updatedAt WHERE id = :gameId")
    abstract suspend fun setHidden(gameId: String, isHidden: Boolean, updatedAt: Long): Int

    @Transaction
    open suspend fun upsertGameWithVersions(
        game: GameEntity,
        versions: List<GameVersionEntity>,
    ) {
        upsert(game)
        upsertVersions(versions)
    }

    @Upsert
    protected abstract suspend fun upsertVersions(versions: List<GameVersionEntity>)
}
