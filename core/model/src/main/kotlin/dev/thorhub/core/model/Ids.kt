package dev.thorhub.core.model

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * IDs are deliberately opaque to the domain.  The core never needs to know
 * whether a storage implementation uses UUIDs, ULIDs, database integers, or a
 * deterministic key; it only needs a stable, validated string.
 */
private object IdRules {
    private val valid = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

    fun validate(type: String, value: String): String {
        require(value == value.trim()) { "$type ID must not have leading or trailing whitespace" }
        require(value.isNotEmpty()) { "$type ID must not be empty" }
        require(valid.matches(value)) { "$type ID contains unsupported characters: $value" }
        return value
    }

    fun deterministic(prefix: String, namespace: String, name: String): String {
        require(namespace.isNotBlank()) { "namespace must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        val input = "$namespace\u0000$name".toByteArray(StandardCharsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        val encoded = digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
        return "$prefix-${encoded.take(40)}"
    }
}

private fun deterministicId(prefix: String, namespace: String, name: String): String =
    IdRules.deterministic(prefix, namespace, name)

@JvmInline
value class GameId(val value: String) {
    init {
        IdRules.validate("Game", value)
    }

    companion object {
        fun of(value: String): GameId = GameId(value)
        fun deterministic(namespace: String, name: String): GameId =
            GameId(deterministicId("game", namespace, name))
    }
}

@JvmInline
value class GameVersionId(val value: String) {
    init {
        IdRules.validate("GameVersion", value)
    }

    companion object {
        fun of(value: String): GameVersionId = GameVersionId(value)
        fun deterministic(namespace: String, name: String): GameVersionId =
            GameVersionId(deterministicId("version", namespace, name))
    }
}

@JvmInline
value class DeviceId(val value: String) {
    init {
        IdRules.validate("Device", value)
    }

    companion object {
        fun of(value: String): DeviceId = DeviceId(value)
        fun deterministic(namespace: String, name: String): DeviceId =
            DeviceId(deterministicId("device", namespace, name))
    }
}

@JvmInline
value class InstallationId(val value: String) {
    init {
        IdRules.validate("Installation", value)
    }

    companion object {
        fun of(value: String): InstallationId = InstallationId(value)
        fun deterministic(namespace: String, name: String): InstallationId =
            InstallationId(deterministicId("installation", namespace, name))
    }
}

@JvmInline
value class LauncherId(val value: String) {
    init {
        IdRules.validate("Launcher", value)
    }

    companion object {
        fun of(value: String): LauncherId = LauncherId(value)
        fun deterministic(namespace: String, name: String): LauncherId =
            LauncherId(deterministicId("launcher", namespace, name))
    }
}

@JvmInline
value class PlayMethodId(val value: String) {
    init {
        IdRules.validate("PlayMethod", value)
    }

    companion object {
        fun of(value: String): PlayMethodId = PlayMethodId(value)
        fun deterministic(namespace: String, name: String): PlayMethodId =
            PlayMethodId(deterministicId("play", namespace, name))
    }
}

@JvmInline
value class GameSessionId(val value: String) {
    init {
        IdRules.validate("GameSession", value)
    }

    companion object {
        fun of(value: String): GameSessionId = GameSessionId(value)
        fun deterministic(namespace: String, name: String): GameSessionId =
            GameSessionId(deterministicId("session", namespace, name))
    }
}

@JvmInline
value class NoteId(val value: String) {
    init {
        IdRules.validate("Note", value)
    }

    companion object {
        fun of(value: String): NoteId = NoteId(value)
        fun deterministic(namespace: String, name: String): NoteId =
            NoteId(deterministicId("note", namespace, name))
    }
}

@JvmInline
value class ScreenshotId(val value: String) {
    init {
        IdRules.validate("Screenshot", value)
    }

    companion object {
        fun of(value: String): ScreenshotId = ScreenshotId(value)
        fun deterministic(namespace: String, name: String): ScreenshotId =
            ScreenshotId(deterministicId("screenshot", namespace, name))
    }
}

@JvmInline
value class SaveProfileId(val value: String) {
    init {
        IdRules.validate("SaveProfile", value)
    }

    companion object {
        fun of(value: String): SaveProfileId = SaveProfileId(value)
        fun deterministic(namespace: String, name: String): SaveProfileId =
            SaveProfileId(deterministicId("save-profile", namespace, name))
    }
}

@JvmInline
value class SaveSnapshotId(val value: String) {
    init {
        IdRules.validate("SaveSnapshot", value)
    }

    companion object {
        fun of(value: String): SaveSnapshotId = SaveSnapshotId(value)
        fun deterministic(namespace: String, name: String): SaveSnapshotId =
            SaveSnapshotId(deterministicId("save-snapshot", namespace, name))
    }
}

@JvmInline
value class CollectionId(val value: String) {
    init {
        IdRules.validate("Collection", value)
    }

    companion object {
        fun of(value: String): CollectionId = CollectionId(value)
        fun deterministic(namespace: String, name: String): CollectionId =
            CollectionId(deterministicId("collection", namespace, name))
    }
}

@JvmInline
value class LibraryRootId(val value: String) {
    init {
        IdRules.validate("LibraryRoot", value)
    }

    companion object {
        fun of(value: String): LibraryRootId = LibraryRootId(value)
        fun deterministic(namespace: String, name: String): LibraryRootId =
            LibraryRootId(deterministicId("root", namespace, name))
    }
}

@JvmInline
value class ArtworkAssetId(val value: String) {
    init {
        IdRules.validate("ArtworkAsset", value)
    }

    companion object {
        fun of(value: String): ArtworkAssetId = ArtworkAssetId(value)
        fun deterministic(namespace: String, name: String): ArtworkAssetId =
            ArtworkAssetId(deterministicId("artwork", namespace, name))
    }
}

@JvmInline
value class CapabilityRecordId(val value: String) {
    init {
        IdRules.validate("CapabilityRecord", value)
    }

    companion object {
        fun of(value: String): CapabilityRecordId = CapabilityRecordId(value)
        fun deterministic(namespace: String, name: String): CapabilityRecordId =
            CapabilityRecordId(deterministicId("capability", namespace, name))
    }
}

@JvmInline
value class DiagnosticEventId(val value: String) {
    init {
        IdRules.validate("DiagnosticEvent", value)
    }

    companion object {
        fun of(value: String): DiagnosticEventId = DiagnosticEventId(value)
        fun deterministic(namespace: String, name: String): DiagnosticEventId =
            DiagnosticEventId(deterministicId("diagnostic", namespace, name))
    }
}

@JvmInline
value class DisplayId(val value: String) {
    init {
        IdRules.validate("Display", value)
    }

    companion object {
        fun of(value: String): DisplayId = DisplayId(value)
        fun deterministic(namespace: String, name: String): DisplayId =
            DisplayId(deterministicId("display", namespace, name))
    }
}

@JvmInline
value class LibrarySourceId(val value: String) {
    init {
        IdRules.validate("LibrarySource", value)
    }

    companion object {
        fun of(value: String): LibrarySourceId = LibrarySourceId(value)
    }
}

@JvmInline
value class LauncherAdapterId(val value: String) {
    init {
        IdRules.validate("LauncherAdapter", value)
    }

    companion object {
        fun of(value: String): LauncherAdapterId = LauncherAdapterId(value)
    }
}

@JvmInline
value class SaveAdapterId(val value: String) {
    init {
        IdRules.validate("SaveAdapter", value)
    }

    companion object {
        fun of(value: String): SaveAdapterId = SaveAdapterId(value)
    }
}

@JvmInline
value class SyncTransportId(val value: String) {
    init {
        IdRules.validate("SyncTransport", value)
    }

    companion object {
        fun of(value: String): SyncTransportId = SyncTransportId(value)
    }
}

@JvmInline
value class MetadataProviderId(val value: String) {
    init {
        IdRules.validate("MetadataProvider", value)
    }

    companion object {
        fun of(value: String): MetadataProviderId = MetadataProviderId(value)
    }
}

@JvmInline
value class ArtworkProviderId(val value: String) {
    init {
        IdRules.validate("ArtworkProvider", value)
    }

    companion object {
        fun of(value: String): ArtworkProviderId = ArtworkProviderId(value)
    }
}

@JvmInline
value class ExternalAppId(val value: String) {
    init {
        IdRules.validate("ExternalApp", value)
    }

    companion object {
        fun of(value: String): ExternalAppId = ExternalAppId(value)
    }
}

@JvmInline
value class ContentHash(val value: String) {
    init {
        require(value == value.trim()) { "Content hash must not have surrounding whitespace" }
        require(value.isNotEmpty()) { "Content hash must not be empty" }
        require(value.length <= 256) { "Content hash is too long" }
        require(value.matches(Regex("[A-Za-z0-9:_-]+"))) { "Content hash contains unsupported characters" }
    }

    companion object {
        fun of(value: String): ContentHash = ContentHash(value)
    }
}

@JvmInline
value class BuildFingerprint(val value: String) {
    init {
        require(value == value.trim()) { "Build fingerprint must not have surrounding whitespace" }
        require(value.isNotEmpty()) { "Build fingerprint must not be empty" }
        require(value.length <= 512) { "Build fingerprint is too long" }
    }

    companion object {
        fun of(value: String): BuildFingerprint = BuildFingerprint(value)
    }
}

/** A stable, deterministic source key useful for fixture and migration tests. */
object DeterministicIds {
    fun game(namespace: String, name: String): GameId = GameId.deterministic(namespace, name)
    fun gameVersion(namespace: String, name: String): GameVersionId =
        GameVersionId.deterministic(namespace, name)
    fun device(namespace: String, name: String): DeviceId = DeviceId.deterministic(namespace, name)
    fun installation(namespace: String, name: String): InstallationId =
        InstallationId.deterministic(namespace, name)
    fun launcher(namespace: String, name: String): LauncherId = LauncherId.deterministic(namespace, name)
    fun playMethod(namespace: String, name: String): PlayMethodId =
        PlayMethodId.deterministic(namespace, name)
    fun session(namespace: String, name: String): GameSessionId =
        GameSessionId.deterministic(namespace, name)
    fun note(namespace: String, name: String): NoteId = NoteId.deterministic(namespace, name)
    fun screenshot(namespace: String, name: String): ScreenshotId = ScreenshotId.deterministic(namespace, name)
    fun saveProfile(namespace: String, name: String): SaveProfileId =
        SaveProfileId.deterministic(namespace, name)
    fun saveSnapshot(namespace: String, name: String): SaveSnapshotId =
        SaveSnapshotId.deterministic(namespace, name)
    fun collection(namespace: String, name: String): CollectionId = CollectionId.deterministic(namespace, name)
    fun libraryRoot(namespace: String, name: String): LibraryRootId =
        LibraryRootId.deterministic(namespace, name)
    fun artworkAsset(namespace: String, name: String): ArtworkAssetId =
        ArtworkAssetId.deterministic(namespace, name)
    fun capabilityRecord(namespace: String, name: String): CapabilityRecordId =
        CapabilityRecordId.deterministic(namespace, name)
    fun diagnosticEvent(namespace: String, name: String): DiagnosticEventId =
        DiagnosticEventId.deterministic(namespace, name)
}

/**
 * A production caller can use this generator when a database needs a fresh
 * identifier.  Tests should prefer [DeterministicIds] so generated values are
 * reproducible and do not depend on a platform UUID implementation.
 */
class SecureRandomIdGenerator(
    private val random: SecureRandom = SecureRandom(),
) {
    fun next(prefix: String): String {
        require(prefix.matches(Regex("[a-z][a-z0-9-]{0,31}"))) { "Invalid ID prefix" }
        val bytes = ByteArray(20)
        random.nextBytes(bytes)
        val suffix = bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
        return IdRules.validate(prefix, "$prefix-$suffix")
    }
}
