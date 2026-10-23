package dev.thorhub.core.data

import com.google.common.truth.Truth.assertThat
import dev.thorhub.core.model.ScanRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DeterministicFixtureCatalogTest {
    @Test
    fun fixture_catalog_scales_to_required_sizes_without_real_content() {
        listOf(100, 1_000, 5_000).forEach { count ->
            val catalog = DeterministicFixtureCatalogGenerator.generate(count, seed = "scale-test")
            assertThat(catalog.items).hasSize(count)
            assertThat(catalog.items.map { it.reference.value }).doesNotContain("/Android/data")
            assertThat(catalog.items.map { it.reference.value }).doesNotContain("bios")
            assertThat(catalog.items.map { it.reference.value }).containsNoDuplicates()
        }
    }

    @Test
    fun same_seed_and_count_produce_identical_ids_and_order() {
        val first = DeterministicFixtureCatalogGenerator.generate(1_000, seed = "repeatable")
        val second = DeterministicFixtureCatalogGenerator.generate(1_000, seed = "repeatable")

        assertThat(first.root.id).isEqualTo(second.root.id)
        assertThat(first.items).isEqualTo(second.items)
    }

    @Test
    fun fixture_source_streams_started_items_and_completed_count() = runTest {
        val catalog = DeterministicFixtureCatalogGenerator.generate(100, seed = "flow-test")
        val events = DeterministicFixtureLibrarySource(catalog).scan(ScanRequest()).toList()

        assertThat(events.first()).isInstanceOf(dev.thorhub.core.model.ScanEvent.Started::class.java)
        assertThat(events.filterIsInstance<dev.thorhub.core.model.ScanEvent.ItemDiscovered>()).hasSize(100)
        assertThat(events.last()).isEqualTo(
            dev.thorhub.core.model.ScanEvent.Completed(
                itemCount = 100,
                emittedAt = events.last().emittedAt,
            ),
        )
    }
}
