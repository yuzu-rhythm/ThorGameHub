package dev.thorhub.app

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.compose.collectAsLazyPagingItems
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.EntryPoints
import dev.thorhub.app.feature.diagnostics.HubViewModel
import dev.thorhub.app.feature.shell.HubScreen
import dev.thorhub.app.feature.shell.HubActions
import dev.thorhub.app.feature.shell.ThorHubShell
import dev.thorhub.app.platform.diagnostics.DiagnosticEventLog
import dev.thorhub.app.platform.display.AndroidDisplayCoordinator
import dev.thorhub.app.platform.home.HomeRoleCoordinator
import dev.thorhub.app.platform.safety.CrashLoopGuard
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@AndroidEntryPoint
class HomeActivity : ComponentActivity() {
    private val viewModel: HubViewModel by viewModels()

    @Inject
    lateinit var homeRoleCoordinator: HomeRoleCoordinator

    @Inject
    lateinit var displayCoordinator: AndroidDisplayCoordinator

    @Inject
    lateinit var crashLoopGuard: CrashLoopGuard

    private var pendingExport: String? = null
    private var hubHomeRouteGeneration by mutableIntStateOf(0)

    private val homeRoleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onHomeRoleResult(result.resultCode == Activity.RESULT_OK)
    }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (uri == null || payload == null) {
            DiagnosticEventLog.record("diagnostics", "export_cancelled")
            return@registerForActivityResult
        }
        runCatching {
            contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { writer ->
                writer.write(payload)
            } ?: error("The selected document could not be opened.")
        }.onSuccess {
            DiagnosticEventLog.record("diagnostics", "redacted_export_written")
            viewModel.refreshEvents()
        }.onFailure { error ->
            DiagnosticEventLog.record("diagnostics", "export_failed", mapOf("exception" to error::class.java.simpleName))
            viewModel.refreshEvents()
        }
    }

    private val libraryRootLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            DiagnosticEventLog.record("library", "root_selection_cancelled")
        } else {
            viewModel.addLibraryRoot(uri)
        }
    }

    private val saveRootLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(uri, grants) }
            .onSuccess { viewModel.configureSelectedSaveRoot(uri) }
            .onFailure { error ->
                DiagnosticEventLog.record(
                    "saves",
                    "grant_persistence_failed",
                    mapOf("exception" to error::class.java.simpleName),
                )
                viewModel.refreshEvents()
            }
    }

    private val artworkLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModel.setSelectedArtwork(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        if (intent.shouldRouteToHubHome()) {
            hubHomeRouteGeneration += 1
        }
        if (crashLoopGuard.isSafeMode) {
            setContent {
                ThorHubTheme {
                    SafeFallbackScreen(
                        onRetry = {
                            val recovered = EntryPoints.get(
                                applicationContext,
                                ThorHubApplication.StartupDependencies::class.java,
                            ).sessionRecovery().start()
                            val safeModeCleared = recovered && crashLoopGuard.clearAndRetry()
                            if (safeModeCleared) {
                                recreate()
                            }
                            safeModeCleared
                        },
                        onOpenHomeSettings = {
                            runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                        },
                    )
                }
            }
            return
        }
        setContent {
            ThorHubTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val pagedLibrary = viewModel.pagedLibrary.collectAsLazyPagingItems()
                var screen by rememberSaveable { mutableStateOf(HubScreen.HOME) }
                LaunchedEffect(hubHomeRouteGeneration) {
                    if (hubHomeRouteGeneration > 0) screen = HubScreen.HOME
                }
                ThorHubShell(
                    state = state,
                    screen = screen,
                    reducedMotion = systemReducedMotion(),
                    pagedLibrary = pagedLibrary,
                    actions = HubActions(
                        onScreenChange = { screen = it },
                        onSelectGame = viewModel::selectGame,
                        onSearchQueryChange = viewModel::setSearchQuery,
                        onNoteSearchQueryChange = viewModel::setNoteSearchQuery,
                        onLaunchSelected = viewModel::launchSelectedGame,
                        onTogglePinned = viewModel::toggleSelectedPinned,
                        onToggleFavorite = viewModel::toggleSelectedFavorite,
                        onHideSelected = viewModel::hideSelectedGame,
                        onUnhideGame = viewModel::unhideGame,
                        onUpdateMetadata = viewModel::updateSelectedMetadata,
                        onPickArtwork = { artworkLauncher.launch(arrayOf("image/*")) },
                        onSaveNote = viewModel::saveNote,
                        onUpdateNote = viewModel::updateNote,
                        onSetNotePinned = viewModel::setNotePinned,
                        onSetNoteCompleted = viewModel::setNoteCompleted,
                        onEndSession = viewModel::endActiveSession,
                        onImportAndroidApp = viewModel::importAndroidApp,
                        onAddGameNative = viewModel::addGameNativeTitle,
                        onConfigureRetroArch = viewModel::configureRetroArch,
                        onAddConfigurableExternal = viewModel::addConfigurableExternalTitle,
                        onPickLibraryRoot = { libraryRootLauncher.launch(null) },
                        onPickSaveRoot = { saveRootLauncher.launch(null) },
                        onSnapshotSaves = viewModel::snapshotSelectedSaves,
                        onRestoreLatestSnapshot = viewModel::restoreLatestSnapshot,
                        onEndRecoveredSession = viewModel::endRecoveredSession,
                        onCycleResumeThreshold = viewModel::cycleResumeThreshold,
                        onRefresh = viewModel::refresh,
                        onLaunchCompanion = viewModel::launchCompanion,
                        onSwapDisplays = viewModel::swapDisplays,
                        onConfirmPlacement = viewModel::confirmPlacement,
                        onRequestHomeRole = {
                            homeRoleCoordinator.requestIntent()?.let(homeRoleLauncher::launch)
                                ?: viewModel.refreshHomeRole()
                        },
                        onOpenHomeSettings = {
                            runCatching { startActivity(homeRoleCoordinator.settingsIntent()) }
                                .onFailure { error ->
                                    DiagnosticEventLog.record("home_role", "settings_open_failed", mapOf("exception" to error::class.java.simpleName))
                                }
                        },
                        onLaunchApp = viewModel::launchInstalledApp,
                        onExportDiagnostics = {
                            pendingExport = viewModel.exportJson()
                            val timestamp = Instant.now().toString().replace(':', '-')
                            exportLauncher.launch("thor-game-hub-diagnostics-$timestamp.json")
                        },
                        onClearMessage = viewModel::clearMessage,
                        onCycleControl = viewModel::cycleControl,
                        onResetControls = viewModel::resetControls,
                        onLibraryFiltersChanged = viewModel::setLibraryFilters,
                    ),
                )
            }
        }
        routeToVerifiedUpperIfNeeded()
        lifecycleScope.launch {
            delay(30_000)
            if (crashLoopGuard.markHealthy()) {
                DiagnosticEventLog.record("safety", "startup_marked_healthy")
            } else {
                DiagnosticEventLog.record("safety", "healthy_marker_persist_failed")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!crashLoopGuard.isSafeMode) viewModel.refreshHomeRole()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val routesToHubHome = intent.shouldRouteToHubHome()
        if (routesToHubHome) {
            hubHomeRouteGeneration += 1
        }
        DiagnosticEventLog.record(
            "home",
            "route_intent_observed",
            mapOf(
                "action" to (intent.action ?: "UNKNOWN"),
                "home_category" to intent.hasCategory(Intent.CATEGORY_HOME).toString(),
                "routes_to_hub_home" to routesToHubHome.toString(),
                "requested_display_id" to intent.getIntExtra(EXTRA_REQUESTED_DISPLAY_ID, -1).toString(),
                "observed_display_id" to (display?.displayId?.toString() ?: "UNKNOWN"),
                "physical_panel" to "UNKNOWN",
            ),
        )
    }

    private fun Intent.shouldRouteToHubHome(): Boolean = shouldRouteToHubHome(
        action = action,
        categories = categories,
        explicitLogicalUpperRoute = getBooleanExtra(EXTRA_LOGICAL_UPPER_ROUTE, false),
    )

    private fun systemReducedMotion(): Boolean = runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    private fun routeToVerifiedUpperIfNeeded() {
        if (intent.getBooleanExtra(EXTRA_LOGICAL_UPPER_ROUTE, false)) return
        lifecycleScope.launch {
            val topology = displayCoordinator.refresh()
            val upperDisplayId = topology.upperDisplayId
            if (
                topology.assignmentVerified &&
                upperDisplayId != null &&
                display?.displayId != upperDisplayId
            ) {
                displayCoordinator.showHomeOnUpper(upperDisplayId)
            }
        }
    }

    companion object {
        const val EXTRA_REQUESTED_DISPLAY_ID = "dev.thorhub.extra.HOME_REQUESTED_DISPLAY_ID"
        const val EXTRA_LOGICAL_UPPER_ROUTE = "dev.thorhub.extra.LOGICAL_UPPER_ROUTE"
    }
}

internal fun shouldRouteToHubHome(
    action: String?,
    categories: Set<String>?,
    explicitLogicalUpperRoute: Boolean,
): Boolean = explicitLogicalUpperRoute ||
    (action == Intent.ACTION_MAIN && Intent.CATEGORY_HOME in categories.orEmpty())

@Composable
private fun SafeFallbackScreen(
    onRetry: () -> Boolean,
    onOpenHomeSettings: () -> Unit,
) {
    var retryFailed by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Thor Game Hub safe mode", color = Color.White, fontSize = 26.sp)
        Text(
            "Three recent crashes were recorded. Database recovery, external launches, and display routing are paused so Android HOME remains reachable.",
            color = Color(0xFFB6C2CF),
            fontSize = 15.sp,
        )
        Button(onClick = { retryFailed = !onRetry() }) { Text("Retry normal mode once") }
        if (retryFailed) {
            Text(
                "Local startup recovery still failed. Safe mode remains active; no data was deleted.",
                color = Color(0xFFFFC56D),
                fontSize = 12.sp,
            )
        }
        Button(onClick = onOpenHomeSettings) { Text("Open Android HOME settings") }
        Text(
            "No game, save, session, or library record was deleted.",
            color = Color(0xFF7FE7D5),
            fontSize = 12.sp,
        )
    }
}

@Composable
fun ThorHubTheme(content: @Composable () -> Unit) {
    val systemDensity = LocalDensity.current
    val readableDensity = Density(
        density = systemDensity.density,
        fontScale = maxOf(systemDensity.fontScale, MINIMUM_HUB_FONT_SCALE),
    )
    CompositionLocalProvider(LocalDensity provides readableDensity) {
        MaterialTheme(
            colorScheme = androidx.compose.material3.darkColorScheme(
                primary = Color(0xFF7FE7D5),
                secondary = Color(0xFFFFC56D),
                background = Color.Black,
                surface = Color(0xFF080B0F),
            ),
            content = content,
        )
    }
}

private const val MINIMUM_HUB_FONT_SCALE = 1.28f
