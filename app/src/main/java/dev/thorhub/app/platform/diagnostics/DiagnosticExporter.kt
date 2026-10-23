package dev.thorhub.app.platform.diagnostics

import android.os.Build
import dev.thorhub.app.BuildConfig
import dev.thorhub.app.platform.display.DisplayAssignmentStore
import dev.thorhub.app.platform.display.DisplayTopology
import dev.thorhub.app.platform.home.HomeRoleState
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class DiagnosticExporter @Inject constructor() {
    fun export(
        topology: DisplayTopology?,
        homeRole: HomeRoleState,
        probes: List<AdapterProbeSummary>,
    ): String {
        val root = JSONObject()
            .put("schemaVersion", 1)
            .put("generatedAt", java.time.Instant.now().toString())
            .put(
                "app",
                JSONObject()
                    .put("applicationId", BuildConfig.APPLICATION_ID)
                    .put("versionName", BuildConfig.VERSION_NAME)
                    .put("versionCode", BuildConfig.VERSION_CODE)
                    .put("flavor", BuildConfig.FLAVOR)
                    .put("enhancedCapabilities", BuildConfig.ENHANCED_CAPABILITIES),
            )
            .put(
                "android",
                JSONObject()
                    .put("sdkInt", Build.VERSION.SDK_INT)
                    .put("release", Build.VERSION.RELEASE)
                    .put("model", Build.MODEL)
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("buildFingerprintHash", DisplayAssignmentStore.sha256(Build.FINGERPRINT))
                    .put("exactFingerprintIncluded", false),
            )
            .put(
                "homeRole",
                JSONObject()
                    .put("status", homeRole.status.name)
                    .put("explanation", homeRole.explanation),
            )
            .put("displayTopology", topologyJson(topology))
            .put("adapterProbes", probesJson(probes))
            .put("recentEvents", eventsJson(DiagnosticEventLog.snapshot(160)))
            .put(
                "redaction",
                JSONObject()
                    .put("rawPathsIncluded", false)
                    .put("rawUrisIncluded", false)
                    .put("secretsIncluded", false)
                    .put("notesIncluded", false)
                    .put("rawIntentExtrasIncluded", false),
            )
        return root.toString(2)
    }

    private fun topologyJson(topology: DisplayTopology?): Any {
        if (topology == null) return JSONObject.NULL
        return JSONObject()
            .put("mode", topology.mode.name)
            .put("upperCandidateId", topology.upperDisplayId)
            .put("lowerCandidateId", topology.lowerDisplayId)
            .put("assignmentVerified", topology.assignmentVerified)
            .put(
                "displays",
                JSONArray().apply {
                    topology.displays.forEach { display ->
                        put(
                            JSONObject()
                                .put("id", display.id)
                                .put("uniqueIdHash", display.uniqueId?.let(DisplayAssignmentStore::sha256))
                                .put("name", display.name)
                                .put("type", display.typeLabel)
                                .put("flags", "0x${display.flags.toString(16)}")
                                .put("state", display.stateLabel)
                                .put("widthPx", display.widthPx)
                                .put("heightPx", display.heightPx)
                                .put("densityDpi", display.densityDpi)
                                .put("rotation", display.rotation)
                                .put("refreshRateHz", display.refreshRateHz.toDouble())
                                .put("supportedRefreshRatesHz", JSONArray(display.supportedRefreshRatesHz))
                                .put("role", display.role.name)
                                .put("isDefault", display.isDefault),
                        )
                    }
                },
            )
    }

    private fun probesJson(probes: List<AdapterProbeSummary>) = JSONArray().apply {
        probes.forEach { probe ->
            put(
                JSONObject()
                    .put("adapter", probe.adapter)
                    .put("state", probe.state.name)
                    .put("explanation", probe.explanation)
                    .put("package", probe.candidate?.packageName)
                    .put("component", probe.candidate?.activityName)
                    .put("versionName", probe.candidate?.versionName)
                    .put("versionCode", probe.candidate?.versionCode)
                    .put("exported", probe.candidate?.exported)
                    .put("staticProfileKnown", probe.staticProfileKnown)
                    .put("launchContractVerified", probe.launchContractVerified),
            )
        }
    }

    private fun eventsJson(events: List<LocalDiagnosticEvent>) = JSONArray().apply {
        events.forEach { event ->
            put(
                JSONObject()
                    .put("sequence", event.sequence)
                    .put("category", event.category)
                    .put("name", event.name)
                    .put("wallTime", event.wallTime.toString())
                    .put("elapsedRealtimeMs", event.elapsedRealtimeMs)
                    .put("details", JSONObject(event.details)),
            )
        }
    }
}
