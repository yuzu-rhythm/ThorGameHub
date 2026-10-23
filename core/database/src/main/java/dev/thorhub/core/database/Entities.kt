package dev.thorhub.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "games",
    indices = [
        Index("canonical_title"),
        Index("sort_title"),
        Index("lifecycle_status"),
        Index("is_pinned"),
    ],
)
data class GameEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "canonical_title")
    val canonicalTitle: String,
    @ColumnInfo(name = "sort_title")
    val sortTitle: String,
    @ColumnInfo(name = "lifecycle_status")
    val lifecycleStatus: GameLifecycleStatus = GameLifecycleStatus.UNKNOWN,
    val description: String? = null,
    @ColumnInfo(name = "release_year")
    val releaseYear: Int? = null,
    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "is_hidden", defaultValue = "0")
    val isHidden: Boolean = false,
    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "user_rating")
    val userRating: Int? = null,
    /** Per-field resolution source, stored as a version-tolerant JSON object. */
    @ColumnInfo(name = "metadata_provenance_json", defaultValue = "{}")
    val metadataProvenanceJson: String = "{}",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "game_versions",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("game_id"),
        Index(value = ["game_id", "platform"]),
    ],
)
data class GameVersionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "game_id")
    val gameId: String,
    val platform: String,
    val region: String? = null,
    val language: String? = null,
    val edition: String? = null,
    @ColumnInfo(name = "version_classification")
    val versionClassification: GameVersionClassification = GameVersionClassification.UNKNOWN,
    @ColumnInfo(name = "external_id")
    val externalId: String? = null,
    @ColumnInfo(name = "is_preferred", defaultValue = "0")
    val isPreferred: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "devices",
    indices = [
        Index("hardware_fingerprint"),
        Index("last_seen_at"),
        Index("availability_state"),
    ],
)
data class DeviceEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    val platform: String,
    @ColumnInfo(name = "os_version")
    val osVersion: String? = null,
    @ColumnInfo(name = "hardware_fingerprint")
    val hardwareFingerprint: String? = null,
    @ColumnInfo(name = "availability_state")
    val availabilityState: DeviceAvailabilityState = DeviceAvailabilityState.UNKNOWN,
    @ColumnInfo(name = "pairing_state")
    val pairingState: PairingState = PairingState.UNKNOWN,
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "content_fingerprints",
    indices = [
        Index(value = ["algorithm", "value"], unique = true),
        Index("identity_key"),
    ],
)
data class ContentFingerprintEntity(
    @PrimaryKey
    val id: String,
    val algorithm: String,
    val value: String,
    @ColumnInfo(name = "identity_key")
    val identityKey: String? = null,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long? = null,
    @ColumnInfo(name = "modified_at")
    val modifiedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
)

@Entity(
    tableName = "library_roots",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("device_id"),
        Index("tree_uri"),
        Index("root_state"),
    ],
)
data class LibraryRootEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,
    /** A persisted SAF tree URI string, never an Android Uri instance or raw path. */
    @ColumnInfo(name = "tree_uri")
    val treeUri: String,
    @ColumnInfo(name = "platform_hint")
    val platformHint: String? = null,
    @ColumnInfo(name = "source_type")
    val sourceType: LibraryRootSourceType = LibraryRootSourceType.UNKNOWN,
    @ColumnInfo(name = "access_flags")
    val accessFlags: Int = 0,
    @ColumnInfo(name = "provider_authority")
    val providerAuthority: String? = null,
    @ColumnInfo(name = "root_state")
    val rootState: LibraryRootState = LibraryRootState.UNKNOWN,
    @ColumnInfo(name = "last_successful_scan_at")
    val lastSuccessfulScanAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "installations",
    foreignKeys = [
        ForeignKey(
            entity = GameVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_version_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LibraryRootEntity::class,
            parentColumns = ["id"],
            childColumns = ["library_root_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ContentFingerprintEntity::class,
            parentColumns = ["id"],
            childColumns = ["content_fingerprint_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("game_version_id"),
        Index("device_id"),
        Index("library_root_id"),
        Index("content_fingerprint_id"),
        Index("availability_state"),
        Index(value = ["game_version_id", "device_id"]),
    ],
)
data class InstallationEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "game_version_id")
    val gameVersionId: String,
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,
    @ColumnInfo(name = "library_root_id")
    val libraryRootId: String? = null,
    @ColumnInfo(name = "source_type")
    val sourceType: InstallationSourceType = InstallationSourceType.UNKNOWN,
    /** A SAF URI or provider-owned external identifier, never an arbitrary private path. */
    @ColumnInfo(name = "source_locator")
    val sourceLocator: String? = null,
    @ColumnInfo(name = "content_fingerprint_id")
    val contentFingerprintId: String? = null,
    @ColumnInfo(name = "availability_state")
    val availabilityState: InstallationAvailability = InstallationAvailability.UNKNOWN,
    @ColumnInfo(name = "discovered_at")
    val discoveredAt: Long = 0L,
    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long? = null,
    @ColumnInfo(name = "missing_since")
    val missingSince: Long? = null,
    @ColumnInfo(name = "unavailable_reason")
    val unavailableReason: String? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "launchers",
    indices = [
        Index("adapter_type"),
        Index("verification_status"),
    ],
)
data class LauncherEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "adapter_type")
    val adapterType: LauncherAdapterType = LauncherAdapterType.UNKNOWN,
    /** JSON arrays of package/component candidates, with a versioned local schema. */
    @ColumnInfo(name = "package_candidates_json")
    val packageCandidatesJson: String = "[]",
    @ColumnInfo(name = "component_candidates_json")
    val componentCandidatesJson: String = "[]",
    @ColumnInfo(name = "launch_action")
    val launchAction: String? = null,
    @ColumnInfo(name = "launch_data_template")
    val launchDataTemplate: String? = null,
    @ColumnInfo(name = "extras_schema_json")
    val extrasSchemaJson: String = "{}",
    @ColumnInfo(name = "configuration_schema_version")
    val configurationSchemaVersion: Int = 1,
    @ColumnInfo(name = "capability_flags_json")
    val capabilityFlagsJson: String = "[]",
    @ColumnInfo(name = "minimum_version_code")
    val minimumVersionCode: Long? = null,
    @ColumnInfo(name = "maximum_version_code")
    val maximumVersionCode: Long? = null,
    @ColumnInfo(name = "verification_status")
    val verificationStatus: VerificationStatus = VerificationStatus.UNKNOWN,
    @ColumnInfo(name = "verified_at")
    val verifiedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "play_methods",
    foreignKeys = [
        ForeignKey(
            entity = InstallationEntity::class,
            parentColumns = ["id"],
            childColumns = ["installation_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LauncherEntity::class,
            parentColumns = ["id"],
            childColumns = ["launcher_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("installation_id"),
        Index("device_id"),
        Index("launcher_id"),
        Index("compatibility_state"),
        Index(value = ["installation_id", "device_id"]),
    ],
)
data class PlayMethodEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "installation_id")
    val installationId: String? = null,
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,
    @ColumnInfo(name = "launcher_id")
    val launcherId: String? = null,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    val priority: Int = 0,
    @ColumnInfo(name = "is_preferred", defaultValue = "0")
    val isPreferred: Boolean = false,
    @ColumnInfo(name = "compatibility_state")
    val compatibilityState: CompatibilityState = CompatibilityState.UNKNOWN,
    /** Versioned, sanitized adapter configuration; never an Android Intent/Bundle. */
    @ColumnInfo(name = "configuration_json")
    val configurationJson: String = "{}",
    @ColumnInfo(name = "configuration_schema_version")
    val configurationSchemaVersion: Int = 1,
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "last_success_at")
    val lastSuccessAt: Long? = null,
    @ColumnInfo(name = "last_failure_code")
    val lastFailureCode: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "game_sessions",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GameVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_version_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InstallationEntity::class,
            parentColumns = ["id"],
            childColumns = ["installation_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlayMethodEntity::class,
            parentColumns = ["id"],
            childColumns = ["play_method_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("game_id"),
        Index("game_version_id"),
        Index("installation_id"),
        Index("device_id"),
        Index("play_method_id"),
        Index("state"),
        Index("wall_started_at"),
        Index(value = ["game_id", "state", "wall_started_at"]),
    ],
)
data class GameSessionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "game_id")
    val gameId: String,
    @ColumnInfo(name = "game_version_id")
    val gameVersionId: String? = null,
    @ColumnInfo(name = "installation_id")
    val installationId: String? = null,
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,
    @ColumnInfo(name = "play_method_id")
    val playMethodId: String? = null,
    @ColumnInfo(name = "wall_started_at")
    val wallStartedAt: Long,
    @ColumnInfo(name = "wall_ended_at")
    val wallEndedAt: Long? = null,
    @ColumnInfo(name = "monotonic_started_nanos")
    val monotonicStartedNanos: Long,
    /** Boot-scoped identity for the currently open monotonic interval. */
    @ColumnInfo(name = "monotonic_boot_id")
    val monotonicBootId: String? = null,
    /** Last already-accounted monotonic point; never compared across boot IDs. */
    @ColumnInfo(name = "last_monotonic_nanos")
    val lastMonotonicNanos: Long? = null,
    @ColumnInfo(name = "monotonic_ended_nanos")
    val monotonicEndedNanos: Long? = null,
    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long = 0L,
    val state: SessionState = SessionState.PENDING,
    @ColumnInfo(name = "close_reason")
    val closeReason: String? = null,
    @ColumnInfo(name = "recovery_reason")
    val recoveryReason: String? = null,
    @ColumnInfo(name = "last_heartbeat_at")
    val lastHeartbeatAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GameVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_version_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GameSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("game_id"),
        Index("game_version_id"),
        Index("session_id"),
        Index("device_id"),
        Index("is_pinned"),
        Index("updated_at"),
    ],
)
data class NoteEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "game_id")
    val gameId: String? = null,
    @ColumnInfo(name = "game_version_id")
    val gameVersionId: String? = null,
    @ColumnInfo(name = "session_id")
    val sessionId: String? = null,
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,
    @ColumnInfo(name = "note_type")
    val noteType: NoteType = NoteType.TEXT,
    /** Text/transcript only; binary attachments are intentionally outside this database. */
    val body: String = "",
    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    val isPinned: Boolean = false,
    @ColumnInfo(name = "is_completed", defaultValue = "0")
    val isCompleted: Boolean = false,
    val revision: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "note_checklist_items",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("note_id"),
        Index(value = ["note_id", "position"]),
    ],
)
data class NoteChecklistItemEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "note_id")
    val noteId: String,
    val position: Int,
    val text: String,
    @ColumnInfo(name = "is_completed", defaultValue = "0")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "save_profiles",
    foreignKeys = [
        ForeignKey(
            entity = GameVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_version_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = InstallationEntity::class,
            parentColumns = ["id"],
            childColumns = ["installation_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("game_version_id"),
        Index("installation_id"),
        Index("device_id"),
        Index("save_state"),
    ],
)
data class SaveProfileEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "game_version_id")
    val gameVersionId: String? = null,
    @ColumnInfo(name = "installation_id")
    val installationId: String? = null,
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,
    @ColumnInfo(name = "adapter_id")
    val adapterId: String? = null,
    @ColumnInfo(name = "access_method")
    val accessMethod: SaveAccessMethod = SaveAccessMethod.UNKNOWN,
    /** A granted SAF URI or provider token; never an arbitrary private path. */
    @ColumnInfo(name = "root_locator")
    val rootLocator: String? = null,
    @ColumnInfo(name = "relative_include_json")
    val relativeIncludeJson: String = "[]",
    @ColumnInfo(name = "relative_exclude_json")
    val relativeExcludeJson: String = "[]",
    @ColumnInfo(name = "save_type")
    val saveType: SaveType = SaveType.UNKNOWN,
    @ColumnInfo(name = "compatibility_fingerprint")
    val compatibilityFingerprint: String? = null,
    @ColumnInfo(name = "save_state")
    val saveState: SaveState = SaveState.UNCONFIGURED,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "save_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = SaveProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["save_profile_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GameSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SaveSnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_snapshot_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("save_profile_id"),
        Index("device_id"),
        Index("session_id"),
        Index("parent_snapshot_id"),
        Index("captured_at"),
        Index("snapshot_state"),
    ],
)
data class SaveSnapshotEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "save_profile_id")
    val saveProfileId: String? = null,
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,
    @ColumnInfo(name = "session_id")
    val sessionId: String? = null,
    @ColumnInfo(name = "parent_snapshot_id")
    val parentSnapshotId: String? = null,
    @ColumnInfo(name = "capture_reason")
    val captureReason: SnapshotReason = SnapshotReason.UNKNOWN,
    @ColumnInfo(name = "snapshot_state")
    val snapshotState: SnapshotState = SnapshotState.UNKNOWN,
    @ColumnInfo(name = "compatibility_fingerprint")
    val compatibilityFingerprint: String? = null,
    @ColumnInfo(name = "manifest_schema_version")
    val manifestSchemaVersion: Int = 1,
    /** App-private relative storage token, not an absolute path. */
    @ColumnInfo(name = "storage_token")
    val storageToken: String? = null,
    @ColumnInfo(name = "content_hash")
    val contentHash: String? = null,
    @ColumnInfo(name = "captured_at")
    val capturedAt: Long = 0L,
    @ColumnInfo(name = "retention_class")
    val retentionClass: String? = null,
    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,
)

@Entity(
    tableName = "save_snapshot_files",
    foreignKeys = [
        ForeignKey(
            entity = SaveSnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["snapshot_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("snapshot_id"),
        Index(value = ["snapshot_id", "relative_path"], unique = true),
    ],
)
data class SaveSnapshotFileEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "snapshot_id")
    val snapshotId: String,
    /** Relative manifest path only; absolute/private paths are rejected before persistence. */
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "content_hash")
    val contentHash: String,
    @ColumnInfo(name = "source_modified_at")
    val sourceModifiedAt: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
)

@Entity(
    tableName = "collections",
    indices = [
        Index("collection_type"),
        Index("sort_order"),
    ],
)
data class CollectionEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    @ColumnInfo(name = "collection_type")
    val collectionType: CollectionType = CollectionType.MANUAL,
    /** Versioned query definition for smart collections; absent for manual collections. */
    @ColumnInfo(name = "query_definition_json")
    val queryDefinitionJson: String? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "collection_members",
    primaryKeys = ["collection_id", "game_id"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("collection_id"),
        Index("game_id"),
        Index(value = ["collection_id", "position"]),
    ],
)
data class CollectionMemberEntity(
    @ColumnInfo(name = "collection_id")
    val collectionId: String,
    @ColumnInfo(name = "game_id")
    val gameId: String,
    val position: Int = 0,
    @ColumnInfo(name = "added_at")
    val addedAt: Long = 0L,
)

@Entity(
    tableName = "artwork_assets",
    foreignKeys = [
        ForeignKey(
            entity = GameEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GameVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["game_version_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("game_id"),
        Index("game_version_id"),
        Index("artwork_kind"),
        Index(value = ["game_id", "artwork_kind"]),
    ],
)
data class ArtworkAssetEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "game_id")
    val gameId: String? = null,
    @ColumnInfo(name = "game_version_id")
    val gameVersionId: String? = null,
    @ColumnInfo(name = "artwork_kind")
    val artworkKind: ArtworkKind = ArtworkKind.UNKNOWN,
    /** App-private relative token or content-provider locator, never an arbitrary path. */
    val locator: String,
    val provenance: String? = null,
    @ColumnInfo(name = "license_attribution")
    val licenseAttribution: String? = null,
    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @ColumnInfo(name = "content_hash")
    val contentHash: String? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "capability_records",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("device_id"),
        Index("capability_key"),
        Index("capability_state"),
        Index(value = ["device_id", "build_fingerprint", "capability_key"], unique = true),
    ],
)
data class CapabilityRecordEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,
    @ColumnInfo(name = "build_fingerprint")
    val buildFingerprint: String? = null,
    @ColumnInfo(name = "capability_key")
    val capabilityKey: String,
    @ColumnInfo(name = "capability_state")
    val capabilityState: CapabilityState = CapabilityState.UNKNOWN,
    @ColumnInfo(name = "evidence_code")
    val evidenceCode: String? = null,
    @ColumnInfo(name = "evidence_summary")
    val evidenceSummary: String? = null,
    @ColumnInfo(name = "diagnostic_code")
    val diagnosticCode: String? = null,
    @ColumnInfo(name = "tested_at")
    val testedAt: Long = 0L,
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null,
)

@Entity(
    tableName = "diagnostic_events",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CapabilityRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["capability_record_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = GameSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("device_id"),
        Index("capability_record_id"),
        Index("session_id"),
        Index("occurred_at"),
        Index("event_code"),
    ],
)
data class DiagnosticEventEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,
    @ColumnInfo(name = "capability_record_id")
    val capabilityRecordId: String? = null,
    @ColumnInfo(name = "session_id")
    val sessionId: String? = null,
    @ColumnInfo(name = "event_code")
    val eventCode: String,
    val severity: DiagnosticSeverity = DiagnosticSeverity.INFO,
    val summary: String,
    @ColumnInfo(name = "package_name")
    val packageName: String? = null,
    @ColumnInfo(name = "component_name")
    val componentName: String? = null,
    @ColumnInfo(name = "requested_display_id")
    val requestedDisplayId: Int? = null,
    @ColumnInfo(name = "actual_display_id")
    val actualDisplayId: Int? = null,
    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long? = null,
    /** Only redacted, versioned key/value diagnostics are allowed at this boundary. */
    @ColumnInfo(name = "sanitized_metadata_json")
    val sanitizedMetadataJson: String = "{}",
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long = 0L,
)

@Entity(
    tableName = "display_assignments",
    foreignKeys = [
        ForeignKey(
            entity = DeviceEntity::class,
            parentColumns = ["id"],
            childColumns = ["device_id"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("device_id"),
        Index("display_role"),
        Index(value = ["device_id", "display_signature_hash", "build_fingerprint_hash"], unique = true),
    ],
)
data class DisplayAssignmentEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,
    /** Hash of stable public display characteristics; never a transient Android display ID. */
    @ColumnInfo(name = "display_signature_hash")
    val displaySignatureHash: String,
    @ColumnInfo(name = "display_role")
    val displayRole: DisplayRole = DisplayRole.UNKNOWN,
    val width: Int? = null,
    val height: Int? = null,
    @ColumnInfo(name = "density_dpi")
    val densityDpi: Int? = null,
    val rotation: Int? = null,
    @ColumnInfo(name = "refresh_rate_hz")
    val refreshRateHz: Float? = null,
    val flags: Int = 0,
    @ColumnInfo(name = "is_verified", defaultValue = "0")
    val isVerified: Boolean = false,
    @ColumnInfo(name = "build_fingerprint_hash")
    val buildFingerprintHash: String? = null,
    @ColumnInfo(name = "assigned_at")
    val assignedAt: Long = 0L,
    @ColumnInfo(name = "evidence_code")
    val evidenceCode: String? = null,
)
