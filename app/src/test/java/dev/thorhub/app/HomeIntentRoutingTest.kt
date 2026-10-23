package dev.thorhub.app

import android.content.Intent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeIntentRoutingTest {
    @Test
    fun `Android HOME intent returns the shell to Hub Home`() {
        assertThat(
            shouldRouteToHubHome(
                action = Intent.ACTION_MAIN,
                categories = setOf(Intent.CATEGORY_HOME),
                explicitLogicalUpperRoute = false,
            ),
        ).isTrue()
    }

    @Test
    fun `explicit logical upper route returns the shell to Hub Home`() {
        assertThat(
            shouldRouteToHubHome(
                action = null,
                categories = null,
                explicitLogicalUpperRoute = true,
            ),
        ).isTrue()
    }

    @Test
    fun `launcher icon and unrelated intents preserve the current Hub screen`() {
        assertThat(
            shouldRouteToHubHome(
                action = Intent.ACTION_MAIN,
                categories = setOf(Intent.CATEGORY_LAUNCHER),
                explicitLogicalUpperRoute = false,
            ),
        ).isFalse()
        assertThat(
            shouldRouteToHubHome(
                action = Intent.ACTION_VIEW,
                categories = setOf(Intent.CATEGORY_HOME),
                explicitLogicalUpperRoute = false,
            ),
        ).isFalse()
    }
}
