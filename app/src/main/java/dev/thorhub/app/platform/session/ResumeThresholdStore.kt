package dev.thorhub.app.platform.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.resumePreferences by preferencesDataStore(name = "resume_preferences")

@Singleton
class ResumeThresholdStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val hours: Flow<Int> = context.resumePreferences.data.map { values ->
        values[THRESHOLD_HOURS]?.takeIf(ALLOWED::contains) ?: DEFAULT_HOURS
    }

    suspend fun cycle() {
        context.resumePreferences.edit { values ->
            val current = values[THRESHOLD_HOURS]?.takeIf(ALLOWED::contains) ?: DEFAULT_HOURS
            values[THRESHOLD_HOURS] = ALLOWED[(ALLOWED.indexOf(current) + 1) % ALLOWED.size]
        }
    }

    private companion object {
        const val DEFAULT_HOURS = 6
        val ALLOWED = listOf(1, 3, 6, 12, 24)
        val THRESHOLD_HOURS = intPreferencesKey("resume_threshold_hours")
    }
}
