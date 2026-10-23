package dev.thorhub.app.platform.session

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.google.common.truth.Truth.assertThat
import dev.thorhub.app.HomeActivity
import dev.thorhub.app.data.HubRepository
import dev.thorhub.app.data.LaunchConfigurationCodec
import dev.thorhub.app.data.LocalDeviceIdentityProvider
import dev.thorhub.app.platform.display.AndroidDisplayCoordinator
import dev.thorhub.app.platform.display.DisplayAssignmentStore
import dev.thorhub.app.platform.launcher.LauncherAdapterFactory
import dev.thorhub.app.platform.storage.LocalSafSaveAdapter
import dev.thorhub.core.database.CompatibilityState
import dev.thorhub.core.database.GameEntity
import dev.thorhub.core.database.GameVersionEntity
import dev.thorhub.core.database.InstallationAvailability
import dev.thorhub.core.database.InstallationEntity
import dev.thorhub.core.database.LauncherAdapterType
import dev.thorhub.core.database.LauncherEntity
import dev.thorhub.core.database.PlayMethodEntity
import dev.thorhub.core.database.SessionState
import dev.thorhub.core.database.ThorHubDatabase
import dev.thorhub.core.model.BeginSessionRequest
import dev.thorhub.core.model.DeviceId
import dev.thorhub.core.model.GameId
import dev.thorhub.core.model.GameSessionId
import dev.thorhub.core.model.GameVersionId
import dev.thorhub.core.model.InstallationId
import dev.thorhub.core.model.LaunchParameterPayload
import dev.thorhub.core.model.MonotonicMark
import dev.thorhub.core.model.PlayMethodId
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EndSessionCoordinatorInstrumentationTest {
    private lateinit var context: Context
    private lateinit var database: ThorHubDatabase
    private lateinit var repository: HubRepository
    private lateinit var tracker: RoomSessionTracker
    private lateinit var coordinator: EndSessionCoordinator
    private lateinit var deviceId: String

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ThorHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val identity = LocalDeviceIdentityProvider(context)
        deviceId = identity.deviceId
        repository = HubRepository(database, identity)
        tracker = RoomSessionTracker(database, context)
        coordinator = EndSessionCoordinator(
            database = database,
            repository = repository,
            sessionTracker = tracker,
            launcherAdapterFactory = LauncherAdapterFactory(context),
            localSaveAdapter = LocalSafSaveAdapter(context, database),
            displayCoordinator = AndroidDisplayCoordinator(context, DisplayAssignmentStore(context)),
        )
        seedPlayableGraph(identity)
    }

    @After
    fun tearDown() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            Stage.entries
                .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                .distinct()
                .forEach(Activity::finish)
        }
        database.close()
    }

    @Test
    fun interruptedEndingRetriesWithoutDuplicateSideEffectsAndKeepsExitEvidenceSeparate() = runBlocking {
        val sessionId = beginActiveSession()
        tracker.beginEndingAt(
            sessionId = sessionId,
            wallClock = Instant.ofEpochMilli(5_000),
            monotonic = MonotonicMark("boot-test", 5_000_000_000L),
        )

        // Simulate process interruption after the note stage but before the
        // terminal session transition. A retry must update the same note ID.
        repository.saveNote(
            gameId = GAME_ID,
            body = NOTE,
            checklist = false,
            pinned = false,
            sessionId = sessionId.value,
            gameVersionId = VERSION_ID,
            deduplicationKey = "end-session:${sessionId.value}",
        )

        val summary = coordinator.end(sessionId, NOTE)
        val repeated = coordinator.end(sessionId, NOTE)

        assertThat(summary.durationMillis).isEqualTo(3_000L)
        assertThat(summary.noteStatus).isEqualTo("SAVED")
        assertThat(summary.snapshotStatus).isEqualTo("UNCONFIGURED")
        assertThat(summary.syncStatus).isEqualTo("NOT_CONFIGURED")
        assertThat(summary.externalRequestStatus).isEqualTo("UNSUPPORTED")
        assertThat(summary.externalClosureStatus).isEqualTo("NOT_VERIFIED")
        assertThat(summary.homeRouteStatus).isEqualTo("REQUEST_ACCEPTED_PHYSICAL_UNKNOWN")
        assertThat(database.sessionDao().getById(sessionId.value)?.state).isEqualTo(SessionState.ENDED)

        val notes = database.notesDao().observeForSession(sessionId.value).first()
        assertThat(notes).hasSize(1)
        assertThat(notes.single().body).isEqualTo(NOTE)
        assertThat(notes.single().gameId).isEqualTo(GAME_ID)
        assertThat(notes.single().gameVersionId).isEqualTo(VERSION_ID)
        assertThat(repeated.noteStatus).isEqualTo("NOT_REPEATED_ALREADY_ENDED")
        assertThat(repeated.snapshotStatus).isEqualTo("NOT_REPEATED_ALREADY_ENDED")
        assertThat(database.notesDao().observeForSession(sessionId.value).first()).hasSize(1)
    }

    private suspend fun beginActiveSession(): GameSessionId {
        val pending = tracker.begin(
            BeginSessionRequest(
                gameId = GameId.of(GAME_ID),
                gameVersionId = GameVersionId.of(VERSION_ID),
                installationId = InstallationId.of(INSTALLATION_ID),
                deviceId = DeviceId.of(deviceId),
                playMethodId = PlayMethodId.of(PLAY_METHOD_ID),
                wallClock = Instant.ofEpochMilli(1_000),
                monotonic = MonotonicMark("boot-test", 1_000_000_000L),
            ),
        ).session ?: error("pending session missing")
        tracker.markLaunchAccepted(
            sessionId = pending.id,
            at = Instant.ofEpochMilli(2_000),
            monotonic = MonotonicMark("boot-test", 2_000_000_000L),
        ) ?: error("active session missing")
        return pending.id
    }

    private suspend fun seedPlayableGraph(identity: LocalDeviceIdentityProvider) {
        val now = Instant.parse("2026-08-24T00:00:00Z").toEpochMilli()
        val component = "${context.packageName}/${HomeActivity::class.java.name}"
        database.deviceDao().upsert(identity.current(now))
        database.gameDao().upsert(GameEntity(GAME_ID, "End Session fixture", "End Session fixture"))
        database.gameVersionDao().upsert(GameVersionEntity(VERSION_ID, GAME_ID, "ANDROID"))
        database.installationDao().upsert(
            InstallationEntity(
                id = INSTALLATION_ID,
                gameVersionId = VERSION_ID,
                deviceId = deviceId,
                availabilityState = InstallationAvailability.AVAILABLE,
            ),
        )
        database.launcherDao().upsert(
            LauncherEntity(
                id = LAUNCHER_ID,
                displayName = "Instrumented Android launcher",
                adapterType = LauncherAdapterType.ANDROID_NATIVE,
                packageCandidatesJson = LaunchConfigurationCodec.stringArray(listOf(context.packageName)),
                componentCandidatesJson = LaunchConfigurationCodec.stringArray(listOf(component)),
            ),
        )
        database.playMethodDao().upsert(
            PlayMethodEntity(
                id = PLAY_METHOD_ID,
                installationId = INSTALLATION_ID,
                deviceId = deviceId,
                launcherId = LAUNCHER_ID,
                displayName = "Instrumented method",
                isPreferred = true,
                compatibilityState = CompatibilityState.READY,
                configurationJson = LaunchConfigurationCodec.encode(
                    LaunchParameterPayload(
                        action = Intent.ACTION_MAIN,
                        componentTemplate = component,
                    ),
                ),
            ),
        )
    }

    private companion object {
        const val GAME_ID = "end-session-game"
        const val VERSION_ID = "end-session-version"
        const val INSTALLATION_ID = "end-session-installation"
        const val LAUNCHER_ID = "end-session-launcher"
        const val PLAY_METHOD_ID = "end-session-play-method"
        const val NOTE = "Retry the hidden route"
    }
}
