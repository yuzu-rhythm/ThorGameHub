package dev.thorhub.app.platform.display

/**
 * Resolves logical upper/lower roles from an Android display snapshot.
 *
 * This contains no calls into DisplayManager so assignment and firmware-change
 * behavior can be exhaustively tested on the host. Physical panel placement is
 * deliberately not inferred here; that still requires the user's visual
 * confirmation on the target device.
 */
internal object DisplayTopologyResolver {
    fun resolve(
        displays: List<DisplayDescriptor>,
        stored: StoredDisplayAssignment?,
        pending: StoredDisplayAssignment? = null,
        firmwareFingerprintHash: String,
    ): DisplayTopology {
        val active = displays.filterNot { it.stateLabel == "OFF" }
        val verifiedStored = stored?.takeIf { it.verified }
        val selectedAssignment = pending ?: verifiedStored
        val assignedUpper = selectedAssignment?.let { assignment ->
            active.firstOrNull {
                DisplayAssignmentStore.sha256(it.identitySignature) == assignment.upperSignatureHash
            }
        }
        val assignedLower = selectedAssignment?.let { assignment ->
            active.firstOrNull {
                DisplayAssignmentStore.sha256(it.identitySignature) == assignment.lowerSignatureHash
            }
        }
        val assignmentValid = assignedUpper != null && assignedLower != null && assignedUpper.id != assignedLower.id

        val upper = if (assignmentValid) {
            assignedUpper
        } else {
            active.firstOrNull { it.isDefault } ?: active.maxByOrNull { it.area }
        }
        val lower = if (assignmentValid) {
            assignedLower
        } else {
            val nonUpper = active.filter { it.id != upper?.id }
            nonUpper.filter { it.typeLabel == "INTERNAL" }.minByOrNull { it.area }
                ?: nonUpper.minByOrNull { it.area }
        }
        val mode = when {
            upper == null -> TopologyMode.DEGRADED
            lower == null -> TopologyMode.SINGLE_DISPLAY
            else -> TopologyMode.DUAL_DISPLAY
        }
        val assigned = displays.map { descriptor ->
            descriptor.copy(
                role = when (descriptor.id) {
                    upper?.id -> DisplayRole.UPPER_CANDIDATE
                    lower?.id -> DisplayRole.LOWER_CANDIDATE
                    else -> DisplayRole.UNASSIGNED
                },
            )
        }

        return DisplayTopology(
            displays = assigned,
            upperDisplayId = upper?.id,
            lowerDisplayId = lower?.id,
            mode = mode,
            assignmentVerified = pending == null && verifiedStored != null && assignmentValid,
            firmwareFingerprintHash = firmwareFingerprintHash,
        )
    }
}
