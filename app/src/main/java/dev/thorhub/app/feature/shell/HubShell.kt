package dev.thorhub.app.feature.shell

import android.view.KeyEvent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import dev.thorhub.app.R
import dev.thorhub.app.data.GameDetailSummary
import dev.thorhub.app.data.LibraryGameSummary
import dev.thorhub.app.data.NoteSummary
import dev.thorhub.app.data.ResumeCardSummary
import dev.thorhub.app.feature.diagnostics.HubUiState
import dev.thorhub.app.platform.diagnostics.CapabilityUiState
import dev.thorhub.app.platform.diagnostics.InstalledLaunchableApp
import dev.thorhub.app.platform.display.DisplayDescriptor
import dev.thorhub.app.platform.display.TopologyMode
import dev.thorhub.app.platform.home.HomeRoleStatus
import dev.thorhub.app.platform.input.ControllerMapping
import dev.thorhub.app.platform.input.HubControlAction
import dev.thorhub.app.platform.launcher.GameNativeSource
import dev.thorhub.app.platform.launcher.LauncherProfiles
import coil3.compose.AsyncImage
import java.time.Duration
import java.time.Instant

enum class HubScreen {
    HOME,
    LIBRARY,
    DETAIL,
    NOTES,
    END_SESSION,
    SETUP,
    DIAGNOSTICS,
}

data class HubActions(
    val onScreenChange: (HubScreen) -> Unit,
    val onSelectGame: (String?) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onNoteSearchQueryChange: (String) -> Unit,
    val onLaunchSelected: () -> Unit,
    val onTogglePinned: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onHideSelected: () -> Unit,
    val onUnhideGame: (String) -> Unit,
    val onUpdateMetadata: (String, String, Int?) -> Unit,
    val onPickArtwork: () -> Unit,
    val onSaveNote: (String, Boolean, Boolean) -> Unit,
    val onUpdateNote: (String, String) -> Unit,
    val onSetNotePinned: (String, Boolean) -> Unit,
    val onSetNoteCompleted: (String, Boolean) -> Unit,
    val onEndSession: (String) -> Unit,
    val onImportAndroidApp: (InstalledLaunchableApp) -> Unit,
    val onAddGameNative: (String, GameNativeSource, Int) -> Unit,
    val onConfigureRetroArch: (String, String, String) -> Unit,
    val onAddConfigurableExternal: (String, InstalledLaunchableApp, String?, String?, String) -> Unit,
    val onPickLibraryRoot: () -> Unit,
    val onPickSaveRoot: () -> Unit,
    val onSnapshotSaves: () -> Unit,
    val onRestoreLatestSnapshot: () -> Unit,
    val onEndRecoveredSession: (String) -> Unit,
    val onCycleResumeThreshold: () -> Unit,
    val onRefresh: () -> Unit,
    val onLaunchCompanion: (Int?) -> Unit,
    val onSwapDisplays: () -> Unit,
    val onConfirmPlacement: () -> Unit,
    val onRequestHomeRole: () -> Unit,
    val onOpenHomeSettings: () -> Unit,
    val onLaunchApp: (InstalledLaunchableApp) -> Unit,
    val onExportDiagnostics: () -> Unit,
    val onClearMessage: () -> Unit,
    val onCycleControl: (HubControlAction) -> Unit,
    val onResetControls: () -> Unit,
    val onLibraryFiltersChanged: (String, String, String) -> Unit = { _, _, _ -> },
)

private val panel = Color(0xFF080D12)
private val panelRaised = Color(0xFF101820)
private val textMuted = Color(0xFF9AA8B7)
private val mint = Color(0xFF7FE7D5)
private val LocalControllerMapping = staticCompositionLocalOf { ControllerMapping.DEFAULT }

@Composable
fun ThorHubShell(
    state: HubUiState,
    screen: HubScreen,
    reducedMotion: Boolean,
    actions: HubActions,
    pagedLibrary: LazyPagingItems<LibraryGameSummary>? = null,
) {
    CompositionLocalProvider(LocalControllerMapping provides state.controllerMapping) {
        Surface(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_UP) return@onPreviewKeyEvent false
                when (native.keyCode) {
                    state.controllerMapping.keyCode(HubControlAction.BACK), KeyEvent.KEYCODE_BACK -> {
                        actions.onScreenChange(parentOf(screen))
                        true
                    }
                    state.controllerMapping.keyCode(HubControlAction.QUICK_MENU) -> {
                        actions.onScreenChange(HubScreen.SETUP)
                        true
                    }
                    state.controllerMapping.keyCode(HubControlAction.SEARCH) -> {
                        actions.onScreenChange(HubScreen.LIBRARY)
                        true
                    }
                    state.controllerMapping.keyCode(HubControlAction.DETAILS) -> {
                        val gameId = state.selectedGameId ?: state.continueGames.firstOrNull()?.id
                        if (gameId != null) {
                            actions.onSelectGame(gameId)
                            actions.onScreenChange(HubScreen.DETAIL)
                            true
                        } else {
                            false
                        }
                    }
                    state.controllerMapping.keyCode(HubControlAction.CONTEXT) -> {
                        if (screen == HubScreen.DETAIL) {
                            actions.onToggleFavorite()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
        color = Color.Black,
    ) {
        Box(Modifier.fillMaxSize()) {
            val content: @Composable (HubScreen) -> Unit = { target ->
                when (target) {
                    HubScreen.HOME -> HomeScreen(state, actions)
                    HubScreen.LIBRARY -> LibraryScreen(state, actions, pagedLibrary)
                    HubScreen.DETAIL -> DetailScreen(state, actions)
                    HubScreen.NOTES -> NotesScreen(state, actions)
                    HubScreen.END_SESSION -> EndSessionScreen(state, actions)
                    HubScreen.SETUP -> SetupScreen(state, actions)
                    HubScreen.DIAGNOSTICS -> DiagnosticsScreen(state, actions)
                }
            }
            if (reducedMotion) {
                content(screen)
            } else {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(210)) togetherWith fadeOut(tween(150)) },
                    label = "hub-screen",
                ) { content(it) }
            }

            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopEnd).padding(22.dp).size(22.dp),
                    color = mint,
                    strokeWidth = 2.dp,
                )
            }
            state.userMessage?.let { message ->
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(0.88f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17242E)),
                    border = BorderStroke(1.dp, mint.copy(alpha = 0.5f)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(message, color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        ControllerButton(stringResource(R.string.dismiss), onClick = actions.onClearMessage)
                    }
                }
            }
        }
        }
    }
}

private fun parentOf(screen: HubScreen): HubScreen = when (screen) {
    HubScreen.HOME -> HubScreen.HOME
    HubScreen.DETAIL -> HubScreen.LIBRARY
    HubScreen.NOTES, HubScreen.END_SESSION -> HubScreen.DETAIL
    HubScreen.LIBRARY, HubScreen.SETUP, HubScreen.DIAGNOSTICS -> HubScreen.HOME
}

@Composable
private fun HomeScreen(state: HubUiState, actions: HubActions) {
    val heroGame = state.continueGames.firstOrNull() ?: state.library.firstOrNull()
    val accent = generatedAccent(heroGame?.id ?: "thor")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        val hero: @Composable (Modifier) -> Unit = { modifier ->
            Box(
                modifier.background(
                    Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.72f), accent.copy(alpha = 0.18f), Color.Black),
                        radius = 1100f,
                    ),
                ),
            ) {
                Column(Modifier.align(Alignment.BottomStart).padding(32.dp)) {
                    Text(stringResource(R.string.home_brand), color = readableOn(accent), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        heroGame?.title ?: stringResource(R.string.your_local_library),
                        color = Color.White,
                        fontSize = if (wide) 42.sp else 31.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    Text(
                        if (heroGame?.lastSessionAt != null) stringResource(R.string.ready_to_continue) else stringResource(R.string.local_data_summary),
                        color = textMuted,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        if (heroGame != null) {
                            ControllerButton(stringResource(R.string.continue_action), onLongA = {
                                actions.onSelectGame(heroGame.id)
                                actions.onScreenChange(HubScreen.DETAIL)
                            }) {
                                actions.onSelectGame(heroGame.id)
                                actions.onScreenChange(HubScreen.DETAIL)
                            }
                        }
                        StatusPill(topologyLabel(state), topologyCapability(state))
                    }
                }
            }
        }

        val controls: @Composable (Modifier) -> Unit = { modifier ->
            Column(
                modifier
                    .background(Color.Black)
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column {
                    Text(stringResource(R.string.continue_action), color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pinned_then_recent), color = textMuted, fontSize = 12.sp)
                    Text(
                        stringResource(R.string.controller_select_details),
                        color = mint,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    ControllerButton(stringResource(R.string.library)) { actions.onScreenChange(HubScreen.LIBRARY) }
                    ControllerButton(stringResource(R.string.notes)) { actions.onScreenChange(HubScreen.NOTES) }
                    ControllerButton(stringResource(R.string.setup)) { actions.onScreenChange(HubScreen.SETUP) }
                    ControllerButton(stringResource(R.string.diagnostics)) { actions.onScreenChange(HubScreen.DIAGNOSTICS) }
                }
                ContinueCarousel(state.continueGames.ifEmpty { state.library.take(6) }, actions)
                state.resumeCard?.let { resume ->
                    ResumeCard(
                        summary = resume,
                        onResume = {
                            actions.onSelectGame(resume.gameId)
                            actions.onScreenChange(HubScreen.DETAIL)
                        },
                        onEndRecovered = if (resume.recovered) {
                            { actions.onEndRecoveredSession(resume.sessionId) }
                        } else {
                            null
                        },
                    )
                }
                state.recoveredSessions.takeIf { it.isNotEmpty() }?.let { recovered ->
                    InfoCard(
                        "Session recovery needs review",
                        "${recovered.size} unresolved local session(s). Time across reboot was not inferred.",
                        CapabilityUiState.NEEDS_ACTION,
                    )
                }
                state.activeSessions.firstOrNull()?.let { active ->
                    Row(
                        Modifier.fillMaxWidth().border(1.dp, Color(0xFF274858), RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.active_local_session), color = mint, modifier = Modifier.weight(1f))
                        ControllerButton(stringResource(R.string.end_session)) { actions.onScreenChange(HubScreen.END_SESSION) }
                    }
                }
                Text(stringResource(R.string.independent_notice), color = Color(0xFF647181), fontSize = 11.sp)
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                hero(Modifier.weight(1f).fillMaxHeight())
                controls(Modifier.weight(1.2f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                hero(Modifier.weight(0.82f).fillMaxWidth())
                controls(Modifier.weight(1.18f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ResumeCard(
    summary: ResumeCardSummary,
    onResume: () -> Unit,
    onEndRecovered: (() -> Unit)?,
) {
    val elapsed = (System.currentTimeMillis() - summary.lastPlayedAt).coerceAtLeast(0L)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101B24)),
        border = BorderStroke(1.dp, Color(0xFF75A8FF).copy(alpha = 0.7f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            summary.artworkLocator?.takeIf { it.startsWith("content://") }?.let { locator ->
                AsyncImage(
                    model = locator,
                    contentDescription = "Resume artwork for ${summary.gameTitle}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(10.dp)),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (summary.recovered) "Recovered session" else "Resume", color = Color(0xFF75A8FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(summary.gameTitle, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "${formatDuration(elapsed)} since play · prior tracked ${formatDuration(summary.priorDurationMillis)}",
                    color = textMuted,
                    fontSize = 10.sp,
                )
                summary.pinnedNote?.let { Text("Pinned: $it", color = mint, fontSize = 11.sp, maxLines = 2) }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ControllerButton(stringResource(R.string.resume_action), onClick = onResume)
                    onEndRecovered?.let { ControllerButton("End recovered session", onClick = it) }
                }
            }
        }
    }
}

@Composable
private fun ContinueCarousel(games: List<LibraryGameSummary>, actions: HubActions) {
    if (games.isEmpty()) {
        EmptyState(stringResource(R.string.nothing_to_continue), stringResource(R.string.nothing_to_continue_body))
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(games, key = { it.id }) { game ->
            GameCard(
                game = game,
                compact = true,
                onClick = {
                    actions.onSelectGame(game.id)
                    actions.onScreenChange(HubScreen.DETAIL)
                },
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    state: HubUiState,
    actions: HubActions,
    pagedLibrary: LazyPagingItems<LibraryGameSummary>?,
) {
    var compactGrid by rememberSaveable { mutableStateOf(false) }
    val platformFilter = state.libraryPlatformFilter
    val lifecycleFilter = state.libraryLifecycleFilter
    val sortMode = state.librarySortMode
    val platforms = state.libraryPlatforms.ifEmpty {
        state.library.map(LibraryGameSummary::platform).distinct().sorted()
    }
    val fallbackGames = state.library
        .asSequence()
        .filter { platformFilter == "ALL" || it.platform == platformFilter }
        .filter { lifecycleFilter == "ALL" || it.lifecycle == lifecycleFilter }
        .let { games ->
            when (sortMode) {
                "PINNED" -> games.sortedWith(compareByDescending<LibraryGameSummary> { it.pinned }.thenBy { it.sortTitle })
                "RECENT" -> games.sortedByDescending { it.lastSessionAt ?: Long.MIN_VALUE }
                else -> games.sortedBy(LibraryGameSummary::sortTitle)
            }
        }
        .toList()
    Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        ScreenHeader(stringResource(R.string.library), stringResource(R.string.library_subtitle)) {
            actions.onScreenChange(HubScreen.HOME)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = actions.onSearchQueryChange,
                label = { Text(stringResource(R.string.search_library)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            ControllerButton(stringResource(R.string.setup)) { actions.onScreenChange(HubScreen.SETUP) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "${pagedLibrary?.itemCount ?: fallbackGames.size} / ${state.libraryTotalCount.takeIf { it > 0 } ?: state.library.size} games",
                color = textMuted,
                fontSize = 12.sp,
            )
            Text("Unknown Games Inbox · ${state.libraryUnknownCount}", color = Color(0xFFFFC56D), fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ControllerButton("All platforms", selected = platformFilter == "ALL") {
                actions.onLibraryFiltersChanged("ALL", lifecycleFilter, sortMode)
            }
            platforms.forEach { platform ->
                ControllerButton(platform, selected = platformFilter == platform) {
                    actions.onLibraryFiltersChanged(platform, lifecycleFilter, sortMode)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 7.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            listOf("ALL", "PLAYING", "BACKLOG", "COMPLETED").forEach { lifecycle ->
                ControllerButton(lifecycle, selected = lifecycleFilter == lifecycle) {
                    actions.onLibraryFiltersChanged(platformFilter, lifecycle, sortMode)
                }
            }
            listOf("TITLE", "PINNED", "RECENT").forEach { sort ->
                ControllerButton("Sort $sort", selected = sortMode == sort) {
                    actions.onLibraryFiltersChanged(platformFilter, lifecycleFilter, sort)
                }
            }
            ControllerButton(if (compactGrid) "Comfortable grid" else "Compact grid") { compactGrid = !compactGrid }
        }
        Spacer(Modifier.height(10.dp))
        val refreshState = pagedLibrary?.loadState?.refresh
        if (refreshState is LoadState.Error) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("The library page could not be loaded.", color = Color(0xFFFFC56D))
                ControllerButton("Retry") { pagedLibrary.retry() }
            }
        } else if (refreshState is LoadState.Loading && pagedLibrary.itemCount == 0) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = mint)
            }
        } else if ((pagedLibrary?.itemCount ?: fallbackGames.size) == 0) {
            EmptyState("No matching games", "Try another search or add a source in Setup.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (compactGrid) 116.dp else 154.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 30.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (pagedLibrary == null) {
                    items(fallbackGames, key = { it.id }) { game ->
                        GameCard(game, compact = false) {
                            actions.onSelectGame(game.id)
                            actions.onScreenChange(HubScreen.DETAIL)
                        }
                    }
                } else {
                    items(
                        count = pagedLibrary.itemCount,
                        key = { index -> pagedLibrary.peek(index)?.id ?: "library-placeholder-$index" },
                    ) { index ->
                        val game = pagedLibrary[index]
                        if (game == null) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(188.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(panelRaised),
                            )
                        } else {
                            GameCard(game, compact = false) {
                                actions.onSelectGame(game.id)
                                actions.onScreenChange(HubScreen.DETAIL)
                            }
                        }
                    }
                    if (pagedLibrary.loadState.append is LoadState.Loading) {
                        item {
                            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = mint, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    if (pagedLibrary.loadState.append is LoadState.Error) {
                        item {
                            ControllerButton("Retry next page") { pagedLibrary.retry() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameCard(game: LibraryGameSummary, compact: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val controllerMapping = LocalControllerMapping.current
    val accent = generatedAccent(game.id)
    val cardSizeModifier = if (compact) {
        Modifier.size(width = 194.dp, height = 112.dp)
    } else {
        Modifier.fillMaxWidth().height(188.dp)
    }
    Card(
        modifier = cardSizeModifier
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == controllerMapping.keyCode(HubControlAction.ACTIVATE) && native.action == KeyEvent.ACTION_UP) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "${game.title}. ${if (game.pinned) "Pinned." else ""} Open details."
            },
        colors = CardDefaults.cardColors(containerColor = panelRaised),
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Color.White else accent.copy(alpha = 0.65f)),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(if (compact) 0.52f else 1f)
                    .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.18f), Color.Black))),
            ) {
                Text(
                    if (game.pinned) "PINNED" else "LOCAL",
                    color = readableOn(accent),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(9.dp),
                )
            }
            Column(Modifier.padding(11.dp)) {
                Text(game.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (game.availableInstallations > 0) "Available locally" else "Source status unknown",
                    color = textMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailScreen(state: HubUiState, actions: HubActions) {
    val game = state.selectedGame
    if (game == null) {
        Column(Modifier.fillMaxSize().background(Color.Black).padding(20.dp)) {
            ScreenHeader(stringResource(R.string.game_detail), stringResource(R.string.select_game_first)) { actions.onScreenChange(HubScreen.LIBRARY) }
            EmptyState("No game selected", "Choose a library entry first.")
        }
        return
    }
    var tab by rememberSaveable(game.id) { mutableIntStateOf(0) }
    var editingMetadata by rememberSaveable(game.id) { mutableStateOf(false) }
    var metadataTitle by rememberSaveable(game.id) { mutableStateOf(game.title) }
    var metadataDescription by rememberSaveable(game.id) { mutableStateOf(game.description.orEmpty()) }
    var metadataYear by rememberSaveable(game.id) { mutableStateOf(game.releaseYear?.toString().orEmpty()) }
    LaunchedEffect(game.title, game.description, game.releaseYear, editingMetadata) {
        if (!editingMetadata) {
            metadataTitle = game.title
            metadataDescription = game.description.orEmpty()
            metadataYear = game.releaseYear?.toString().orEmpty()
        }
    }
    val tabs = listOf(stringResource(R.string.overview), stringResource(R.string.sessions), stringResource(R.string.notes))
    val accent = generatedAccent(game.id)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), Color.Black, Color.Black)))
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_UP) return@onPreviewKeyEvent false
                when (native.keyCode) {
                    state.controllerMapping.keyCode(HubControlAction.PREVIOUS_TAB) -> {
                        tab = (tab - 1).mod(tabs.size)
                        true
                    }
                    state.controllerMapping.keyCode(HubControlAction.NEXT_TAB) -> {
                        tab = (tab + 1).mod(tabs.size)
                        true
                    }
                    else -> false
                }
            },
    ) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 36.dp),
        ) {
            item {
                Spacer(Modifier.height(14.dp))
                ScreenHeader(stringResource(R.string.game_detail), "L/R changes tab · Long A shows play methods") {
                    actions.onScreenChange(HubScreen.LIBRARY)
                }
            }
            item { DetailHero(game, actions) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabs.forEachIndexed { index, title ->
                        ControllerButton(title, selected = tab == index) { tab = index }
                    }
                }
            }
            when (tab) {
                0 -> {
                    item {
                        InfoCard(
                            "Availability",
                            game.installations.joinToString { "${it.source}: ${it.availability.name}" }
                                .ifBlank { "No installation is linked. History remains intact." },
                            if (game.installations.any { it.availability.name == "AVAILABLE" }) {
                                CapabilityUiState.READY
                            } else {
                                CapabilityUiState.NEEDS_ACTION
                            },
                        )
                    }
                    item {
                        SectionTitle("Play methods")
                        if (game.playMethods.isEmpty()) {
                            EmptyState("No play method", "Open Setup to import an app or configure an external launcher.")
                        }
                    }
                    items(game.playMethods, key = { it.id }) { method ->
                        InfoCard(
                            method.title,
                            "Compatibility: ${method.compatibility.name}. Validation runs again before launch.",
                            when (method.compatibility.name) {
                                "READY" -> CapabilityUiState.READY
                                "NEEDS_ACTION" -> CapabilityUiState.NEEDS_ACTION
                                "UNSUPPORTED" -> CapabilityUiState.UNSUPPORTED
                                "ERROR" -> CapabilityUiState.ERROR
                                else -> CapabilityUiState.UNKNOWN
                            },
                        )
                    }
                    item {
                        SectionTitle(stringResource(R.string.metadata))
                        if (editingMetadata) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(metadataTitle, { metadataTitle = it }, label = { Text(stringResource(R.string.title_label)) }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(
                                    metadataDescription,
                                    { metadataDescription = it },
                                    label = { Text(stringResource(R.string.description_label)) },
                                    minLines = 3,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    metadataYear,
                                    { metadataYear = it.filter(Char::isDigit).take(4) },
                                    label = { Text(stringResource(R.string.release_year)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ControllerButton(stringResource(R.string.save_metadata)) {
                                        if (metadataTitle.isNotBlank()) {
                                            actions.onUpdateMetadata(
                                                metadataTitle,
                                                metadataDescription,
                                                metadataYear.toIntOrNull(),
                                            )
                                            editingMetadata = false
                                        }
                                    }
                                    ControllerButton(stringResource(R.string.cancel)) { editingMetadata = false }
                                }
                            }
                        } else {
                            Text(
                                game.description ?: "Filename or app-label fallback. Editability is preserved without a provider key.",
                                color = textMuted,
                            )
                            Text(
                                "Provenance: ${game.metadataProvenance.entries.joinToString { "${it.key}=${it.value}" }.ifBlank { "UNKNOWN" }}",
                                color = mint,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                "Artwork: ${game.artworkProvenance ?: "GENERATED"}",
                                color = textMuted,
                                fontSize = 11.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                ControllerButton(stringResource(R.string.edit_metadata)) { editingMetadata = true }
                                ControllerButton(stringResource(R.string.choose_artwork)) { actions.onPickArtwork() }
                            }
                        }
                    }
                }
                1 -> {
                    if (game.sessions.isEmpty()) {
                        item { EmptyState("No sessions yet", "A PENDING record is stored before every external launch.") }
                    } else {
                        items(game.sessions, key = { it.id }) { session ->
                            InfoCard(
                                session.state.name,
                                "${formatDuration(session.durationMillis)} · ${Instant.ofEpochMilli(session.startedAt)}",
                                if (session.state.name == "ENDED") CapabilityUiState.READY else CapabilityUiState.NEEDS_ACTION,
                            )
                        }
                    }
                }
                2 -> {
                    item {
                        ControllerButton("Open note editor") { actions.onScreenChange(HubScreen.NOTES) }
                    }
                    if (game.notes.isEmpty()) {
                        item { EmptyState("No notes", "Create text notes or checklists linked to this game.") }
                    } else {
                        items(game.notes, key = { it.id }) { note -> NoteCard(note, actions) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHero(game: GameDetailSummary, actions: HubActions) {
    var showMethods by rememberSaveable(game.id) { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xDD080D12)),
        border = BorderStroke(1.dp, generatedAccent(game.id).copy(alpha = 0.7f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            game.artworkLocator?.takeIf { it.startsWith("content://") }?.let { locator ->
                AsyncImage(
                    model = locator,
                    contentDescription = "User-selected artwork for ${game.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            Text(game.title, color = Color.White, fontSize = 33.sp, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(game.versions.firstOrNull()?.platform, game.releaseYear?.toString()).joinToString(" · ").ifBlank { "Local metadata" },
                color = textMuted,
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControllerButton(stringResource(R.string.play), onLongA = { showMethods = !showMethods }) {
                    if (game.playMethods.isEmpty()) actions.onScreenChange(HubScreen.SETUP) else actions.onLaunchSelected()
                }
                ControllerButton(if (game.pinned) stringResource(R.string.unpin) else stringResource(R.string.pin)) { actions.onTogglePinned() }
                ControllerButton(if (game.favorite) stringResource(R.string.unfavorite) else stringResource(R.string.favorite)) { actions.onToggleFavorite() }
                ControllerButton(stringResource(R.string.notes)) { actions.onScreenChange(HubScreen.NOTES) }
                ControllerButton(stringResource(R.string.hide)) { actions.onHideSelected() }
            }
            if (showMethods) {
                Text("Choose PlayMethod", color = mint, fontWeight = FontWeight.Bold)
                game.playMethods.forEach { method ->
                    Text("• ${method.title} · ${method.compatibility.name}", color = textMuted)
                }
                if (game.playMethods.isEmpty()) Text("No configured method.", color = Color(0xFFFFC56D))
            }
        }
    }
}

@Composable
private fun NotesScreen(state: HubUiState, actions: HubActions) {
    val game = state.selectedGame
    var body by rememberSaveable(game?.id) { mutableStateOf("") }
    var checklist by rememberSaveable { mutableStateOf(false) }
    var pinned by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val visibleNotes = if (state.noteSearchQuery.isBlank()) game?.notes.orEmpty() else state.noteResults
    LazyColumn(
        Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 34.dp),
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            ScreenHeader(stringResource(R.string.notes), game?.title ?: stringResource(R.string.select_game_first)) { actions.onScreenChange(HubScreen.DETAIL) }
        }
        item {
            OutlinedTextField(
                value = state.noteSearchQuery,
                onValueChange = actions.onNoteSearchQueryChange,
                label = { Text("Search all notes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(if (editingId == null) "New local note" else "Edit note", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Automatically linked to Game, Version, active Session, and Device when available.", color = textMuted, fontSize = 11.sp)
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text(if (checklist) "One checklist item per line" else "Note") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checklist, onCheckedChange = { checklist = it })
                        Text(stringResource(R.string.checklist), color = Color.White)
                        Spacer(Modifier.width(12.dp))
                        Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                        Text("Pin to Resume Card", color = Color.White)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ControllerButton(if (editingId == null) stringResource(R.string.save_note) else "Save changes") {
                            if (body.isNotBlank() && game != null) {
                                val target = editingId
                                if (target == null) actions.onSaveNote(body, checklist, pinned) else actions.onUpdateNote(target, body)
                                body = ""
                                editingId = null
                            }
                        }
                        if (editingId != null) {
                            OutlinedButton(onClick = { editingId = null; body = "" }) { Text(stringResource(R.string.cancel)) }
                        }
                    }
                }
            }
        }
        if (visibleNotes.isEmpty()) {
            item { EmptyState("No matching notes", "Text and checklist notes remain available offline.") }
        } else {
            items(visibleNotes, key = { it.id }) { note ->
                NoteCard(note, actions, onEdit = {
                    editingId = note.id
                    body = note.body
                    checklist = note.type.name == "CHECKLIST"
                    pinned = note.pinned
                })
            }
        }
        item {
            InfoCard(
                "Later note types",
                "Voice transcription, screenshot notes, and handwriting have data-model types but no fake controls in this slice.",
                CapabilityUiState.UNSUPPORTED,
            )
        }
    }
}

@Composable
private fun NoteCard(note: NoteSummary, actions: HubActions, onEdit: (() -> Unit)? = null) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(note.type.name, color = mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(if (note.pinned) "PINNED" else "LOCAL", color = if (note.pinned) Color(0xFFFFC56D) else textMuted, fontSize = 10.sp)
            }
            Text(note.body, color = if (note.completed) textMuted else Color.White)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                onEdit?.let { ControllerButton("Edit", onClick = it) }
                ControllerButton(if (note.pinned) "Unpin" else "Pin") { actions.onSetNotePinned(note.id, !note.pinned) }
                if (note.type.name == "CHECKLIST") {
                    ControllerButton(if (note.completed) "Reopen" else "Complete") {
                        actions.onSetNoteCompleted(note.id, !note.completed)
                    }
                }
            }
        }
    }
}

@Composable
private fun EndSessionScreen(state: HubUiState, actions: HubActions) {
    var note by rememberSaveable { mutableStateOf("") }
    val active = state.activeSessions.firstOrNull()
    val summary = state.endSessionSummary
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(stringResource(R.string.end_session), "Ends local Game Hub state; it does not claim the external app closed") {
            actions.onScreenChange(HubScreen.DETAIL)
        }
        if (summary == null) {
            InfoCard(
                "Local session",
                active?.let { "Started ${Instant.ofEpochMilli(it.startedAt)} · current state ${it.state.name}" }
                    ?: "No active local session is available.",
                if (active == null) CapabilityUiState.NEEDS_ACTION else CapabilityUiState.READY,
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.optional_closing_note)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            ControllerButton(stringResource(R.string.end_local_session), onClick = { actions.onEndSession(note) })
            Text("No force-stop is used in standard mode.", color = Color(0xFFFFC56D), fontSize = 12.sp)
        } else {
            SectionTitle("Session saved")
            InfoCard("Duration", formatDuration(summary.durationMillis), CapabilityUiState.READY)
            InfoCard(
                "Note and history",
                summary.noteStatus,
                if (summary.noteStatus == "ERROR") CapabilityUiState.NEEDS_ACTION else CapabilityUiState.READY,
            )
            InfoCard(
                "Save snapshot",
                summary.snapshotStatus,
                when (summary.snapshotStatus) {
                    "SNAPSHOTTED" -> CapabilityUiState.READY
                    "MANAGED_EXTERNALLY" -> CapabilityUiState.UNKNOWN
                    else -> CapabilityUiState.NEEDS_ACTION
                },
            )
            summary.snapshotDetail?.let { Text(it, color = Color(0xFF9AA8B7), fontSize = 12.sp) }
            InfoCard("Sync", summary.syncStatus, CapabilityUiState.UNKNOWN)
            InfoCard("Graceful exit request", summary.externalRequestStatus, CapabilityUiState.UNKNOWN)
            summary.externalRequestDetail?.let { Text(it, color = Color(0xFF9AA8B7), fontSize = 12.sp) }
            InfoCard("External process", summary.externalClosureStatus, CapabilityUiState.UNKNOWN)
            InfoCard("HOME route", summary.homeRouteStatus, CapabilityUiState.UNKNOWN)
            ControllerButton(stringResource(R.string.return_home)) { actions.onScreenChange(HubScreen.HOME) }
        }
    }
}

@Composable
private fun SetupScreen(state: HubUiState, actions: HubActions) {
    var gameNativeTitle by rememberSaveable { mutableStateOf("") }
    var gameNativeAppId by rememberSaveable { mutableStateOf("") }
    var gameNativeSource by rememberSaveable { mutableStateOf(GameNativeSource.STEAM) }
    var externalComponent by rememberSaveable { mutableStateOf<String?>(null) }
    var externalTitle by rememberSaveable { mutableStateOf("") }
    var externalAction by rememberSaveable { mutableStateOf("android.intent.action.VIEW") }
    var externalData by rememberSaveable { mutableStateOf("") }
    var externalExtras by rememberSaveable { mutableStateOf("") }
    var retroGameId by rememberSaveable { mutableStateOf(state.selectedGameId) }
    var retroPackage by rememberSaveable { mutableStateOf("com.retroarch.aarch64") }
    var retroCorePath by rememberSaveable { mutableStateOf("") }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    val selectedExternal = state.launchableApps.firstOrNull { it.componentName.flattenToString() == externalComponent }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 20.dp)
            .testTag("setup-list"),
        verticalArrangement = Arrangement.spacedBy(13.dp),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            ScreenHeader(stringResource(R.string.setup_center), stringResource(R.string.setup_subtitle)) {
                actions.onScreenChange(HubScreen.HOME)
            }
        }
        item {
            SetupSection(stringResource(R.string.displays), topologyCapability(state), topologyLabel(state)) {
                ControllerButton("Placement diagnostics") { actions.onScreenChange(HubScreen.DIAGNOSTICS) }
                ControllerButton("Launch Companion") { actions.onLaunchCompanion(state.topology?.lowerDisplayId) }
            }
        }
        item {
            SetupSection(
                stringResource(R.string.home_role),
                if (state.homeRole.status == HomeRoleStatus.HELD) CapabilityUiState.READY else CapabilityUiState.NEEDS_ACTION,
                state.homeRole.explanation,
            ) {
                ControllerButton("Request through Android") { actions.onRequestHomeRole() }
                ControllerButton("Recovery settings") { actions.onOpenHomeSettings() }
            }
        }
        item {
            SetupSection(
                stringResource(R.string.library_roots),
                if (state.libraryRoots.any { it.state == "ACTIVE" }) CapabilityUiState.READY else CapabilityUiState.NEEDS_ACTION,
                if (state.libraryRoots.isEmpty()) "Choose a ROM root with Storage Access Framework."
                else state.libraryRoots.joinToString { "${it.platformHint ?: "AUTO"}: ${it.state}" },
            ) {
                ControllerButton(stringResource(R.string.choose_rom_folder)) { actions.onPickLibraryRoot() }
            }
        }
        item {
            SectionTitle(stringResource(R.string.android_apps))
            Text("Only visible ACTION_MAIN + CATEGORY_LAUNCHER activities are listed. Game Hub excludes itself.", color = textMuted)
        }
        items(state.launchableApps.take(12), key = { it.componentName.flattenToString() }) { app ->
            Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("${app.packageName} · ${app.versionName ?: "version unknown"}", color = textMuted, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ControllerButton("Add") { actions.onImportAndroidApp(app) }
                        ControllerButton("External") {
                            externalComponent = app.componentName.flattenToString()
                            externalTitle = app.label
                        }
                    }
                }
            }
        }
        item {
            SetupSection(
                "GameNative manual title",
                CapabilityUiState.NEEDS_ACTION,
                "Uses the verified public action/component/extras. Saves remain Managed externally unless separate access is verified.",
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(gameNativeTitle, { gameNativeTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        gameNativeAppId,
                        { gameNativeAppId = it.filter(Char::isDigit) },
                        label = { Text("App ID") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GameNativeSource.entries.forEach { source ->
                            ControllerButton(source.name, selected = source == gameNativeSource) { gameNativeSource = source }
                        }
                    }
                    ControllerButton("Add GameNative title") {
                        gameNativeAppId.toIntOrNull()?.let { actions.onAddGameNative(gameNativeTitle, gameNativeSource, it) }
                    }
                }
            }
        }
        item {
            SetupSection(
                "RetroArch",
                state.adapterProbes.firstOrNull { it.adapter == "RetroArch" }?.state ?: CapabilityUiState.UNKNOWN,
                "Select an imported SAF ROM, the installed package candidate, and the exact core path shown by RetroArch. Every combination remains UNVERIFIED until test-launched locally.",
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        retroGameId?.let { selected ->
                            "ROM: ${state.library.firstOrNull { it.id == selected }?.title ?: selected}"
                        } ?: "No imported ROM selected.",
                        color = if (retroGameId == null) Color(0xFFFFC56D) else mint,
                        fontSize = 11.sp,
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.library
                            .filter { it.platform !in setOf("ANDROID", "GAME_NATIVE") }
                            .take(12)
                            .forEach { game ->
                                ControllerButton(game.title, selected = retroGameId == game.id) {
                                    retroGameId = game.id
                                }
                            }
                    }
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LauncherProfiles.retroArchPackages.forEach { packageName ->
                            ControllerButton(packageName, selected = retroPackage == packageName) {
                                retroPackage = packageName
                            }
                        }
                    }
                    OutlinedTextField(
                        value = retroCorePath,
                        onValueChange = { retroCorePath = it },
                        label = { Text("Core path reported by RetroArch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "The path is stored only in app-private configuration; Game Hub does not read RetroArch private storage.",
                        color = textMuted,
                        fontSize = 10.sp,
                    )
                    ControllerButton("Save unverified RetroArch method") {
                        retroGameId?.let { gameId ->
                            actions.onConfigureRetroArch(gameId, retroPackage, retroCorePath)
                        }
                    }
                }
            }
        }
        item {
            SetupSection(
                "Configurable PS2 / external",
                if (selectedExternal == null) CapabilityUiState.NEEDS_ACTION else CapabilityUiState.UNKNOWN,
                "Choose External beside an installed activity, then edit action, data, and typed extras. Save access is declared separately.",
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        selectedExternal?.let { "Selected: ${it.label} · ${it.componentName.flattenToShortString()}" }
                            ?: "No external activity selected.",
                        color = if (selectedExternal == null) Color(0xFFFFC56D) else mint,
                        fontSize = 11.sp,
                    )
                    OutlinedTextField(externalTitle, { externalTitle = it }, label = { Text("Game title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(externalAction, { externalAction = it }, label = { Text("Intent action") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(externalData, { externalData = it }, label = { Text("Data URI or template") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        externalExtras,
                        { externalExtras = it },
                        label = { Text("Typed extras, one key:type=value per line") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Types: text, int, boolean, list. Profile stays editable and UNVERIFIED until test launch.", color = textMuted, fontSize = 10.sp)
                    ControllerButton("Add editable profile") {
                        selectedExternal?.let { app ->
                            actions.onAddConfigurableExternal(
                                externalTitle,
                                app,
                                externalAction.takeIf(String::isNotBlank),
                                externalData.takeIf(String::isNotBlank),
                                externalExtras,
                            )
                        }
                    }
                }
            }
        }
        item {
            SetupSection(
                "Hidden library items",
                if (state.hiddenGames.isEmpty()) CapabilityUiState.READY else CapabilityUiState.NEEDS_ACTION,
                if (state.hiddenGames.isEmpty()) "No hidden games." else "Hidden games keep all versions, sessions, notes, and save history.",
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    state.hiddenGames.forEach { game ->
                        ControllerButton("Unhide ${game.title}") { actions.onUnhideGame(game.id) }
                    }
                }
            }
        }
        item {
            SetupSection(
                stringResource(R.string.controller_mapping),
                CapabilityUiState.READY,
                "Bindings are stored locally. Select an action to cycle through A, B, X, Y, L1, R1, Start, and Select.",
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HubControlAction.entries.forEach { action ->
                        ControllerButton(
                            "${action.label}: ${ControllerMapping.keyLabel(state.controllerMapping.keyCode(action))}",
                        ) { actions.onCycleControl(action) }
                    }
                    ControllerButton("Reset controller defaults", onClick = actions.onResetControls)
                }
            }
        }
        item {
            SetupSection(
                stringResource(R.string.metadata_artwork),
                CapabilityUiState.READY,
                "Local app icons and generated contrast-checked gradients work without provider keys. Network providers are optional.",
            ) { }
        }
        item {
            SetupSection(
                stringResource(R.string.saves),
                when (state.saveContinuity.profile?.state) {
                    "LOCAL_ONLY", "SNAPSHOTTED" -> CapabilityUiState.READY
                    "ERROR" -> CapabilityUiState.ERROR
                    "UNAVAILABLE" -> CapabilityUiState.NEEDS_ACTION
                    "MANAGED_EXTERNALLY" -> CapabilityUiState.UNKNOWN
                    else -> CapabilityUiState.NEEDS_ACTION
                },
                state.saveContinuity.profile?.let { profile ->
                    "${profile.type} · ${profile.access} · ${profile.state}. ${state.saveContinuity.snapshots.size} local snapshot(s)."
                } ?: "Private app paths are never assumed. Select a game, then grant an explicit read/write SAF directory.",
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        state.selectedGame?.let { stringResource(R.string.selected_game_format, it.title) }
                            ?: stringResource(R.string.select_game_before_save),
                        color = if (state.selectedGame == null) Color(0xFFFFC56D) else mint,
                        fontSize = 11.sp,
                    )
                    ControllerButton(stringResource(R.string.choose_save_directory)) { actions.onPickSaveRoot() }
                    ControllerButton(stringResource(R.string.resume_threshold_format, state.resumeThresholdHours)) {
                        actions.onCycleResumeThreshold()
                    }
                    if (state.saveContinuity.profile?.configured == true) {
                        ControllerButton(stringResource(R.string.create_local_snapshot)) { actions.onSnapshotSaves() }
                    }
                    if (state.saveContinuity.snapshots.any { it.state == "COMPLETE" }) {
                        if (confirmRestore) {
                            Text(
                                "This writes snapshot files back to the granted directory. A pre-restore backup is required first; unlisted live files are preserved.",
                                color = Color(0xFFFFC56D),
                                fontSize = 11.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ControllerButton(stringResource(R.string.confirm_restore)) {
                                    actions.onRestoreLatestSnapshot()
                                    confirmRestore = false
                                }
                                ControllerButton(stringResource(R.string.cancel_restore)) { confirmRestore = false }
                            }
                        } else {
                            ControllerButton(stringResource(R.string.prepare_latest_restore)) { confirmRestore = true }
                        }
                    }
                }
            }
        }
        item {
            SetupSection(
                stringResource(R.string.optional_capabilities),
                CapabilityUiState.UNSUPPORTED,
                "Standard mode uses no Shizuku dependency, broad storage, force-stop, or Accessibility automation.",
            ) { }
        }
        item {
            SetupSection(stringResource(R.string.diagnostics), CapabilityUiState.READY, "Redacted local evidence can be exported as versioned JSON.") {
                ControllerButton(stringResource(R.string.open_diagnostics)) { actions.onScreenChange(HubScreen.DIAGNOSTICS) }
            }
        }
    }
}

@Composable
private fun SetupSection(
    title: String,
    state: CapabilityUiState,
    body: String,
    actions: @Composable () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                StatusPill(state.name.replace('_', ' '), state)
            }
            Text(body, color = textMuted, fontSize = 12.sp)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions()
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(state: HubUiState, actions: HubActions) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 38.dp),
    ) {
        item {
            Spacer(Modifier.height(14.dp))
            ScreenHeader(stringResource(R.string.diagnostics), "Observed Android state, requests, and honest UNKNOWN results") {
                actions.onScreenChange(HubScreen.HOME)
            }
        }
        state.error?.let { error -> item { InfoCard("Diagnostic error", error, CapabilityUiState.ERROR) } }
        item {
            SectionTitle(stringResource(R.string.displays))
            Text("Display IDs are runtime observations and are never persisted as identity by themselves.", color = textMuted)
        }
        items(state.topology?.displays.orEmpty(), key = { it.id }) { DisplayCard(it) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Visual placement test", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        if (state.topology?.assignmentVerified == true) "Assignment confirmed for this firmware fingerprint."
                        else "Launch the distinct pattern, swap if needed, then confirm only what you physically observe.",
                        color = textMuted,
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ControllerButton("Launch lower pattern") { actions.onLaunchCompanion(state.topology?.lowerDisplayId) }
                        ControllerButton("Swap") { actions.onSwapDisplays() }
                        ControllerButton("Confirm observed") { actions.onConfirmPlacement() }
                    }
                    state.lastLaunch?.let { launch ->
                        InfoCard(
                            if (launch.requestAccepted) "Launch request accepted" else "Launch request failed",
                            launch.message,
                            if (launch.requestAccepted) CapabilityUiState.UNKNOWN else CapabilityUiState.ERROR,
                        )
                    }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.home_role)) }
        item {
            InfoCard(
                state.homeRole.status.name.replace('_', ' '),
                state.homeRole.explanation,
                when (state.homeRole.status) {
                    HomeRoleStatus.HELD -> CapabilityUiState.READY
                    HomeRoleStatus.AVAILABLE_NOT_HELD, HomeRoleStatus.DENIED_OR_CANCELLED -> CapabilityUiState.NEEDS_ACTION
                    HomeRoleStatus.UNAVAILABLE -> CapabilityUiState.UNSUPPORTED
                    HomeRoleStatus.ERROR -> CapabilityUiState.ERROR
                    HomeRoleStatus.REQUEST_IN_PROGRESS -> CapabilityUiState.UNKNOWN
                },
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControllerButton("Request HOME role") { actions.onRequestHomeRole() }
                ControllerButton("Recovery settings") { actions.onOpenHomeSettings() }
            }
        }
        item { SectionTitle("Launcher adapter probes") }
        items(state.adapterProbes, key = { it.adapter }) { probe ->
            InfoCard(
                probe.adapter,
                buildString {
                    append(probe.explanation)
                    probe.candidate?.let { append("\n${it.packageName} · ${it.versionName ?: "version unknown"}") }
                },
                probe.state,
            )
        }
        item {
            SectionTitle("Visible app test launch")
            Text("Android acceptance is not proof of physical upper-panel placement.", color = textMuted)
        }
        items(state.launchableApps.take(12), key = { it.componentName.flattenToString() }) { app ->
            Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("${app.packageName} · ${app.versionName ?: "unknown version"}", color = textMuted, fontSize = 11.sp)
                    }
                    ControllerButton("Test launch") { actions.onLaunchApp(app) }
                }
            }
        }
        item { SectionTitle("Observable lifecycle signals") }
        items(state.recentEvents.take(24), key = { it.sequence }) { event ->
            Row(Modifier.fillMaxWidth().border(1.dp, Color(0xFF1D2935), RoundedCornerShape(10.dp)).padding(11.dp)) {
                Text(event.category, color = mint, modifier = Modifier.width(110.dp), fontSize = 11.sp)
                Column {
                    Text(event.name, color = Color.White, fontSize = 12.sp)
                    if (event.details.isNotEmpty()) {
                        Text(event.details.entries.joinToString(" · ") { "${it.key}=${it.value}" }, color = textMuted, fontSize = 10.sp)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControllerButton(stringResource(R.string.refresh)) { actions.onRefresh() }
                ControllerButton(stringResource(R.string.export_redacted_json)) { actions.onExportDiagnostics() }
            }
        }
    }
}

@Composable
private fun DisplayCard(display: DisplayDescriptor) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${display.name} · ID ${display.id}", color = Color.White, fontWeight = FontWeight.Bold)
                StatusPill(display.role.name.replace('_', ' '), if (display.role.name == "UNASSIGNED") CapabilityUiState.UNKNOWN else CapabilityUiState.NEEDS_ACTION)
            }
            Text("Unique ID: ${display.uniqueId ?: "Unavailable through current public API"}", color = textMuted, fontSize = 11.sp)
            Text("${display.widthPx}×${display.heightPx} · ${display.densityDpi} dpi · rotation ${display.rotation}", color = textMuted, fontSize = 11.sp)
            Text("${display.typeLabel} · ${display.stateLabel} · flags 0x${display.flags.toString(16)}", color = textMuted, fontSize = 11.sp)
            Text("${"%.2f".format(display.refreshRateHz)} Hz · modes ${display.supportedRefreshRatesHz.joinToString { "%.2f".format(it) }}", color = textMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ControllerButton(stringResource(R.string.back), onClick = onBack)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            Text(subtitle, color = textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 7.dp).semantics { heading() })
}

@Composable
private fun EmptyState(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(body, color = textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String, state: CapabilityUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
                StatusPill(state.name.replace('_', ' '), state)
            }
            Text(body, color = textMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StatusPill(label: String, state: CapabilityUiState) {
    val color = when (state) {
        CapabilityUiState.READY -> Color(0xFF56D6A8)
        CapabilityUiState.NEEDS_ACTION -> Color(0xFFFFC56D)
        CapabilityUiState.UNSUPPORTED -> Color(0xFF8692A1)
        CapabilityUiState.UNKNOWN -> Color(0xFF75A8FF)
        CapabilityUiState.ERROR -> Color(0xFFFF7D87)
    }
    Text(
        text = label,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.13f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
internal fun ControllerButton(
    label: String,
    selected: Boolean = false,
    onLongA: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var longPressHandled by remember { mutableStateOf(false) }
    val controllerMapping = LocalControllerMapping.current
    Button(
        onClick = onClick,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = "$label. Controller A activates." }
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode != controllerMapping.keyCode(HubControlAction.ACTIVATE)) return@onPreviewKeyEvent false
                when (native.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (onLongA != null && !longPressHandled && (native.isLongPress || native.repeatCount > 0)) {
                            longPressHandled = true
                            onLongA()
                        }
                        true
                    }
                    KeyEvent.ACTION_UP -> {
                        if (!longPressHandled) onClick()
                        longPressHandled = false
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused || selected) mint else Color(0xFF17212C),
            contentColor = if (focused || selected) Color.Black else Color.White,
        ),
        border = if (focused) BorderStroke(2.dp, Color.White) else BorderStroke(1.dp, Color(0xFF334355)),
        shape = RoundedCornerShape(11.dp),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
    }
}

private fun topologyLabel(state: HubUiState): String = when (state.topology?.mode) {
    TopologyMode.DUAL_DISPLAY -> if (state.topology.assignmentVerified) "Dual display · user verified" else "Dual display · confirm placement"
    TopologyMode.SINGLE_DISPLAY -> "Single-display fallback"
    TopologyMode.DEGRADED -> "Display diagnostics required"
    null -> "Discovering displays"
}

private fun topologyCapability(state: HubUiState): CapabilityUiState = when (state.topology?.mode) {
    TopologyMode.DUAL_DISPLAY -> if (state.topology.assignmentVerified) CapabilityUiState.READY else CapabilityUiState.NEEDS_ACTION
    TopologyMode.SINGLE_DISPLAY -> CapabilityUiState.UNKNOWN
    TopologyMode.DEGRADED -> CapabilityUiState.ERROR
    null -> CapabilityUiState.UNKNOWN
}

private fun generatedAccent(key: String): Color {
    val hash = key.fold(17) { acc, char -> acc * 31 + char.code }
    val hue = ((hash.toLong() and 0x7fffffffL) % 360L).toFloat()
    return Color.hsl(hue, 0.56f, 0.42f)
}

private fun readableOn(color: Color): Color = if (color.luminance() > 0.45f) Color.Black else Color.White

private fun formatDuration(millis: Long): String {
    val duration = Duration.ofMillis(millis.coerceAtLeast(0L))
    val hours = duration.toHours()
    val minutes = duration.minusHours(hours).toMinutes()
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private infix fun EnterTransition.togetherWith(exit: ExitTransition): ContentTransform = ContentTransform(this, exit)
