package dev.thorhub.app.platform.display

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.thorhub.app.CompanionActivity
import dev.thorhub.app.HomeActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecondaryDisplayInstrumentationTest {
    @After
    fun finishHubActivities() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            Stage.entries
                .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .filter { it is HomeActivity || it is CompanionActivity }
                .distinct()
                .forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun companionLaunchesOnAvailableSecondaryDisplay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val target = displayManager.displays.firstOrNull { it.displayId != 0 }
        assumeTrue("A simulated secondary display is required for this test.", target != null)
        requireNotNull(target)

        val intent = Intent(context, CompanionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(CompanionActivity.EXTRA_REQUESTED_DISPLAY_ID, target.displayId)
            .putExtra(CompanionActivity.EXTRA_PLACEMENT_TEST, true)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(target.displayId).toBundle()
        context.startActivity(intent, options)

        var observedDisplayId: Int? = null
        var remainingAttempts = 30
        while (observedDisplayId != target.displayId && remainingAttempts-- > 0) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                observedDisplayId = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<CompanionActivity>()
                    .firstOrNull()
                    ?.display
                    ?.displayId
            }
            if (observedDisplayId != target.displayId) SystemClock.sleep(100)
        }

        assertEquals(target.displayId, observedDisplayId)
    }

    @Test
    fun homeAndCompanionRemainResumedOnSeparateVirtualDisplays() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val target = secondaryDisplay(context)
        assumeTrue("A simulated secondary display is required for this test.", target != null)
        requireNotNull(target)

        val homeIntent = Intent(context, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(
            homeIntent,
            ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle(),
        )
        assertEquals(
            Display.DEFAULT_DISPLAY,
            waitForResumedDisplay(instrumentation, HomeActivity::class.java),
        )

        val companionIntent = Intent(context, CompanionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(CompanionActivity.EXTRA_REQUESTED_DISPLAY_ID, target.displayId)
            .putExtra(CompanionActivity.EXTRA_PLACEMENT_TEST, true)
        context.startActivity(
            companionIntent,
            ActivityOptions.makeBasic().setLaunchDisplayId(target.displayId).toBundle(),
        )

        var homeDisplayId: Int? = null
        var companionDisplayId: Int? = null
        var remainingAttempts = 100
        while (
            (homeDisplayId != Display.DEFAULT_DISPLAY || companionDisplayId != target.displayId) &&
            remainingAttempts-- > 0
        ) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                val resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                homeDisplayId = resumed.filterIsInstance<HomeActivity>().firstOrNull()?.display?.displayId
                companionDisplayId = resumed.filterIsInstance<CompanionActivity>().firstOrNull()?.display?.displayId
            }
            if (homeDisplayId != Display.DEFAULT_DISPLAY || companionDisplayId != target.displayId) {
                SystemClock.sleep(100)
            }
        }

        assertEquals(Display.DEFAULT_DISPLAY, homeDisplayId)
        assertEquals(target.displayId, companionDisplayId)
    }

    @Test
    fun companionRecreationRetainsRequestedVirtualDisplay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val target = secondaryDisplay(context)
        assumeTrue("A simulated secondary display is required for this test.", target != null)
        requireNotNull(target)

        val intent = Intent(context, CompanionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(CompanionActivity.EXTRA_REQUESTED_DISPLAY_ID, target.displayId)
            .putExtra(CompanionActivity.EXTRA_PLACEMENT_TEST, true)
        context.startActivity(
            intent,
            ActivityOptions.makeBasic().setLaunchDisplayId(target.displayId).toBundle(),
        )

        var original: CompanionActivity? = null
        var remainingAttempts = 50
        while (original == null && remainingAttempts-- > 0) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                original = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<CompanionActivity>()
                    .firstOrNull { it.display?.displayId == target.displayId }
            }
            if (original == null) SystemClock.sleep(100)
        }
        requireNotNull(original) { "Companion did not reach RESUMED on the virtual display." }

        instrumentation.runOnMainSync { original?.recreate() }

        var recreated: CompanionActivity? = null
        remainingAttempts = 100
        while (recreated == null && remainingAttempts-- > 0) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                recreated = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<CompanionActivity>()
                    .firstOrNull { it !== original && it.display?.displayId == target.displayId }
            }
            if (recreated == null) SystemClock.sleep(100)
        }

        assertNotSame(original, recreated)
        assertEquals(target.displayId, recreated?.display?.displayId)
        assertEquals(
            target.displayId,
            recreated?.intent?.getIntExtra(CompanionActivity.EXTRA_REQUESTED_DISPLAY_ID, -1),
        )
    }

    @Test
    fun existingSingleTaskCompanionAcceptsUpdatedSessionIntent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val target = secondaryDisplay(context)
        assumeTrue("A simulated secondary display is required for this test.", target != null)
        requireNotNull(target)
        val coordinator = AndroidDisplayCoordinator(context, DisplayAssignmentStore(context))

        assertEquals(
            true,
            coordinator.launchCompanion(
                displayId = target.displayId,
                placementTest = true,
                sessionId = "old-session",
            ).requestAccepted,
        )
        var original: CompanionActivity? = null
        var remainingAttempts = 100
        while (original == null && remainingAttempts-- > 0) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                original = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<CompanionActivity>()
                    .firstOrNull()
            }
            if (original == null) SystemClock.sleep(100)
        }
        requireNotNull(original)

        assertEquals(
            true,
            coordinator.launchCompanion(
                displayId = target.displayId,
                placementTest = false,
                sessionId = "new-session",
            ).requestAccepted,
        )

        var updated: CompanionActivity? = null
        remainingAttempts = 100
        while (updated?.intent?.getStringExtra(CompanionActivity.EXTRA_SESSION_ID) != "new-session" &&
            remainingAttempts-- > 0
        ) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                updated = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<CompanionActivity>()
                    .firstOrNull()
            }
            if (updated?.intent?.getStringExtra(CompanionActivity.EXTRA_SESSION_ID) != "new-session") {
                SystemClock.sleep(100)
            }
        }

        assertSame(original, updated)
        assertEquals(target.displayId, updated?.display?.displayId)
        assertEquals("new-session", updated?.intent?.getStringExtra(CompanionActivity.EXTRA_SESSION_ID))
        assertEquals(false, updated?.intent?.getBooleanExtra(CompanionActivity.EXTRA_PLACEMENT_TEST, true))
    }

    @Test
    fun homeCanBeExplicitlyRoutedToSelectedVirtualUpper() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val target = secondaryDisplay(context)
        assumeTrue("A simulated secondary display is required for this test.", target != null)
        requireNotNull(target)
        val coordinator = AndroidDisplayCoordinator(context, DisplayAssignmentStore(context))

        val evidence = coordinator.showHomeOnUpper(target.displayId)

        assertEquals(true, evidence.requestAccepted)
        assertEquals(target.displayId, evidence.requestedDisplayId)
        assertEquals(target.displayId, waitForResumedDisplay(instrumentation, HomeActivity::class.java))
    }

    private fun secondaryDisplay(context: Context) =
        (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .displays
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }

    private fun <T : android.app.Activity> waitForResumedDisplay(
        instrumentation: android.app.Instrumentation,
        activityClass: Class<T>,
    ): Int? {
        var observedDisplayId: Int? = null
        var remainingAttempts = 100
        while (observedDisplayId == null && remainingAttempts-- > 0) {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                observedDisplayId = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .firstOrNull(activityClass::isInstance)
                    ?.display
                    ?.displayId
            }
            if (observedDisplayId == null) SystemClock.sleep(100)
        }
        return observedDisplayId
    }
}
