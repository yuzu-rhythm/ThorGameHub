package dev.thorhub.app.platform.safety

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CrashLoopGuardInstrumentationTest {
    private lateinit var context: Context
    private lateinit var guard: CrashLoopGuard
    private var originalExceptionHandler: Thread.UncaughtExceptionHandler? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        originalExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        guard = CrashLoopGuard(context)
        assertThat(guard.clearAndRetry()).isTrue()
    }

    @After
    fun tearDown() {
        guard.clearAndRetry()
        Thread.setDefaultUncaughtExceptionHandler(originalExceptionHandler)
    }

    @Test
    fun threeRecordedCrashesPersistSafeModeAcrossGuardRecreationUntilExplicitRetry() {
        val base = 10_000_000L
        guard.recordCrash(base - 30_000L)
        guard.recordCrash(base - 10_000L)
        guard.recordCrash(base)
        assertThat(guard.isSafeMode).isTrue()

        val restarted = CrashLoopGuard(context)
        restarted.onProcessStart(base + 1_000L)
        assertThat(restarted.isSafeMode).isTrue()

        assertThat(restarted.clearAndRetry()).isTrue()
        val healthyRestart = CrashLoopGuard(context)
        healthyRestart.onProcessStart(base + 2_000L)
        assertThat(healthyRestart.isSafeMode).isFalse()
    }
}
