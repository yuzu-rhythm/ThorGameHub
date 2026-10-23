package dev.thorhub.app.platform.storage

import com.google.common.truth.Truth.assertThat
import dev.thorhub.core.database.SaveSnapshotFileEntity
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class SnapshotPathPolicyTest {
    @Test
    fun relativePathRejectsTraversalAndAbsolutePaths() {
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotPathPolicy.validateRelativePath("../escape.sav")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotPathPolicy.validateRelativePath("/absolute.sav")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SnapshotPathPolicy.validateRelativePath("folder/../../escape.sav")
        }

        assertThat(SnapshotPathPolicy.validateRelativePath("slot/one.sav"))
            .containsExactly("slot", "one.sav")
            .inOrder()
    }

    @Test
    fun canonicalManifestDigestIsOrderIndependentButContentSensitive() {
        val first = file("b.sav", 2, "sha256:bb")
        val second = file("a.sav", 1, "sha256:aa")
        val digest = SnapshotPathPolicy.manifestDigest(listOf(first, second))

        assertThat(SnapshotPathPolicy.manifestDigest(listOf(second, first))).isEqualTo(digest)
        assertThat(SnapshotPathPolicy.manifestDigest(listOf(second.copy(sizeBytes = 3), first)))
            .isNotEqualTo(digest)
        assertThat(digest).startsWith("sha256:")
    }

    @Test
    fun canonicalContainmentRejectsSiblingPrefix() {
        val base = File(System.getProperty("java.io.tmpdir"), "thor-snapshot-root")
        SnapshotPathPolicy.requireInside(base, File(base, "child/file.sav"))

        assertThrows(IllegalArgumentException::class.java) {
            SnapshotPathPolicy.requireInside(base, File(base.parentFile, "thor-snapshot-root-evil/file.sav"))
        }
    }

    private fun file(path: String, size: Long, hash: String) = SaveSnapshotFileEntity(
        id = "file-$path",
        snapshotId = "snapshot-test",
        relativePath = path,
        sizeBytes = size,
        contentHash = hash,
    )
}
