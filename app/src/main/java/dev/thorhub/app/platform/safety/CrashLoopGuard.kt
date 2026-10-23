package dev.thorhub.app.platform.safety

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

object CrashLoopPolicy {
    const val DEFAULT_THRESHOLD = 3
    const val DEFAULT_WINDOW_MILLIS = 2 * 60 * 1_000L

    fun recentCrashes(
        timestamps: Iterable<Long>,
        now: Long,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    ): List<Long> = timestamps
        .filter { timestamp -> timestamp in (now - windowMillis)..now }
        .sorted()

    fun shouldUseSafeMode(
        timestamps: Iterable<Long>,
        now: Long,
        threshold: Int = DEFAULT_THRESHOLD,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    ): Boolean = recentCrashes(timestamps, now, windowMillis).size >= threshold
}

/** App-private crash-loop breaker for a HOME candidate. */
// These writes are crash-boundary persistence gates. Their Boolean commit
// result is deliberately available before recovery state or process health is
// reported; asynchronous SharedPreferences.apply() cannot provide that proof.
@SuppressLint("ApplySharedPref", "UseKtx")
@Singleton
class CrashLoopGuard @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val installed = AtomicBoolean(false)
    private val lock = Any()

    @Volatile
    var isSafeMode: Boolean = false
        private set

    fun onProcessStart(now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val recent = CrashLoopPolicy.recentCrashes(readTimestamps(), now)
            isSafeMode = preferences.getBoolean(KEY_SAFE_MODE, false) ||
                CrashLoopPolicy.shouldUseSafeMode(recent, now)
            writeState(recent, isSafeMode)
        }
        installExceptionRecorder()
    }

    fun markHealthy(): Boolean = synchronized(lock) {
        val persisted = preferences.edit()
                .remove(KEY_CRASH_TIMESTAMPS)
                .putBoolean(KEY_SAFE_MODE, false)
                .commit()
        if (persisted) isSafeMode = false
        persisted
    }

    fun clearAndRetry(): Boolean = markHealthy()

    fun enterSafeMode() {
        synchronized(lock) {
            preferences.edit().putBoolean(KEY_SAFE_MODE, true).commit()
            isSafeMode = true
        }
    }

    internal fun recordCrash(now: Long = System.currentTimeMillis()) {
        synchronized(lock) {
            val recent = CrashLoopPolicy.recentCrashes(readTimestamps() + now, now)
            val safe = CrashLoopPolicy.shouldUseSafeMode(recent, now)
            writeState(recent, safe)
            isSafeMode = safe
        }
    }

    private fun installExceptionRecorder() {
        if (!installed.compareAndSet(false, true)) return
        val prior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { recordCrash() }
            prior?.uncaughtException(thread, error)
        }
    }

    private fun readTimestamps(): List<Long> = preferences.getString(KEY_CRASH_TIMESTAMPS, null)
        ?.split(',')
        ?.mapNotNull(String::toLongOrNull)
        .orEmpty()

    private fun writeState(timestamps: List<Long>, safeMode: Boolean) {
        preferences.edit()
            .putString(KEY_CRASH_TIMESTAMPS, timestamps.joinToString(","))
            .putBoolean(KEY_SAFE_MODE, safeMode)
            .commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "thor_crash_loop_guard"
        const val KEY_CRASH_TIMESTAMPS = "crash_timestamps"
        const val KEY_SAFE_MODE = "safe_mode"
    }
}
