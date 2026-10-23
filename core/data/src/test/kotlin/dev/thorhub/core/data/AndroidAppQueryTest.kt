package dev.thorhub.core.data

import com.google.common.truth.Truth.assertThat
import android.content.Intent
import org.junit.Test

class AndroidAppQueryTest {
    @Test
    fun launcher_query_uses_main_and_launcher_without_broad_package_query() {
        assertThat(AndroidAppQuery.spec.action).isEqualTo(Intent.ACTION_MAIN)
        assertThat(AndroidAppQuery.spec.category).isEqualTo(Intent.CATEGORY_LAUNCHER)
        assertThat(AndroidAppQuery.spec.action).doesNotContain("QUERY_ALL_PACKAGES")
    }

    @Test
    fun stable_component_identifier_is_repeatable_and_self_package_is_excluded_by_contract() {
        val first = LibraryRecordIds.androidComponent("com.example.game", "com.example.game.MainActivity")
        val second = LibraryRecordIds.androidComponent("com.example.game", "com.example.game.MainActivity")

        assertThat(first).isEqualTo(second)
        assertThat(first).startsWith("android-app-")
        assertThat(first).doesNotContain("com.example.game")
    }
}
