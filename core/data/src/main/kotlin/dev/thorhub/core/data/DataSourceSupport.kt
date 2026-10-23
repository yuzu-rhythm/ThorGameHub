package dev.thorhub.core.data

import android.net.Uri
import dev.thorhub.core.model.CheapContentIdentity
import dev.thorhub.core.model.DeterministicIds
import dev.thorhub.core.model.DiscoveredItem
import dev.thorhub.core.model.GamePlatform
import dev.thorhub.core.model.GameVersionId
import dev.thorhub.core.model.GameId
import dev.thorhub.core.model.InstallationId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/**
 * Extension rules are deliberately kept outside the Android scanner so that
 * each selected root can be tested without a device or a DocumentsProvider.
 */
object RomExtensionPolicy {
    private val allKnownExtensions = setOf(
        "3ds",
        "7z",
        "bin",
        "ccd",
        "chd",
        "cso",
        "cue",
        "ecm",
        "gcz",
        "gba",
        "gbc",
        "gg",
        "img",
        "iso",
        "md",
        "nds",
        "nes",
        "n64",
        "pbp",
        "rom",
        "rvz",
        "zip",
        "sfc",
        "smc",
        "vb",
        "wbfs",
        "xci",
        "xiso",
        "z64",
    )

    private val defaultsByPlatform = mapOf(
        GamePlatform.NINTENDO to setOf(
            "3ds",
            "gba",
            "gbc",
            "nds",
            "n64",
            "nes",
            "sfc",
            "smc",
            "xci",
            "z64",
        ),
        GamePlatform.PLAYSTATION to setOf("bin", "ccd", "chd", "cue", "cso", "ecm", "img", "iso", "pbp"),
        GamePlatform.XBOX to setOf("iso", "xiso"),
        GamePlatform.ARCADE to setOf("7z", "chd", "zip"),
        GamePlatform.PC to setOf("7z", "chd", "iso", "rom"),
    )

    fun normalize(extension: String): String = extension
        .trim()
        .trimStart('.')
        .lowercase(Locale.US)

    fun extensionOf(displayName: String?): String? = displayName
        ?.substringAfterLast('.', missingDelimiterValue = "")
        ?.takeIf { it.isNotBlank() }
        ?.let(::normalize)

    fun effectiveExtensions(platform: GamePlatform, rootExtensions: Set<String>): Set<String> {
        val explicit = rootExtensions
            .map(::normalize)
            .filter(String::isNotBlank)
            .toSet()
        return explicit.ifEmpty { defaultsByPlatform[platform] ?: allKnownExtensions }
    }

    fun accepts(
        displayName: String?,
        platform: GamePlatform,
        rootExtensions: Set<String>,
    ): Boolean {
        val extension = extensionOf(displayName) ?: return false
        return extension in effectiveExtensions(platform, rootExtensions)
    }
}

/**
 * A single place for IDs used by all Gate 2 sources.  The input may contain a
 * granted URI, but only the deterministic digest is exposed as an ID.
 */
object LibraryRecordIds {
    fun itemKey(item: DiscoveredItem): String =
        "${item.sourceType.name.lowercase(Locale.US)}\u0000${item.reference.value}"

    fun game(item: DiscoveredItem): GameId =
        DeterministicIds.game("library-${item.sourceType.name.lowercase(Locale.US)}", itemKey(item))

    fun version(item: DiscoveredItem): GameVersionId =
        DeterministicIds.gameVersion("library-${item.sourceType.name.lowercase(Locale.US)}", itemKey(item))

    fun installation(item: DiscoveredItem): InstallationId =
        DeterministicIds.installation("library-${item.sourceType.name.lowercase(Locale.US)}", itemKey(item))

    fun androidComponent(packageName: String, activityName: String): String =
        "android-app-${sha256Hex("$packageName/$activityName").take(40)}"

    fun fingerprint(identity: CheapContentIdentity, algorithm: String, value: String): String =
        "fingerprint-${sha256Hex("${identity.sourceKey}\u0000$algorithm\u0000$value").take(40)}"

    fun sha256Hex(value: String): String = sha256Hex(value.toByteArray(StandardCharsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte)
        }
}

/**
 * URI/document references can be retained for an actual launch or provider
 * operation, but diagnostics must use this redacted representation.
 */
object DocumentIdentifierRedactor {
    fun redact(value: String): String = "sha256:${LibraryRecordIds.sha256Hex(value)}"

    fun redact(uri: Uri): String = redact(uri.toString())
}

object ContentNamePolicy {
    fun normalize(displayName: String?): String {
        val name = displayName.orEmpty()
            .substringBeforeLast('.', missingDelimiterValue = displayName.orEmpty())
            .replace(Regex("[_-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return name.ifBlank { "Unknown game" }
    }
}
