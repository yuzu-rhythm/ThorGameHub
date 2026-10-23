package dev.thorhub.app.platform.contracts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thorhub.core.model.ArtworkAsset
import dev.thorhub.core.model.ArtworkKind
import dev.thorhub.core.model.ArtworkProvenance
import dev.thorhub.core.model.ArtworkProvider
import dev.thorhub.core.model.ArtworkProviderId
import dev.thorhub.core.model.ArtworkProviderResult
import dev.thorhub.core.model.ArtworkProviderState
import dev.thorhub.core.model.ArtworkQuery
import dev.thorhub.core.model.CapabilityStatus
import dev.thorhub.core.model.ChangeSet
import dev.thorhub.core.model.DeterministicIds
import dev.thorhub.core.model.DisplayId
import dev.thorhub.core.model.DisplayLifecycleKind
import dev.thorhub.core.model.DisplayLifecycleObservation
import dev.thorhub.core.model.ExternalAppId
import dev.thorhub.core.model.LidCapabilities
import dev.thorhub.core.model.LidSignal
import dev.thorhub.core.model.LidSignalProvider
import dev.thorhub.core.model.LidSignalSource
import dev.thorhub.core.model.MetadataCandidate
import dev.thorhub.core.model.MetadataConfidence
import dev.thorhub.core.model.MetadataField
import dev.thorhub.core.model.MetadataProvenance
import dev.thorhub.core.model.MetadataProvider
import dev.thorhub.core.model.MetadataProviderId
import dev.thorhub.core.model.MetadataProviderResult
import dev.thorhub.core.model.MetadataProviderState
import dev.thorhub.core.model.MetadataQuery
import dev.thorhub.core.model.PrivilegeBridge
import dev.thorhub.core.model.PrivilegeState
import dev.thorhub.core.model.PrivilegeStatus
import dev.thorhub.core.model.PrivilegedResult
import dev.thorhub.core.model.PrivilegedResultState
import dev.thorhub.core.model.ProtectedPathReference
import dev.thorhub.core.model.SyncResult
import dev.thorhub.core.model.SyncResultState
import dev.thorhub.core.model.SyncScope
import dev.thorhub.core.model.SyncState
import dev.thorhub.core.model.SyncStatus
import dev.thorhub.core.model.SyncTransport
import dev.thorhub.core.model.SyncTransportId
import dev.thorhub.core.model.TransportAvailability
import dev.thorhub.core.model.TransportAvailabilityState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Screen/display lifecycle fallback. It deliberately emits no synthetic lid event. */
@Singleton
class StandardLidSignalProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LidSignalProvider {
    override val events: Flow<LidSignal> = emptyFlow()

    override val displayLifecycle: Flow<DisplayLifecycleObservation> = callbackFlow {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        fun send(kind: DisplayLifecycleKind, displayId: Int?, evidence: String) {
            trySend(
                DisplayLifecycleObservation(
                    kind = kind,
                    displayId = displayId?.let { DisplayId.of("android-$it") },
                    observedAt = Instant.now(),
                    evidence = listOf(evidence, "lid_interpretation=UNKNOWN"),
                ),
            )
        }
        val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = send(DisplayLifecycleKind.CONNECTED, displayId, "display_added")
            override fun onDisplayRemoved(displayId: Int) = send(DisplayLifecycleKind.DISCONNECTED, displayId, "display_removed")
            override fun onDisplayChanged(displayId: Int) {
                val state = displayManager.getDisplay(displayId)?.state
                send(
                    if (state == Display.STATE_OFF) DisplayLifecycleKind.INACTIVE else DisplayLifecycleKind.METRICS_CHANGED,
                    displayId,
                    "display_changed_state=${state ?: Display.STATE_UNKNOWN}",
                )
            }
        }
        val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> send(DisplayLifecycleKind.ACTIVE, null, "screen_on")
                    Intent.ACTION_SCREEN_OFF -> send(DisplayLifecycleKind.INACTIVE, null, "screen_off")
                }
            }
        }
        displayManager.registerDisplayListener(displayListener, null)
        context.registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        awaitClose {
            displayManager.unregisterDisplayListener(displayListener)
            runCatching { context.unregisterReceiver(screenReceiver) }
        }
    }

    override suspend fun capabilities(): LidCapabilities = LidCapabilities(
        status = CapabilityStatus.UNKNOWN,
        source = LidSignalSource.UNKNOWN,
        supportsOpen = false,
        supportsClosed = false,
        detail = "Standard mode observes screen/display lifecycle only; a physical lid signal is UNKNOWN.",
    )
}

/** Standard flavor has no privileged process, task, or protected-path access. */
@Singleton
class StandardPrivilegeBridge @Inject constructor() : PrivilegeBridge {
    private val _state = MutableStateFlow(
        PrivilegeStatus(PrivilegeState.UNSUPPORTED, "Standard mode uses public Android APIs only."),
    )
    override val state: StateFlow<PrivilegeStatus> = _state

    override suspend fun moveTask(taskId: Int, displayId: DisplayId) = unsupported()
    override suspend fun stopPackage(packageName: ExternalAppId) = unsupported()
    override suspend fun inspectPath(path: ProtectedPathReference) = unsupported()

    private fun unsupported() = PrivilegedResult(
        PrivilegedResultState.UNSUPPORTED,
        "No privileged operation was attempted in standard mode.",
    )
}

/** Phase 1 transport: accurately reports local-only state and transfers nothing. */
@Singleton
class LocalOnlySyncTransport @Inject constructor() : SyncTransport {
    override val id: SyncTransportId = SyncTransportId.of("local-only")

    override suspend fun availability() = TransportAvailability(
        TransportAvailabilityState.READY,
        "Local snapshot/export state is available; no remote transport is configured.",
        Instant.now(),
    )

    override suspend fun status(scope: SyncScope) = SyncStatus(
        SyncState.LOCAL_ONLY,
        detail = "No remote synchronization was attempted.",
    )

    override suspend fun push(changeSet: ChangeSet) = localOnlyResult()
    override suspend fun pull(scope: SyncScope) = localOnlyResult()

    private fun localOnlyResult() = SyncResult(
        SyncResultState.NO_CHANGES,
        SyncStatus(SyncState.LOCAL_ONLY, detail = "No remote synchronization was attempted."),
        "Local-only transport intentionally performs no network operation.",
    )
}

/** Offline fallback that makes the source of a retained title explicit. */
@Singleton
class FilenameMetadataProvider @Inject constructor() : MetadataProvider {
    override val id: MetadataProviderId = MetadataProviderId.of("filename-fallback")

    override suspend fun resolve(query: MetadataQuery): MetadataProviderResult {
        val title = query.game?.title?.takeIf(String::isNotBlank)
            ?: return MetadataProviderResult(MetadataProviderState.NO_MATCH, detail = "No local title hint exists.")
        return MetadataProviderResult(
            MetadataProviderState.RESOLVED,
            listOf(
                MetadataCandidate(
                    field = MetadataField.TITLE,
                    value = title,
                    confidence = MetadataConfidence.LOW,
                    provenance = MetadataProvenance.FILENAME_FALLBACK,
                    providerId = id,
                    observedAt = Instant.now(),
                ),
            ),
            "Offline filename/app-label fallback; user overrides remain higher priority.",
        )
    }
}

/** Coherent no-key fallback; the UI renders this reference as a generated gradient. */
@Singleton
class GeneratedArtworkProvider @Inject constructor() : ArtworkProvider {
    override val id: ArtworkProviderId = ArtworkProviderId.of("generated-gradient")

    override suspend fun resolve(query: ArtworkQuery) = ArtworkProviderResult(
        state = ArtworkProviderState.RESOLVED,
        assets = listOf(
            ArtworkAsset(
                id = DeterministicIds.artworkAsset("generated-gradient", query.game.id.value),
                gameId = query.game.id,
                kind = ArtworkKind.PLACEHOLDER,
                reference = "generated-gradient:${query.game.id.value}",
                provenance = ArtworkProvenance.GENERATED,
                providerId = id,
                isDerived = true,
                createdAt = Instant.now(),
            ),
        ),
        detail = "Generated locally without a provider key or network request.",
    )
}
