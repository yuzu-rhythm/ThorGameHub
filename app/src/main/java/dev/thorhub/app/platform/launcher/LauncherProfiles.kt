package dev.thorhub.app.platform.launcher

import dev.thorhub.core.model.ContentUriGrantMode
import dev.thorhub.core.model.ExternalAppId
import dev.thorhub.core.model.LaunchExtraValue
import dev.thorhub.core.model.LaunchParameterPayload
import dev.thorhub.core.model.LauncherAdapterType
import dev.thorhub.core.model.LauncherId
import dev.thorhub.core.model.LauncherProfile
import dev.thorhub.core.model.LauncherProfileSource
import dev.thorhub.core.model.VerificationState

/**
 * Built-in profiles describe candidate contracts derived from current upstream
 * manifests/source. They remain unverified until the exact installed package,
 * version, and content combination passes a local test launch, and remain
 * editable once copied into the user's Room-backed launcher table.
 */
object LauncherProfiles {
    const val GAME_NATIVE_ACTION = "app.gamenative.LAUNCH_GAME"
    const val GAME_NATIVE_PACKAGE = "app.gamenative"
    const val GAME_NATIVE_ACTIVITY = "app.gamenative.MainActivity"

    val retroArchPackages = listOf(
        "com.retroarch",
        "com.retroarch.aarch64",
        "com.retroarch.ra32",
    )

    private const val retroActivity = "com.retroarch.browser.retroactivity.RetroActivityFuture"

    fun androidNative(packageName: String, activityName: String): LauncherProfile = LauncherProfile(
        id = LauncherId.deterministic("builtin", "android-native:$packageName/$activityName"),
        displayName = "Android Native",
        adapterType = LauncherAdapterType.ANDROID_NATIVE,
        packageCandidates = listOf(ExternalAppId.of(packageName)),
        componentCandidates = listOf("$packageName/$activityName"),
        launchParameters = LaunchParameterPayload(
            action = "android.intent.action.MAIN",
            componentTemplate = "$packageName/$activityName",
        ),
        source = LauncherProfileSource.BUILT_IN,
        verification = VerificationState.UNVERIFIED,
    )

    fun retroArch(contentUri: String, corePath: String, packageName: String): LauncherProfile {
        require(packageName in retroArchPackages) { "Unsupported built-in RetroArch package candidate" }
        return LauncherProfile(
            id = LauncherId.deterministic("builtin", "retroarch:$packageName"),
            displayName = "RetroArch",
            adapterType = LauncherAdapterType.RETROARCH,
            packageCandidates = retroArchPackages.map(ExternalAppId::of),
            componentCandidates = retroArchPackages.map { "$it/$retroActivity" },
            launchParameters = LaunchParameterPayload(
                componentTemplate = "$packageName/$retroActivity",
                extras = mapOf(
                    "ROM" to LaunchExtraValue.Text(contentUri),
                    "LIBRETRO" to LaunchExtraValue.Text(corePath),
                ),
                contentUriGrant = ContentUriGrantMode.READ,
            ),
            source = LauncherProfileSource.BUILT_IN,
            // The payload shape is sourced from upstream, but each installed
            // package/core combination still requires a local test launch.
            verification = VerificationState.UNVERIFIED,
        )
    }

    fun gameNative(source: GameNativeSource, appId: Int): LauncherProfile {
        require(appId > 0) { "GameNative App ID must be positive" }
        return LauncherProfile(
            id = LauncherId.deterministic("builtin", "gamenative"),
            displayName = "GameNative",
            adapterType = LauncherAdapterType.GAME_NATIVE,
            packageCandidates = listOf(ExternalAppId.of(GAME_NATIVE_PACKAGE)),
            componentCandidates = listOf("$GAME_NATIVE_PACKAGE/$GAME_NATIVE_ACTIVITY"),
            launchParameters = LaunchParameterPayload(
                action = GAME_NATIVE_ACTION,
                componentTemplate = "$GAME_NATIVE_PACKAGE/$GAME_NATIVE_ACTIVITY",
                extras = mapOf(
                    "app_id" to LaunchExtraValue.Integer(appId.toLong()),
                    "game_source" to LaunchExtraValue.Text(source.name),
                ),
            ),
            source = LauncherProfileSource.BUILT_IN,
            verification = VerificationState.UNVERIFIED,
        )
    }

    fun configurable(
        id: LauncherId,
        displayName: String,
        packageName: String,
        activityName: String,
        payload: LaunchParameterPayload,
    ): LauncherProfile = LauncherProfile(
        id = id,
        displayName = displayName,
        adapterType = LauncherAdapterType.CONFIGURABLE_EXTERNAL,
        packageCandidates = listOf(ExternalAppId.of(packageName)),
        componentCandidates = listOf("$packageName/$activityName"),
        launchParameters = payload.copy(componentTemplate = "$packageName/$activityName"),
        source = LauncherProfileSource.USER_DEFINED,
        verification = VerificationState.UNVERIFIED,
    )
}

enum class GameNativeSource {
    STEAM,
    GOG,
    EPIC,
    AMAZON,
}
