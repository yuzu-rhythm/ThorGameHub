package dev.thorhub.core.database

/**
 * Values stored by the database are intentionally stable strings rather than
 * ordinals.  This keeps an enum reorder from silently changing persisted
 * meaning and leaves room for a future model module to map these values.
 */
enum class GameLifecycleStatus {
    UNKNOWN,
    BACKLOG,
    PLAYING,
    COMPLETED,
    ABANDONED,
}

enum class GameVersionClassification {
    UNKNOWN,
    ORIGINAL,
    HACK,
    FAN_TRANSLATION,
    REMASTER,
    PORT,
    EXPANSION,
    OTHER,
}

enum class DeviceAvailabilityState {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE,
}

enum class PairingState {
    UNKNOWN,
    UNPAIRED,
    PAIRED,
    TRUSTED,
    REVOKED,
}

enum class InstallationSourceType {
    UNKNOWN,
    ROM,
    ANDROID,
    GAMENATIVE,
    STEAM,
    STREAM,
    MANUAL,
}

enum class InstallationAvailability {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE,
    NEEDS_RESCAN,
    ERROR,
}

enum class LauncherAdapterType {
    UNKNOWN,
    ANDROID_NATIVE,
    RETROARCH,
    GAMENATIVE,
    CONFIGURABLE_EXTERNAL,
    OTHER,
}

enum class VerificationStatus {
    UNKNOWN,
    VERIFIED,
    UNVERIFIED,
    NEEDS_SETUP,
    UNSUPPORTED,
    ERROR,
}

enum class CompatibilityState {
    UNKNOWN,
    READY,
    NEEDS_ACTION,
    UNSUPPORTED,
    ERROR,
}

enum class SessionState {
    PENDING,
    ACTIVE,
    SUSPENDED,
    ENDING,
    ENDED,
    RECOVERED_ACTIVE,
    RECOVERED,
    ABANDONED,
    INVALID,
}

enum class NoteType {
    UNKNOWN,
    TEXT,
    CHECKLIST,
    VOICE_TRANSCRIPT,
    HANDWRITING,
    SCREENSHOT_NOTE,
}

enum class SaveAccessMethod {
    UNKNOWN,
    SAF_FILE,
    SAF_DIRECTORY,
    APP_EXPORT,
    SHARED_DIRECTORY,
    EXTERNAL_ADAPTER,
}

enum class SaveType {
    UNKNOWN,
    NORMAL_SAVE,
    MEMORY_CARD,
    SAVE_STATE,
    CONFIG,
}

enum class SaveState {
    UNCONFIGURED,
    UNAVAILABLE,
    LOCAL_ONLY,
    SNAPSHOTTING,
    SNAPSHOTTED,
    SYNCING,
    SYNCED,
    MANAGED_EXTERNALLY,
    CONFLICT,
    ERROR,
}

enum class SnapshotReason {
    UNKNOWN,
    PRELAUNCH,
    END_SESSION,
    MANUAL,
    IMPORT,
    PRE_RESTORE,
}

enum class SnapshotState {
    UNKNOWN,
    PENDING,
    COMPLETE,
    INVALID,
    ERROR,
}

enum class CollectionType {
    UNKNOWN,
    MANUAL,
    SMART,
    SYSTEM,
}

enum class LibraryRootSourceType {
    UNKNOWN,
    ROM,
    ANDROID,
    GAMENATIVE,
    STEAM,
    MANUAL,
}

enum class LibraryRootState {
    UNKNOWN,
    ACTIVE,
    STALE,
    REVOKED,
    ERROR,
}

enum class ArtworkKind {
    UNKNOWN,
    ORIGINAL,
    POSTER,
    CARD,
    HERO,
    THUMBNAIL,
    BLURRED,
    PLACEHOLDER,
}

enum class CapabilityState {
    UNKNOWN,
    READY,
    NEEDS_ACTION,
    UNSUPPORTED,
    ERROR,
}

enum class DiagnosticSeverity {
    INFO,
    WARNING,
    ERROR,
}

enum class DisplayRole {
    UNKNOWN,
    UPPER,
    LOWER,
}
