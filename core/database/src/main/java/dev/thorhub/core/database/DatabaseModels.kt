package dev.thorhub.core.database

/**
 * The result used by the Continue carousel.  The last session timestamp is
 * derived from session history, so deleting or losing an installation does not
 * remove a game from the user's library.
 */
data class ContinueGameRow(
    val gameId: String,
    val canonicalTitle: String,
    val sortTitle: String,
    val isPinned: Boolean,
    val lastSessionId: String?,
    val lastSessionAt: Long?,
    val sessionCount: Int,
)

data class SearchGameRow(
    val gameId: String,
    val canonicalTitle: String,
    val sortTitle: String,
    val lifecycleStatus: GameLifecycleStatus,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    val availableInstallationCount: Int,
)

/**
 * Projection used by both the paged library and its bounded Home/Setup preview.
 * Keeping the joins inside Room prevents a large catalog from being assembled
 * from several complete in-memory lists in the app process.
 */
data class LibraryGameRow(
    val gameId: String,
    val canonicalTitle: String,
    val sortTitle: String,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    val availableInstallationCount: Int,
    val platform: String,
    val lifecycleStatus: GameLifecycleStatus,
    val lastSessionAt: Long?,
    val sessionCount: Int,
)

data class SessionWithGameRow(
    val sessionId: String,
    val gameId: String,
    val gameTitle: String,
    val state: SessionState,
    val wallStartedAt: Long,
    val wallEndedAt: Long?,
    val durationMillis: Long,
)

data class CollectionGameRow(
    val collectionId: String,
    val gameId: String,
    val position: Int,
    val canonicalTitle: String,
    val sortTitle: String,
)

data class LibraryImport(
    val games: List<GameEntity> = emptyList(),
    val versions: List<GameVersionEntity> = emptyList(),
    val devices: List<DeviceEntity> = emptyList(),
    val roots: List<LibraryRootEntity> = emptyList(),
    val fingerprints: List<ContentFingerprintEntity> = emptyList(),
    val installations: List<InstallationEntity> = emptyList(),
)

data class ScanReconciliation(
    val libraryRootId: String,
    val seenInstallationIds: List<String>,
    val reconciledAt: Long,
    val unavailableReason: String = "MISSING_FROM_SCAN",
)

data class NoteWithChecklist(
    val note: NoteEntity,
    val checklistItems: List<NoteChecklistItemEntity>,
)
