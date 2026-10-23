package dev.thorhub.core.model

import java.time.Instant

enum class DisplayState {
    ACTIVE,
    INACTIVE,
    DISCONNECTED,
    UNKNOWN,
}

enum class DisplayType {
    BUILT_IN,
    EXTERNAL,
    VIRTUAL,
    UNKNOWN,
}

enum class DisplayFlag {
    PRESENTATION,
    SECURE,
    SUPPORTS_TOUCH,
    SUPPORTS_INPUT,
    UNKNOWN,
}

data class DisplayMetrics(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val rotationDegrees: Int = 0,
    val refreshRateHz: Float? = null,
) {
    init {
        require(widthPx >= 0) { "Display width must not be negative" }
        require(heightPx >= 0) { "Display height must not be negative" }
        require(density > 0f) { "Display density must be positive" }
        require(rotationDegrees in 0..359) { "Display rotation must be between 0 and 359 degrees" }
        require(refreshRateHz == null || refreshRateHz > 0f) { "Display refresh rate must be positive" }
    }
}

data class DisplayDescriptor(
    val id: DisplayId,
    val uniqueId: String? = null,
    val name: String,
    val type: DisplayType = DisplayType.UNKNOWN,
    val state: DisplayState = DisplayState.UNKNOWN,
    val metrics: DisplayMetrics,
    val flags: Set<DisplayFlag> = emptySet(),
    val isDefaultDisplay: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "Display name must not be blank" }
        require(uniqueId == null || uniqueId.isNotBlank()) { "Display unique ID must not be blank" }
    }

    val isActive: Boolean
        get() = state == DisplayState.ACTIVE
}

enum class DisplayRole {
    UPPER,
    LOWER,
    UNASSIGNED,
}

data class VerifiedDisplayAssignment(
    val buildFingerprint: BuildFingerprint,
    val upperDisplayId: DisplayId? = null,
    val lowerDisplayId: DisplayId? = null,
    val verifiedAt: Instant = Instant.EPOCH,
) {
    init {
        require(upperDisplayId == null || upperDisplayId != lowerDisplayId) {
            "Upper and lower displays must be different when both are assigned"
        }
    }
}

enum class DisplaySelectionMode {
    DUAL_DISPLAY,
    SINGLE_DISPLAY_FALLBACK,
    NO_ACTIVE_DISPLAY,
}

enum class DisplaySelectionReason {
    VERIFIED_SAME_BUILD,
    DEFAULT_DISPLAY_CANDIDATE,
    LARGEST_ACTIVE_DISPLAY,
    VERIFIED_LOWER_SAME_BUILD,
    BUILT_IN_NON_DEFAULT,
    SMALLEST_NON_UPPER,
    SINGLE_DISPLAY,
    NONE_AVAILABLE,
}

data class DisplaySelection(
    val upper: DisplayDescriptor?,
    val lower: DisplayDescriptor?,
    val mode: DisplaySelectionMode,
    val upperReason: DisplaySelectionReason,
    val lowerReason: DisplaySelectionReason,
) {
    val isSingleDisplayFallback: Boolean
        get() = mode == DisplaySelectionMode.SINGLE_DISPLAY_FALLBACK
}

data class DisplayTopology(
    val buildFingerprint: BuildFingerprint,
    val displays: List<DisplayDescriptor>,
    val verifiedAssignment: VerifiedDisplayAssignment? = null,
    val capturedAt: Instant = Instant.EPOCH,
) {
    init {
        require(displays.map { it.id }.distinct().size == displays.size) { "Display IDs must be unique" }
        // A stale persisted assignment is valid input for diagnostics and is
        // intentionally rejected by DisplaySelectionPolicy at selection time.
    }
}

enum class CompanionDestination {
    HOME,
    NOTES,
    GAME,
    TOOLS,
    SYSTEM,
    RESUME_CARD,
    DIAGNOSTICS,
}

data class CompanionRoute(
    val destination: CompanionDestination,
    val gameId: GameId? = null,
    val sessionId: GameSessionId? = null,
)

enum class HomeDestination {
    HOME,
    LIBRARY,
    SEARCH,
    COLLECTIONS,
    GAME_DETAIL,
    SETUP,
    SETTINGS,
    DIAGNOSTICS,
}

data class HomeRoute(
    val destination: HomeDestination = HomeDestination.HOME,
    val gameId: GameId? = null,
)

data class UpperLaunchRequest(
    val targetDisplayId: DisplayId?,
    val target: ExternalLaunchTarget,
    val requestedAt: Instant = Instant.EPOCH,
)

data class ExternalLaunchTarget(
    val methodId: PlayMethodId,
    val payload: LaunchParameterPayload,
)

enum class DisplayLaunchAcceptanceState {
    ACCEPTED,
    REJECTED,
    UNKNOWN,
    UNSUPPORTED,
}

data class DisplayLaunchAcceptance(
    val state: DisplayLaunchAcceptanceState,
    val requestedDisplayId: DisplayId?,
    val resolvedTarget: String? = null,
    val detail: String? = null,
)

enum class PhysicalPlacementState {
    CONFIRMED,
    NOT_CONFIRMED,
    UNKNOWN,
}

data class PhysicalPlacementConfirmation(
    val state: PhysicalPlacementState,
    val observedDisplayId: DisplayId? = null,
    val observedRole: DisplayRole = DisplayRole.UNASSIGNED,
    val evidence: List<String> = emptyList(),
)

data class DisplayLaunchResult(
    val requestedDisplayId: DisplayId?,
    val acceptance: DisplayLaunchAcceptance,
    val physicalPlacement: PhysicalPlacementConfirmation = PhysicalPlacementConfirmation(
        state = PhysicalPlacementState.UNKNOWN,
    ),
)

data class PlacementTestObservation(
    val displayId: DisplayId,
    val requestedRole: DisplayRole,
    val acceptance: DisplayLaunchAcceptance,
    val physicalPlacement: PhysicalPlacementConfirmation,
)

data class PlacementTestResult(
    val observations: List<PlacementTestObservation>,
    val swapped: Boolean = false,
    val persistedAssignment: VerifiedDisplayAssignment? = null,
)

enum class DisplayLifecycleKind {
    CONNECTED,
    DISCONNECTED,
    ACTIVE,
    INACTIVE,
    METRICS_CHANGED,
    UNKNOWN,
}

/** A display/screen observation is intentionally not a lid event. */
data class DisplayLifecycleObservation(
    val kind: DisplayLifecycleKind,
    val displayId: DisplayId? = null,
    val observedAt: Instant = Instant.EPOCH,
    val evidence: List<String> = emptyList(),
)

enum class LidSignalKind {
    OPEN,
    CLOSED,
    UNKNOWN,
}

enum class LidSignalSource {
    EXPLICIT_HARDWARE,
    OEM_PROVIDER,
    UNKNOWN,
}

data class LidSignal(
    val kind: LidSignalKind,
    val source: LidSignalSource,
    val observedAt: Instant = Instant.EPOCH,
    val evidence: List<String> = emptyList(),
)

data class LidCapabilities(
    val status: CapabilityStatus,
    val source: LidSignalSource = LidSignalSource.UNKNOWN,
    val supportsOpen: Boolean = false,
    val supportsClosed: Boolean = false,
    val detail: String? = null,
)
