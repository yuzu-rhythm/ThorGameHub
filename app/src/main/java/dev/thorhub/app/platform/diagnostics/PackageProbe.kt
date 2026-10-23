package dev.thorhub.app.platform.diagnostics

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class CapabilityUiState { READY, NEEDS_ACTION, UNSUPPORTED, UNKNOWN, ERROR }

data class InstalledLaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val versionName: String?,
    val versionCode: Long?,
    val exported: Boolean,
) {
    val componentName: ComponentName get() = ComponentName(packageName, activityName)
}

data class AdapterProbeSummary(
    val adapter: String,
    val state: CapabilityUiState,
    val explanation: String,
    val candidate: InstalledLaunchableApp? = null,
    val staticProfileKnown: Boolean = false,
    val launchContractVerified: Boolean = false,
)

@Singleton
class PackageProbe @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val packageManager = context.packageManager

    fun launchableApps(): List<InstalledLaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            ).asSequence()
                .mapNotNull { info ->
                    val activity = info.activityInfo ?: return@mapNotNull null
                    if (activity.packageName == context.packageName) return@mapNotNull null
                    val packageInfo = runCatching {
                        packageManager.getPackageInfo(activity.packageName, PackageManager.PackageInfoFlags.of(0))
                    }.getOrNull()
                    InstalledLaunchableApp(
                        label = info.loadLabel(packageManager).toString(),
                        packageName = activity.packageName,
                        activityName = activity.name,
                        versionName = packageInfo?.versionName,
                        versionCode = packageInfo?.longVersionCode,
                        exported = activity.exported,
                    )
                }
                .distinctBy { it.componentName }
                .sortedBy { it.label.lowercase() }
                .toList()
        }.onFailure { error ->
            DiagnosticEventLog.record("package_probe", "launcher_query_failed", mapOf("exception" to error::class.java.simpleName))
        }.getOrDefault(emptyList())
    }

    fun probeAdapters(apps: List<InstalledLaunchableApp> = launchableApps()): List<AdapterProbeSummary> =
        summarizeAdapterProbes(apps).also { probes ->
            probes.forEach { probe ->
                DiagnosticEventLog.record(
                    "package_probe",
                    "adapter_probe",
                    mapOf(
                        "adapter" to probe.adapter,
                        "state" to probe.state.name,
                        "candidate_package" to (probe.candidate?.packageName ?: "UNKNOWN"),
                        "static_profile_known" to probe.staticProfileKnown.toString(),
                        "launch_contract_verified" to probe.launchContractVerified.toString(),
                    ),
                )
            }
        }
}

internal fun summarizeAdapterProbes(apps: List<InstalledLaunchableApp>): List<AdapterProbeSummary> {
    fun find(vararg terms: String): InstalledLaunchableApp? = apps.firstOrNull { app ->
        terms.any { term -> app.label.contains(term, ignoreCase = true) || app.packageName.contains(term, ignoreCase = true) }
    }

    val retroArch = find("retroarch")
    val gameNative = find("gamenative", "game native")
    return listOf(
            AdapterProbeSummary(
                adapter = "Android Native",
                state = if (apps.isEmpty()) CapabilityUiState.NEEDS_ACTION else CapabilityUiState.READY,
                explanation = if (apps.isEmpty()) {
                    "No visible launcher activities were returned. Package visibility may be involved."
                } else {
                    "${apps.size} visible launcher activities can be selected for an explicit test launch."
                },
                staticProfileKnown = true,
                launchContractVerified = false,
            ),
            discoveredAdapter(
                name = "RetroArch",
                candidate = retroArch,
                staticProfileKnown = true,
                contractDetail = "Current upstream source documents the RetroActivityFuture ROM/LIBRETRO payload shape. The installed package, core, and content combination is not verified yet.",
            ),
            discoveredAdapter(
                name = "GameNative",
                candidate = gameNative,
                staticProfileKnown = true,
                contractDetail = "Current upstream source exposes app.gamenative.LAUNCH_GAME with typed app_id and game_source extras. The installed package and selected title are not verified yet.",
            ),
            AdapterProbeSummary(
                adapter = "Configurable PS2",
                state = CapabilityUiState.NEEDS_ACTION,
                explanation = "Choose an installed app and configure its exported activity and launch template. No emulator is mandatory.",
                launchContractVerified = false,
            ),
        )
}

private fun discoveredAdapter(
    name: String,
    candidate: InstalledLaunchableApp?,
    staticProfileKnown: Boolean,
    contractDetail: String,
): AdapterProbeSummary =
    if (candidate == null) {
        AdapterProbeSummary(
            adapter = name,
            state = CapabilityUiState.UNKNOWN,
            explanation = "$contractDetail No visible installed candidate was found; this does not prove absence.",
            staticProfileKnown = staticProfileKnown,
            launchContractVerified = false,
        )
    } else {
        AdapterProbeSummary(
            adapter = name,
            state = CapabilityUiState.NEEDS_ACTION,
            explanation = "$contractDetail Installed candidate found; run a user-observed test launch.",
            candidate = candidate,
            staticProfileKnown = staticProfileKnown,
            launchContractVerified = false,
        )
    }
