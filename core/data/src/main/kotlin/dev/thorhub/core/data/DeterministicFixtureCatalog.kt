package dev.thorhub.core.data

import dev.thorhub.core.model.DeviceId
import dev.thorhub.core.model.DiscoveredItem
import dev.thorhub.core.model.Game
import dev.thorhub.core.model.GameLifecycle
import dev.thorhub.core.model.GamePlatform
import dev.thorhub.core.model.GameVersion
import dev.thorhub.core.model.GameVersionKind
import dev.thorhub.core.model.Installation
import dev.thorhub.core.model.InstallationAvailability
import dev.thorhub.core.model.InstallationReference
import dev.thorhub.core.model.InstallationSourceType
import dev.thorhub.core.model.LibraryRoot
import dev.thorhub.core.model.LibraryRootAccessState
import dev.thorhub.core.model.LibraryRootId
import dev.thorhub.core.model.LibrarySource
import dev.thorhub.core.model.LibrarySourceId
import dev.thorhub.core.model.ResolutionResult
import dev.thorhub.core.model.ResolutionState
import dev.thorhub.core.model.ScanEvent
import dev.thorhub.core.model.ScanRequest
import dev.thorhub.core.model.SourceAvailability
import dev.thorhub.core.model.SourceAvailabilityState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Instant

data class DeterministicFixtureCatalog(
    val seed: String,
    val root: LibraryRoot,
    val items: List<DiscoveredItem>,
)

/**
 * Generates metadata-only records. No ROM bytes, BIOS entries, emulator
 * packages, or filesystem paths are created by this fixture catalog.
 */
object DeterministicFixtureCatalogGenerator {
    fun generate(
        count: Int,
        seed: String = "gate2",
        platform: GamePlatform = GamePlatform.NINTENDO,
    ): DeterministicFixtureCatalog {
        require(count >= 0) { "Fixture count must not be negative" }
        require(count <= 100_000) { "Fixture count is unexpectedly large" }
        require(seed.isNotBlank()) { "Fixture seed must not be blank" }
        val rootId = LibraryRootId.deterministic("fixture", seed)
        val extension = RomExtensionPolicy.effectiveExtensions(platform, emptySet())
            .sorted()
            .firstOrNull()
            ?: "rom"
        val items = List(count) { index ->
            val ordinal = index.toString().padStart(5, '0')
            val reference = "fixture-rom:$seed:$ordinal"
            DiscoveredItem(
                rootId = rootId,
                sourceType = InstallationSourceType.ROM,
                reference = InstallationReference.ExternalIdentifier(reference),
                identity = dev.thorhub.core.model.CheapContentIdentity(
                    sourceKey = "fixture:$seed:$ordinal",
                    rootId = rootId,
                    sizeBytes = 1_048_576L + index,
                    modifiedAt = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index.toLong()),
                    extension = extension,
                    normalizedName = "fixture-game-$ordinal",
                ),
                titleHint = "Fixture Game $ordinal",
                platformHint = platform,
            )
        }
        return DeterministicFixtureCatalog(
            seed = seed,
            root = LibraryRoot(
                id = rootId,
                displayName = "Fixture ROM Library ($seed)",
                sourceType = InstallationSourceType.ROM,
                reference = InstallationReference.Manual("fixture-root:$seed"),
                platformHint = platform,
                supportedExtensions = setOf(extension),
                accessState = LibraryRootAccessState.READY,
            ),
            items = items,
        )
    }
}

class DeterministicFixtureLibrarySource(
    private val catalog: DeterministicFixtureCatalog,
    private val deviceId: DeviceId? = null,
    private val scanDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Instant = { Instant.now() },
) : LibrarySource {
    override val id: LibrarySourceId = LibrarySourceId.of("fixture-catalog")

    override suspend fun availability(): SourceAvailability = SourceAvailability(
        state = SourceAvailabilityState.READY,
        detail = "Metadata-only local fixture source",
        checkedAt = clock(),
    )

    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        emit(ScanEvent.Started(clock()))
        if (request.rootId != null && request.rootId != catalog.root.id) {
            emit(
                ScanEvent.Failed(
                    code = "ROOT_MISMATCH",
                    detail = "The requested scan root does not match this fixture catalog.",
                    emittedAt = clock(),
                ),
            )
            return@flow
        }
        catalog.items.forEach { item ->
            emit(ScanEvent.ItemDiscovered(item, clock()))
        }
        emit(ScanEvent.Completed(catalog.items.size, clock()))
    }.flowOn(scanDispatcher)

    override suspend fun resolve(item: DiscoveredItem): ResolutionResult = withContext(scanDispatcher) {
        if (item !in catalog.items) {
            return@withContext ResolutionResult(ResolutionState.UNKNOWN, detail = "Fixture item is not in the catalog")
        }
        val observedAt = clock()
        val title = item.titleHint ?: item.identity?.normalizedName ?: "Fixture game"
        val game = Game(
            id = LibraryRecordIds.game(item),
            title = title,
            sortTitle = title,
            lifecycle = GameLifecycle.UNKNOWN,
            createdAt = observedAt,
            updatedAt = observedAt,
        )
        val version = GameVersion(
            id = LibraryRecordIds.version(item),
            gameId = game.id,
            platform = item.platformHint,
            kind = GameVersionKind.ORIGINAL,
            createdAt = observedAt,
            updatedAt = observedAt,
        )
        if (deviceId == null) {
            return@withContext ResolutionResult(
                state = ResolutionState.NEEDS_REVIEW,
                game = game,
                version = version,
                detail = "A local device identity is required before a fixture installation can be fully resolved.",
            )
        }
        ResolutionResult(
            state = ResolutionState.RESOLVED,
            game = game,
            version = version,
            installation = Installation(
                id = LibraryRecordIds.installation(item),
                gameVersionId = version.id,
                deviceId = deviceId,
                sourceType = InstallationSourceType.ROM,
                reference = item.reference,
                availability = InstallationAvailability.AVAILABLE,
                discoveredAt = observedAt,
                lastSeenAt = observedAt,
            ),
        )
    }
}
