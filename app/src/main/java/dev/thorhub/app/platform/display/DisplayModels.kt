package dev.thorhub.app.platform.display

enum class DisplayRole { UPPER_CANDIDATE, LOWER_CANDIDATE, UNASSIGNED }

enum class TopologyMode { DUAL_DISPLAY, SINGLE_DISPLAY, DEGRADED }

data class DisplayDescriptor(
    val id: Int,
    val uniqueId: String?,
    val name: String,
    val type: Int,
    val typeLabel: String,
    val flags: Int,
    val state: Int,
    val stateLabel: String,
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val rotation: Int,
    val refreshRateHz: Float,
    val supportedRefreshRatesHz: List<Float>,
    val isDefault: Boolean,
    val role: DisplayRole = DisplayRole.UNASSIGNED,
) {
    val area: Long get() = widthPx.toLong() * heightPx.toLong()

    val identitySignature: String
        get() = listOf(uniqueId.orEmpty(), name, type, widthPx, heightPx, densityDpi).joinToString("|")
}

data class DisplayTopology(
    val displays: List<DisplayDescriptor>,
    val upperDisplayId: Int?,
    val lowerDisplayId: Int?,
    val mode: TopologyMode,
    val assignmentVerified: Boolean,
    val firmwareFingerprintHash: String,
) {
    val upper: DisplayDescriptor? get() = displays.firstOrNull { it.id == upperDisplayId }
    val lower: DisplayDescriptor? get() = displays.firstOrNull { it.id == lowerDisplayId }
}

data class DisplayLaunchEvidence(
    val requestedDisplayId: Int?,
    val requestAccepted: Boolean,
    val resolvedComponent: String?,
    val observedOwnActivityDisplayId: Int?,
    val physicalPlacementConfirmed: Boolean?,
    val companionKnownActive: Boolean?,
    val message: String,
)
