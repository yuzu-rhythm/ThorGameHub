package dev.thorhub.app.platform.session

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.thorhub.core.database.CompatibilityState
import dev.thorhub.core.database.DeviceEntity
import dev.thorhub.core.database.GameEntity
import dev.thorhub.core.database.GameSessionEntity
import dev.thorhub.core.database.GameVersionEntity
import dev.thorhub.core.database.InstallationAvailability
import dev.thorhub.core.database.InstallationEntity
import dev.thorhub.core.database.LauncherEntity
import dev.thorhub.core.database.PlayMethodEntity
import dev.thorhub.core.database.SessionState
import dev.thorhub.core.database.ThorHubDatabase
import dev.thorhub.core.model.BeginSessionRequest
import dev.thorhub.core.model.DeviceId
import dev.thorhub.core.model.EndReason
import dev.thorhub.core.model.GameId
import dev.thorhub.core.model.GameSessionId
import dev.thorhub.core.model.GameVersionId
import dev.thorhub.core.model.InstallationId
import dev.thorhub.core.model.MonotonicMark
import dev.thorhub.core.model.PlayMethodId
import dev.thorhub.core.model.SessionEndState
import dev.thorhub.core.model.SuspendReason
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSessionTrackerInstrumentationTest {
    private lateinit var database: ThorHubDatabase
    private lateinit var tracker: RoomSessionTracker

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ThorHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedPlayableGraph()
        tracker = RoomSessionTracker(database, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun durationCountsOnlyActiveIntervalsAndEndRemainsIdempotent() = runBlocking {
        val sessionId = beginAndActivate()

        tracker.heartbeat(sessionId, Instant.ofEpochMilli(4_000), mark("boot-a", 5_000_000_000L))
        tracker.heartbeat(sessionId, Instant.ofEpochMilli(4_100), mark("boot-a", 5_000_000_000L))
        tracker.suspendAt(
            sessionId,
            SuspendReason.USER_REQUEST,
            Instant.ofEpochMilli(6_000),
            mark("boot-a", 7_000_000_000L),
        )
        tracker.resume(sessionId, Instant.ofEpochMilli(100_000), mark("boot-a", 100_000_000_000L))
        tracker.heartbeat(sessionId, Instant.ofEpochMilli(103_000), mark("boot-a", 103_000_000_000L))

        val ended = tracker.endAt(
            sessionId,
            EndReason.USER_REQUEST,
            Instant.ofEpochMilli(105_000),
            mark("boot-a", 105_000_000_000L),
        )
        val repeated = tracker.endAt(
            sessionId,
            EndReason.USER_REQUEST,
            Instant.ofEpochMilli(200_000),
            mark("boot-a", 200_000_000_000L),
        )

        assertThat(ended.state).isEqualTo(SessionEndState.ENDED)
        assertThat(ended.session.timing.accumulatedActive.toMillis()).isEqualTo(10_000L)
        assertThat(repeated.state).isEqualTo(SessionEndState.ALREADY_ENDED)
        assertThat(repeated.session.timing.accumulatedActive.toMillis()).isEqualTo(10_000L)
        assertThat(database.sessionDao().getById(sessionId.value)?.durationMillis).isEqualTo(10_000L)
    }

    @Test
    fun heartbeatAfterBootChangeRecoversWithoutInventingDuration() = runBlocking {
        val sessionId = beginAndActivate()
        tracker.heartbeat(sessionId, Instant.ofEpochMilli(4_000), mark("boot-a", 5_000_000_000L))

        tracker.heartbeat(sessionId, Instant.ofEpochMilli(5_000), mark("boot-b", 1_000_000_000L))

        val persisted = database.sessionDao().getById(sessionId.value) ?: error("missing session")
        assertThat(persisted.state).isEqualTo(SessionState.RECOVERED_ACTIVE)
        assertThat(persisted.durationMillis).isEqualTo(3_000L)
        assertThat(persisted.monotonicBootId).isNull()
        assertThat(persisted.lastMonotonicNanos).isNull()
        assertThat(tracker.activeSession.value).isNull()
    }

    @Test
    fun processRecoveryIsIdempotentAndKeepsLastComparableDuration() = runBlocking {
        val sessionId = beginAndActivate()
        tracker.heartbeat(sessionId, Instant.ofEpochMilli(4_000), mark("boot-a", 5_000_000_000L))

        tracker.recover()
        val first = database.sessionDao().getById(sessionId.value) ?: error("missing recovered session")
        tracker.recover()
        val second = database.sessionDao().getById(sessionId.value) ?: error("missing repeated recovery")

        assertThat(first.state).isEqualTo(SessionState.RECOVERED_ACTIVE)
        assertThat(first.durationMillis).isEqualTo(3_000L)
        assertThat(second).isEqualTo(first)
        assertThat(tracker.activeSession.value).isNull()
    }

    @Test
    fun incompleteLegacyOpenSessionIsPreservedAsInvalidInsteadOfCrashingStartup() = runBlocking {
        database.sessionDao().createPendingSession(
            GameSessionEntity(
                id = "legacy-incomplete-session",
                gameId = GAME_ID,
                gameVersionId = null,
                installationId = null,
                deviceId = null,
                playMethodId = null,
                wallStartedAt = 1_000L,
                monotonicStartedNanos = 1_000_000_000L,
                state = SessionState.PENDING,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )

        val result = tracker.recover()
        val preserved = database.sessionDao().getById("legacy-incomplete-session")

        assertThat(preserved?.state).isEqualTo(SessionState.INVALID)
        assertThat(preserved?.recoveryReason).isEqualTo("INCOMPLETE_SESSION_LINKS")
        assertThat(result.recovered.map { it.id.value }).doesNotContain("legacy-incomplete-session")
    }

    private suspend fun beginAndActivate(): GameSessionId {
        val pending = tracker.begin(
            BeginSessionRequest(
                gameId = GameId.of(GAME_ID),
                gameVersionId = GameVersionId.of(VERSION_ID),
                installationId = InstallationId.of(INSTALLATION_ID),
                deviceId = DeviceId.of(DEVICE_ID),
                playMethodId = PlayMethodId.of(PLAY_METHOD_ID),
                wallClock = Instant.ofEpochMilli(1_000),
                monotonic = mark("boot-a", 1_000_000_000L),
            ),
        ).session ?: error("pending session missing")
        tracker.markLaunchAccepted(
            pending.id,
            Instant.ofEpochMilli(2_000),
            mark("boot-a", 2_000_000_000L),
        ) ?: error("active session missing")
        return pending.id
    }

    private suspend fun seedPlayableGraph() {
        database.deviceDao().upsert(DeviceEntity(DEVICE_ID, "Test device", "ANDROID"))
        database.gameDao().upsert(GameEntity(GAME_ID, "Test game", "Test game"))
        database.gameVersionDao().upsert(GameVersionEntity(VERSION_ID, GAME_ID, "TEST"))
        database.installationDao().upsert(
            InstallationEntity(
                id = INSTALLATION_ID,
                gameVersionId = VERSION_ID,
                deviceId = DEVICE_ID,
                availabilityState = InstallationAvailability.AVAILABLE,
            ),
        )
        database.launcherDao().upsert(LauncherEntity(LAUNCHER_ID, "Test launcher"))
        database.playMethodDao().upsert(
            PlayMethodEntity(
                id = PLAY_METHOD_ID,
                installationId = INSTALLATION_ID,
                deviceId = DEVICE_ID,
                launcherId = LAUNCHER_ID,
                displayName = "Test method",
                compatibilityState = CompatibilityState.READY,
            ),
        )
    }

    private fun mark(bootId: String, nanos: Long) = MonotonicMark(bootId, nanos)

    private companion object {
        const val GAME_ID = "session-game"
        const val VERSION_ID = "session-version"
        const val DEVICE_ID = "session-device"
        const val INSTALLATION_ID = "session-installation"
        const val LAUNCHER_ID = "session-launcher"
        const val PLAY_METHOD_ID = "session-play-method"
    }
}
