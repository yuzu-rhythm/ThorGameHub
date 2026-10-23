package dev.thorhub.app.platform.launcher

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thorhub.app.platform.diagnostics.DiagnosticEventLog
import dev.thorhub.app.platform.display.AndroidDisplayCoordinator
import dev.thorhub.app.platform.display.DisplayLaunchEvidence
import dev.thorhub.core.model.AdapterDiagnostics
import dev.thorhub.core.model.AdapterProbeResult
import dev.thorhub.core.model.DeviceDescriptor
import dev.thorhub.core.model.ExitRequestResult
import dev.thorhub.core.model.ExternalExitRequestState
import dev.thorhub.core.model.ExternalLaunchTarget
import dev.thorhub.core.model.ExternalProcessClosureState
import dev.thorhub.core.model.LaunchExtraValue
import dev.thorhub.core.model.LaunchExecutionState
import dev.thorhub.core.model.LaunchIntentResult
import dev.thorhub.core.model.LaunchParameterPayload
import dev.thorhub.core.model.LaunchResult
import dev.thorhub.core.model.LaunchValidation
import dev.thorhub.core.model.LauncherAdapter
import dev.thorhub.core.model.LauncherAdapterId
import dev.thorhub.core.model.LauncherAdapterType
import dev.thorhub.core.model.LauncherProfile
import dev.thorhub.core.model.PlayMethod
import dev.thorhub.core.model.PlayMethodValidity
import dev.thorhub.core.model.ProbeState
import dev.thorhub.core.model.GameSession
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

abstract class AndroidLauncherAdapter(
    @param:ApplicationContext protected val context: Context,
    protected val profile: LauncherProfile,
) : LauncherAdapter {
    protected val packageManager: PackageManager = context.packageManager

    override suspend fun probe(device: DeviceDescriptor): AdapterProbeResult {
        val resolved = profile.componentCandidates.firstNotNullOfOrNull(::resolveComponent)
        if (resolved == null) {
            return AdapterProbeResult(
                state = ProbeState.NEEDS_SETUP,
                evidence = listOf("No visible exported component matched the configured candidates."),
                detail = "Open Setup and choose or install the launcher app.",
            )
        }
        val packageInfo = runCatching {
            packageManager.getPackageInfo(resolved.packageName, PackageManager.PackageInfoFlags.of(0))
        }.getOrNull()
        return AdapterProbeResult(
            state = ProbeState.READY,
            resolvedApp = dev.thorhub.core.model.ExternalAppId.of(resolved.packageName),
            version = packageInfo?.versionName,
            evidence = listOf("exported_component_resolved", "version_code=${packageInfo?.longVersionCode ?: "UNKNOWN"}"),
        )
    }

    override suspend fun validate(method: PlayMethod): LaunchValidation {
        val issues = LaunchContractValidator.validate(profile.adapterType, method.launchParameters)
        if (issues.isNotEmpty()) {
            return LaunchValidation(
                validity = PlayMethodValidity.INVALID,
                checkedAt = Instant.now(),
                reasonCode = issues.first().code,
                evidence = issues.map { it.explanation },
            )
        }
        val component = method.launchParameters.componentTemplate?.let(::resolveComponent)
        return if (component != null) {
            LaunchValidation(
                validity = PlayMethodValidity.VALID,
                checkedAt = Instant.now(),
                evidence = listOf("exported_component_resolved:${component.flattenToShortString()}"),
            )
        } else {
            LaunchValidation(
                validity = PlayMethodValidity.INVALID,
                checkedAt = Instant.now(),
                reasonCode = "component_unresolved",
                evidence = listOf("The configured activity is not visible, exported, or installed."),
            )
        }
    }

    override suspend fun buildLaunchIntent(method: PlayMethod): LaunchIntentResult {
        val validation = validate(method)
        if (validation.validity != PlayMethodValidity.VALID) {
            return LaunchIntentResult(
                state = ProbeState.NEEDS_SETUP,
                evidence = validation.evidence,
                detail = validation.reasonCode,
            )
        }
        val component = method.launchParameters.componentTemplate?.let(ComponentName::unflattenFromString)
        return LaunchIntentResult(
            state = ProbeState.READY,
            request = ExternalLaunchTarget(method.id, method.launchParameters),
            resolvedComponent = component?.flattenToShortString(),
            evidence = listOf("resolveActivity passed; launch has not yet been attempted."),
        )
    }

    override suspend fun requestGracefulExit(session: GameSession): ExitRequestResult = ExitRequestResult(
        requestState = ExternalExitRequestState.UNSUPPORTED,
        closureState = ExternalProcessClosureState.NOT_VERIFIED,
        detail = "Standard mode ends the Game Hub session but does not force-stop the external app.",
    )

    override suspend fun collectDiagnostics(method: PlayMethod): AdapterDiagnostics = AdapterDiagnostics(
        adapterId = id,
        values = mapOf(
            "adapter_type" to profile.adapterType.name,
            "verification" to profile.verification.name,
            "component_configured" to (!method.launchParameters.componentTemplate.isNullOrBlank()).toString(),
            "action" to (method.launchParameters.action ?: "DEFAULT"),
        ),
        redactionRequired = true,
    )

    private fun resolveComponent(flattened: String): ComponentName? {
        val component = ComponentName.unflattenFromString(flattened) ?: return null
        val info = runCatching {
            packageManager.getActivityInfo(component, PackageManager.ComponentInfoFlags.of(0))
        }.getOrNull() ?: return null
        if (!info.exported || !info.enabled) return null
        return component
    }
}

class AndroidNativeLauncherAdapter(context: Context, profile: LauncherProfile) :
    AndroidLauncherAdapter(context, profile) {
    override val id: LauncherAdapterId = LauncherAdapterId.of("android-native")
}

class RetroArchLauncherAdapter(context: Context, profile: LauncherProfile) :
    AndroidLauncherAdapter(context, profile) {
    override val id: LauncherAdapterId = LauncherAdapterId.of("retroarch")
}

class GameNativeLauncherAdapter(context: Context, profile: LauncherProfile) :
    AndroidLauncherAdapter(context, profile) {
    override val id: LauncherAdapterId = LauncherAdapterId.of("gamenative")
}

class ConfigurableExternalLauncherAdapter(context: Context, profile: LauncherProfile) :
    AndroidLauncherAdapter(context, profile) {
    override val id: LauncherAdapterId = LauncherAdapterId.of("configurable-external")
}

@Singleton
class AndroidLaunchExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val displayCoordinator: AndroidDisplayCoordinator,
) {
    fun testResolve(payload: LaunchParameterPayload): ResolvedAndroidLaunch {
        val intent = buildIntent(payload)
        val info = context.packageManager.resolveActivity(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )?.activityInfo
        return if (info != null && info.exported) {
            ResolvedAndroidLaunch(intent, ComponentName(info.packageName, info.name), null)
        } else {
            ResolvedAndroidLaunch(intent, null, "No visible exported activity resolved the configured intent.")
        }
    }

    fun launch(method: PlayMethod, upperDisplayId: Int?): LauncherExecutionEvidence {
        val resolved = testResolve(method.launchParameters)
        val component = resolved.component
        if (component == null) {
            DiagnosticEventLog.record("launch", "contract_unresolved", mapOf("method" to method.id.value))
            return LauncherExecutionEvidence(
                result = LaunchResult(
                    state = LaunchExecutionState.FAILED,
                    methodId = method.id,
                    detail = resolved.error,
                ),
                display = null,
            )
        }

        resolved.intent.component = component
        val display = displayCoordinator.launchIntent(resolved.intent, upperDisplayId)
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(component.packageName, PackageManager.PackageInfoFlags.of(0))
        }.getOrNull()
        DiagnosticEventLog.record(
            "launch",
            "adapter_launch_result",
            mapOf(
                "method" to method.id.value,
                "package" to component.packageName,
                "version_code" to (packageInfo?.longVersionCode?.toString() ?: "UNKNOWN"),
                "request_accepted" to display.requestAccepted.toString(),
                "physical_placement" to "UNKNOWN",
            ),
        )
        return LauncherExecutionEvidence(
            result = LaunchResult(
                state = if (display.requestAccepted) LaunchExecutionState.ACCEPTED else LaunchExecutionState.FAILED,
                methodId = method.id,
                requestedDisplayId = upperDisplayId?.let { dev.thorhub.core.model.DisplayId.of(it.toString()) },
                resolvedComponent = display.resolvedComponent,
                detail = display.message,
                evidence = listOf("physical_placement=UNKNOWN"),
            ),
            display = display,
        )
    }

    private fun buildIntent(payload: LaunchParameterPayload): Intent {
        val action = payload.action ?: Intent.ACTION_MAIN
        return Intent(action).apply {
            payload.componentTemplate?.let(ComponentName::unflattenFromString)?.let { component = it }
            payload.dataTemplate?.toUri()?.let { data = it }
            if (action == Intent.ACTION_MAIN) addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            when (payload.contentUriGrant) {
                dev.thorhub.core.model.ContentUriGrantMode.NONE -> Unit
                dev.thorhub.core.model.ContentUriGrantMode.READ -> addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                dev.thorhub.core.model.ContentUriGrantMode.READ_WRITE -> addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            payload.extras.forEach { (key, value) ->
                when (value) {
                    is LaunchExtraValue.Text -> putExtra(key, value.value)
                    is LaunchExtraValue.Integer -> if (value.value in Int.MIN_VALUE..Int.MAX_VALUE) {
                        putExtra(key, value.value.toInt())
                    } else {
                        putExtra(key, value.value)
                    }
                    is LaunchExtraValue.BooleanValue -> putExtra(key, value.value)
                    is LaunchExtraValue.TextList -> putStringArrayListExtra(key, ArrayList(value.value))
                }
            }
            val contentUri = sequenceOf(payload.dataTemplate)
                .plus(payload.extras.values.filterIsInstance<LaunchExtraValue.Text>().map { it.value })
                .filterNotNull()
                .firstOrNull { it.startsWith("content://") }
            if (contentUri != null && payload.contentUriGrant != dev.thorhub.core.model.ContentUriGrantMode.NONE) {
                clipData = ClipData.newUri(context.contentResolver, "Thor Game Hub content", contentUri.toUri())
            }
        }
    }
}

data class ResolvedAndroidLaunch(
    val intent: Intent,
    val component: ComponentName?,
    val error: String?,
)

data class LauncherExecutionEvidence(
    val result: LaunchResult,
    val display: DisplayLaunchEvidence?,
)

class LauncherAdapterFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun create(profile: LauncherProfile): LauncherAdapter = when (profile.adapterType) {
        LauncherAdapterType.ANDROID_NATIVE -> AndroidNativeLauncherAdapter(context, profile)
        LauncherAdapterType.RETROARCH -> RetroArchLauncherAdapter(context, profile)
        LauncherAdapterType.GAME_NATIVE -> GameNativeLauncherAdapter(context, profile)
        LauncherAdapterType.CONFIGURABLE_EXTERNAL -> ConfigurableExternalLauncherAdapter(context, profile)
        LauncherAdapterType.UNKNOWN -> error("A launcher adapter type must be selected before use.")
    }
}
