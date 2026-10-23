package dev.thorhub.app.platform.display

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DisplayTopologyResolverTest {
    @Test
    fun `empty snapshot is degraded without inventing display ids`() {
        val result = resolve(emptyList())

        assertThat(result.mode).isEqualTo(TopologyMode.DEGRADED)
        assertThat(result.upperDisplayId).isNull()
        assertThat(result.lowerDisplayId).isNull()
        assertThat(result.assignmentVerified).isFalse()
    }

    @Test
    fun `single default display uses adaptive fallback`() {
        val result = resolve(listOf(display(id = 0, default = true, width = 1920, height = 1080)))

        assertThat(result.mode).isEqualTo(TopologyMode.SINGLE_DISPLAY)
        assertThat(result.upperDisplayId).isEqualTo(0)
        assertThat(result.lowerDisplayId).isNull()
        assertThat(result.upper?.role).isEqualTo(DisplayRole.UPPER_CANDIDATE)
    }

    @Test
    fun `default is upper and smallest active nondefault is lower`() {
        val result = resolve(
            listOf(
                display(id = 0, default = true, width = 1920, height = 1080),
                display(id = 4, width = 2560, height = 1440),
                display(id = 7, width = 800, height = 600),
            ),
        )

        assertThat(result.mode).isEqualTo(TopologyMode.DUAL_DISPLAY)
        assertThat(result.upperDisplayId).isEqualTo(0)
        assertThat(result.lowerDisplayId).isEqualTo(7)
        assertThat(result.displays.single { it.id == 4 }.role).isEqualTo(DisplayRole.UNASSIGNED)
    }

    @Test
    fun `internal nondefault display wins before smaller external display`() {
        val result = resolve(
            listOf(
                display(id = 0, default = true, typeLabel = "INTERNAL", width = 1920, height = 1080),
                display(id = 4, typeLabel = "INTERNAL", width = 1280, height = 720),
                display(id = 7, typeLabel = "NON_INTERNAL", width = 320, height = 240),
            ),
        )

        assertThat(result.upperDisplayId).isEqualTo(0)
        assertThat(result.lowerDisplayId).isEqualTo(4)
    }

    @Test
    fun `largest active display becomes upper when no default exists`() {
        val result = resolve(
            listOf(
                display(id = 12, width = 1280, height = 720),
                display(id = 21, width = 1920, height = 1200),
            ),
        )

        assertThat(result.upperDisplayId).isEqualTo(21)
        assertThat(result.lowerDisplayId).isEqualTo(12)
    }

    @Test
    fun `off displays remain visible in diagnostics but cannot receive a role`() {
        val result = resolve(
            listOf(
                display(id = 0, default = true),
                display(id = 2, stateLabel = "OFF", width = 320, height = 240),
            ),
        )

        assertThat(result.mode).isEqualTo(TopologyMode.SINGLE_DISPLAY)
        assertThat(result.lowerDisplayId).isNull()
        assertThat(result.displays.single { it.id == 2 }.role).isEqualTo(DisplayRole.UNASSIGNED)
    }

    @Test
    fun `verified signatures survive transient display id changes`() {
        val previousUpper = display(id = 0, default = true, name = "Upper", width = 1920, height = 1080)
        val previousLower = display(id = 4, name = "Lower", width = 800, height = 600)
        val stored = assignment(previousUpper, previousLower, verified = true)
        val result = resolve(
            listOf(
                previousUpper.copy(id = 31, isDefault = false),
                previousLower.copy(id = 47, isDefault = true),
            ),
            stored,
        )

        assertThat(result.upperDisplayId).isEqualTo(31)
        assertThat(result.lowerDisplayId).isEqualTo(47)
        assertThat(result.assignmentVerified).isTrue()
    }

    @Test
    fun `unverified persisted swap is ignored after a fresh coordinator`() {
        val upper = display(id = 0, default = true, name = "Upper")
        val lower = display(id = 2, name = "Lower", width = 800, height = 600)
        val result = resolve(listOf(upper, lower), assignment(lower, upper, verified = false))

        assertThat(result.upperDisplayId).isEqualTo(0)
        assertThat(result.lowerDisplayId).isEqualTo(2)
        assertThat(result.assignmentVerified).isFalse()
    }

    @Test
    fun `pending in-process swap is honored without claiming verification`() {
        val upper = display(id = 0, default = true, name = "Upper")
        val lower = display(id = 2, name = "Lower", width = 800, height = 600)
        val pending = assignment(lower, upper, verified = false)
        val result = DisplayTopologyResolver.resolve(
            displays = listOf(upper, lower),
            stored = null,
            pending = pending,
            firmwareFingerprintHash = "firmware-hash",
        )

        assertThat(result.upperDisplayId).isEqualTo(2)
        assertThat(result.lowerDisplayId).isEqualTo(0)
        assertThat(result.assignmentVerified).isFalse()
    }

    @Test
    fun `signature mismatch falls back and invalidates confirmation`() {
        val oldUpper = display(id = 0, default = true, name = "Old upper")
        val oldLower = display(id = 2, name = "Old lower", width = 800, height = 600)
        val currentUpper = oldUpper.copy(name = "Updated upper")
        val currentLower = oldLower.copy(name = "Updated lower")
        val result = resolve(
            listOf(currentUpper, currentLower),
            assignment(oldLower, oldUpper, verified = true),
        )

        assertThat(result.upperDisplayId).isEqualTo(0)
        assertThat(result.lowerDisplayId).isEqualTo(2)
        assertThat(result.assignmentVerified).isFalse()
    }

    @Test
    fun `duplicate signatures cannot assign one display to both roles`() {
        val first = display(id = 3, name = "Same", width = 800, height = 600)
        val second = first.copy(id = 8, isDefault = true)
        val stored = assignment(first, second, verified = true)
        val result = resolve(listOf(first, second), stored)

        assertThat(result.upperDisplayId).isEqualTo(8)
        assertThat(result.lowerDisplayId).isEqualTo(3)
        assertThat(result.assignmentVerified).isFalse()
    }

    private fun resolve(
        displays: List<DisplayDescriptor>,
        stored: StoredDisplayAssignment? = null,
    ): DisplayTopology = DisplayTopologyResolver.resolve(
        displays = displays,
        stored = stored,
        firmwareFingerprintHash = "firmware-hash",
    )

    private fun assignment(
        upper: DisplayDescriptor,
        lower: DisplayDescriptor,
        verified: Boolean,
    ) = StoredDisplayAssignment(
        upperSignatureHash = DisplayAssignmentStore.sha256(upper.identitySignature),
        lowerSignatureHash = DisplayAssignmentStore.sha256(lower.identitySignature),
        verified = verified,
    )

    private fun display(
        id: Int,
        default: Boolean = false,
        name: String = "Display $id",
        stateLabel: String = "ON",
        typeLabel: String = "UNKNOWN_PUBLIC_API",
        width: Int = 1280,
        height: Int = 720,
    ) = DisplayDescriptor(
        id = id,
        uniqueId = null,
        name = name,
        type = 0,
        typeLabel = typeLabel,
        flags = 0,
        state = if (stateLabel == "OFF") 1 else 2,
        stateLabel = stateLabel,
        widthPx = width,
        heightPx = height,
        densityDpi = 240,
        rotation = 0,
        refreshRateHz = 60f,
        supportedRefreshRatesHz = listOf(60f),
        isDefault = default,
    )
}
