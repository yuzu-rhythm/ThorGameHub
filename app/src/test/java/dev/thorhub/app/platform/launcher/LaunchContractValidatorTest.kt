package dev.thorhub.app.platform.launcher

import com.google.common.truth.Truth.assertThat
import dev.thorhub.core.model.ContentUriGrantMode
import dev.thorhub.core.model.LaunchExtraValue
import dev.thorhub.core.model.LaunchParameterPayload
import dev.thorhub.core.model.LauncherAdapterType
import org.junit.Test

class LaunchContractValidatorTest {
    @Test
    fun `GameNative requires typed positive app id and known source`() {
        val payload = LauncherProfiles.gameNative(GameNativeSource.STEAM, 620).launchParameters

        assertThat(LaunchContractValidator.validate(LauncherAdapterType.GAME_NATIVE, payload)).isEmpty()
        assertThat(payload.extras["app_id"]).isEqualTo(LaunchExtraValue.Integer(620))
    }

    @Test
    fun `GameNative rejects string app id`() {
        val payload = LaunchParameterPayload(
            action = LauncherProfiles.GAME_NATIVE_ACTION,
            componentTemplate = "app.gamenative/app.gamenative.MainActivity",
            extras = mapOf(
                "app_id" to LaunchExtraValue.Text("620"),
                "game_source" to LaunchExtraValue.Text("STEAM"),
            ),
        )

        assertThat(LaunchContractValidator.validate(LauncherAdapterType.GAME_NATIVE, payload).map { it.code })
            .contains("gamenative_app_id")
    }

    @Test
    fun `RetroArch requires SAF content uri and core`() {
        val valid = LaunchParameterPayload(
            componentTemplate = "com.retroarch/com.retroarch.browser.retroactivity.RetroActivityFuture",
            extras = mapOf(
                "ROM" to LaunchExtraValue.Text("content://provider/game.chd"),
                "LIBRETRO" to LaunchExtraValue.Text("pcsx_rearmed_libretro_android.so"),
            ),
            contentUriGrant = ContentUriGrantMode.READ,
        )
        val invalid = valid.copy(extras = valid.extras + ("ROM" to LaunchExtraValue.Text("/storage/game.chd")))

        assertThat(LaunchContractValidator.validate(LauncherAdapterType.RETROARCH, valid)).isEmpty()
        assertThat(LaunchContractValidator.validate(LauncherAdapterType.RETROARCH, invalid).map { it.code })
            .contains("retroarch_rom")
    }

    @Test
    fun `configurable launcher is not considered ready with an empty template`() {
        val issues = LaunchContractValidator.validate(
            LauncherAdapterType.CONFIGURABLE_EXTERNAL,
            LaunchParameterPayload(componentTemplate = "example.player/example.player.MainActivity"),
        )

        assertThat(issues.map { it.code }).contains("external_empty_template")
    }

    @Test
    fun `typed external extras parser preserves Android primitive types`() {
        val parsed = TypedExtrasTemplateParser.parse(
            """
            path:text={contentUri}
            slot:int=2
            resume:boolean=true
            flags:list=fast,safe
            """.trimIndent(),
        )

        assertThat(parsed.issues).isEmpty()
        assertThat(parsed.extras["slot"]).isEqualTo(LaunchExtraValue.Integer(2))
        assertThat(parsed.extras["resume"]).isEqualTo(LaunchExtraValue.BooleanValue(true))
        assertThat(parsed.extras["flags"]).isEqualTo(LaunchExtraValue.TextList(listOf("fast", "safe")))
    }
}
