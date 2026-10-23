package dev.thorhub.core.model

import java.time.Duration
import java.time.Instant

object DisplaySelectionPolicy {
    fun select(topology: DisplayTopology): DisplaySelection =
        select(
            displays = topology.displays,
            currentBuildFingerprint = topology.buildFingerprint,
            verifiedAssignment = topology.verifiedAssignment,
        )

    fun select(
        displays: List<DisplayDescriptor>,
        verifiedAssignment: VerifiedDisplayAssignment? = null,
    ): DisplaySelection = select(
        displays = displays,
        currentBuildFingerprint = verifiedAssignment?.buildFingerprint,
        verifiedAssignment = verifiedAssignment,
    )

    fun select(
        displays: List<DisplayDescriptor>,
        currentBuildFingerprint: BuildFingerprint?,
        verifiedAssignment: VerifiedDisplayAssignment?,
    ): DisplaySelection {
        val active = displays.filter(DisplayDescriptor::isActive)
        if (active.isEmpty()) {
            return DisplaySelection(
                upper = null,
                lower = null,
                mode = DisplaySelectionMode.NO_ACTIVE_DISPLAY,
                upperReason = DisplaySelectionReason.NONE_AVAILABLE,
                lowerReason = DisplaySelectionReason.NONE_AVAILABLE,
            )
        }

        val assignmentIsCurrent = verifiedAssignment != null &&
            currentBuildFingerprint != null &&
            verifiedAssignment.buildFingerprint == currentBuildFingerprint
        val currentAssignment = verifiedAssignment.takeIf { assignmentIsCurrent }
        val upperFromAssignment = currentAssignment?.upperDisplayId?.let { displayId ->
            active.firstOrNull { it.id == displayId }
        }
        val upper = upperFromAssignment
            ?: active.firstOrNull(DisplayDescriptor::isDefaultDisplay)
            ?: active.maxWithOrNull(
                compareBy<DisplayDescriptor> { it.metrics.widthPx.toLong() * it.metrics.heightPx }
                    .thenBy { it.id.value },
            )
        val upperReason = when {
            upperFromAssignment != null -> DisplaySelectionReason.VERIFIED_SAME_BUILD
            upper?.isDefaultDisplay == true -> DisplaySelectionReason.DEFAULT_DISPLAY_CANDIDATE
            upper != null -> DisplaySelectionReason.LARGEST_ACTIVE_DISPLAY
            else -> DisplaySelectionReason.NONE_AVAILABLE
        }

        if (active.size == 1 || upper == null) {
            return DisplaySelection(
                upper = upper,
                lower = null,
                mode = DisplaySelectionMode.SINGLE_DISPLAY_FALLBACK,
                upperReason = upperReason,
                lowerReason = DisplaySelectionReason.SINGLE_DISPLAY,
            )
        }

        val remaining = active.filterNot { it.id == upper.id }
        val lowerFromAssignment = currentAssignment?.lowerDisplayId?.let { displayId ->
            remaining.firstOrNull { it.id == displayId }
        }
        val builtInNonDefault = remaining
            .filter { !it.isDefaultDisplay && it.type == DisplayType.BUILT_IN }
            .minWithOrNull(displaySizeComparator)
        val smallestNonUpper = remaining.minWithOrNull(displaySizeComparator)
        val lower = lowerFromAssignment ?: builtInNonDefault ?: smallestNonUpper
        val lowerReason = when {
            lowerFromAssignment != null -> DisplaySelectionReason.VERIFIED_LOWER_SAME_BUILD
            builtInNonDefault != null -> DisplaySelectionReason.BUILT_IN_NON_DEFAULT
            smallestNonUpper != null -> DisplaySelectionReason.SMALLEST_NON_UPPER
            else -> DisplaySelectionReason.NONE_AVAILABLE
        }
        return DisplaySelection(
            upper = upper,
            lower = lower,
            mode = if (lower == null) DisplaySelectionMode.SINGLE_DISPLAY_FALLBACK else DisplaySelectionMode.DUAL_DISPLAY,
            upperReason = upperReason,
            lowerReason = lowerReason,
        )
    }

    private val displaySizeComparator: Comparator<DisplayDescriptor> =
        compareBy<DisplayDescriptor> { it.metrics.widthPx.toLong() * it.metrics.heightPx }
            .thenBy { it.id.value }
}

data class ContinueEntry(
    val gameId: GameId,
    val title: String,
    val pinned: Boolean = false,
    val lastMeaningfulSessionAt: Instant? = null,
    val stableOrder: Int = 0,
) {
    init {
        require(title.isNotBlank()) { "Continue title must not be blank" }
    }

    val hasMeaningfulSession: Boolean
        get() = lastMeaningfulSessionAt != null
}

object ContinueOrderingPolicy {
    /** Pinned entries are retained even if they have no session; other entries need a meaningful session. */
    fun order(entries: Iterable<ContinueEntry>): List<ContinueEntry> = entries
        .filter { it.pinned || it.hasMeaningfulSession }
        .distinctBy { it.gameId }
        .sortedWith(
            compareByDescending<ContinueEntry> { it.pinned }
                .thenByDescending { it.lastMeaningfulSessionAt ?: Instant.MIN }
                .thenBy { it.stableOrder }
                .thenBy { it.title.lowercase() }
                .thenBy { it.gameId.value },
        )
}

sealed interface PlayMethodSelectionResult {
    data class Selected(
        val method: PlayMethod,
        val validation: LaunchValidation,
    ) : PlayMethodSelectionResult

    data class NoUsableMethod(
        val reason: NoUsablePlayMethodReason,
        val considered: List<PlayMethod>,
    ) : PlayMethodSelectionResult
}

enum class NoUsablePlayMethodReason {
    EMPTY,
    ALL_INVALID,
    ALL_UNSUPPORTED,
    UNKNOWN_VALIDITY,
}

object PlayMethodSelectionPolicy {
    fun select(
        methods: Iterable<PlayMethod>,
        validations: Map<PlayMethodId, LaunchValidation> = emptyMap(),
    ): PlayMethodSelectionResult {
        val considered = methods.toList()
        if (considered.isEmpty()) {
            return PlayMethodSelectionResult.NoUsableMethod(NoUsablePlayMethodReason.EMPTY, considered)
        }
        val effective = considered.map { it to (validations[it.id] ?: it.validation) }
        val valid = effective.filter { (_, validation) -> validation.validity == PlayMethodValidity.VALID }
        if (valid.isEmpty()) {
            val reason = when {
                effective.all { (_, validation) -> validation.validity == PlayMethodValidity.UNSUPPORTED } ->
                    NoUsablePlayMethodReason.ALL_UNSUPPORTED
                effective.all { (_, validation) -> validation.validity == PlayMethodValidity.INVALID } ->
                    NoUsablePlayMethodReason.ALL_INVALID
                else -> NoUsablePlayMethodReason.UNKNOWN_VALIDITY
            }
            return PlayMethodSelectionResult.NoUsableMethod(reason, considered)
        }
        val selected = valid.sortedWith(
            compareByDescending<Pair<PlayMethod, LaunchValidation>> { it.first.preferred }
                .thenByDescending { it.first.priority }
                .thenByDescending { it.first.lastStartedAt ?: Instant.MIN }
                .thenBy { it.first.id.value },
        ).first()
        return PlayMethodSelectionResult.Selected(selected.first, selected.second)
    }
}

data class LauncherProfileIssue(
    val code: String,
    val detail: String,
)

data class LauncherProfileValidation(
    val profileId: LauncherId,
    val issues: List<LauncherProfileIssue>,
) {
    val isValid: Boolean
        get() = issues.isEmpty()
}

object LauncherProfileValidator {
    fun validate(profile: LauncherProfile): LauncherProfileValidation {
        val issues = buildList {
            if (profile.adapterType == LauncherAdapterType.UNKNOWN) {
                add(LauncherProfileIssue("unknown_adapter", "Choose a concrete launcher adapter type"))
            }
            if (profile.packageCandidates.isEmpty() && profile.componentCandidates.isEmpty()) {
                add(
                    LauncherProfileIssue(
                        "missing_resolution_candidates",
                        "Provide at least one external application or component candidate",
                    ),
                )
            }
            if (profile.verification == VerificationState.VERIFIED && profile.verifiedAt == null) {
                add(LauncherProfileIssue("missing_verification_time", "Verified profiles need a verification timestamp"))
            }
            val constraint = profile.versionConstraint
            if (constraint != null && constraint.exactVersions.isNotEmpty() &&
                (constraint.minimumVersion != null || constraint.maximumVersion != null)
            ) {
                add(
                    LauncherProfileIssue(
                        "ambiguous_version_constraint",
                        "Exact versions cannot be combined with a range without an explicit resolver",
                    ),
                )
            }
        }
        return LauncherProfileValidation(profile.id, issues)
    }
}

enum class FingerprintWork {
    REUSE_EXISTING,
    COMPUTE_QUICK,
    COMPUTE_FULL,
    UNKNOWN,
}

data class FingerprintRequest(
    val requireFull: Boolean = false,
    val contentAmbiguous: Boolean = false,
    val allowExpensiveWork: Boolean = false,
    val preferFullForSmallContent: Boolean = true,
    val largeFileThresholdBytes: Long = 512L * 1024L * 1024L,
) {
    init {
        require(largeFileThresholdBytes > 0L) { "Large-file threshold must be positive" }
    }
}

data class FingerprintDecision(
    val work: FingerprintWork,
    val reason: String,
    val reuse: ContentFingerprint? = null,
) {
    val avoidsFullHash: Boolean
        get() = work != FingerprintWork.COMPUTE_FULL
}

object StagedFingerprintPolicy {
    private val opticalExtensions = setOf("iso", "chd", "cue", "bin", "img", "cso", "rvz", "wbfs", "gcz")

    fun decide(
        current: CheapContentIdentity,
        previous: ContentFingerprint? = null,
        request: FingerprintRequest = FingerprintRequest(),
    ): FingerprintDecision {
        val unchangedFingerprint = previous?.takeIf { sameCheapIdentity(current, it.identity) }
        val largeOrOptical = current.sizeBytes >= request.largeFileThresholdBytes ||
            current.extension?.trimStart('.')?.lowercase() in opticalExtensions
        val fullAllowed = request.allowExpensiveWork || request.contentAmbiguous || !largeOrOptical

        if (unchangedFingerprint != null) {
            if (unchangedFingerprint.tier == FingerprintTier.FULL) {
                return FingerprintDecision(
                    work = FingerprintWork.REUSE_EXISTING,
                    reason = "cheap identity is unchanged and a full fingerprint is cached",
                    reuse = unchangedFingerprint,
                )
            }
            if (!request.requireFull || !fullAllowed) {
                return FingerprintDecision(
                    work = FingerprintWork.REUSE_EXISTING,
                    reason = if (largeOrOptical) {
                        "unchanged large or optical content reuses its quick fingerprint"
                    } else {
                        "cheap identity is unchanged and the cached fingerprint is sufficient"
                    },
                    reuse = unchangedFingerprint,
                )
            }
            return FingerprintDecision(
                work = FingerprintWork.COMPUTE_FULL,
                reason = "a full fingerprint was explicitly requested for unchanged content",
            )
        }

        if ((request.requireFull || (request.preferFullForSmallContent && !largeOrOptical)) && fullAllowed) {
            return FingerprintDecision(
                work = FingerprintWork.COMPUTE_FULL,
                reason = if (largeOrOptical) {
                    "full fingerprint requested with explicit expensive-work permission"
                } else {
                    "small or non-optical content can be fully fingerprinted during scan"
                },
            )
        }
        return FingerprintDecision(
            work = FingerprintWork.COMPUTE_QUICK,
            reason = if (largeOrOptical) {
                "changed large or optical content is staged through a quick fingerprint"
            } else {
                "content changed or has no cache; compute a cheap identity fingerprint first"
            },
        )
    }

    fun sameCheapIdentity(left: CheapContentIdentity, right: CheapContentIdentity): Boolean =
        left == right
}

private val metadataProvenancePriority = mapOf(
    MetadataProvenance.USER_OVERRIDE to 700,
    MetadataProvenance.KNOWN_HASH to 600,
    MetadataProvenance.PLATFORM_DATABASE to 500,
    MetadataProvenance.STEAM_IMPORT to 400,
    MetadataProvenance.ARTWORK_PROVIDER to 300,
    MetadataProvenance.FILENAME_FALLBACK to 200,
    MetadataProvenance.GENERATED to 100,
    MetadataProvenance.UNKNOWN to 0,
)

private val metadataConfidencePriority = mapOf(
    MetadataConfidence.EXACT to 5,
    MetadataConfidence.HIGH to 4,
    MetadataConfidence.MEDIUM to 3,
    MetadataConfidence.LOW to 2,
    MetadataConfidence.UNKNOWN to 0,
)

object MetadataResolutionPolicy {
    fun resolve(candidates: Iterable<MetadataCandidate>): ResolvedMetadata? {
        val values = candidates.toList()
        val winner = values.maxWithOrNull(
            compareBy<MetadataCandidate> { metadataProvenancePriority[it.provenance] ?: 0 }
                .thenBy { metadataConfidencePriority[it.confidence] ?: 0 }
                .thenByDescending { it.observedAt }
                .thenByDescending { it.value },
        ) ?: return null
        return ResolvedMetadata(
            field = winner.field,
            value = winner.value,
            confidence = winner.confidence,
            provenance = winner.provenance,
            providerId = winner.providerId,
            candidatesConsidered = values.size,
        )
    }

    fun resolveField(field: MetadataField, candidates: Iterable<MetadataCandidate>): ResolvedMetadata? =
        resolve(candidates.filter { it.field == field })
}

object SessionDurationPolicy {
    fun begin(timing: SessionTiming, at: MonotonicMark): SessionTiming =
        if (timing.activeSince != null) timing else timing.copy(activeSince = at, lastObserved = at)

    fun observe(timing: SessionTiming, at: MonotonicMark): SessionTiming {
        val last = timing.lastObserved ?: return timing
        if (timing.activeSince == null) return timing
        if (last.bootId != at.bootId || timing.activeSince.bootId != at.bootId) {
            // Monotonic clocks are not comparable across a reboot.  Preserve
            // persisted duration and close the old interval without guessing.
            return recover(timing)
        }
        if (at.elapsedNanos <= last.elapsedNanos) return timing
        val delta = at.elapsedNanos - last.elapsedNanos
        return timing.copy(
            accumulatedActive = timing.accumulatedActive.plus(Duration.ofNanos(delta)),
            lastObserved = at,
        )
    }

    fun pause(timing: SessionTiming, at: MonotonicMark): SessionTiming {
        val observed = observe(timing, at)
        return observed.copy(activeSince = null, lastObserved = null)
    }

    fun resume(timing: SessionTiming, at: MonotonicMark): SessionTiming = begin(
        timing.copy(activeSince = null, lastObserved = null),
        at,
    )

    fun recover(timing: SessionTiming): SessionTiming =
        timing.copy(activeSince = null, lastObserved = null)
}

enum class SessionCommand {
    ACTIVATE,
    HEARTBEAT,
    SUSPEND,
    RESUME,
    BEGIN_ENDING,
    END,
    FAIL_LAUNCH,
    RECOVER,
}

data class SessionTransitionResult(
    val session: GameSession,
    val applied: Boolean,
    val idempotent: Boolean = false,
    val detail: String? = null,
)

object SessionPolicy {
    fun transition(
        session: GameSession,
        command: SessionCommand,
        wallClock: Instant = Instant.EPOCH,
        monotonic: MonotonicMark? = null,
    ): SessionTransitionResult = when (command) {
        SessionCommand.ACTIVATE -> activate(session, monotonic ?: session.timing.activeSince ?: MonotonicMark("unknown", 0L))
        SessionCommand.HEARTBEAT -> heartbeat(
            session,
            wallClock,
            monotonic ?: session.timing.lastObserved ?: MonotonicMark("unknown", 0L),
        )
        SessionCommand.SUSPEND -> suspend(session, monotonic ?: session.timing.lastObserved ?: MonotonicMark("unknown", 0L))
        SessionCommand.RESUME -> resume(
            session,
            monotonic ?: session.timing.lastObserved ?: MonotonicMark("unknown", 0L),
        )
        SessionCommand.BEGIN_ENDING -> beginEnding(
            session,
            monotonic,
        )
        SessionCommand.END -> end(session, wallClock, monotonic, SessionCloseReason.USER_ENDED)
        SessionCommand.FAIL_LAUNCH -> failLaunch(session, wallClock)
        SessionCommand.RECOVER -> recover(session)
    }

    fun activate(session: GameSession, at: MonotonicMark): SessionTransitionResult = when (session.state) {
        SessionState.PENDING -> SessionTransitionResult(
            session.copy(state = SessionState.ACTIVE, timing = SessionDurationPolicy.begin(session.timing, at)),
            applied = true,
        )
        SessionState.ACTIVE -> SessionTransitionResult(session, applied = false, idempotent = true)
        else -> SessionTransitionResult(session, applied = false, detail = "Only a pending session can be activated")
    }

    fun heartbeat(session: GameSession, wallClock: Instant, at: MonotonicMark): SessionTransitionResult = when {
        session.state != SessionState.ACTIVE -> SessionTransitionResult(
            session,
            applied = false,
            idempotent = session.state == SessionState.ENDED,
            detail = "Heartbeats are only accepted for active sessions",
        )
        else -> SessionTransitionResult(
            session.copy(
                timing = SessionDurationPolicy.observe(session.timing, at),
                lastHeartbeatAt = wallClock,
            ),
            applied = true,
        )
    }

    fun suspend(session: GameSession, at: MonotonicMark): SessionTransitionResult = when (session.state) {
        SessionState.ACTIVE -> SessionTransitionResult(
            session.copy(state = SessionState.SUSPENDED, timing = SessionDurationPolicy.pause(session.timing, at)),
            applied = true,
        )
        SessionState.SUSPENDED -> SessionTransitionResult(session, applied = false, idempotent = true)
        else -> SessionTransitionResult(session, applied = false, detail = "Only an active session can be suspended")
    }

    fun resume(session: GameSession, at: MonotonicMark): SessionTransitionResult = when (session.state) {
        SessionState.SUSPENDED, SessionState.RECOVERED -> SessionTransitionResult(
            session.copy(state = SessionState.ACTIVE, timing = SessionDurationPolicy.resume(session.timing, at)),
            applied = true,
        )
        SessionState.ACTIVE -> SessionTransitionResult(session, applied = false, idempotent = true)
        else -> SessionTransitionResult(session, applied = false, detail = "Only a suspended or recovered session can resume")
    }

    fun beginEnding(session: GameSession, at: MonotonicMark? = null): SessionTransitionResult = when (session.state) {
        SessionState.PENDING,
        SessionState.SUSPENDED,
        SessionState.RECOVERED,
        -> SessionTransitionResult(session.copy(state = SessionState.ENDING), applied = true)
        SessionState.ACTIVE -> SessionTransitionResult(
            session.copy(
                state = SessionState.ENDING,
                timing = if (at == null) SessionDurationPolicy.recover(session.timing) else {
                    SessionDurationPolicy.pause(session.timing, at)
                },
            ),
            applied = true,
        )
        SessionState.ENDING -> SessionTransitionResult(session, applied = false, idempotent = true)
        SessionState.ENDED -> SessionTransitionResult(session, applied = false, idempotent = true)
        SessionState.LAUNCH_FAILED,
        SessionState.ABANDONED,
        -> SessionTransitionResult(session, applied = false, detail = "This session cannot enter ending state")
    }

    fun end(
        session: GameSession,
        wallClock: Instant,
        at: MonotonicMark? = null,
        reason: SessionCloseReason = SessionCloseReason.USER_ENDED,
    ): SessionTransitionResult {
        if (session.state == SessionState.ENDED) {
            return SessionTransitionResult(session, applied = false, idempotent = true)
        }
        val timing = if (session.state == SessionState.ACTIVE && at != null) {
            SessionDurationPolicy.pause(session.timing, at)
        } else {
            SessionDurationPolicy.recover(session.timing)
        }
        return SessionTransitionResult(
            session.copy(
                state = SessionState.ENDED,
                wallEndedAt = wallClock,
                timing = timing,
                closeReason = reason,
            ),
            applied = true,
        )
    }

    fun failLaunch(session: GameSession, wallClock: Instant): SessionTransitionResult = when (session.state) {
        SessionState.PENDING -> SessionTransitionResult(
            session.copy(
                state = SessionState.LAUNCH_FAILED,
                wallEndedAt = wallClock,
                closeReason = SessionCloseReason.LAUNCH_FAILED,
            ),
            applied = true,
        )
        SessionState.LAUNCH_FAILED -> SessionTransitionResult(session, applied = false, idempotent = true)
        else -> SessionTransitionResult(session, applied = false, detail = "Only a pending session can fail launch")
    }

    fun recover(session: GameSession): SessionTransitionResult = when (session.state) {
        SessionState.PENDING,
        SessionState.ACTIVE,
        SessionState.SUSPENDED,
        SessionState.ENDING,
        -> SessionTransitionResult(
            session.copy(state = SessionState.RECOVERED, timing = SessionDurationPolicy.recover(session.timing)),
            applied = true,
        )
        SessionState.RECOVERED,
        SessionState.ENDED,
        SessionState.LAUNCH_FAILED,
        SessionState.ABANDONED,
        -> SessionTransitionResult(session, applied = false, idempotent = true)
    }
}

enum class SnapshotLineageClassification {
    SAME,
    SAME_CONTENT,
    LOCAL_AHEAD,
    REMOTE_AHEAD,
    DIVERGED,
    UNRELATED,
    UNKNOWN,
}

data class SnapshotLineageResult(
    val classification: SnapshotLineageClassification,
    val commonAncestor: SaveSnapshotId? = null,
    val detail: String? = null,
)

object SaveSnapshotLineagePolicy {
    fun classify(
        local: SaveSnapshot,
        remote: SaveSnapshot,
        snapshots: Map<SaveSnapshotId, SaveSnapshot>,
    ): SnapshotLineageResult {
        if (local.id == remote.id) return SnapshotLineageResult(SnapshotLineageClassification.SAME)
        if (local.manifestDigest != null && local.manifestDigest == remote.manifestDigest) {
            return SnapshotLineageResult(SnapshotLineageClassification.SAME_CONTENT)
        }
        val localAncestors = ancestors(local, snapshots)
        val remoteAncestors = ancestors(remote, snapshots)
        if (localAncestors == null || remoteAncestors == null) {
            return SnapshotLineageResult(
                SnapshotLineageClassification.UNKNOWN,
                detail = "A parent snapshot needed for lineage comparison is unavailable",
            )
        }
        if (remote.id in localAncestors) {
            return SnapshotLineageResult(SnapshotLineageClassification.LOCAL_AHEAD, remote.id)
        }
        if (local.id in remoteAncestors) {
            return SnapshotLineageResult(SnapshotLineageClassification.REMOTE_AHEAD, local.id)
        }
        val common = localAncestors.intersect(remoteAncestors).firstOrNull()
        return if (common != null) {
            SnapshotLineageResult(SnapshotLineageClassification.DIVERGED, common)
        } else {
            SnapshotLineageResult(SnapshotLineageClassification.UNRELATED)
        }
    }

    private fun ancestors(
        head: SaveSnapshot,
        snapshots: Map<SaveSnapshotId, SaveSnapshot>,
    ): LinkedHashSet<SaveSnapshotId>? {
        val result = linkedSetOf(head.id)
        var parent = head.parentSnapshotId
        while (parent != null) {
            if (!result.add(parent)) return null
            val snapshot = snapshots[parent] ?: return null
            parent = snapshot.parentSnapshotId
        }
        return result
    }
}

data class SyncTransitionDecision(
    val state: SyncState,
    val acceptedEvidence: SyncEvidence? = null,
    val detail: String? = null,
) {
    val isEvidenceBackedSuccess: Boolean
        get() = state == SyncState.SYNCED && acceptedEvidence != null
}

object SyncStatePolicy {
    fun fromAvailability(availability: TransportAvailability): SyncState = when (availability.state) {
        TransportAvailabilityState.READY -> SyncState.LOCAL_ONLY
        TransportAvailabilityState.NEEDS_SETUP -> SyncState.UNCONFIGURED
        TransportAvailabilityState.UNAVAILABLE -> SyncState.UNAVAILABLE
        TransportAvailabilityState.UNSUPPORTED -> SyncState.UNSUPPORTED
        TransportAvailabilityState.UNKNOWN -> SyncState.UNKNOWN
        TransportAvailabilityState.ERROR -> SyncState.ERROR
    }

    fun afterSnapshot(snapshot: SnapshotResult): SaveStatus = when (snapshot.state) {
        SnapshotResultState.CREATED,
        SnapshotResultState.UNCHANGED,
        -> SaveStatus(SaveState.SNAPSHOTTED, SyncState.LOCAL_ONLY, snapshot.snapshot?.id)
        SnapshotResultState.UNAVAILABLE -> SaveStatus(SaveState.UNAVAILABLE, SyncState.UNAVAILABLE)
        SnapshotResultState.UNSUPPORTED -> SaveStatus(SaveState.UNAVAILABLE, SyncState.UNSUPPORTED)
        SnapshotResultState.ERROR -> SaveStatus(SaveState.ERROR, SyncState.ERROR)
        SnapshotResultState.UNKNOWN -> SaveStatus(SaveState.UNKNOWN, SyncState.UNKNOWN)
    }

    fun applyResult(current: SaveStatus, result: SyncResult): SyncTransitionDecision {
        if (current.syncState == SyncState.MANAGED_EXTERNALLY) {
            return SyncTransitionDecision(
                state = SyncState.MANAGED_EXTERNALLY,
                detail = "An externally managed save is not evidence of Game Hub synchronization",
            )
        }
        return when (result.state) {
            SyncResultState.COMPLETED -> {
                val evidence = result.status.evidence
                if (result.status.state == SyncState.SYNCED && evidence != null) {
                    SyncTransitionDecision(SyncState.SYNCED, evidence)
                } else {
                    SyncTransitionDecision(
                        SyncState.UNKNOWN,
                        detail = "A completed transport result without verification evidence is not synced",
                    )
                }
            }
            SyncResultState.NO_CHANGES -> {
                if (result.status.state == SyncState.SYNCED && result.status.evidence != null) {
                    SyncTransitionDecision(SyncState.SYNCED, result.status.evidence)
                } else {
                    SyncTransitionDecision(SyncState.LOCAL_ONLY, detail = "No verified remote change was observed")
                }
            }
            SyncResultState.PENDING -> SyncTransitionDecision(SyncState.SYNCING)
            SyncResultState.CONFLICT -> SyncTransitionDecision(SyncState.CONFLICT)
            SyncResultState.UNAVAILABLE -> SyncTransitionDecision(SyncState.UNAVAILABLE)
            SyncResultState.UNSUPPORTED -> SyncTransitionDecision(SyncState.UNSUPPORTED)
            SyncResultState.ERROR -> SyncTransitionDecision(SyncState.ERROR)
            SyncResultState.UNKNOWN -> SyncTransitionDecision(SyncState.UNKNOWN)
        }
    }

    fun canReportSynced(status: SyncStatus): Boolean =
        status.state == SyncState.SYNCED && status.evidence != null
}
