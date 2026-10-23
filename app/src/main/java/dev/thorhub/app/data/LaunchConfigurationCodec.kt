package dev.thorhub.app.data

import dev.thorhub.core.database.CompatibilityState
import dev.thorhub.core.database.LauncherAdapterType as DatabaseAdapterType
import dev.thorhub.core.database.LauncherEntity
import dev.thorhub.core.database.PlayMethodEntity
import dev.thorhub.core.model.ContentUriGrantMode
import dev.thorhub.core.model.DeviceId
import dev.thorhub.core.model.ExternalAppId
import dev.thorhub.core.model.LaunchExtraValue
import dev.thorhub.core.model.LaunchParameterPayload
import dev.thorhub.core.model.LaunchValidation
import dev.thorhub.core.model.LauncherAdapterType
import dev.thorhub.core.model.LauncherId
import dev.thorhub.core.model.LauncherProfile
import dev.thorhub.core.model.LauncherProfileSource
import dev.thorhub.core.model.PlayMethod
import dev.thorhub.core.model.PlayMethodId
import dev.thorhub.core.model.PlayMethodValidity
import dev.thorhub.core.model.InstallationId
import dev.thorhub.core.model.VerificationState
import org.json.JSONArray
import org.json.JSONObject

object LaunchConfigurationCodec {
    fun encode(payload: LaunchParameterPayload): String = JSONObject().apply {
        put("schemaVersion", 1)
        put("action", payload.action)
        put("data", payload.dataTemplate)
        put("component", payload.componentTemplate)
        put("grant", payload.contentUriGrant.name)
        put("extras", JSONObject().apply {
            payload.extras.forEach { (key, value) ->
                put(key, JSONObject().apply {
                    put("type", value.type.name)
                    when (value) {
                        is LaunchExtraValue.Text -> put("value", value.value)
                        is LaunchExtraValue.Integer -> put("value", value.value)
                        is LaunchExtraValue.BooleanValue -> put("value", value.value)
                        is LaunchExtraValue.TextList -> put("value", JSONArray(value.value))
                    }
                })
            }
        })
    }.toString()

    fun decode(raw: String): LaunchParameterPayload {
        val json = runCatching { JSONObject(raw) }.getOrElse { return LaunchParameterPayload() }
        val extras = buildMap {
            val values = json.optJSONObject("extras") ?: JSONObject()
            values.keys().forEach { key ->
                val encoded = values.optJSONObject(key) ?: return@forEach
                val value = when (encoded.optString("type")) {
                    "TEXT" -> LaunchExtraValue.Text(encoded.optString("value"))
                    "INTEGER" -> LaunchExtraValue.Integer(encoded.optLong("value"))
                    "BOOLEAN" -> LaunchExtraValue.BooleanValue(encoded.optBoolean("value"))
                    "TEXT_LIST" -> LaunchExtraValue.TextList(
                        encoded.optJSONArray("value")?.let { array ->
                            List(array.length()) { index -> array.optString(index) }
                        }.orEmpty(),
                    )
                    else -> null
                }
                if (value != null) put(key, value)
            }
        }
        return LaunchParameterPayload(
            action = json.optNullableString("action"),
            dataTemplate = json.optNullableString("data"),
            componentTemplate = json.optNullableString("component"),
            extras = extras,
            contentUriGrant = runCatching {
                ContentUriGrantMode.valueOf(json.optString("grant", ContentUriGrantMode.NONE.name))
            }.getOrDefault(ContentUriGrantMode.NONE),
        )
    }

    fun toDomain(method: PlayMethodEntity): PlayMethod = PlayMethod(
        id = PlayMethodId.of(method.id),
        installationId = InstallationId.of(requireNotNull(method.installationId)),
        deviceId = DeviceId.of(requireNotNull(method.deviceId)),
        launcherId = LauncherId.of(requireNotNull(method.launcherId)),
        launchParameters = decode(method.configurationJson),
        priority = method.priority,
        preferred = method.isPreferred,
        validation = LaunchValidation(
            validity = when (method.compatibilityState) {
                CompatibilityState.READY -> PlayMethodValidity.VALID
                CompatibilityState.NEEDS_ACTION, CompatibilityState.ERROR -> PlayMethodValidity.INVALID
                CompatibilityState.UNSUPPORTED -> PlayMethodValidity.UNSUPPORTED
                CompatibilityState.UNKNOWN -> PlayMethodValidity.UNKNOWN
            },
            reasonCode = method.lastFailureCode,
        ),
        lastFailureCode = method.lastFailureCode,
    )

    fun toDomain(launcher: LauncherEntity, payload: LaunchParameterPayload): LauncherProfile = LauncherProfile(
        id = LauncherId.of(launcher.id),
        displayName = launcher.displayName,
        adapterType = launcher.adapterType.toModel(),
        packageCandidates = parseStringArray(launcher.packageCandidatesJson).map(ExternalAppId::of),
        componentCandidates = parseStringArray(launcher.componentCandidatesJson),
        launchParameters = payload,
        source = LauncherProfileSource.USER_DEFINED,
        verification = when (launcher.verificationStatus.name) {
            "VERIFIED" -> VerificationState.VERIFIED
            "UNVERIFIED" -> VerificationState.UNVERIFIED
            "ERROR" -> VerificationState.FAILED
            else -> VerificationState.UNKNOWN
        },
        verifiedAt = launcher.verifiedAt?.let(java.time.Instant::ofEpochMilli),
    )

    fun stringArray(values: Iterable<String>): String = JSONArray(values.toList()).toString()

    private fun parseStringArray(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        List(array.length()) { array.optString(it) }.filter(String::isNotBlank)
    }.getOrDefault(emptyList())

    private fun JSONObject.optNullableString(key: String): String? =
        optString(key).takeUnless { isNull(key) || it.isBlank() }

    private fun DatabaseAdapterType.toModel(): LauncherAdapterType = when (this) {
        DatabaseAdapterType.ANDROID_NATIVE -> LauncherAdapterType.ANDROID_NATIVE
        DatabaseAdapterType.RETROARCH -> LauncherAdapterType.RETROARCH
        DatabaseAdapterType.GAMENATIVE -> LauncherAdapterType.GAME_NATIVE
        DatabaseAdapterType.CONFIGURABLE_EXTERNAL, DatabaseAdapterType.OTHER -> LauncherAdapterType.CONFIGURABLE_EXTERNAL
        DatabaseAdapterType.UNKNOWN -> LauncherAdapterType.UNKNOWN
    }
}

data class ResolvedPlayMethod(
    val method: PlayMethod,
    val profile: LauncherProfile,
    val gameId: String,
    val gameVersionId: String,
)
