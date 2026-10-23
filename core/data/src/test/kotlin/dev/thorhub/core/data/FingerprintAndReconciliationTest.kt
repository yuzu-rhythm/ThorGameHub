package dev.thorhub.core.data

import com.google.common.truth.Truth.assertThat
import dev.thorhub.core.model.CheapContentIdentity
import dev.thorhub.core.model.ContentFingerprint
import dev.thorhub.core.model.ContentHash
import dev.thorhub.core.model.FingerprintAlgorithm
import dev.thorhub.core.model.FingerprintRequest
import dev.thorhub.core.model.FingerprintTier
import dev.thorhub.core.model.FingerprintWork
import dev.thorhub.core.model.StagedFingerprintPolicy
import dev.thorhub.core.model.ResolutionResult
import dev.thorhub.core.model.ResolutionState
import org.junit.Test

class FingerprintAndReconciliationTest {
    @Test
    fun unchanged_multi_gigabyte_optical_content_reuses_quick_hash() {
        val identity = CheapContentIdentity(
            sourceKey = "content://provider/document/disc",
            sizeBytes = 8L * 1024L * 1024L * 1024L,
            extension = "iso",
            normalizedName = "disc-image",
        )
        val previous = ContentFingerprint(
            identity = identity,
            tier = FingerprintTier.QUICK,
            algorithm = FingerprintAlgorithm.QUICK_WINDOW,
            hash = ContentHash.of("quick-hash"),
        )

        val decision = StagedFingerprintPolicy.decide(identity, previous)

        assertThat(decision.work).isEqualTo(FingerprintWork.REUSE_EXISTING)
        assertThat(decision.reuse).isEqualTo(previous)
        assertThat(decision.avoidsFullHash).isTrue()
    }

    @Test
    fun full_hash_requires_explicit_expensive_work_permission() {
        val identity = CheapContentIdentity(
            sourceKey = "content://provider/document/cartridge",
            sizeBytes = 2L * 1024L * 1024L,
            extension = "gba",
            normalizedName = "cartridge",
        )

        val defaultDecision = StagedFingerprintPolicy.decide(
            identity,
            request = FingerprintRequest(preferFullForSmallContent = false),
        )
        val explicitDecision = StagedFingerprintPolicy.decide(
            identity,
            request = FingerprintRequest(
                requireFull = true,
                allowExpensiveWork = true,
                preferFullForSmallContent = false,
            ),
        )

        assertThat(defaultDecision.work).isEqualTo(FingerprintWork.COMPUTE_QUICK)
        assertThat(explicitDecision.work).isEqualTo(FingerprintWork.COMPUTE_FULL)
    }

    @Test
    fun revoked_scan_is_not_reconciled_and_maps_to_actionable_root_state() {
        assertThat(ScanReconciliationPolicy.shouldReconcile(false, "SAF_PERMISSION_REVOKED")).isFalse()
        assertThat(ScanReconciliationPolicy.shouldReconcile(true, "SAF_PERMISSION_REVOKED")).isFalse()
        assertThat(ScanReconciliationPolicy.rootStateForFailure("SAF_PERMISSION_REVOKED").name)
            .isEqualTo("REVOKED")
        assertThat(ScanReconciliationPolicy.rootStateForFailure("SAF_ROOT_UNAVAILABLE").name)
            .isEqualTo("STALE")
    }

    @Test
    fun unresolved_discovery_is_never_imported_as_available() {
        listOf(
            ResolutionState.NEEDS_REVIEW,
            ResolutionState.UNKNOWN,
            ResolutionState.UNSUPPORTED,
            ResolutionState.FAILED,
        ).forEach { state ->
            assertThat(ResolutionImportPolicy.availabilityFor(ResolutionResult(state)))
                .isEqualTo(dev.thorhub.core.database.InstallationAvailability.UNKNOWN)
        }
    }
}
