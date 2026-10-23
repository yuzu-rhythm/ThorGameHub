package dev.thorhub.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class NotesDao {
    @Upsert
    protected abstract suspend fun upsertNote(note: NoteEntity)

    @Update
    protected abstract suspend fun updateNote(note: NoteEntity): Int

    @Upsert
    protected abstract suspend fun upsertChecklistItems(items: List<NoteChecklistItemEntity>)

    @Query("DELETE FROM note_checklist_items WHERE note_id = :noteId")
    protected abstract suspend fun deleteChecklistItems(noteId: String)

    @Query("SELECT * FROM notes WHERE id = :noteId")
    abstract suspend fun getById(noteId: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :noteId")
    abstract fun observeById(noteId: String): Flow<NoteEntity?>

    @Query(
        """
        SELECT *
        FROM notes
        WHERE game_id = :gameId
        ORDER BY is_pinned DESC, updated_at DESC
        """,
    )
    abstract fun observeForGame(gameId: String): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT *
        FROM notes
        WHERE session_id = :sessionId
        ORDER BY updated_at DESC
        """,
    )
    abstract fun observeForSession(sessionId: String): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT *
        FROM notes
        WHERE is_pinned = 1
          AND (:gameId IS NULL OR game_id = :gameId)
        ORDER BY updated_at DESC
        """,
    )
    abstract fun observePinned(gameId: String? = null): Flow<List<NoteEntity>>

    @Query(
        """
        SELECT *
        FROM notes
        WHERE (:gameId IS NULL OR game_id = :gameId)
          AND body LIKE '%' || :query || '%' COLLATE NOCASE
        ORDER BY is_pinned DESC, updated_at DESC
        """,
    )
    abstract fun search(query: String, gameId: String? = null): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM note_checklist_items WHERE note_id = :noteId ORDER BY position ASC",
    )
    abstract suspend fun getChecklist(noteId: String): List<NoteChecklistItemEntity>

    @Query(
        "SELECT * FROM note_checklist_items WHERE note_id = :noteId ORDER BY position ASC",
    )
    abstract fun observeChecklist(noteId: String): Flow<List<NoteChecklistItemEntity>>

    @Query(
        "UPDATE notes SET is_pinned = :isPinned, revision = revision + 1, updated_at = :updatedAt WHERE id = :noteId",
    )
    abstract suspend fun setPinned(noteId: String, isPinned: Boolean, updatedAt: Long): Int

    @Query(
        "UPDATE notes SET is_completed = :isCompleted, revision = revision + 1, updated_at = :updatedAt WHERE id = :noteId",
    )
    abstract suspend fun setCompleted(noteId: String, isCompleted: Boolean, updatedAt: Long): Int

    @Query(
        "UPDATE notes SET body = :body, revision = revision + 1, updated_at = :updatedAt WHERE id = :noteId",
    )
    abstract suspend fun updateBody(noteId: String, body: String, updatedAt: Long): Int

    /** Replaces checklist children atomically with the note revision. */
    @Transaction
    open suspend fun saveNoteWithChecklist(
        note: NoteEntity,
        checklistItems: List<NoteChecklistItemEntity>,
    ): NoteWithChecklist {
        upsertNote(note)
        deleteChecklistItems(note.id)
        if (checklistItems.isNotEmpty()) {
            upsertChecklistItems(checklistItems.map { it.copy(noteId = note.id) })
        }
        return NoteWithChecklist(
            note = getById(note.id) ?: error("Note insert did not persist: ${note.id}"),
            checklistItems = getChecklist(note.id),
        )
    }

    @Transaction
    open suspend fun updateNoteAndChecklist(
        note: NoteEntity,
        checklistItems: List<NoteChecklistItemEntity>,
    ): NoteWithChecklist = saveNoteWithChecklist(note, checklistItems)

    @Transaction
    open suspend fun getNoteWithChecklist(noteId: String): NoteWithChecklist? {
        val note = getById(noteId) ?: return null
        return NoteWithChecklist(note = note, checklistItems = getChecklist(noteId))
    }
}
