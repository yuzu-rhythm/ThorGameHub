package dev.thorhub.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CollectionDao {
    @Upsert
    abstract suspend fun upsert(collection: CollectionEntity)

    @Upsert
    protected abstract suspend fun upsertMembers(members: List<CollectionMemberEntity>)

    @Query("SELECT * FROM collections ORDER BY sort_order ASC, title COLLATE NOCASE ASC")
    abstract fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :collectionId")
    abstract fun observeById(collectionId: String): Flow<CollectionEntity?>

    @Query("SELECT * FROM collection_members WHERE collection_id = :collectionId ORDER BY position ASC")
    abstract fun observeMembers(collectionId: String): Flow<List<CollectionMemberEntity>>

    @Query("DELETE FROM collection_members WHERE collection_id = :collectionId")
    protected abstract suspend fun deleteMembers(collectionId: String)

    @Query(
        """
        SELECT
            cm.collection_id AS collectionId,
            cm.game_id AS gameId,
            cm.position AS position,
            g.canonical_title AS canonicalTitle,
            g.sort_title AS sortTitle
        FROM collection_members cm
        INNER JOIN games g ON g.id = cm.game_id
        WHERE cm.collection_id = :collectionId
        ORDER BY cm.position ASC, g.sort_title COLLATE NOCASE ASC
        """,
    )
    abstract fun observeGames(collectionId: String): Flow<List<CollectionGameRow>>

    /** Manual collection membership changes are one atomic replacement. */
    @Transaction
    open suspend fun replaceMembers(
        collectionId: String,
        members: List<CollectionMemberEntity>,
    ) {
        deleteMembers(collectionId)
        if (members.isNotEmpty()) {
            upsertMembers(members.map { it.copy(collectionId = collectionId) })
        }
    }

    @Transaction
    open suspend fun upsertWithMembers(
        collection: CollectionEntity,
        members: List<CollectionMemberEntity>,
    ) {
        upsert(collection)
        replaceMembers(collection.id, members)
    }
}
