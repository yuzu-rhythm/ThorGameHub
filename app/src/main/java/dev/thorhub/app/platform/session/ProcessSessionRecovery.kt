package dev.thorhub.app.platform.session

import dev.thorhub.app.platform.diagnostics.DiagnosticEventLog
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Runs unresolved-session recovery once at process startup, not once per UI model. */
@Singleton
class ProcessSessionRecovery @Inject constructor(
    private val sessionTracker: RoomSessionTracker,
) {
    private val started = AtomicBoolean(false)

    fun start(): Boolean {
        if (!started.compareAndSet(false, true)) return true
        // Finish the tiny local transaction before any Activity can create a
        // ViewModel or accept a launch, eliminating startup/recovery races.
        val successful = runBlocking(Dispatchers.IO) {
            runCatching { sessionTracker.recover() }
                .onFailure { error ->
                    DiagnosticEventLog.record(
                        "session",
                        "startup_recovery_failed",
                        mapOf("exception" to error::class.java.simpleName),
                    )
                }.isSuccess
        }
        if (!successful) started.set(false)
        return successful
    }
}
