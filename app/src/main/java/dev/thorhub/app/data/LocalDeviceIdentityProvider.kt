package dev.thorhub.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thorhub.core.database.DeviceAvailabilityState
import dev.thorhub.core.database.DeviceEntity
import dev.thorhub.core.database.PairingState
import dev.thorhub.core.model.DeterministicIds
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app-private identity for this physical Android endpoint.
 *
 * Android build fingerprints change after firmware updates, so they are not a
 * safe primary key for a Device. The random token below survives process and
 * firmware restarts inside this app installation, never leaves private app
 * storage, and is persisted only as a one-way hash in Room.
 */
// Identity materialization must complete before an ID is exposed. A checked,
// synchronous commit prevents one process from returning an identity that a
// later process cannot recover; the private token itself is never stored in Room.
@SuppressLint("ApplySharedPref", "UseKtx")
@Singleton
class LocalDeviceIdentityProvider @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val identityHash: String by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        sha256(getOrCreatePrivateToken())
    }

    private val firstSeenAt: Long by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        synchronized(preferences) {
            preferences.getLong(KEY_FIRST_SEEN_AT, 0L).takeIf { it > 0L }
                ?: System.currentTimeMillis().also { createdAt ->
                    check(preferences.edit().putLong(KEY_FIRST_SEEN_AT, createdAt).commit()) {
                        "Unable to persist the local device creation time"
                    }
                }
        }
    }

    val deviceId: String
        get() = DeterministicIds.device(NAMESPACE, identityHash).value

    fun current(at: Long): DeviceEntity = DeviceEntity(
        id = deviceId,
        displayName = "This Android device",
        platform = "ANDROID",
        osVersion = Build.VERSION.RELEASE,
        hardwareFingerprint = identityHash,
        availabilityState = DeviceAvailabilityState.AVAILABLE,
        pairingState = PairingState.TRUSTED,
        lastSeenAt = at,
        createdAt = firstSeenAt.coerceAtMost(at),
        updatedAt = at,
    )

    private fun getOrCreatePrivateToken(): String = synchronized(preferences) {
        preferences.getString(KEY_PRIVATE_TOKEN, null)?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString().also { token ->
                check(preferences.edit().putString(KEY_PRIVATE_TOKEN, token).commit()) {
                    "Unable to persist the local device identity"
                }
            }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val PREFERENCES_NAME = "thor_local_device_identity"
        const val KEY_PRIVATE_TOKEN = "private_token"
        const val KEY_FIRST_SEEN_AT = "first_seen_at"
        const val NAMESPACE = "thor-local-install"
    }
}
