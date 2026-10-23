package dev.thorhub.app.platform.diagnostics

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import java.time.Instant

data class LocalDiagnosticEvent(
    val sequence: Long,
    val category: String,
    val name: String,
    val wallTime: Instant,
    val elapsedRealtimeMs: Long,
    val details: Map<String, String>,
)

/**
 * A bounded, app-owned log. It intentionally never reads system logcat and never stores raw URIs,
 * paths, intent payloads, notes, or secrets.
 */
object DiagnosticEventLog {
    private const val MAX_EVENTS = 240
    private val events = ArrayDeque<LocalDiagnosticEvent>(MAX_EVENTS)
    private var nextSequence = 1L

    @Synchronized
    fun record(category: String, name: String, details: Map<String, String> = emptyMap()) {
        if (events.size == MAX_EVENTS) events.removeFirst()
        events.addLast(
            LocalDiagnosticEvent(
                sequence = nextSequence++,
                category = category.take(40),
                name = name.take(80),
                wallTime = Instant.now(),
                elapsedRealtimeMs = SystemClock.elapsedRealtime(),
                details = details.entries
                    .take(12)
                    .associate { (key, value) -> key.take(40) to sanitize(value) },
            ),
        )
    }

    @Synchronized
    fun snapshot(limit: Int = MAX_EVENTS): List<LocalDiagnosticEvent> =
        events.takeLast(limit.coerceIn(0, MAX_EVENTS))

    private fun sanitize(value: String): String = value
        .replace(Regex("content://\\S+", RegexOption.IGNORE_CASE), "[content-uri]")
        .replace(Regex("(?:/[^/\\s]+){2,}"), "[path]")
        .replace(Regex("(?i)(token|secret|api[_-]?key)\\s*[=:]\\s*\\S+"), "$1=[redacted]")
        .take(240)
}

class AndroidLifecycleMonitor(
    private val application: Application,
) : Application.ActivityLifecycleCallbacks, DisplayManager.DisplayListener {
    private val displayManager = application.getSystemService(DisplayManager::class.java)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val event = when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> "screen_on"
                Intent.ACTION_SCREEN_OFF -> "screen_off"
                Intent.ACTION_USER_PRESENT -> "user_present"
                else -> "screen_signal_unknown"
            }
            DiagnosticEventLog.record(
                category = "screen_state",
                name = event,
                details = mapOf("lid_interpretation" to "UNKNOWN"),
            )
        }
    }

    fun start() {
        application.registerActivityLifecycleCallbacks(this)
        displayManager.registerDisplayListener(this, Handler(Looper.getMainLooper()))
        application.registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        DiagnosticEventLog.record("process", "application_created")
    }

    override fun onDisplayAdded(displayId: Int) = recordDisplay("display_added", displayId)
    override fun onDisplayRemoved(displayId: Int) = recordDisplay("display_removed", displayId)
    override fun onDisplayChanged(displayId: Int) = recordDisplay("display_changed", displayId)

    private fun recordDisplay(name: String, displayId: Int) {
        val display = displayManager.getDisplay(displayId)
        DiagnosticEventLog.record(
            category = "display_listener",
            name = name,
            details = mapOf(
                "display_id" to displayId.toString(),
                "state" to (display?.state ?: Display.STATE_UNKNOWN).toString(),
                "lid_interpretation" to "UNKNOWN",
            ),
        )
    }

    private fun recordActivity(activity: Activity, event: String) {
        DiagnosticEventLog.record(
            category = "app_activity",
            name = event,
            details = mapOf(
                "activity" to activity::class.java.simpleName,
                "observed_display_id" to (activity.display?.displayId?.toString() ?: "UNKNOWN"),
            ),
        )
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) = recordActivity(activity, "created")
    override fun onActivityStarted(activity: Activity) = recordActivity(activity, "started")
    override fun onActivityResumed(activity: Activity) = recordActivity(activity, "resumed")
    override fun onActivityPaused(activity: Activity) = recordActivity(activity, "paused")
    override fun onActivityStopped(activity: Activity) = recordActivity(activity, "stopped")
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = recordActivity(activity, "state_saved")
    override fun onActivityDestroyed(activity: Activity) = recordActivity(activity, "destroyed")
}
