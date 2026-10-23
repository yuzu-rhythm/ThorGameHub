package dev.thorhub.app.data

import androidx.room.withTransaction
import dev.thorhub.core.database.DeviceAvailabilityState
import dev.thorhub.core.database.DeviceEntity
import dev.thorhub.core.database.GameEntity
import dev.thorhub.core.database.GameLifecycleStatus
import dev.thorhub.core.database.GameSessionEntity
import dev.thorhub.core.database.GameVersionClassification
import dev.thorhub.core.database.GameVersionEntity
import dev.thorhub.core.database.InstallationAvailability
import dev.thorhub.core.database.InstallationEntity
import dev.thorhub.core.database.InstallationSourceType
import dev.thorhub.core.database.LibraryImport
import dev.thorhub.core.database.PairingState
import dev.thorhub.core.database.SessionState
import dev.thorhub.core.database.ThorHubDatabase
import dev.thorhub.core.model.DeterministicIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Seeds fictional local data only; no ROM, BIOS, emulator, or external service is bundled. */
@Singleton
class FixtureSeeder @Inject constructor(
    private val database: ThorHubDatabase,
) {
    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        val markerId = DeterministicIds.game(NAMESPACE, "Orion Trail").value
        if (database.gameDao().getById(markerId) != null) return@withContext

        val now = Instant.now().toEpochMilli()
        val deviceId = DeterministicIds.device(NAMESPACE, "local-device").value
        val device = DeviceEntity(
            id = deviceId,
            displayName = "Local Android device",
            platform = "ANDROID",
            availabilityState = DeviceAvailabilityState.AVAILABLE,
            pairingState = PairingState.TRUSTED,
            lastSeenAt = now,
            createdAt = now,
            updatedAt = now,
        )
        val games = titles.mapIndexed { index, title ->
            GameEntity(
                id = DeterministicIds.game(NAMESPACE, title).value,
                canonicalTitle = title,
                sortTitle = title,
                lifecycleStatus = if (index < 7) GameLifecycleStatus.PLAYING else GameLifecycleStatus.BACKLOG,
                description = descriptions[index % descriptions.size],
                releaseYear = 1991 + (index % 34),
                isFavorite = index in setOf(1, 4, 9),
                isPinned = index < 3,
                metadataProvenanceJson = "{\"title\":\"GENERATED\",\"description\":\"GENERATED\",\"releaseYear\":\"GENERATED\"}",
                createdAt = now - index * 10_000L,
                updatedAt = now - index * 10_000L,
            )
        }
        val versions = games.mapIndexed { index, game ->
            GameVersionEntity(
                id = DeterministicIds.gameVersion(NAMESPACE, game.id).value,
                gameId = game.id,
                platform = platforms[index % platforms.size],
                edition = if (index % 8 == 0) "Original" else null,
                versionClassification = GameVersionClassification.ORIGINAL,
                isPreferred = true,
                createdAt = now,
                updatedAt = now,
            )
        }
        val installations = versions.map { version ->
            InstallationEntity(
                id = DeterministicIds.installation(NAMESPACE, version.id).value,
                gameVersionId = version.id,
                deviceId = deviceId,
                sourceType = InstallationSourceType.MANUAL,
                sourceLocator = "fixture:${version.id}",
                availabilityState = InstallationAvailability.AVAILABLE,
                discoveredAt = now,
                lastSeenAt = now,
                updatedAt = now,
            )
        }

        database.withTransaction {
            database.libraryDao().importLibrary(
                LibraryImport(
                    games = games,
                    versions = versions,
                    devices = listOf(device),
                    installations = installations,
                ),
            )
            games.take(7).forEachIndexed { index, game ->
                val version = versions[index]
                val installation = installations[index]
                val startedAt = now - (index + 1) * 86_400_000L
                val session = GameSessionEntity(
                    id = DeterministicIds.session(NAMESPACE, "history:${game.id}").value,
                    gameId = game.id,
                    gameVersionId = version.id,
                    installationId = installation.id,
                    deviceId = deviceId,
                    wallStartedAt = startedAt,
                    monotonicStartedNanos = 1_000_000_000L,
                    state = SessionState.PENDING,
                    createdAt = startedAt,
                    updatedAt = startedAt,
                )
                database.sessionDao().createPendingSession(session)
                database.sessionDao().endSession(
                    sessionId = session.id,
                    wallEndedAt = startedAt + (25 + index * 7) * 60_000L,
                    monotonicEndedNanos = 2_000_000_000L,
                    durationMillis = (25 + index * 7) * 60_000L,
                    closeReason = "FIXTURE_HISTORY",
                )
            }
        }
    }

    private companion object {
        const val NAMESPACE = "thor-phase1a-fixture"
        val titles = listOf(
            "Orion Trail", "Glass Harbor", "Midnight Circuit", "Paper Kingdom",
            "Echoes of Cobalt", "Sunken Relay", "Mosslight", "Afterimage Zero",
            "Distant Engines", "Aster Library", "Signal Coast", "Velvet Comet",
            "Rain Archive", "Northbound", "Cinder Atlas", "Static Garden",
            "Soft Collision", "Kite Protocol", "Amber Machine", "Blue Hour",
            "Hollow Frequency", "Lantern Drift", "Cloud Terminal", "Quiet Vector",
        )
        val platforms = listOf("ANDROID", "PS1", "ARCADE", "PC", "PS2", "HANDHELD")
        val descriptions = listOf(
            "A fictional local fixture used to exercise library, detail, notes, and session flows.",
            "Generated metadata keeps the offline experience coherent without a provider key.",
            "No external game data, ROM, BIOS, or emulator is included with this entry.",
        )
    }
}
