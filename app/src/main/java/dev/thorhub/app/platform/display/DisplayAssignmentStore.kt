package dev.thorhub.app.platform.display

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.displayAssignmentDataStore by preferencesDataStore(name = "display_assignments")

data class StoredDisplayAssignment(
    val upperSignatureHash: String,
    val lowerSignatureHash: String,
    val verified: Boolean,
)

@Singleton
class DisplayAssignmentStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.displayAssignmentDataStore

    suspend fun load(buildFingerprintHash: String): StoredDisplayAssignment? {
        val values = dataStore.data.first()
        // Older development builds persisted transient Android display IDs.
        // Remove those keys eagerly even when the firmware hash no longer
        // matches; signatures are the only durable display identity.
        if (values[LEGACY_UPPER_ID_HINT] != null || values[LEGACY_LOWER_ID_HINT] != null) {
            dataStore.edit { migrated ->
                migrated.remove(LEGACY_UPPER_ID_HINT)
                migrated.remove(LEGACY_LOWER_ID_HINT)
            }
        }
        if (values[BUILD_HASH] != buildFingerprintHash) return null
        val upperSignature = values[UPPER_SIGNATURE] ?: return null
        val lowerSignature = values[LOWER_SIGNATURE] ?: return null
        return StoredDisplayAssignment(
            upperSignatureHash = upperSignature,
            lowerSignatureHash = lowerSignature,
            verified = values[VERIFIED] ?: false,
        )
    }

    suspend fun save(
        buildFingerprintHash: String,
        upper: DisplayDescriptor,
        lower: DisplayDescriptor,
        verified: Boolean,
    ) {
        dataStore.edit { values ->
            values[BUILD_HASH] = buildFingerprintHash
            values[UPPER_SIGNATURE] = sha256(upper.identitySignature)
            values[LOWER_SIGNATURE] = sha256(lower.identitySignature)
            values.remove(LEGACY_UPPER_ID_HINT)
            values.remove(LEGACY_LOWER_ID_HINT)
            values[VERIFIED] = verified
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private val BUILD_HASH = stringPreferencesKey("build_hash")
        private val UPPER_SIGNATURE = stringPreferencesKey("upper_signature")
        private val LOWER_SIGNATURE = stringPreferencesKey("lower_signature")
        private val LEGACY_UPPER_ID_HINT = intPreferencesKey("upper_id_hint")
        private val LEGACY_LOWER_ID_HINT = intPreferencesKey("lower_id_hint")
        private val VERIFIED = booleanPreferencesKey("verified")

        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
    }
}
