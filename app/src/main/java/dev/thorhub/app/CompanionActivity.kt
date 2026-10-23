package dev.thorhub.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import dev.thorhub.app.feature.companion.CompanionDeck
import dev.thorhub.app.feature.companion.CompanionViewModel
import dev.thorhub.app.platform.diagnostics.DiagnosticEventLog
import dev.thorhub.app.platform.display.AndroidDisplayCoordinator
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CompanionActivity : ComponentActivity() {
    private val viewModel: CompanionViewModel by viewModels()
    private var requestedDisplayId by mutableStateOf<Int?>(null)
    private var placementTest by mutableStateOf(false)

    @Inject
    lateinit var displayCoordinator: AndroidDisplayCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
        )
        applyLaunchIntent(intent, "activity_observed")
        setContent {
            ThorHubTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                CompanionDeck(
                    requestedDisplayId = requestedDisplayId,
                    observedDisplayId = display?.displayId,
                    placementTest = placementTest,
                    state = state,
                    onSelectTab = viewModel::selectTab,
                    onSaveNote = viewModel::saveQuickNote,
                    onSetNotePinned = viewModel::setNotePinned,
                    onEndSession = viewModel::endLocalSession,
                    onReturnHome = ::returnHomeOnLogicalUpper,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunchIntent(intent, "new_intent_observed")
    }

    private fun applyLaunchIntent(intent: Intent, event: String) {
        requestedDisplayId = intent.getIntExtra(EXTRA_REQUESTED_DISPLAY_ID, -1).takeIf { it >= 0 }
        placementTest = intent.getBooleanExtra(EXTRA_PLACEMENT_TEST, false)
        DiagnosticEventLog.record(
            "companion",
            event,
            mapOf(
                "requested_display_id" to (requestedDisplayId?.toString() ?: "UNKNOWN"),
                "observed_display_id" to (display?.displayId?.toString() ?: "UNKNOWN"),
                "placement_test" to placementTest.toString(),
                "physical_panel" to "UNKNOWN",
            ),
        )
    }

    private fun returnHomeOnLogicalUpper() {
        lifecycleScope.launch {
            val topology = displayCoordinator.refresh()
            displayCoordinator.showHomeOnUpper(topology.upperDisplayId)
            finish()
        }
    }

    companion object {
        const val EXTRA_REQUESTED_DISPLAY_ID = "dev.thorhub.extra.REQUESTED_DISPLAY_ID"
        const val EXTRA_PLACEMENT_TEST = "dev.thorhub.extra.PLACEMENT_TEST"
        const val EXTRA_SESSION_ID = "dev.thorhub.extra.SESSION_ID"
    }
}
