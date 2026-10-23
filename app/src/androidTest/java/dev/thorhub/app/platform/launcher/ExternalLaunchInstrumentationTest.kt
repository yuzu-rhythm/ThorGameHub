package dev.thorhub.app.platform.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.common.truth.Truth.assertThat
import dev.thorhub.app.CompanionActivity
import dev.thorhub.app.platform.display.AndroidDisplayCoordinator
import dev.thorhub.app.platform.display.DisplayAssignmentStore
import dev.thorhub.app.testfixture.FakeExternalGameActivity
import dev.thorhub.core.model.ContentUriGrantMode
import dev.thorhub.core.model.DeviceId
import dev.thorhub.core.model.InstallationId
import dev.thorhub.core.model.LaunchExtraValue
import dev.thorhub.core.model.LaunchExecutionState
import dev.thorhub.core.model.LaunchParameterPayload
import dev.thorhub.core.model.LauncherId
import dev.thorhub.core.model.PlayMethod
import dev.thorhub.core.model.PlayMethodId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalLaunchInstrumentationTest {
    @After
    fun finishCompanion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            Stage.entries
                .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .filterIsInstance<CompanionActivity>()
                .distinct()
                .forEach { it.finish() }
        }
    }

    @Test
    fun executorPreservesContractAcrossApksAndCompanionRemainsOnLowerDisplay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testContext = instrumentation.context
        val secondary = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .displays
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        assumeTrue("A simulated secondary display is required for this test.", secondary != null)
        requireNotNull(secondary)

        val coordinator = AndroidDisplayCoordinator(context, DisplayAssignmentStore(context))
        val companionEvidence = coordinator.launchCompanion(secondary.displayId, placementTest = true)
        assertThat(companionEvidence.requestAccepted).isTrue()
        assertThat(waitForCompanionDisplay(instrumentation)).isEqualTo(secondary.displayId)

        val reportAction = "${context.packageName}.EXTERNAL_REPORT.${System.nanoTime()}"
        val report = AtomicReference<Intent>()
        val latch = CountDownLatch(1)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                report.set(intent)
                latch.countDown()
            }
        }
        context.registerReceiver(receiver, IntentFilter(reportAction), Context.RECEIVER_EXPORTED)
        try {
            val contentUri = "content://${context.packageName}.launch-fixture/item/42"
            val component = "${testContext.packageName}/${FakeExternalGameActivity::class.java.name}"
            val method = PlayMethod(
                id = PlayMethodId.of("instrumented-external"),
                installationId = InstallationId.of("instrumented-installation"),
                deviceId = DeviceId.of("instrumented-device"),
                launcherId = LauncherId.of("instrumented-launcher"),
                launchParameters = LaunchParameterPayload(
                    action = ACTION_FAKE_GAME,
                    dataTemplate = contentUri,
                    componentTemplate = component,
                    extras = mapOf(
                        FakeExternalGameActivity.EXTRA_REPORT_ACTION to LaunchExtraValue.Text(reportAction),
                        FakeExternalGameActivity.EXTRA_REPORT_PACKAGE to LaunchExtraValue.Text(context.packageName),
                        FakeExternalGameActivity.EXTRA_SLOT to LaunchExtraValue.Integer(7),
                        FakeExternalGameActivity.EXTRA_RESUME to LaunchExtraValue.BooleanValue(true),
                        FakeExternalGameActivity.EXTRA_LABELS to LaunchExtraValue.TextList(listOf("fast", "safe")),
                    ),
                    contentUriGrant = ContentUriGrantMode.READ,
                ),
            )
            val executor = AndroidLaunchExecutor(context, coordinator)

            val execution = executor.launch(method, Display.DEFAULT_DISPLAY)

            assertThat(execution.result.state).isEqualTo(LaunchExecutionState.ACCEPTED)
            assertThat(execution.display?.requestAccepted).isTrue()
            assertThat(execution.display?.requestedDisplayId).isEqualTo(Display.DEFAULT_DISPLAY)
            assertThat(execution.result.evidence).containsExactly("physical_placement=UNKNOWN")
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue()
            val observed = requireNotNull(report.get())
            assertThat(observed.getStringExtra(FakeExternalGameActivity.EXTRA_OBSERVED_ACTION))
                .isEqualTo(ACTION_FAKE_GAME)
            assertThat(observed.getStringExtra(FakeExternalGameActivity.EXTRA_OBSERVED_DATA)).isEqualTo(contentUri)
            assertThat(observed.getStringExtra(FakeExternalGameActivity.EXTRA_OBSERVED_CLIP_URI)).isEqualTo(contentUri)
            assertThat(observed.getIntExtra(FakeExternalGameActivity.EXTRA_OBSERVED_DISPLAY_ID, -1))
                .isEqualTo(Display.DEFAULT_DISPLAY)
            assertThat(observed.getIntExtra(FakeExternalGameActivity.EXTRA_OBSERVED_FLAGS, 0) and
                Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
            assertThat(observed.getIntExtra(FakeExternalGameActivity.EXTRA_OBSERVED_SLOT, -1)).isEqualTo(7)
            assertThat(observed.getBooleanExtra(FakeExternalGameActivity.EXTRA_OBSERVED_RESUME, false)).isTrue()
            assertThat(observed.getStringArrayListExtra(FakeExternalGameActivity.EXTRA_OBSERVED_LABELS))
                .containsExactly("fast", "safe")
                .inOrder()
            assertThat(observed.getStringExtra(FakeExternalGameActivity.EXTRA_OBSERVED_CONTENT))
                .isEqualTo("thor-launch-fixture")
            assertThat(waitForCompanionDisplay(instrumentation)).isEqualTo(secondary.displayId)
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun waitForCompanionDisplay(instrumentation: android.app.Instrumentation): Int? {
        var displayId: Int? = null
        var remainingAttempts = 100
        while (displayId == null && remainingAttempts-- > 0) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                displayId = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<CompanionActivity>()
                    .firstOrNull()
                    ?.display
                    ?.displayId
            }
            if (displayId == null) SystemClock.sleep(100)
        }
        return displayId
    }

    companion object {
        private const val ACTION_FAKE_GAME = "dev.thorhub.test.action.LAUNCH_FAKE_GAME"
    }
}
