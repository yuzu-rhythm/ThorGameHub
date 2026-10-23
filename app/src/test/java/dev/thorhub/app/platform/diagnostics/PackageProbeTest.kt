package dev.thorhub.app.platform.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PackageProbeTest {
    @Test
    fun `static upstream profiles never imply installed launch verification`() {
        val installed = listOf(
            InstalledLaunchableApp(
                label = "RetroArch",
                packageName = "com.retroarch.aarch64",
                activityName = "com.retroarch.browser.mainmenu.MainMenuActivity",
                versionName = "fixture",
                versionCode = 1,
                exported = true,
            ),
            InstalledLaunchableApp(
                label = "GameNative",
                packageName = "app.gamenative",
                activityName = "app.gamenative.MainActivity",
                versionName = "fixture",
                versionCode = 1,
                exported = true,
            ),
        )

        val probes = summarizeAdapterProbes(installed)

        assertThat(
            probes.filter { it.adapter in setOf("RetroArch", "GameNative") }
                .map { it.staticProfileKnown && !it.launchContractVerified },
        ).containsExactly(true, true)
    }

    @Test
    fun `missing package remains unknown and unverified`() {
        val probes = summarizeAdapterProbes(emptyList())

        assertThat(probes.first { it.adapter == "RetroArch" }.state).isEqualTo(CapabilityUiState.UNKNOWN)
        assertThat(probes.map { it.launchContractVerified }).containsExactly(false, false, false, false)
    }
}
