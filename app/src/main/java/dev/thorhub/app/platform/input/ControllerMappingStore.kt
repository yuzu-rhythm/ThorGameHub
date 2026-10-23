package dev.thorhub.app.platform.input

import android.content.Context
import android.view.KeyEvent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.controllerMappingDataStore by preferencesDataStore(name = "controller_mapping")

enum class HubControlAction(val label: String, val defaultKeyCode: Int) {
    ACTIVATE("A · Activate", KeyEvent.KEYCODE_BUTTON_A),
    BACK("B · Back", KeyEvent.KEYCODE_BUTTON_B),
    DETAILS("X · Details", KeyEvent.KEYCODE_BUTTON_X),
    CONTEXT("Y · Context", KeyEvent.KEYCODE_BUTTON_Y),
    PREVIOUS_TAB("L · Previous tab", KeyEvent.KEYCODE_BUTTON_L1),
    NEXT_TAB("R · Next tab", KeyEvent.KEYCODE_BUTTON_R1),
    QUICK_MENU("Start · Quick menu", KeyEvent.KEYCODE_BUTTON_START),
    SEARCH("Select · Search", KeyEvent.KEYCODE_BUTTON_SELECT),
}

data class ControllerMapping(val bindings: Map<HubControlAction, Int>) {
    fun keyCode(action: HubControlAction): Int = bindings[action] ?: action.defaultKeyCode

    companion object {
        val DEFAULT = ControllerMapping(HubControlAction.entries.associateWith(HubControlAction::defaultKeyCode))
        val editableKeyCodes = listOf(
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT,
        )

        fun keyLabel(keyCode: Int): String = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "A"
            KeyEvent.KEYCODE_BUTTON_B -> "B"
            KeyEvent.KEYCODE_BUTTON_X -> "X"
            KeyEvent.KEYCODE_BUTTON_Y -> "Y"
            KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
            KeyEvent.KEYCODE_BUTTON_START -> "Start"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
            else -> KeyEvent.keyCodeToString(keyCode)
        }
    }
}

@Singleton
class ControllerMappingStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.controllerMappingDataStore

    val mapping: Flow<ControllerMapping> = dataStore.data.map { values ->
        ControllerMapping(
            HubControlAction.entries.associateWith { action ->
                values[key(action)] ?: action.defaultKeyCode
            },
        )
    }

    suspend fun cycle(action: HubControlAction) {
        dataStore.edit { values ->
            val current = values[key(action)] ?: action.defaultKeyCode
            val options = ControllerMapping.editableKeyCodes
            values[key(action)] = options[(options.indexOf(current).coerceAtLeast(0) + 1) % options.size]
        }
    }

    suspend fun reset() {
        dataStore.edit { values -> HubControlAction.entries.forEach { values.remove(key(it)) } }
    }

    private fun key(action: HubControlAction) = intPreferencesKey("binding_${action.name.lowercase()}")
}
