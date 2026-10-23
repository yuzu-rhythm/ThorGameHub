package dev.thorhub.core.model

import java.time.Duration
import java.time.Instant

private fun requireText(value: String, field: String): String {
    require(value.isNotBlank()) { "$field must not be blank" }
    return value.trim()
}

private fun requireNonNegative(value: Long, field: String): Long {
    require(value >= 0L) { "$field must not be negative" }
    return value
}

enum class GameLifecycle {
    UNKNOWN,
    BACKLOG,
    PLAYING,
    COMPLETED,
    ABANDONED,
}

enum class GamePlatform {
    ANDROID,
    ARCADE,
    GAME_NATIVE,
    PC,
    PLAYSTATION,
    NINTENDO,
    XBOX,
    STREAMING,
    UNKNOWN,
}

enum class GameVersionKind {
    ORIGINAL,
    HACK,
    FAN_TRANSLATION,
    REMASTER,
    COLLECTION,
    DEMO,
    UNKNOWN,
}

enum class InstallationSourceType {
    ROM,
    ANDROID_APP,
    GAME_NATIVE,
    STEAM,
    STREAM,
    MANUAL,
    IMPORTED,
    UNKNOWN,
}

typealias SourceType = InstallationSourceType

enum class InstallationAvailability {
    UNKNOWN,
    AVAILABLE,
    MISSING,
    UNAVAILABLE,
    REVOKED,
    ERROR,
}

enum class DevicePlatform {
    ANDROID,
    WINDOWS,
    MACOS,
    LINUX,
    IOS,
    UNKNOWN,
}

enum class DeviceAvailability {
    UNKNOWN,
    ONLINE,
    OFFLINE,
    UNREACHABLE,
}

enum class TrustState {
    UNKNOWN,
    UNPAIRED,
    PENDING,
    TRUSTED,
    REVOKED,
}

enum class LauncherAdapterType {
    ANDROID_NATIVE,
    RETROARCH,
    GAME_NATIVE,
    CONFIGURABLE_EXTERNAL,
    UNKNOWN,
}

enum class VerificationState {
    UNKNOWN,
    UNVERIFIED,
    VERIFIED,
    STALE,
    FAILED,
}

enum class LaunchParameterType {
    TEXT,
    INTEGER,
    BOOLEAN,
    TEXT_LIST,
}

sealed interface LaunchExtraValue {
    val type: LaunchParameterType

    data class Text(val value: String) : LaunchExtraValue {
        override val type: LaunchParameterType = LaunchParameterType.TEXT
    }

    data class Integer(val value: Long) : LaunchExtraValue {
        override val type: LaunchParameterType = LaunchParameterType.INTEGER
    }

    data class BooleanValue(val value: Boolean) : LaunchExtraValue {
        override val type: LaunchParameterType = LaunchParameterType.BOOLEAN
    }

    data class TextList(val value: List<String>) : LaunchExtraValue {
        init {
            require(value.none { it.isBlank() }) { "Text-list launch extras must not contain blanks" }
        }

        override val type: LaunchParameterType = LaunchParameterType.TEXT_LIST
    }
}

enum class ContentUriGrantMode {
    NONE,
    READ,
    READ_WRITE,
}

data class LaunchParameterPayload(
    val action: String? = null,
    val dataTemplate: String? = null,
    val componentTemplate: String? = null,
    val extras: Map<String, LaunchExtraValue> = emptyMap(),
    val contentUriGrant: ContentUriGrantMode = ContentUriGrantMode.NONE,
) {
    init {
        require(action == null || action.isNotBlank()) { "Launch action must not be blank" }
        require(dataTemplate == null || dataTemplate.isNotBlank()) { "Data template must not be blank" }
        require(componentTemplate == null || componentTemplate.isNotBlank()) {
            "Component template must not be blank"
        }
        require(extras.keys.none { it.isBlank() }) { "Launch extra keys must not be blank" }
    }
}

data class Game(
    val id: GameId,
    val title: String,
    val sortTitle: String = title,
    val lifecycle: GameLifecycle = GameLifecycle.UNKNOWN,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    val description: String? = null,
    val releaseYear: Int? = null,
    val artworkIds: List<ArtworkAssetId> = emptyList(),
    val tags: Set<String> = emptySet(),
    val userRating: Int? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = createdAt,
) {
    init {
        requireText(title, "Game title")
        requireText(sortTitle, "Game sort title")
        require(releaseYear == null || releaseYear in 1800..3000) { "Game release year is out of range" }
        require(userRating == null || userRating in 0..5) { "Game rating must be between 0 and 5" }
        require(tags.none { it.isBlank() }) { "Game tags must not contain blanks" }
    }
}

data class GameVersion(
    val id: GameVersionId,
    val gameId: GameId,
    val platform: GamePlatform,
    val region: String? = null,
    val language: String? = null,
    val edition: String? = null,
    val revision: String? = null,
    val kind: GameVersionKind = GameVersionKind.ORIGINAL,
    val externalIdentifiers: Map<String, String> = emptyMap(),
    val preferredForDeviceIds: Set<DeviceId> = emptySet(),
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = createdAt,
) {
    init {
        require(externalIdentifiers.keys.none { it.isBlank() }) { "External identifier namespaces must not be blank" }
        require(externalIdentifiers.values.none { it.isBlank() }) { "External identifiers must not be blank" }
    }
}

data class Device(
    val id: DeviceId,
    val displayName: String,
    val platform: DevicePlatform,
    val buildFingerprint: BuildFingerprint? = null,
    val availability: DeviceAvailability = DeviceAvailability.UNKNOWN,
    val trust: TrustState = TrustState.UNKNOWN,
    val lastSeenAt: Instant? = null,
    val capabilities: Set<CapabilityType> = emptySet(),
) {
    init {
        requireText(displayName, "Device display name")
    }
}

sealed interface InstallationReference {
    val value: String

    data class GrantedDocument(override val value: String) : InstallationReference {
        init {
            requireText(value, "Granted document reference")
        }
    }

    data class ExternalIdentifier(override val value: String) : InstallationReference {
        init {
            requireText(value, "External installation identifier")
        }
    }

    data class Manual(override val value: String) : InstallationReference {
        init {
            requireText(value, "Manual installation reference")
        }
    }
}

data class Installation(
    val id: InstallationId,
    val gameVersionId: GameVersionId,
    val deviceId: DeviceId,
    val sourceType: InstallationSourceType,
    val reference: InstallationReference,
    val availability: InstallationAvailability = InstallationAvailability.UNKNOWN,
    val contentFingerprint: ContentFingerprint? = null,
    val discoveredAt: Instant = Instant.EPOCH,
    val lastSeenAt: Instant = discoveredAt,
    val unavailableReason: String? = null,
)

data class LauncherVersionConstraint(
    val minimumVersion: String? = null,
    val maximumVersion: String? = null,
    val exactVersions: Set<String> = emptySet(),
) {
    init {
        require(minimumVersion == null || minimumVersion.isNotBlank()) { "Minimum launcher version must not be blank" }
        require(maximumVersion == null || maximumVersion.isNotBlank()) { "Maximum launcher version must not be blank" }
        require(exactVersions.none { it.isBlank() }) { "Exact launcher versions must not contain blanks" }
    }
}

enum class LauncherProfileSource {
    BUILT_IN,
    USER_DEFINED,
    IMPORTED,
}

data class LauncherProfile(
    val id: LauncherId,
    val displayName: String,
    val adapterType: LauncherAdapterType,
    val packageCandidates: List<ExternalAppId> = emptyList(),
    val componentCandidates: List<String> = emptyList(),
    val launchParameters: LaunchParameterPayload = LaunchParameterPayload(),
    val versionConstraint: LauncherVersionConstraint? = null,
    val capabilityFlags: Set<CapabilityType> = emptySet(),
    val source: LauncherProfileSource = LauncherProfileSource.USER_DEFINED,
    val verification: VerificationState = VerificationState.UNKNOWN,
    val verifiedAt: Instant? = null,
    val userOverrides: Map<String, String> = emptyMap(),
) {
    init {
        requireText(displayName, "Launcher profile display name")
        require(packageCandidates.distinct().size == packageCandidates.size) {
            "Launcher package candidates must be unique"
        }
        require(componentCandidates.none { it.isBlank() }) { "Launcher component candidates must not contain blanks" }
        require(userOverrides.keys.none { it.isBlank() }) { "Launcher override keys must not be blank" }
    }
}

data class Launcher(
    val id: LauncherId,
    val displayName: String,
    val adapterType: LauncherAdapterType,
    val profile: LauncherProfile,
    val verification: VerificationState = profile.verification,
    val lastProbeAt: Instant? = null,
)

enum class PlayMethodValidity {
    VALID,
    INVALID,
    UNKNOWN,
    UNSUPPORTED,
}

data class LaunchValidation(
    val validity: PlayMethodValidity,
    val checkedAt: Instant = Instant.EPOCH,
    val reasonCode: String? = null,
    val evidence: List<String> = emptyList(),
) {
    init {
        require(reasonCode == null || reasonCode.isNotBlank()) { "Launch validation reason must not be blank" }
    }
}

data class PlayMethod(
    val id: PlayMethodId,
    val installationId: InstallationId,
    val deviceId: DeviceId,
    val launcherId: LauncherId,
    val launchParameters: LaunchParameterPayload = LaunchParameterPayload(),
    val priority: Int = 0,
    val preferred: Boolean = false,
    val validation: LaunchValidation = LaunchValidation(PlayMethodValidity.UNKNOWN),
    val lastStartedAt: Instant? = null,
    val lastFailureCode: String? = null,
)

enum class LibraryRootAccessState {
    UNKNOWN,
    READY,
    NEEDS_SETUP,
    REVOKED,
    ERROR,
}

data class LibraryRoot(
    val id: LibraryRootId,
    val displayName: String,
    val sourceType: InstallationSourceType,
    val reference: InstallationReference,
    val platformHint: GamePlatform = GamePlatform.UNKNOWN,
    val supportedExtensions: Set<String> = emptySet(),
    val accessState: LibraryRootAccessState = LibraryRootAccessState.UNKNOWN,
    val lastScanAt: Instant? = null,
    val providerAuthority: String? = null,
) {
    init {
        requireText(displayName, "Library root display name")
        require(supportedExtensions.none { it.isBlank() }) { "Supported extensions must not contain blanks" }
    }
}

data class CheapContentIdentity(
    val sourceKey: String,
    val rootId: LibraryRootId? = null,
    val sizeBytes: Long,
    val modifiedAt: Instant? = null,
    val extension: String? = null,
    val normalizedName: String,
) {
    init {
        requireText(sourceKey, "Content source key")
        requireNonNegative(sizeBytes, "Content size")
        requireText(normalizedName, "Normalized content name")
        require(extension == null || extension.isNotBlank()) { "Content extension must not be blank" }
    }
}

enum class FingerprintTier {
    NONE,
    QUICK,
    FULL,
}

enum class FingerprintAlgorithm {
    QUICK_WINDOW,
    CRC32,
    SHA1,
    SHA256,
    UNKNOWN,
}

data class ContentFingerprint(
    val identity: CheapContentIdentity,
    val tier: FingerprintTier,
    val algorithm: FingerprintAlgorithm,
    val hash: ContentHash,
    val computedAt: Instant = Instant.EPOCH,
) {
    init {
        require(tier != FingerprintTier.NONE) { "A content fingerprint must have a tier" }
        require(algorithm != FingerprintAlgorithm.UNKNOWN || tier == FingerprintTier.QUICK) {
            "Unknown algorithms are only allowed for quick fingerprints"
        }
    }
}

enum class ArtworkKind {
    ORIGINAL,
    POSTER,
    CARD,
    HERO,
    THUMBNAIL,
    BLURRED,
    PLACEHOLDER,
}

enum class ArtworkProvenance {
    USER_OVERRIDE,
    METADATA_PROVIDER,
    ARTWORK_PROVIDER,
    LOCAL_ICON,
    GENERATED,
    UNKNOWN,
}

data class ArtworkAsset(
    val id: ArtworkAssetId,
    val gameId: GameId,
    val kind: ArtworkKind,
    val reference: String,
    val provenance: ArtworkProvenance,
    val providerId: ArtworkProviderId? = null,
    val attribution: String? = null,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val isDerived: Boolean = kind != ArtworkKind.ORIGINAL,
    val createdAt: Instant = Instant.EPOCH,
) {
    init {
        requireText(reference, "Artwork reference")
        require(widthPx == null || widthPx > 0) { "Artwork width must be positive" }
        require(heightPx == null || heightPx > 0) { "Artwork height must be positive" }
    }
}

enum class MetadataField {
    TITLE,
    SORT_TITLE,
    DESCRIPTION,
    RELEASE_YEAR,
    REGION,
    LANGUAGE,
    PLATFORM,
    ARTWORK,
}

enum class MetadataProvenance {
    USER_OVERRIDE,
    KNOWN_HASH,
    PLATFORM_DATABASE,
    STEAM_IMPORT,
    ARTWORK_PROVIDER,
    FILENAME_FALLBACK,
    GENERATED,
    UNKNOWN,
}

enum class MetadataConfidence {
    EXACT,
    HIGH,
    MEDIUM,
    LOW,
    UNKNOWN,
}

data class MetadataCandidate(
    val field: MetadataField,
    val value: String,
    val confidence: MetadataConfidence,
    val provenance: MetadataProvenance,
    val providerId: MetadataProviderId? = null,
    val observedAt: Instant = Instant.EPOCH,
) {
    init {
        requireText(value, "Metadata value")
    }
}

data class ResolvedMetadata(
    val field: MetadataField,
    val value: String,
    val confidence: MetadataConfidence,
    val provenance: MetadataProvenance,
    val providerId: MetadataProviderId? = null,
    val candidatesConsidered: Int = 1,
)

enum class NoteType {
    TEXT,
    CHECKLIST,
    VOICE_TRANSCRIPT,
    HANDWRITING,
    SCREENSHOT_NOTE,
}

enum class NoteState {
    DRAFT,
    ACTIVE,
    ARCHIVED,
    DELETED,
    UNKNOWN,
}

data class Note(
    val id: NoteId,
    val gameId: GameId,
    val gameVersionId: GameVersionId? = null,
    val sessionId: GameSessionId? = null,
    val deviceId: DeviceId? = null,
    val type: NoteType = NoteType.TEXT,
    val body: String,
    val pinned: Boolean = false,
    val completed: Boolean = false,
    val state: NoteState = NoteState.ACTIVE,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = createdAt,
)

data class Screenshot(
    val id: ScreenshotId,
    val gameId: GameId,
    val sessionId: GameSessionId? = null,
    val deviceId: DeviceId? = null,
    val reference: String,
    val captureProvider: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val noteId: NoteId? = null,
    val capturedAt: Instant = Instant.EPOCH,
) {
    init {
        requireText(reference, "Screenshot reference")
        requireText(captureProvider, "Screenshot capture provider")
        require(widthPx == null || widthPx > 0) { "Screenshot width must be positive" }
        require(heightPx == null || heightPx > 0) { "Screenshot height must be positive" }
    }
}

enum class CollectionType {
    MANUAL,
    SMART,
    SYSTEM,
}

data class Collection(
    val id: CollectionId,
    val title: String,
    val type: CollectionType = CollectionType.MANUAL,
    val description: String? = null,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = createdAt,
)

data class CollectionMember(
    val collectionId: CollectionId,
    val gameId: GameId,
    val position: Int = 0,
)

enum class SaveCanonicalType {
    NORMAL_SAVE,
    MEMORY_CARD,
    SAVE_STATE,
    CONFIG,
    UNKNOWN,
}

sealed interface SaveLocation {
    val reference: String

    data class GrantedRoot(override val reference: String) : SaveLocation {
        init {
            requireText(reference, "Save root reference")
        }
    }

    data class SingleDocument(override val reference: String) : SaveLocation {
        init {
            requireText(reference, "Save document reference")
        }
    }

    data class AdapterExport(override val reference: String) : SaveLocation {
        init {
            requireText(reference, "Save adapter reference")
        }
    }
}

enum class SaveProfileState {
    UNCONFIGURED,
    AVAILABLE,
    UNAVAILABLE,
    REVOKED,
    MANAGED_EXTERNALLY,
    ERROR,
    UNKNOWN,
}

data class SaveProfile(
    val id: SaveProfileId,
    val gameVersionId: GameVersionId,
    val installationId: InstallationId,
    val adapterId: SaveAdapterId,
    val location: SaveLocation,
    val includeRules: List<String> = emptyList(),
    val excludeRules: List<String> = emptyList(),
    val canonicalType: SaveCanonicalType = SaveCanonicalType.NORMAL_SAVE,
    val compatibilityFingerprint: ContentHash? = null,
    val state: SaveProfileState = SaveProfileState.UNKNOWN,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = createdAt,
) {
    init {
        require(includeRules.none { it.isBlank() }) { "Save include rules must not contain blanks" }
        require(excludeRules.none { it.isBlank() }) { "Save exclude rules must not contain blanks" }
    }
}

enum class SnapshotReason {
    PRELAUNCH,
    END_SESSION,
    MANUAL,
    IMPORT,
    PRE_RESTORE,
}

enum class SnapshotState {
    CREATING,
    COMPLETE,
    INVALID,
    RETAINED,
    ERROR,
}

data class SaveSnapshotFile(
    val relativePath: String,
    val sizeBytes: Long,
    val hash: ContentHash,
    val sourceModifiedAt: Instant? = null,
) {
    init {
        requireText(relativePath, "Snapshot relative path")
        require(!relativePath.startsWith("/")) { "Snapshot path must be relative" }
        require(!relativePath.split('/').contains("..")) { "Snapshot path must not escape its root" }
        requireNonNegative(sizeBytes, "Snapshot file size")
    }
}

data class SaveSnapshot(
    val id: SaveSnapshotId,
    val profileId: SaveProfileId,
    val deviceId: DeviceId,
    val sessionId: GameSessionId? = null,
    val parentSnapshotId: SaveSnapshotId? = null,
    val files: List<SaveSnapshotFile> = emptyList(),
    val manifestDigest: ContentHash? = null,
    val storageReference: String,
    val reason: SnapshotReason,
    val state: SnapshotState = SnapshotState.COMPLETE,
    val compatibilityFingerprint: ContentHash? = null,
    val capturedAt: Instant = Instant.EPOCH,
) {
    init {
        requireText(storageReference, "Snapshot storage reference")
        require(files.map { it.relativePath }.distinct().size == files.size) {
            "Snapshot file paths must be unique"
        }
    }
}

enum class SaveState {
    UNCONFIGURED,
    UNAVAILABLE,
    LOCAL_ONLY,
    SNAPSHOTTING,
    SNAPSHOTTED,
    MANAGED_EXTERNALLY,
    ERROR,
    UNKNOWN,
}

enum class SyncState {
    UNCONFIGURED,
    UNKNOWN,
    UNSUPPORTED,
    UNAVAILABLE,
    LOCAL_ONLY,
    SYNCING,
    SYNCED,
    MANAGED_EXTERNALLY,
    CONFLICT,
    ERROR,
}

data class SaveStatus(
    val saveState: SaveState,
    val syncState: SyncState,
    val latestSnapshotId: SaveSnapshotId? = null,
    val detail: String? = null,
)

enum class CapabilityType {
    DISPLAY_ENUMERATION,
    SECONDARY_DISPLAY,
    DISPLAY_LAUNCH,
    PHYSICAL_PLACEMENT_CONFIRMATION,
    HOME_ROLE,
    LID_SIGNAL,
    SCREEN_LIFECYCLE,
    GRACEFUL_EXTERNAL_EXIT,
    SAVE_ACCESS,
    SAVE_STATE,
    SYNC_TRANSPORT,
    MEDIA_PROJECTION,
    PRIVILEGED_TASK_CONTROL,
    USAGE_ACCESS,
    UNKNOWN,
}

enum class CapabilityStatus {
    UNKNOWN,
    READY,
    NEEDS_ACTION,
    UNSUPPORTED,
    DENIED,
    ERROR,
    STALE,
}

data class CapabilityRecord(
    val id: CapabilityRecordId,
    val deviceId: DeviceId? = null,
    val type: CapabilityType,
    val status: CapabilityStatus,
    val evidence: List<String> = emptyList(),
    val buildFingerprint: BuildFingerprint? = null,
    val observedAt: Instant = Instant.EPOCH,
    val expiresAt: Instant? = null,
)

enum class SessionState {
    PENDING,
    ACTIVE,
    SUSPENDED,
    ENDING,
    ENDED,
    RECOVERED,
    LAUNCH_FAILED,
    ABANDONED,
}

enum class SessionCloseReason {
    USER_ENDED,
    LAUNCH_FAILED,
    RECOVERED_AND_ENDED,
    CRASH_RECOVERY,
    REBOOT_RECOVERY,
    UNKNOWN,
}

data class MonotonicMark(
    val bootId: String,
    val elapsedNanos: Long,
) {
    init {
        requireText(bootId, "Monotonic clock boot ID")
        requireNonNegative(elapsedNanos, "Monotonic elapsed time")
    }
}

data class SessionTiming(
    val accumulatedActive: Duration = Duration.ZERO,
    val activeSince: MonotonicMark? = null,
    val lastObserved: MonotonicMark? = null,
) {
    init {
        require(!accumulatedActive.isNegative) { "Accumulated session duration must not be negative" }
    }
}

data class GameSession(
    val id: GameSessionId,
    val gameId: GameId,
    val gameVersionId: GameVersionId,
    val installationId: InstallationId,
    val deviceId: DeviceId,
    val playMethodId: PlayMethodId,
    val state: SessionState = SessionState.PENDING,
    val wallStartedAt: Instant = Instant.EPOCH,
    val wallEndedAt: Instant? = null,
    val timing: SessionTiming = SessionTiming(),
    val closeReason: SessionCloseReason? = null,
    val notesCount: Int = 0,
    val screenshotsCount: Int = 0,
    val lastHeartbeatAt: Instant? = null,
) {
    init {
        requireNonNegative(notesCount.toLong(), "Session note count")
        requireNonNegative(screenshotsCount.toLong(), "Session screenshot count")
    }
}

enum class HomeRoleState {
    UNKNOWN,
    NOT_REQUESTED,
    REQUESTED,
    ACTIVE,
    DECLINED,
    UNSUPPORTED,
    ERROR,
}

data class HomeRoleStatus(
    val state: HomeRoleState,
    val checkedAt: Instant = Instant.EPOCH,
    val detail: String? = null,
)
