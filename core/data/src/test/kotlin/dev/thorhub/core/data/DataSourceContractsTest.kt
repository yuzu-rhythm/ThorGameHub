package dev.thorhub.core.data

import com.google.common.truth.Truth.assertThat
import dev.thorhub.core.model.GamePlatform
import org.junit.Test

class DataSourceContractsTest {
    @Test
    fun root_specific_extensions_override_platform_defaults() {
        assertThat(
            RomExtensionPolicy.accepts(
                displayName = "Super.NES",
                platform = GamePlatform.NINTENDO,
                rootExtensions = setOf(".NES"),
            ),
        ).isTrue()
        assertThat(
            RomExtensionPolicy.accepts(
                displayName = "disc.iso",
                platform = GamePlatform.NINTENDO,
                rootExtensions = setOf(".NES"),
            ),
        ).isFalse()
        assertThat(
            RomExtensionPolicy.accepts(
                displayName = "disc.iso",
                platform = GamePlatform.PLAYSTATION,
                rootExtensions = emptySet(),
            ),
        ).isTrue()
        assertThat(RomExtensionPolicy.extensionOf("README")).isNull()
    }

    @Test
    fun document_identifiers_are_hashed_for_diagnostics() {
        val raw = "content://com.example.documents/document/private-id"
        val redacted = DocumentIdentifierRedactor.redact(raw)

        assertThat(redacted).startsWith("sha256:")
        assertThat(redacted).doesNotContain("content://")
        assertThat(redacted).doesNotContain("private-id")
        assertThat(redacted).isEqualTo(DocumentIdentifierRedactor.redact(raw))
    }

    @Test
    fun stable_ids_are_derived_from_source_identity_and_do_not_change_with_title() {
        val original = fixtureItem("Original title")
        val renamed = original.copy(titleHint = "User renamed title")

        assertThat(LibraryRecordIds.installation(original)).isEqualTo(LibraryRecordIds.installation(renamed))
        assertThat(LibraryRecordIds.version(original)).isEqualTo(LibraryRecordIds.version(renamed))
        assertThat(LibraryRecordIds.game(original)).isEqualTo(LibraryRecordIds.game(renamed))
    }

    private fun fixtureItem(title: String) = DeterministicFixtureCatalogGenerator
        .generate(1, seed = "identity-test")
        .items
        .single()
        .copy(titleHint = title)
}
