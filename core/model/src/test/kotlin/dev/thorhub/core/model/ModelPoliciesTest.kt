package dev.thorhub.core.model

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertThrows
import org.junit.Test

class ModelPoliciesTest {
    private val gameId = GameId.of("game-1")
    private val versionId = GameVersionId.of("version-1")
    private val deviceId = DeviceId.of("device-1")
    private val installationId = InstallationId.of("installation-1")
    private val launcherId = LauncherId.of("launcher-1")
    private val playMethodId = PlayMethodId.of("play-1")

    @Test
    fun ids_are_validated_and_deterministic_without_uuid_apis() {
        assertThat(GameId.deterministic("fixture", "zelda")).isEqualTo(
            GameId.deterministic("fixture", "zelda"),
        )
        assertThat(GameId.deterministic("fixture", "zelda")).isNotEqualTo(
            GameId.deterministic("fixture", "mario"),
        )
        assertThrows(IllegalArgumentException::class.java) { GameId.of("contains whitespace") }
        assertThrows(IllegalArgumentException::class.java) { DeviceId.of("") }
    }

    @Test
    fun display_selection_prefers_current_verified_assignment_then_default_and_lower_fallbacks() {
        val build = BuildFingerprint.of("build-a")
        val default = display("default", isDefault = true, type = DisplayType.BUILT_IN, width = 1920, height = 1080)
        val upper = display("upper", type = DisplayType.BUILT_IN, width = 1280, height = 720)
        val lower = display("lower", type = DisplayType.BUILT_IN, width = 800, height = 480)
        val topology = DisplayTopology(
            buildFingerprint = build,
            displays = listOf(default, upper, lower),
            verifiedAssignment = VerifiedDisplayAssignment(build, upper.id, lower.id),
        )

        val verified = DisplaySelectionPolicy.select(topology)
        assertThat(verified.upper?.id).isEqualTo(upper.id)
        assertThat(verified.lower?.id).isEqualTo(lower.id)
        assertThat(verified.upperReason).isEqualTo(DisplaySelectionReason.VERIFIED_SAME_BUILD)
        assertThat(verified.lowerReason).isEqualTo(DisplaySelectionReason.VERIFIED_LOWER_SAME_BUILD)

        val stale = DisplaySelectionPolicy.select(
            topology.copy(
                verifiedAssignment = VerifiedDisplayAssignment(
                    buildFingerprint = BuildFingerprint.of("old-build"),
                    upperDisplayId = upper.id,
                    lowerDisplayId = lower.id,
                ),
            ),
        )
        assertThat(stale.upper?.id).isEqualTo(default.id)
        assertThat(stale.lower?.id).isEqualTo(lower.id)
        assertThat(stale.upperReason).isEqualTo(DisplaySelectionReason.DEFAULT_DISPLAY_CANDIDATE)
        assertThat(stale.lowerReason).isEqualTo(DisplaySelectionReason.BUILT_IN_NON_DEFAULT)
    }

    @Test
    fun display_selection_uses_smallest_non_default_and_has_single_display_fallback() {
        val displays = listOf(
            display("main", isDefault = true, type = DisplayType.EXTERNAL, width = 1920, height = 1080),
            display("large-external", type = DisplayType.EXTERNAL, width = 1600, height = 900),
            display("small-external", type = DisplayType.EXTERNAL, width = 640, height = 480),
        )
        val selection = DisplaySelectionPolicy.select(displays)
        assertThat(selection.upper?.id).isEqualTo(DisplayId.of("main"))
        assertThat(selection.lower?.id).isEqualTo(DisplayId.of("small-external"))
        assertThat(selection.lowerReason).isEqualTo(DisplaySelectionReason.SMALLEST_NON_UPPER)

        val single = DisplaySelectionPolicy.select(displays.take(1))
        assertThat(single.mode).isEqualTo(DisplaySelectionMode.SINGLE_DISPLAY_FALLBACK)
        assertThat(single.upper?.id).isEqualTo(DisplayId.of("main"))
        assertThat(single.lower).isNull()
    }

    @Test
    fun continue_ordering_keeps_pins_first_and_drops_unplayed_unpinned_items() {
        val older = Instant.parse("2026-01-01T00:00:00Z")
        val newer = Instant.parse("2026-02-01T00:00:00Z")
        val ordered = ContinueOrderingPolicy.order(
            listOf(
                ContinueEntry(GameId.of("unpinned-no-session"), "No Session"),
                ContinueEntry(GameId.of("recent"), "Recent", lastMeaningfulSessionAt = newer),
                ContinueEntry(GameId.of("pinned"), "Pinned", pinned = true),
                ContinueEntry(GameId.of("older"), "Older", lastMeaningfulSessionAt = older),
            ),
        )
        assertThat(ordered.map { it.gameId.value }).containsExactly("pinned", "recent", "older").inOrder()
    }

    @Test
    fun play_method_selection_requires_validity_then_uses_preference_and_priority() {
        val invalid = method("invalid", PlayMethodValidity.INVALID, priority = 100)
        val validLow = method("valid-low", PlayMethodValidity.VALID, priority = 1)
        val validHigh = method("valid-high", PlayMethodValidity.VALID, priority = 10)
        val selected = PlayMethodSelectionPolicy.select(listOf(invalid, validLow, validHigh))
        assertThat((selected as PlayMethodSelectionResult.Selected).method.id).isEqualTo(validHigh.id)

        val unsupported = PlayMethodSelectionPolicy.select(
            listOf(method("unsupported", PlayMethodValidity.UNSUPPORTED, priority = 0)),
        )
        assertThat((unsupported as PlayMethodSelectionResult.NoUsableMethod).reason)
            .isEqualTo(NoUsablePlayMethodReason.ALL_UNSUPPORTED)
        val unknown = PlayMethodSelectionPolicy.select(
            listOf(method("unknown", PlayMethodValidity.UNKNOWN, priority = 0)),
        )
        assertThat((unknown as PlayMethodSelectionResult.NoUsableMethod).reason)
            .isEqualTo(NoUsablePlayMethodReason.UNKNOWN_VALIDITY)
    }

    @Test
    fun launcher_profile_validation_rejects_unresolved_or_unverifiable_profiles() {
        val valid = LauncherProfile(
            id = launcherId,
            displayName = "Test launcher",
            adapterType = LauncherAdapterType.CONFIGURABLE_EXTERNAL,
            packageCandidates = listOf(ExternalAppId.of("external-app")),
        )
        assertThat(LauncherProfileValidator.validate(valid).isValid).isTrue()

        val noTarget = valid.copy(packageCandidates = emptyList(), componentCandidates = emptyList())
        assertThat(LauncherProfileValidator.validate(noTarget).issues.map { it.code })
            .contains("missing_resolution_candidates")
        val unverifiedAtTime = valid.copy(verification = VerificationState.VERIFIED, verifiedAt = null)
        assertThat(LauncherProfileValidator.validate(unverifiedAtTime).issues.map { it.code })
            .contains("missing_verification_time")
    }

    @Test
    fun staged_fingerprint_reuses_unchanged_optical_quick_hash_and_never_repeats_full_hash_by_default() {
        val identity = CheapContentIdentity(
            sourceKey = "fixture-document",
            sizeBytes = 4L * 1024L * 1024L * 1024L,
            extension = "iso",
            normalizedName = "game-disc",
        )
        val previous = ContentFingerprint(
            identity = identity,
            tier = FingerprintTier.QUICK,
            algorithm = FingerprintAlgorithm.QUICK_WINDOW,
            hash = ContentHash.of("quick-hash"),
        )
        val reused = StagedFingerprintPolicy.decide(identity, previous)
        assertThat(reused.work).isEqualTo(FingerprintWork.REUSE_EXISTING)
        assertThat(reused.reuse).isEqualTo(previous)
        assertThat(reused.avoidsFullHash).isTrue()

        val explicitlyFull = StagedFingerprintPolicy.decide(
            identity,
            previous,
            FingerprintRequest(requireFull = true, allowExpensiveWork = true),
        )
        assertThat(explicitlyFull.work).isEqualTo(FingerprintWork.COMPUTE_FULL)

        val changed = StagedFingerprintPolicy.decide(
            identity.copy(sizeBytes = identity.sizeBytes + 1),
            previous,
        )
        assertThat(changed.work).isEqualTo(FingerprintWork.COMPUTE_QUICK)

        val smallRom = CheapContentIdentity(
            sourceKey = "fixture-cartridge",
            sizeBytes = 2L * 1024L * 1024L,
            extension = "gba",
            normalizedName = "small-game",
        )
        assertThat(StagedFingerprintPolicy.decide(smallRom).work).isEqualTo(FingerprintWork.COMPUTE_FULL)
    }

    @Test
    fun metadata_resolution_preserves_user_override_provenance_over_provider_confidence() {
        val candidates = listOf(
            MetadataCandidate(
                field = MetadataField.TITLE,
                value = "Filename guess",
                confidence = MetadataConfidence.EXACT,
                provenance = MetadataProvenance.KNOWN_HASH,
            ),
            MetadataCandidate(
                field = MetadataField.TITLE,
                value = "My corrected title",
                confidence = MetadataConfidence.LOW,
                provenance = MetadataProvenance.USER_OVERRIDE,
            ),
        )
        val resolved = MetadataResolutionPolicy.resolveField(MetadataField.TITLE, candidates)
        assertThat(resolved?.value).isEqualTo("My corrected title")
        assertThat(resolved?.provenance).isEqualTo(MetadataProvenance.USER_OVERRIDE)
    }

    @Test
    fun session_end_is_idempotent_and_duration_is_monotonic_without_double_counting() {
        val pending = session()
        val active = SessionPolicy.activate(pending, MonotonicMark("boot-a", 0L)).session
        val atFive = SessionPolicy.heartbeat(
            active,
            Instant.parse("2026-01-01T00:00:05Z"),
            MonotonicMark("boot-a", 5_000_000_000L),
        ).session
        val duplicateHeartbeat = SessionPolicy.heartbeat(
            atFive,
            Instant.parse("2026-01-01T00:00:05Z"),
            MonotonicMark("boot-a", 5_000_000_000L),
        ).session
        val ended = SessionPolicy.end(
            duplicateHeartbeat,
            Instant.parse("2026-01-01T00:00:10Z"),
            MonotonicMark("boot-a", 10_000_000_000L),
        ).session
        val endedAgain = SessionPolicy.end(
            ended,
            Instant.parse("2026-01-01T00:00:11Z"),
            MonotonicMark("boot-a", 11_000_000_000L),
        )
        assertThat(ended.timing.accumulatedActive).isEqualTo(Duration.ofSeconds(10))
        assertThat(endedAgain.idempotent).isTrue()
        assertThat(endedAgain.session).isEqualTo(ended)
    }

    @Test
    fun reboot_recovery_preserves_persisted_duration_and_does_not_bridge_monotonic_clocks() {
        val timing = SessionDurationPolicy.begin(SessionTiming(), MonotonicMark("boot-a", 0L))
        val observed = SessionDurationPolicy.observe(timing, MonotonicMark("boot-a", 4_000_000_000L))
        val afterReboot = SessionDurationPolicy.observe(observed, MonotonicMark("boot-b", 50_000_000_000L))
        assertThat(observed.accumulatedActive).isEqualTo(Duration.ofSeconds(4))
        assertThat(afterReboot.accumulatedActive).isEqualTo(Duration.ofSeconds(4))
        assertThat(afterReboot.activeSince).isNull()
    }

    @Test
    fun snapshot_lineage_distinguishes_ancestor_and_divergent_heads() {
        val base = snapshot("base")
        val local = snapshot("local", parent = base.id)
        val remote = snapshot("remote", parent = base.id)
        val snapshots = mapOf(base.id to base, local.id to local, remote.id to remote)
        assertThat(SaveSnapshotLineagePolicy.classify(local, base, snapshots).classification)
            .isEqualTo(SnapshotLineageClassification.LOCAL_AHEAD)
        val divergence = SaveSnapshotLineagePolicy.classify(local, remote, snapshots)
        assertThat(divergence.classification).isEqualTo(SnapshotLineageClassification.DIVERGED)
        assertThat(divergence.commonAncestor).isEqualTo(base.id)
    }

    @Test
    fun sync_state_never_reports_synced_without_transport_evidence_and_keeps_managed_external_distinct() {
        val local = SaveStatus(SaveState.SNAPSHOTTED, SyncState.LOCAL_ONLY)
        val noEvidence = SyncResult(
            state = SyncResultState.COMPLETED,
            status = SyncStatus(SyncState.SYNCED, evidence = null),
        )
        val rejected = SyncStatePolicy.applyResult(local, noEvidence)
        assertThat(rejected.state).isEqualTo(SyncState.UNKNOWN)
        assertThat(rejected.isEvidenceBackedSuccess).isFalse()

        val evidence = SyncEvidence("operation-1", Instant.EPOCH, source = SyncTransportId.of("transport"))
        val accepted = SyncStatePolicy.applyResult(
            local,
            noEvidence.copy(status = SyncStatus(SyncState.SYNCED, evidence)),
        )
        assertThat(accepted.state).isEqualTo(SyncState.SYNCED)
        assertThat(accepted.isEvidenceBackedSuccess).isTrue()

        val managed = SyncStatePolicy.applyResult(
            SaveStatus(SaveState.MANAGED_EXTERNALLY, SyncState.MANAGED_EXTERNALLY),
            noEvidence,
        )
        assertThat(managed.state).isEqualTo(SyncState.MANAGED_EXTERNALLY)
        assertThat(SyncStatePolicy.fromAvailability(TransportAvailability(TransportAvailabilityState.UNKNOWN)))
            .isEqualTo(SyncState.UNKNOWN)
        assertThat(SyncStatePolicy.fromAvailability(TransportAvailability(TransportAvailabilityState.UNSUPPORTED)))
            .isEqualTo(SyncState.UNSUPPORTED)
    }

    @Test
    fun display_acceptance_and_physical_placement_are_independent_evidence_states() {
        val result = DisplayLaunchResult(
            requestedDisplayId = DisplayId.of("display-1"),
            acceptance = DisplayLaunchAcceptance(
                state = DisplayLaunchAcceptanceState.ACCEPTED,
                requestedDisplayId = DisplayId.of("display-1"),
            ),
            physicalPlacement = PhysicalPlacementConfirmation(PhysicalPlacementState.UNKNOWN),
        )
        assertThat(result.acceptance.state).isEqualTo(DisplayLaunchAcceptanceState.ACCEPTED)
        assertThat(result.physicalPlacement.state).isEqualTo(PhysicalPlacementState.UNKNOWN)

        val exit = ExitRequestResult(
            requestState = ExternalExitRequestState.REQUESTED,
            closureState = ExternalProcessClosureState.NOT_VERIFIED,
        )
        assertThat(exit.requestState).isEqualTo(ExternalExitRequestState.REQUESTED)
        assertThat(exit.closureState).isEqualTo(ExternalProcessClosureState.NOT_VERIFIED)
    }

    @Test
    fun diagnostics_ring_is_bounded_and_redaction_is_typed() {
        val ring = DiagnosticEventRingBuffer(2)
        fun event(name: String) = DiagnosticEvent(
            id = DiagnosticEventId.of(name),
            occurredAt = Instant.EPOCH,
            severity = DiagnosticSeverity.INFO,
            category = DiagnosticCategory.SYSTEM,
            code = name,
            message = name,
        )
        ring.append(event("one"))
        ring.append(event("two"))
        ring.append(event("three"))
        assertThat(ring.snapshot().map { it.id.value }).containsExactly("two", "three").inOrder()

        val report = DiagnosticReport(
            appVersion = "fixture",
            buildFingerprint = null,
            topology = null,
            homeRole = HomeRoleStatus(HomeRoleState.UNKNOWN),
            recentEvents = listOf(
                event("sensitive").copy(
                    attributes = listOf(
                        DiagnosticAttribute("api_key", DiagnosticValue.Text("secret")),
                        DiagnosticAttribute("document_uri", DiagnosticValue.Text("private")),
                        DiagnosticAttribute("status", DiagnosticValue.Text("ready")),
                    ),
                ),
            ),
        )
        val redacted = DiagnosticRedactor.redact(report)
        assertThat(redacted.redactedAttributeCount).isEqualTo(2)
        val values = redacted.report.recentEvents.single().attributes.map { it.value }
        assertThat(values[0]).isEqualTo(DiagnosticValue.Redacted)
        assertThat(values[1]).isEqualTo(DiagnosticValue.Redacted)
        assertThat(values[2]).isEqualTo(DiagnosticValue.Text("ready"))
    }

    @Test
    fun save_snapshot_file_rejects_absolute_and_parent_paths() {
        assertThrows(IllegalArgumentException::class.java) {
            SaveSnapshotFile("/absolute", 0L, ContentHash.of("hash"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SaveSnapshotFile("nested/../escape", 0L, ContentHash.of("hash"))
        }
    }

    private fun display(
        id: String,
        isDefault: Boolean = false,
        type: DisplayType,
        width: Int,
        height: Int,
    ): DisplayDescriptor = DisplayDescriptor(
        id = DisplayId.of(id),
        name = id,
        type = type,
        state = DisplayState.ACTIVE,
        metrics = DisplayMetrics(width, height, density = 1f),
        isDefaultDisplay = isDefault,
    )

    private fun method(id: String, validity: PlayMethodValidity, priority: Int): PlayMethod = PlayMethod(
        id = PlayMethodId.of(id),
        installationId = installationId,
        deviceId = deviceId,
        launcherId = launcherId,
        priority = priority,
        validation = LaunchValidation(validity),
    )

    private fun session(): GameSession = GameSession(
        id = GameSessionId.of("session-1"),
        gameId = gameId,
        gameVersionId = versionId,
        installationId = installationId,
        deviceId = deviceId,
        playMethodId = playMethodId,
    )

    private fun snapshot(id: String, parent: SaveSnapshotId? = null): SaveSnapshot = SaveSnapshot(
        id = SaveSnapshotId.of(id),
        profileId = SaveProfileId.of("profile-1"),
        deviceId = deviceId,
        parentSnapshotId = parent,
        storageReference = "fixture-$id",
        reason = SnapshotReason.MANUAL,
        manifestDigest = ContentHash.of("digest-$id"),
    )
}
