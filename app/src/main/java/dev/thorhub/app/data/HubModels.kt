package dev.thorhub.app.data

import dev.thorhub.core.database.CompatibilityState
import dev.thorhub.core.database.InstallationAvailability
import dev.thorhub.core.database.NoteType
import dev.thorhub.core.database.SessionState

data class LibraryGameSummary(
    val id: String,
    val title: String,
    val sortTitle: String,
    val pinned: Boolean,
    val favorite: Boolean,
    val hidden: Boolean,
    val availableInstallations: Int,
    val platform: String = "UNKNOWN",
    val lifecycle: String = "UNKNOWN",
    val lastSessionAt: Long? = null,
    val sessionCount: Int = 0,
)

data class GameDetailSummary(
    val id: String,
    val title: String,
    val description: String?,
    val releaseYear: Int?,
    val pinned: Boolean,
    val favorite: Boolean,
    val versions: List<VersionSummary>,
    val installations: List<InstallationSummary>,
    val playMethods: List<PlayMethodSummary>,
    val sessions: List<SessionSummary>,
    val notes: List<NoteSummary>,
    val metadataProvenance: Map<String, String> = emptyMap(),
    val artworkLocator: String? = null,
    val artworkProvenance: String? = null,
)

data class VersionSummary(val id: String, val platform: String, val edition: String?)

data class InstallationSummary(
    val id: String,
    val versionId: String,
    val source: String,
    val availability: InstallationAvailability,
)

data class PlayMethodSummary(
    val id: String,
    val title: String,
    val compatibility: CompatibilityState,
    val preferred: Boolean,
)

data class SessionSummary(
    val id: String,
    val gameId: String,
    val gameTitle: String,
    val state: SessionState,
    val startedAt: Long,
    val endedAt: Long?,
    val durationMillis: Long,
)

data class NoteSummary(
    val id: String,
    val gameId: String?,
    val body: String,
    val type: NoteType,
    val pinned: Boolean,
    val completed: Boolean,
    val updatedAt: Long,
)

data class LibraryRootSummary(
    val id: String,
    val state: String,
    val platformHint: String?,
    val provider: String?,
    val lastScanAt: Long?,
)

data class EndSessionSummary(
    val sessionId: String,
    val durationMillis: Long,
    val noteSaved: Boolean,
    val noteStatus: String = if (noteSaved) "SAVED" else "NOT_SUPPLIED",
    val snapshotStatus: String,
    val snapshotDetail: String? = null,
    val syncStatus: String,
    val externalRequestStatus: String = "NOT_REQUESTED",
    val externalRequestDetail: String? = null,
    val externalClosureStatus: String = "NOT_VERIFIED",
    val homeRouteStatus: String = "NOT_REQUESTED",
)

data class SaveProfileSummary(
    val id: String,
    val state: String,
    val type: String,
    val access: String,
    val configured: Boolean,
)

data class SaveSnapshotSummary(
    val id: String,
    val capturedAt: Long,
    val state: String,
    val reason: String,
    val parentId: String?,
    val contentHash: String?,
)

data class SaveContinuitySummary(
    val profile: SaveProfileSummary? = null,
    val snapshots: List<SaveSnapshotSummary> = emptyList(),
)

data class ResumeCardSummary(
    val gameId: String,
    val gameTitle: String,
    val sessionId: String,
    val lastPlayedAt: Long,
    val priorDurationMillis: Long,
    val pinnedNote: String?,
    val artworkLocator: String?,
    val recovered: Boolean,
)
