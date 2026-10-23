package dev.thorhub.app.platform.safety

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CrashLoopPolicyTest {
    @Test
    fun `three crashes inside window enable safe mode`() {
        val now = 1_000_000L

        assertThat(
            CrashLoopPolicy.shouldUseSafeMode(
                listOf(now - 90_000L, now - 30_000L, now - 1_000L),
                now,
            ),
        ).isTrue()
    }

    @Test
    fun `old future and malformed timing evidence are excluded`() {
        val now = 1_000_000L

        assertThat(
            CrashLoopPolicy.recentCrashes(
                listOf(now - 500_000L, now - 10_000L, now + 1L),
                now,
            ),
        ).containsExactly(now - 10_000L)
        assertThat(CrashLoopPolicy.shouldUseSafeMode(listOf(now - 10_000L), now)).isFalse()
    }
}
