package dev.thorhub.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CapabilityDao {
    @Upsert
    abstract suspend fun upsert(record: CapabilityRecordEntity)

    @Upsert
    abstract suspend fun upsertAll(records: List<CapabilityRecordEntity>)

    @Query(
        "SELECT * FROM capability_records WHERE device_id = :deviceId ORDER BY capability_key ASC, tested_at DESC",
    )
    abstract fun observeForDevice(deviceId: String): Flow<List<CapabilityRecordEntity>>

    @Query(
        """
        SELECT *
        FROM capability_records
        WHERE (:deviceId IS NULL OR device_id = :deviceId)
          AND capability_key = :capabilityKey
        ORDER BY tested_at DESC
        """,
    )
    abstract fun observeByKey(deviceId: String?, capabilityKey: String): Flow<List<CapabilityRecordEntity>>

    @Query("SELECT * FROM capability_records WHERE id = :recordId")
    abstract suspend fun getById(recordId: String): CapabilityRecordEntity?
}

@Dao
abstract class DiagnosticDao {
    @Upsert
    abstract suspend fun insert(event: DiagnosticEventEntity)

    @Upsert
    abstract suspend fun insertAll(events: List<DiagnosticEventEntity>)

    @Query(
        "SELECT * FROM diagnostic_events ORDER BY occurred_at DESC LIMIT :limit",
    )
    abstract fun observeRecent(limit: Int = 100): Flow<List<DiagnosticEventEntity>>

    @Query(
        "SELECT * FROM diagnostic_events WHERE session_id = :sessionId ORDER BY occurred_at ASC",
    )
    abstract fun observeForSession(sessionId: String): Flow<List<DiagnosticEventEntity>>

    @Query(
        "SELECT * FROM diagnostic_events WHERE event_code = :eventCode ORDER BY occurred_at DESC",
    )
    abstract fun observeByCode(eventCode: String): Flow<List<DiagnosticEventEntity>>
}

@Dao
abstract class DisplayAssignmentDao {
    @Upsert
    abstract suspend fun upsert(assignment: DisplayAssignmentEntity)

    @Upsert
    abstract suspend fun upsertAll(assignments: List<DisplayAssignmentEntity>)

    @Query(
        "SELECT * FROM display_assignments WHERE device_id = :deviceId ORDER BY display_role ASC, display_signature_hash ASC",
    )
    abstract fun observeForDevice(deviceId: String): Flow<List<DisplayAssignmentEntity>>

    @Query("SELECT * FROM display_assignments WHERE id = :assignmentId")
    abstract fun observeById(assignmentId: String): Flow<DisplayAssignmentEntity?>

    @Query(
        """
        SELECT *
        FROM display_assignments
        WHERE device_id = :deviceId AND build_fingerprint_hash = :buildFingerprintHash
        ORDER BY display_signature_hash ASC
        """,
    )
    abstract fun observeForBuild(
        deviceId: String,
        buildFingerprintHash: String,
    ): Flow<List<DisplayAssignmentEntity>>
}
