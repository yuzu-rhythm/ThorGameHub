package dev.thorhub.app.platform.storage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SnapshotContentPolicyTest {
    @Test
    fun `equal verified manifest is retained without another snapshot`() {
        assertThat(SnapshotContentPolicy.unchanged("sha256:same", "sha256:same")).isTrue()
    }

    @Test
    fun `missing or different parent requires a committed snapshot`() {
        assertThat(SnapshotContentPolicy.unchanged(null, "sha256:new")).isFalse()
        assertThat(SnapshotContentPolicy.unchanged("", "sha256:new")).isFalse()
        assertThat(SnapshotContentPolicy.unchanged("sha256:old", "sha256:new")).isFalse()
    }
}
