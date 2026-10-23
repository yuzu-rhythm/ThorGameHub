package dev.thorhub.app.feature.shell

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.thorhub.app.R
import dev.thorhub.app.data.LibraryGameSummary
import dev.thorhub.app.data.ResumeCardSummary
import dev.thorhub.app.feature.companion.CompanionDeck
import dev.thorhub.app.feature.companion.CompanionTab
import dev.thorhub.app.feature.companion.CompanionUiState
import dev.thorhub.app.feature.diagnostics.HubUiState
import dev.thorhub.app.platform.home.HomeRoleState
import dev.thorhub.app.platform.home.HomeRoleStatus
import dev.thorhub.app.platform.input.ControllerMapping
import dev.thorhub.app.platform.input.HubControlAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HubShellTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun singleDisplayHomeIsUsableAndTouchOpensLibrary() {
        var screen by mutableStateOf(HubScreen.HOME)
        compose.setContent {
            MaterialTheme {
                ThorHubShell(
                    state = state(),
                    screen = screen,
                    reducedMotion = true,
                    actions = actions(onScreenChange = { screen = it }),
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.home_brand)).assertExists()
        compose.onNodeWithContentDescription(
            "${context.getString(R.string.library)}. Controller A activates.",
        ).assertExists().performClick()
        compose.onNode(hasText(context.getString(R.string.library)).and(isHeading())).assertExists()
        compose.onNodeWithText(context.getString(R.string.search_library)).assertExists()
        assertEquals(HubScreen.LIBRARY, screen)
    }

    @Test
    fun controllerAActivatesFocusedButtonAndBReturnsHome() {
        var screen by mutableStateOf(HubScreen.HOME)
        compose.setContent {
            MaterialTheme {
                ThorHubShell(
                    state = state(),
                    screen = screen,
                    reducedMotion = true,
                    actions = actions(onScreenChange = { screen = it }),
                )
            }
        }

        val library = compose.onNodeWithContentDescription(
            "${context.getString(R.string.library)}. Controller A activates.",
        )
        library.performSemanticsAction(SemanticsActions.RequestFocus)
        library.assertIsFocused()
        compose.onRoot().performKeyInput {
            keyDown(Key.ButtonA)
            keyUp(Key.ButtonA)
        }
        compose.waitForIdle()
        assertEquals(HubScreen.LIBRARY, screen)

        compose.onNodeWithText(context.getString(R.string.back)).performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithText(context.getString(R.string.back)).performKeyInput {
            keyDown(Key.ButtonB)
            keyUp(Key.ButtonB)
        }
        compose.waitForIdle()
        assertEquals(HubScreen.HOME, screen)
    }

    @Test
    fun dpadCreatesExactlyOneVisibleFocusTarget() {
        compose.setContent {
            MaterialTheme {
                ThorHubShell(
                    state = state(),
                    screen = HubScreen.HOME,
                    reducedMotion = true,
                    actions = actions(),
                )
            }
        }

        compose.onRoot().performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        compose.waitForIdle()

        compose.onAllNodes(isFocused()).assertCountEquals(1)
    }

    @Test
    fun controllerLongPressRunsLongActionWithoutClick() {
        var clicks = 0
        var longPresses = 0
        compose.setContent {
            MaterialTheme {
                ControllerButton(
                    label = "Long action",
                    onLongA = { longPresses += 1 },
                    onClick = { clicks += 1 },
                )
            }
        }

        val button = compose.onNodeWithContentDescription("Long action. Controller A activates.")
        button.performSemanticsAction(SemanticsActions.RequestFocus)
        button.assertIsFocused()

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        val down = KeyEvent(
            downTime,
            downTime,
            KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_BUTTON_A,
            0,
            0,
            0,
            0,
            0,
            InputDevice.SOURCE_GAMEPAD,
        )
        assertTrue(instrumentation.uiAutomation.injectInputEvent(down, true))
        // Do not timestamp a repeat in the future and inject it immediately:
        // Android 15 correctly rejects that sequence while newer releases may
        // accept it. Advance real uptime so both APIs receive a valid stream.
        SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong() + 100L)
        val repeated = KeyEvent(
            downTime,
            SystemClock.uptimeMillis(),
            KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_BUTTON_A,
            1,
            0,
            0,
            0,
            KeyEvent.FLAG_LONG_PRESS,
            InputDevice.SOURCE_GAMEPAD,
        )
        assertTrue(instrumentation.uiAutomation.injectInputEvent(repeated, true))
        val up = KeyEvent(
            downTime,
            SystemClock.uptimeMillis(),
            KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_BUTTON_A,
            0,
            0,
            0,
            0,
            0,
            InputDevice.SOURCE_GAMEPAD,
        )
        assertTrue(instrumentation.uiAutomation.injectInputEvent(up, true))
        compose.waitForIdle()

        assertEquals(1, longPresses)
        assertEquals(0, clicks)
    }

    @Test
    fun resumeCardOpensItsGame() {
        var screen by mutableStateOf(HubScreen.HOME)
        var selectedGameId: String? = null
        val resume = ResumeCardSummary(
            gameId = "resume-game",
            gameTitle = "Resume Candidate",
            sessionId = "resume-session",
            lastPlayedAt = System.currentTimeMillis() - 8 * 60 * 60 * 1_000L,
            priorDurationMillis = 42 * 60 * 1_000L,
            pinnedNote = "Check the north gate",
            artworkLocator = null,
            recovered = false,
        )
        compose.setContent {
            MaterialTheme {
                ThorHubShell(
                    state = state().copy(resumeCard = resume),
                    screen = screen,
                    reducedMotion = true,
                    actions = actions(
                        onScreenChange = { screen = it },
                        onSelectGame = { selectedGameId = it },
                    ),
                )
            }
        }

        compose.onNodeWithText("Resume Candidate").assertExists()
        compose.onNodeWithText("Pinned: Check the north gate").assertExists()
        compose.onNodeWithContentDescription(
            "${context.getString(R.string.resume_action)}. Controller A activates.",
        ).performScrollTo().performClick()

        assertEquals("resume-game", selectedGameId)
        assertEquals(HubScreen.DETAIL, screen)
    }

    @Test
    fun setupExposesSafetyAndControllerSections() {
        compose.setContent {
            MaterialTheme {
                ThorHubShell(
                    state = state(),
                    screen = HubScreen.SETUP,
                    reducedMotion = true,
                    actions = actions(),
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.setup_center)).assertExists()
        val setupList = compose.onNodeWithTag("setup-list")
        setupList.performScrollToNode(hasText(context.getString(R.string.controller_mapping)))
        compose.onNodeWithText(context.getString(R.string.controller_mapping)).assertExists()
        setupList.performScrollToNode(hasText(context.getString(R.string.saves)))
        compose.onNodeWithText(context.getString(R.string.saves)).assertExists()
    }

    @Test
    fun companionBlackoutRestoresByTouch() {
        compose.setContent {
            MaterialTheme {
                CompanionDeck(
                    requestedDisplayId = 2,
                    observedDisplayId = 2,
                    placementTest = false,
                    state = CompanionUiState(),
                    onSelectTab = {},
                    onSaveNote = { _, _, _ -> },
                    onSetNotePinned = { _, _ -> },
                    onEndSession = {},
                    onReturnHome = {},
                )
            }
        }

        compose.onNodeWithText(context.getString(R.string.oled_blackout))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.onNodeWithText(context.getString(R.string.touch_to_restore)).assertExists().performClick()
        compose.onNodeWithText(context.getString(R.string.companion)).assertExists()
    }

    @Test
    fun companionEndedPanelKeepsReturnHomeReachableAtCompactMetrics() {
        var returnedHome = false
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(width = 400.dp, height = 300.dp)) {
                    CompanionDeck(
                        requestedDisplayId = 2,
                        observedDisplayId = 2,
                        placementTest = false,
                        state = CompanionUiState(endCompleted = true),
                        onSelectTab = {},
                        onSaveNote = { _, _, _ -> },
                        onSetNotePinned = { _, _ -> },
                        onEndSession = {},
                        onReturnHome = { returnedHome = true },
                    )
                }
            }
        }

        compose.onNodeWithText(context.getString(R.string.return_home))
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertTrue(returnedHome) }
    }

    @Test
    fun placementPatternRendersAtTwoFiniteLowerScreenMetrics() {
        var compact by mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(width = if (compact) 320.dp else 400.dp, height = if (compact) 240.dp else 300.dp)) {
                    CompanionDeck(
                        requestedDisplayId = 2,
                        observedDisplayId = 2,
                        placementTest = true,
                        state = CompanionUiState(),
                        onSelectTab = {},
                        onSaveNote = { _, _, _ -> },
                        onSetNotePinned = { _, _ -> },
                        onEndSession = {},
                        onReturnHome = {},
                    )
                }
            }
        }

        compose.onNodeWithText("LOWER?").assertExists()
        compose.runOnIdle { compact = false }
        compose.onNodeWithText("requested=2 observed=2").assertExists()
    }

    @Test
    fun companionUsesSharedCustomMappingForTabNavigation() {
        var selectedTab by mutableStateOf(CompanionTab.HOME)
        val customMapping = ControllerMapping(
            ControllerMapping.DEFAULT.bindings + (HubControlAction.NEXT_TAB to KeyEvent.KEYCODE_BUTTON_Z),
        )
        compose.setContent {
            MaterialTheme {
                CompanionDeck(
                    requestedDisplayId = 2,
                    observedDisplayId = 2,
                    placementTest = false,
                    state = CompanionUiState(
                        selectedTab = selectedTab,
                        controllerMapping = customMapping,
                    ),
                    onSelectTab = { selectedTab = it },
                    onSaveNote = { _, _, _ -> },
                    onSetNotePinned = { _, _ -> },
                    onEndSession = {},
                    onReturnHome = {},
                )
            }
        }
        compose.waitForIdle()

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val eventTime = SystemClock.uptimeMillis()
        assertTrue(
            instrumentation.uiAutomation.injectInputEvent(
                KeyEvent(
                    eventTime,
                    eventTime,
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_BUTTON_Z,
                    0,
                    0,
                    0,
                    0,
                    0,
                    InputDevice.SOURCE_GAMEPAD,
                ),
                true,
            ),
        )
        assertTrue(
            instrumentation.uiAutomation.injectInputEvent(
                KeyEvent(
                    eventTime,
                    SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_BUTTON_Z,
                    0,
                    0,
                    0,
                    0,
                    0,
                    InputDevice.SOURCE_GAMEPAD,
                ),
                true,
            ),
        )

        compose.waitUntil(3_000) { selectedTab == CompanionTab.NOTES }
        assertEquals(CompanionTab.NOTES, selectedTab)
    }

    private fun state() = HubUiState(
        loading = false,
        homeRole = HomeRoleState(HomeRoleStatus.AVAILABLE_NOT_HELD, "System confirmation required."),
        library = listOf(
            LibraryGameSummary(
                id = "game-ui-test",
                title = "Test Horizon",
                sortTitle = "Test Horizon",
                pinned = true,
                favorite = false,
                hidden = false,
                availableInstallations = 1,
            ),
        ),
    )

    private fun actions(
        onScreenChange: (HubScreen) -> Unit = {},
        onSelectGame: (String?) -> Unit = {},
    ) = HubActions(
        onScreenChange = onScreenChange,
        onSelectGame = onSelectGame,
        onSearchQueryChange = {},
        onNoteSearchQueryChange = {},
        onLaunchSelected = {},
        onTogglePinned = {},
        onToggleFavorite = {},
        onHideSelected = {},
        onUnhideGame = {},
        onUpdateMetadata = { _, _, _ -> },
        onPickArtwork = {},
        onSaveNote = { _, _, _ -> },
        onUpdateNote = { _, _ -> },
        onSetNotePinned = { _, _ -> },
        onSetNoteCompleted = { _, _ -> },
        onEndSession = {},
        onImportAndroidApp = {},
        onAddGameNative = { _, _, _ -> },
        onConfigureRetroArch = { _, _, _ -> },
        onAddConfigurableExternal = { _, _, _, _, _ -> },
        onPickLibraryRoot = {},
        onPickSaveRoot = {},
        onSnapshotSaves = {},
        onRestoreLatestSnapshot = {},
        onEndRecoveredSession = {},
        onCycleResumeThreshold = {},
        onRefresh = {},
        onLaunchCompanion = {},
        onSwapDisplays = {},
        onConfirmPlacement = {},
        onRequestHomeRole = {},
        onOpenHomeSettings = {},
        onLaunchApp = {},
        onExportDiagnostics = {},
        onClearMessage = {},
        onCycleControl = {},
        onResetControls = {},
    )
}
