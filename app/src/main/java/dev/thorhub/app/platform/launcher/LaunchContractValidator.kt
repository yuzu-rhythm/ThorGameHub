package dev.thorhub.app.platform.launcher

import dev.thorhub.core.model.LaunchExtraValue
import dev.thorhub.core.model.LaunchParameterPayload
import dev.thorhub.core.model.LauncherAdapterType

data class LaunchContractIssue(
    val code: String,
    val explanation: String,
)

object LaunchContractValidator {
    fun validate(type: LauncherAdapterType, payload: LaunchParameterPayload): List<LaunchContractIssue> = buildList {
        val componentTemplate = payload.componentTemplate
        if (componentTemplate.isNullOrBlank()) {
            add(LaunchContractIssue("component_required", "Choose an installed exported activity."))
        } else if (!componentTemplate.contains('/')) {
            add(LaunchContractIssue("component_malformed", "Use a flattened package/activity component."))
        }

        when (type) {
            LauncherAdapterType.ANDROID_NATIVE -> {
                if (payload.action != null && payload.action != "android.intent.action.MAIN") {
                    add(LaunchContractIssue("android_action", "Android Native requires ACTION_MAIN or no action."))
                }
            }

            LauncherAdapterType.RETROARCH -> {
                val rom = payload.extras["ROM"] as? LaunchExtraValue.Text
                val core = payload.extras["LIBRETRO"] as? LaunchExtraValue.Text
                if (rom == null || !rom.value.startsWith("content://")) {
                    add(LaunchContractIssue("retroarch_rom", "Select a ROM through SAF so RetroArch receives a content URI."))
                }
                if (core == null || core.value.isBlank()) {
                    add(LaunchContractIssue("retroarch_core", "Choose an installed RetroArch core."))
                }
            }

            LauncherAdapterType.GAME_NATIVE -> {
                if (payload.action != LauncherProfiles.GAME_NATIVE_ACTION) {
                    add(LaunchContractIssue("gamenative_action", "The GameNative launch action does not match the built-in candidate contract."))
                }
                val appId = payload.extras["app_id"] as? LaunchExtraValue.Integer
                if (appId == null || appId.value !in 1..Int.MAX_VALUE.toLong()) {
                    add(LaunchContractIssue("gamenative_app_id", "Enter a positive GameNative App ID."))
                }
                val source = (payload.extras["game_source"] as? LaunchExtraValue.Text)?.value
                if (source == null || GameNativeSource.entries.none { it.name == source.uppercase() }) {
                    add(LaunchContractIssue("gamenative_source", "Choose a supported GameNative source."))
                }
            }

            LauncherAdapterType.CONFIGURABLE_EXTERNAL -> {
                if (payload.action == null && payload.dataTemplate == null && payload.extras.isEmpty()) {
                    add(LaunchContractIssue("external_empty_template", "Configure an action, data value, or extras before test launch."))
                }
            }

            LauncherAdapterType.UNKNOWN -> add(
                LaunchContractIssue("unknown_adapter", "Choose a concrete launcher adapter."),
            )
        }
    }
}

/** Parses one editable `key:type=value` extra per line without evaluating code or shell syntax. */
object TypedExtrasTemplateParser {
    data class Result(val extras: Map<String, LaunchExtraValue>, val issues: List<LaunchContractIssue>)

    fun parse(raw: String): Result {
        val extras = linkedMapOf<String, LaunchExtraValue>()
        val issues = mutableListOf<LaunchContractIssue>()
        raw.lineSequence().map(String::trim).filter(String::isNotBlank).forEachIndexed { index, line ->
            val keyAndValue = line.split('=', limit = 2)
            if (keyAndValue.size != 2) {
                issues += LaunchContractIssue("extra_line_${index + 1}", "Use key:type=value on line ${index + 1}.")
                return@forEachIndexed
            }
            val keyAndType = keyAndValue[0].split(':', limit = 2)
            val key = keyAndType[0].trim()
            val type = keyAndType.getOrElse(1) { "text" }.trim().lowercase()
            val value = keyAndValue[1].trim()
            if (key.isBlank()) {
                issues += LaunchContractIssue("extra_key_${index + 1}", "Extra key cannot be blank.")
                return@forEachIndexed
            }
            val parsed = when (type) {
                "text" -> LaunchExtraValue.Text(value)
                "int", "integer" -> value.toLongOrNull()?.let(LaunchExtraValue::Integer)
                "bool", "boolean" -> when (value.lowercase()) {
                    "true" -> LaunchExtraValue.BooleanValue(true)
                    "false" -> LaunchExtraValue.BooleanValue(false)
                    else -> null
                }
                "list" -> LaunchExtraValue.TextList(value.split(',').map(String::trim).filter(String::isNotBlank))
                else -> null
            }
            if (parsed == null) {
                issues += LaunchContractIssue("extra_value_${index + 1}", "Unsupported type or value on line ${index + 1}.")
            } else {
                extras[key] = parsed
            }
        }
        return Result(extras, issues)
    }
}
