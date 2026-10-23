package dev.thorhub.app.feature.companion

import android.os.BatteryManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.thorhub.app.R
import dev.thorhub.app.data.EndSessionSummary
import dev.thorhub.app.platform.input.ControllerMapping
import dev.thorhub.app.platform.input.HubControlAction
import kotlinx.coroutines.delay
import java.time.Duration

private val LocalCompanionControllerMapping = staticCompositionLocalOf { ControllerMapping.DEFAULT }

@Composable
fun CompanionDeck(
    requestedDisplayId: Int?,
    observedDisplayId: Int?,
    placementTest: Boolean,
    state: CompanionUiState,
    onSelectTab: (CompanionTab) -> Unit,
    onSaveNote: (String, Boolean, Boolean) -> Unit,
    onSetNotePinned: (String, Boolean) -> Unit,
    onEndSession: (String) -> Unit,
    onReturnHome: () -> Unit,
) {
    CompositionLocalProvider(LocalCompanionControllerMapping provides state.controllerMapping) {
        CompanionDeckContent(
            requestedDisplayId = requestedDisplayId,
            observedDisplayId = observedDisplayId,
            placementTest = placementTest,
            state = state,
            onSelectTab = onSelectTab,
            onSaveNote = onSaveNote,
            onSetNotePinned = onSetNotePinned,
            onEndSession = onEndSession,
            onReturnHome = onReturnHome,
        )
    }
}

@Composable
private fun CompanionDeckContent(
    requestedDisplayId: Int?,
    observedDisplayId: Int?,
    placementTest: Boolean,
    state: CompanionUiState,
    onSelectTab: (CompanionTab) -> Unit,
    onSaveNote: (String, Boolean, Boolean) -> Unit,
    onSetNotePinned: (String, Boolean) -> Unit,
    onEndSession: (String) -> Unit,
    onReturnHome: () -> Unit,
) {
    var blackedOut by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val firstTabFocus = remember { FocusRequester() }
    val controllerMapping = LocalCompanionControllerMapping.current

    LaunchedEffect(lastInteraction) {
        delay(120_000)
        if (SystemClock.elapsedRealtime() - lastInteraction >= 120_000) blackedOut = true
    }
    LaunchedEffect(blackedOut) {
        if (!blackedOut) runCatching { firstTabFocus.requestFocus() }
    }

    if (blackedOut) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = {
                        blackedOut = false
                        lastInteraction = SystemClock.elapsedRealtime()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.touch_to_restore), color = Color(0xFF202020), fontSize = 11.sp)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (placementTest) {
                    Brush.linearGradient(listOf(Color(0xFF001B35), Color(0xFF063C55), Color.Black))
                } else {
                    Brush.verticalGradient(listOf(Color(0xFF0C151D), Color.Black))
                },
            )
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = { lastInteraction = SystemClock.elapsedRealtime() },
            )
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_UP) return@onPreviewKeyEvent false
                val tabs = CompanionTab.entries
                val currentIndex = tabs.indexOf(state.selectedTab).coerceAtLeast(0)
                when (native.keyCode) {
                    controllerMapping.keyCode(HubControlAction.BACK), KeyEvent.KEYCODE_BACK -> {
                        if (state.selectedTab == CompanionTab.HOME) onReturnHome() else onSelectTab(CompanionTab.HOME)
                        true
                    }
                    controllerMapping.keyCode(HubControlAction.PREVIOUS_TAB) -> {
                        onSelectTab(tabs[(currentIndex - 1 + tabs.size) % tabs.size])
                        true
                    }
                    controllerMapping.keyCode(HubControlAction.NEXT_TAB) -> {
                        onSelectTab(tabs[(currentIndex + 1) % tabs.size])
                        true
                    }
                    controllerMapping.keyCode(HubControlAction.QUICK_MENU) -> {
                        onSelectTab(CompanionTab.SYSTEM)
                        true
                    }
                    controllerMapping.keyCode(HubControlAction.SEARCH) -> {
                        onSelectTab(CompanionTab.NOTES)
                        true
                    }
                    controllerMapping.keyCode(HubControlAction.DETAILS) -> {
                        onSelectTab(CompanionTab.GAME)
                        true
                    }
                    controllerMapping.keyCode(HubControlAction.CONTEXT) -> {
                        onSelectTab(CompanionTab.TOOLS)
                        true
                    }
                    else -> false
                }
            }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(if (placementTest) "LOWER CANDIDATE PATTERN" else stringResource(R.string.companion), color = Color(0xFF7FE7D5), fontWeight = FontWeight.Bold)
                Text(
                    "Requested ${requestedDisplayId ?: "UNKNOWN"} · Observed ${observedDisplayId ?: "UNKNOWN"}",
                    color = Color(0xFF9AA8B7),
                    fontSize = 11.sp,
                )
            }
            Text(stringResource(R.string.physical_panel_unknown), color = Color(0xFFFFC56D), fontSize = 10.sp)
        }

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xCC080D12)),
            shape = RoundedCornerShape(17.dp),
        ) {
            if (placementTest) {
                PlacementPattern(requestedDisplayId, observedDisplayId)
            } else if (state.endCompleted) {
                EndedPanel(state.endSummary, onReturnHome)
            } else {
                when (state.selectedTab) {
                    CompanionTab.HOME -> CompanionHome(state, onBlackout = { blackedOut = true }, onEndSession = onEndSession)
                    CompanionTab.NOTES -> NotesPanel(state, onSaveNote, onSetNotePinned)
                    CompanionTab.GAME -> GamePanel(state)
                    CompanionTab.TOOLS -> CapabilityPanel(
                        "Tools",
                        "Screenshot capture, task movement, and input bridges remain disabled until explicit capabilities are verified.",
                        "UNSUPPORTED",
                    )
                    CompanionTab.SYSTEM -> SystemPanel(onBlackout = { blackedOut = true })
                }
            }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CompanionTab.entries.forEach { item ->
                CompanionButton(
                    onClick = {
                        onSelectTab(item)
                        lastInteraction = SystemClock.elapsedRealtime()
                    },
                    modifier = if (item == CompanionTab.HOME) Modifier.focusRequester(firstTabFocus) else Modifier,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.selectedTab == item) Color(0xFF7FE7D5) else Color(0xFF15212C),
                        contentColor = if (state.selectedTab == item) Color.Black else Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(companionTabLabel(item), fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun companionTabLabel(tab: CompanionTab): String = when (tab) {
    CompanionTab.HOME -> stringResource(R.string.home)
    CompanionTab.NOTES -> stringResource(R.string.notes)
    CompanionTab.GAME -> stringResource(R.string.game)
    CompanionTab.TOOLS -> stringResource(R.string.tools)
    CompanionTab.SYSTEM -> stringResource(R.string.system)
}

@Composable
private fun PlacementPattern(requested: Int?, observed: Int?) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF003A5C), Color(0xFF008A96), Color(0xFF00131B))),
        ),
    ) {
        Text("LOWER?", color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Black, modifier = Modifier.align(Alignment.Center))
        Text("requested=$requested observed=$observed", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(18.dp))
    }
}

@Composable
private fun CompanionHome(
    state: CompanionUiState,
    onBlackout: () -> Unit,
    onEndSession: (String) -> Unit,
) {
    var closingNote by rememberSaveable { mutableStateOf("") }
    val session = state.activeSession
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(state.game?.title ?: "No active game", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(
                    session?.let { "Session ${it.state.name} · ${it.timing.accumulatedActive.toMinutes()} min persisted" } ?: "Session time —",
                    color = Color(0xFF9AA8B7),
                )
            }
            Text(
                "Save: ${state.saveContinuity.profile?.state ?: "UNCONFIGURED"}",
                color = Color(0xFF75A8FF),
                fontSize = 10.sp,
            )
        }
        OutlinedTextField(
            value = closingNote,
            onValueChange = { closingNote = it },
            label = { Text(stringResource(R.string.optional_closing_note)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
        )
        CompanionButton(
            onClick = onBlackout,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.oled_blackout))
        }
        CompanionButton(
            onClick = { onEndSession(closingNote) },
            modifier = Modifier.fillMaxWidth(),
            enabled = session != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF5A2730),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF3A2328),
                disabledContentColor = Color.White.copy(alpha = 0.52f),
            ),
        ) {
            Text(stringResource(R.string.end_local_session))
        }
        Text("External process closure is reported separately and defaults to NOT_VERIFIED.", color = Color(0xFFFFC56D), fontSize = 10.sp)
        if ("PINNED_NOTE" in state.homeCards) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111A23)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.pinned_note), color = Color(0xFF7FE7D5), fontWeight = FontWeight.SemiBold)
                    Text(state.pinnedNote?.body ?: stringResource(R.string.no_pinned_note), color = Color(0xFF9AA8B7), modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}

@Composable
private fun NotesPanel(
    state: CompanionUiState,
    onSaveNote: (String, Boolean, Boolean) -> Unit,
    onSetNotePinned: (String, Boolean) -> Unit,
) {
    var body by rememberSaveable { mutableStateOf("") }
    var checklist by rememberSaveable { mutableStateOf(false) }
    var pinned by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.notes), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(body, { body = it }, label = { Text(stringResource(R.string.quick_note)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checklist, { checklist = it })
            Text(stringResource(R.string.checklist), color = Color.White)
            Checkbox(pinned, { pinned = it })
            Text("Pin", color = Color.White)
            CompanionButton(
                onClick = { if (body.isNotBlank()) { onSaveNote(body, checklist, pinned); body = "" } },
                enabled = state.activeSession != null,
                modifier = Modifier.padding(start = 8.dp),
            ) { Text(stringResource(R.string.save_note)) }
        }
        state.game?.notes?.take(4)?.forEach { note ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF111A23)), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(note.body, color = Color.White, modifier = Modifier.weight(1f), maxLines = 2)
                    CompanionButton(onClick = { onSetNotePinned(note.id, !note.pinned) }) {
                        Text(if (note.pinned) "Unpin" else "Pin", fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GamePanel(state: CompanionUiState) {
    val game = state.game
    Column(Modifier.fillMaxSize().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(game?.title ?: stringResource(R.string.game), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(game?.description ?: "No active game metadata.", color = Color(0xFF9AA8B7))
        Text("Installation: ${game?.installations?.firstOrNull()?.availability?.name ?: "UNKNOWN"}", color = Color(0xFF75A8FF))
        Text("PlayMethod: ${game?.playMethods?.firstOrNull()?.compatibility?.name ?: "UNKNOWN"}", color = Color(0xFF75A8FF))
        Text("Cloud saves: ${if (game?.versions?.firstOrNull()?.platform == "GAME_NATIVE") "MANAGED EXTERNALLY" else "UNCONFIGURED"}", color = Color(0xFFFFC56D))
    }
}

@Composable
private fun EndedPanel(summary: EndSessionSummary?, onReturnHome: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.local_session_saved), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            summary?.let { "Duration: ${Duration.ofMillis(it.durationMillis).toMinutes()} min" }
                ?: "Local session ended.",
            color = Color(0xFF9AA8B7),
        )
        Text("Note: ${summary?.noteStatus ?: "UNKNOWN"}", color = Color(0xFF75A8FF))
        Text("Save snapshot: ${summary?.snapshotStatus ?: "UNKNOWN"}", color = Color(0xFF75A8FF))
        summary?.snapshotDetail?.let { Text(it, color = Color(0xFF9AA8B7), fontSize = 10.sp) }
        Text("Sync: ${summary?.syncStatus ?: "UNKNOWN"}", color = Color(0xFF75A8FF))
        Text(
            "External exit request: ${summary?.externalRequestStatus ?: "UNKNOWN"}",
            color = Color(0xFFFFC56D),
        )
        summary?.externalRequestDetail?.let { Text(it, color = Color(0xFF9AA8B7), fontSize = 10.sp) }
        Text(
            "External process: ${summary?.externalClosureStatus ?: "NOT_VERIFIED"}",
            color = Color(0xFFFFC56D),
        )
        Text("Home route: ${summary?.homeRouteStatus ?: "UNKNOWN"}", color = Color(0xFF9AA8B7), fontSize = 10.sp)
        CompanionButton(onClick = onReturnHome, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.return_home))
        }
    }
}

@Composable
private fun CompanionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val controllerMapping = LocalCompanionControllerMapping.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = "Controller A activates this button." }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode != controllerMapping.keyCode(HubControlAction.ACTIVATE)) {
                    return@onPreviewKeyEvent false
                }
                when (native.action) {
                    KeyEvent.ACTION_DOWN -> true
                    KeyEvent.ACTION_UP -> {
                        if (enabled) onClick()
                        true
                    }
                    else -> false
                }
            }
            .focusable(enabled),
        colors = colors ?: ButtonDefaults.buttonColors(
            containerColor = if (focused) Color(0xFF7FE7D5) else Color(0xFF15212C),
            contentColor = if (focused) Color.Black else Color.White,
        ),
        border = if (focused) BorderStroke(2.dp, Color.White) else null,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
private fun CapabilityPanel(title: String, body: String, status: String) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(status, color = Color(0xFFFFC56D), fontSize = 11.sp)
        }
        Text(body, color = Color(0xFF9AA8B7))
    }
}

@Composable
private fun SystemPanel(onBlackout: () -> Unit) {
    val context = LocalContext.current
    val batteryManager = remember { context.getSystemService(BatteryManager::class.java) }
    var battery by remember { mutableIntStateOf(batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)) }
    LaunchedEffect(Unit) {
        while (true) {
            battery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(60_000)
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("System", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("Battery: ${battery.takeIf { it in 0..100 }?.let { "$it%" } ?: "Unavailable"}", color = Color(0xFF9AA8B7))
        Text("Temperature / FPS: Unavailable", color = Color(0xFF9AA8B7))
        Text("Lid capability: UNKNOWN", color = Color(0xFFFFC56D))
        Text("Lower hardware power control: UNKNOWN", color = Color(0xFFFFC56D))
        CompanionButton(onClick = onBlackout) { Text("Enter OLED Blackout") }
    }
}
