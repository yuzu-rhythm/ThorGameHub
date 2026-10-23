package dev.thorhub.app.platform.display

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thorhub.app.CompanionActivity
import dev.thorhub.app.HomeActivity
import dev.thorhub.app.platform.diagnostics.DiagnosticEventLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDisplayCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val assignmentStore: DisplayAssignmentStore,
) {
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val mutex = Mutex()
    private var pendingAssignment: Pair<String, StoredDisplayAssignment>? = null

    suspend fun refresh(): DisplayTopology = mutex.withLock { refreshLocked() }

    private suspend fun refreshLocked(): DisplayTopology {
        val buildHash = DisplayAssignmentStore.sha256(Build.FINGERPRINT)
        val raw = displayManager.displays.map(::toDescriptor)
        val stored = assignmentStore.load(buildHash)
        val pending = pendingAssignment?.takeIf { it.first == buildHash }?.second
        return DisplayTopologyResolver.resolve(raw, stored, pending, buildHash).also { topology ->
            if (pending != null && !topology.matches(pending)) {
                pendingAssignment = null
            }
            DiagnosticEventLog.record(
                "display",
                "topology_refreshed",
                mapOf(
                    "count" to topology.displays.size.toString(),
                    "mode" to topology.mode.name,
                    "upper_candidate" to (topology.upperDisplayId?.toString() ?: "UNKNOWN"),
                    "lower_candidate" to (topology.lowerDisplayId?.toString() ?: "UNKNOWN"),
                    "assignment_verified" to topology.assignmentVerified.toString(),
                ),
            )
        }
    }

    suspend fun swap(topology: DisplayTopology): DisplayTopology = mutex.withLock {
        val upper = topology.upper ?: return@withLock topology
        val lower = topology.lower ?: return@withLock topology
        pendingAssignment = topology.firmwareFingerprintHash to StoredDisplayAssignment(
            upperSignatureHash = DisplayAssignmentStore.sha256(lower.identitySignature),
            lowerSignatureHash = DisplayAssignmentStore.sha256(upper.identitySignature),
            verified = false,
        )
        DiagnosticEventLog.record("display", "placement_swapped", mapOf("confirmation" to "pending"))
        refreshLocked()
    }

    suspend fun confirm(topology: DisplayTopology): DisplayTopology = mutex.withLock {
        val upper = topology.upper ?: return@withLock topology
        val lower = topology.lower ?: return@withLock topology
        assignmentStore.save(topology.firmwareFingerprintHash, upper, lower, verified = true)
        pendingAssignment = null
        DiagnosticEventLog.record("display", "placement_confirmed_by_user")
        refreshLocked()
    }

    fun launchCompanion(
        displayId: Int,
        placementTest: Boolean = true,
        sessionId: String? = null,
    ): DisplayLaunchEvidence {
        val intent = Intent(context, CompanionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(CompanionActivity.EXTRA_REQUESTED_DISPLAY_ID, displayId)
            putExtra(CompanionActivity.EXTRA_PLACEMENT_TEST, placementTest)
            sessionId?.let { putExtra(CompanionActivity.EXTRA_SESSION_ID, it) }
        }
        return startIntent(intent, displayId, ownActivity = true)
    }

    fun showHomeOnUpper(displayId: Int?): DisplayLaunchEvidence {
        val intent = Intent(context, HomeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            displayId?.let { putExtra(HomeActivity.EXTRA_REQUESTED_DISPLAY_ID, it) }
            putExtra(HomeActivity.EXTRA_LOGICAL_UPPER_ROUTE, true)
        }
        return startIntent(intent, displayId, ownActivity = true)
    }

    fun launchExternal(componentName: ComponentName, displayId: Int?): DisplayLaunchEvidence {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = componentName
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return startIntent(intent, displayId, ownActivity = false)
    }

    /** Launches a fully validated adapter intent without discarding its data or typed extras. */
    fun launchIntent(intent: Intent, displayId: Int?): DisplayLaunchEvidence =
        startIntent(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), displayId, ownActivity = false)

    private fun startIntent(intent: Intent, displayId: Int?, ownActivity: Boolean): DisplayLaunchEvidence {
        val resolved = context.packageManager.resolveActivity(intent, 0)?.activityInfo
        val component = resolved?.let { "${it.packageName}/${it.name}" }
        if (resolved == null) {
            return DisplayLaunchEvidence(
                requestedDisplayId = displayId,
                requestAccepted = false,
                resolvedComponent = null,
                observedOwnActivityDisplayId = null,
                physicalPlacementConfirmed = null,
                companionKnownActive = null,
                message = "No exported activity resolved.",
            )
        }
        return runCatching {
            val options = ActivityOptions.makeBasic().apply {
                if (displayId != null) setLaunchDisplayId(displayId)
            }
            context.startActivity(intent, options.toBundle())
            DiagnosticEventLog.record(
                "launch",
                if (ownActivity) "companion_launch_requested" else "external_launch_requested",
                mapOf(
                    "requested_display_id" to (displayId?.toString() ?: "DEFAULT"),
                    "resolved_component" to component.orEmpty(),
                    "physical_placement" to "UNKNOWN",
                ),
            )
            DisplayLaunchEvidence(
                requestedDisplayId = displayId,
                requestAccepted = true,
                resolvedComponent = component,
                observedOwnActivityDisplayId = null,
                physicalPlacementConfirmed = null,
                companionKnownActive = null,
                message = "Android accepted the launch request; physical placement remains UNKNOWN.",
            )
        }.getOrElse { error ->
            DiagnosticEventLog.record(
                "launch",
                "launch_failed",
                mapOf("exception" to error::class.java.simpleName),
            )
            DisplayLaunchEvidence(
                requestedDisplayId = displayId,
                requestAccepted = false,
                resolvedComponent = component,
                observedOwnActivityDisplayId = null,
                physicalPlacementConfirmed = null,
                companionKnownActive = null,
                message = "Launch failed: ${error::class.java.simpleName}",
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun toDescriptor(display: Display): DisplayDescriptor {
        val metrics = DisplayMetrics().also(display::getRealMetrics)
        val mode = display.mode
        return DisplayDescriptor(
            id = display.displayId,
            // Android 37 no longer exposes Display#getUniqueId in the public SDK.
            // Do not substitute a transient display ID or product label as identity.
            uniqueId = null,
            name = display.name,
            type = publicDisplayType(display),
            typeLabel = publicDisplayTypeLabel(display),
            flags = display.flags,
            state = display.state,
            stateLabel = displayStateLabel(display.state),
            widthPx = mode.physicalWidth.takeIf { it > 0 } ?: metrics.widthPixels,
            heightPx = mode.physicalHeight.takeIf { it > 0 } ?: metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            rotation = display.rotation,
            refreshRateHz = display.refreshRate,
            supportedRefreshRatesHz = display.supportedModes.map { it.refreshRate }.distinct().sorted(),
            isDefault = display.displayId == Display.DEFAULT_DISPLAY,
        )
    }

    private fun publicDisplayType(display: Display): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && display.isInternal -> 1
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN -> 2
        else -> 0
    }

    private fun publicDisplayTypeLabel(display: Display): String = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && display.isInternal -> "INTERNAL"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN -> "NON_INTERNAL"
        else -> "UNKNOWN_PUBLIC_API"
    }

    private fun displayStateLabel(state: Int): String = when (state) {
        Display.STATE_OFF -> "OFF"
        Display.STATE_ON -> "ON"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        Display.STATE_VR -> "VR"
        else -> "UNKNOWN($state)"
    }

    private fun DisplayTopology.matches(assignment: StoredDisplayAssignment): Boolean =
        upper?.let { DisplayAssignmentStore.sha256(it.identitySignature) } == assignment.upperSignatureHash &&
            lower?.let { DisplayAssignmentStore.sha256(it.identitySignature) } == assignment.lowerSignatureHash
}
