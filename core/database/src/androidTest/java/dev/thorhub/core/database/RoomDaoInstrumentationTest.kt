package dev.thorhub.core.database

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDaoInstrumentationTest {
    private lateinit var database: ThorHubDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ThorHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun transactionalImportFeedsLibrarySearchAndContinue() = runBlocking {
        seedPlayableData()

        val pending = database.sessionDao().createPendingSession(TestFixtures.session())
        assertThat(pending.state).isEqualTo(SessionState.PENDING)
        database.sessionDao().markActive(TestFixtures.sessionId, 2_100L)
        database.sessionDao().endSession(
            sessionId = TestFixtures.sessionId,
            wallEndedAt = 2_500L,
            monotonicEndedNanos = 15_000L,
            durationMillis = 500L,
            closeReason = "USER_END",
        )

        val library = database.gameDao().observeLibrary().first()
        assertThat(library).containsExactly(TestFixtures.game())

        val search = database.gameDao().observeSearch("quest").first()
        assertThat(search).hasSize(1)
        assertThat(search.single().availableInstallationCount).isEqualTo(1)

        val continueRows = database.gameDao().observeContinue().first()
        assertThat(continueRows).hasSize(1)
        assertThat(continueRows.single().lastSessionId).isEqualTo(TestFixtures.sessionId)
        assertThat(continueRows.single().sessionCount).isEqualTo(1)
    }

    @Test
    fun missingInstallationIsUnavailableWithoutDeletingGameOrHistory() = runBlocking {
        seedPlayableData()
        database.sessionDao().createPendingSession(TestFixtures.session())

        val changed = database.installationDao().reconcileScan(
            libraryRootId = TestFixtures.rootId,
            seenInstallationIds = emptyList(),
            at = 4_000L,
        )

        assertThat(changed).isEqualTo(1)
        assertThat(database.installationDao().getById(TestFixtures.installationId)?.availabilityState)
            .isEqualTo(InstallationAvailability.UNAVAILABLE)
        assertThat(database.gameDao().getById(TestFixtures.gameId)?.id)
            .isEqualTo(TestFixtures.gameId)
        assertThat(database.sessionDao().getById(TestFixtures.sessionId)?.id)
            .isEqualTo(TestFixtures.sessionId)
    }

    @Test
    fun sessionPendingRecoveryAndEndAreIdempotent() = runBlocking {
        seedPlayableData()
        val created = database.sessionDao().createPendingSession(TestFixtures.session())
        assertThat(database.sessionDao().observePending().first()).containsExactly(created)

        database.sessionDao().markActive(TestFixtures.sessionId, 2_100L)
        val recoveryRecords = database.sessionDao().recoverAfterProcessRestart(
            at = 3_000L,
            reason = "PROCESS_RESTART_TEST",
        )
        assertThat(recoveryRecords).hasSize(1)
        assertThat(recoveryRecords.single().state).isEqualTo(SessionState.RECOVERED_ACTIVE)
        assertThat(recoveryRecords.single().recoveryReason).isEqualTo("PROCESS_RESTART_TEST")

        val ended = database.sessionDao().endSession(
            sessionId = TestFixtures.sessionId,
            wallEndedAt = 4_000L,
            monotonicEndedNanos = 30_000L,
            durationMillis = 1_900L,
            closeReason = "USER_END",
        ) ?: error("expected session")
        val repeated = database.sessionDao().endSession(
            sessionId = TestFixtures.sessionId,
            wallEndedAt = 5_000L,
            monotonicEndedNanos = 40_000L,
            durationMillis = 2_900L,
            closeReason = "DUPLICATE_END",
        ) ?: error("expected session")

        assertThat(ended.state).isEqualTo(SessionState.ENDED)
        assertThat(repeated.wallEndedAt).isEqualTo(ended.wallEndedAt)
        assertThat(repeated.durationMillis).isEqualTo(ended.durationMillis)
        assertThat(repeated.closeReason).isEqualTo(ended.closeReason)
    }

    @Test
    fun noteChecklistPinAndSearchAreStoredAtomically() = runBlocking {
        seedPlayableData()
        database.sessionDao().createPendingSession(TestFixtures.session())
        val note = NoteEntity(
            id = "note-1",
            gameId = TestFixtures.gameId,
            gameVersionId = TestFixtures.versionId,
            sessionId = TestFixtures.sessionId,
            deviceId = TestFixtures.deviceId,
            noteType = NoteType.CHECKLIST,
            body = "Find the moon key",
            createdAt = 5_000L,
            updatedAt = 5_000L,
        )
        val items = listOf(
            NoteChecklistItemEntity("item-1", note.id, 0, "Open the west door"),
            NoteChecklistItemEntity("item-2", note.id, 1, "Return to the shrine"),
        )

        val saved = database.notesDao().saveNoteWithChecklist(note, items)
        assertThat(saved.checklistItems.map { it.text })
            .containsExactly("Open the west door", "Return to the shrine")
            .inOrder()

        database.notesDao().setPinned(note.id, true, 5_100L)
        val pinned = database.notesDao().observePinned(TestFixtures.gameId).first()
        assertThat(pinned.single().id).isEqualTo(note.id)
        assertThat(database.notesDao().search("moon").first().single().id).isEqualTo(note.id)
        assertThat(database.notesDao().getById(note.id)?.isPinned).isTrue()
    }

    @Test
    fun snapshotParentLineageAndManifestFilesAreQueryable() = runBlocking {
        seedPlayableData()
        database.saveDao().upsertProfile(TestFixtures.profile())
        val root = SaveSnapshotEntity(
            id = "snapshot-root",
            saveProfileId = TestFixtures.profileId,
            deviceId = TestFixtures.deviceId,
            captureReason = SnapshotReason.PRELAUNCH,
            snapshotState = SnapshotState.COMPLETE,
            compatibilityFingerprint = "engine-v1",
            storageToken = "snapshots/snapshot-root",
            contentHash = "root-hash",
            capturedAt = 6_000L,
        )
        val child = root.copy(
            id = "snapshot-child",
            parentSnapshotId = root.id,
            captureReason = SnapshotReason.END_SESSION,
            storageToken = "snapshots/snapshot-child",
            contentHash = "child-hash",
            capturedAt = 7_000L,
        )
        database.saveDao().saveSnapshotWithFiles(
            root,
            listOf(SaveSnapshotFileEntity("file-root", root.id, "save.dat", 12L, "file-hash")),
        )
        database.saveDao().saveSnapshotWithFiles(
            child,
            listOf(SaveSnapshotFileEntity("file-child", child.id, "save.dat", 14L, "child-file-hash")),
        )

        assertThat(database.saveDao().getLineage(child.id).map { it.id })
            .containsExactly(root.id, child.id)
            .inOrder()
        assertThat(database.saveDao().getFiles(child.id).single().relativePath).isEqualTo("save.dat")
        assertThat(database.saveDao().observeChildren(root.id).first().single().id).isEqualTo(child.id)
    }

    @Test
    fun collectionsCapabilitiesDiagnosticsAndDisplayAssignmentsRemainTyped() = runBlocking {
        seedPlayableData()
        val collection = CollectionEntity("collection-1", "Favorites", CollectionType.MANUAL, createdAt = 8_000L)
        database.collectionDao().upsertWithMembers(
            collection,
            listOf(CollectionMemberEntity(collection.id, TestFixtures.gameId, position = 0, addedAt = 8_000L)),
        )
        assertThat(database.collectionDao().observeGames(collection.id).first().single().gameId)
            .isEqualTo(TestFixtures.gameId)

        val capability = CapabilityRecordEntity(
            id = "capability-1",
            deviceId = TestFixtures.deviceId,
            buildFingerprint = "test-build",
            capabilityKey = "secondary_display",
            capabilityState = CapabilityState.NEEDS_ACTION,
            evidenceCode = "PERMISSION_REQUIRED",
            evidenceSummary = "User setup is required",
            testedAt = 8_000L,
        )
        database.capabilityDao().upsert(capability)
        database.diagnosticDao().insert(
            DiagnosticEventEntity(
                id = "diagnostic-1",
                deviceId = TestFixtures.deviceId,
                capabilityRecordId = capability.id,
                eventCode = "DISPLAY_PROBE",
                summary = "Probe completed",
                sanitizedMetadataJson = "{\"result\":\"setup\"}",
                occurredAt = 8_000L,
            ),
        )
        database.displayAssignmentDao().upsert(
            DisplayAssignmentEntity(
                id = "display-1",
                deviceId = TestFixtures.deviceId,
                displaySignatureHash = "display-signature-hash",
                displayRole = DisplayRole.LOWER,
                isVerified = false,
                buildFingerprintHash = "test-build-hash",
                assignedAt = 8_000L,
                evidenceCode = "UNKNOWN",
            ),
        )

        assertThat(database.capabilityDao().observeForDevice(TestFixtures.deviceId).first().single().capabilityState)
            .isEqualTo(CapabilityState.NEEDS_ACTION)
        assertThat(database.diagnosticDao().observeByCode("DISPLAY_PROBE").first().single().sanitizedMetadataJson)
            .doesNotContain("api_key")
        assertThat(database.displayAssignmentDao().observeForDevice(TestFixtures.deviceId).first().single().displayRole)
            .isEqualTo(DisplayRole.LOWER)
    }

    @Test
    fun fiveThousandGameImportAndSearchRemainUsable() = runBlocking {
        val count = 5_000
        val games = ArrayList<GameEntity>(count)
        val versions = ArrayList<GameVersionEntity>(count)
        val installations = ArrayList<InstallationEntity>(count)
        repeat(count) { index ->
            val ordinal = index.toString().padStart(5, '0')
            val gameId = "fixture-game-$ordinal"
            val versionId = "fixture-version-$ordinal"
            games += GameEntity(
                id = gameId,
                canonicalTitle = "Fixture Game $ordinal",
                sortTitle = "Fixture Game $ordinal",
                createdAt = 1_000L,
                updatedAt = 1_000L,
            )
            versions += GameVersionEntity(
                id = versionId,
                gameId = gameId,
                platform = "FIXTURE",
                createdAt = 1_000L,
                updatedAt = 1_000L,
            )
            installations += InstallationEntity(
                id = "fixture-installation-$ordinal",
                gameVersionId = versionId,
                deviceId = TestFixtures.deviceId,
                libraryRootId = TestFixtures.rootId,
                sourceType = InstallationSourceType.ROM,
                sourceLocator = "fixture:$ordinal",
                availabilityState = InstallationAvailability.AVAILABLE,
                discoveredAt = 1_000L,
                lastSeenAt = 1_000L,
                updatedAt = 1_000L,
            )
        }

        val importStarted = SystemClock.elapsedRealtime()
        database.libraryDao().importLibrary(
            LibraryImport(
                games = games,
                versions = versions,
                devices = listOf(TestFixtures.device()),
                roots = listOf(TestFixtures.root()),
                installations = installations,
            ),
        )
        val importMillis = SystemClock.elapsedRealtime() - importStarted

        val pagingStarted = SystemClock.elapsedRealtime()
        val pageResult = database.gameDao().pagingLibrary().load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 64,
                placeholdersEnabled = true,
            ),
        )
        val pagingMillis = SystemClock.elapsedRealtime() - pagingStarted
        val firstPage = pageResult as PagingSource.LoadResult.Page

        val searchStarted = SystemClock.elapsedRealtime()
        val search = database.gameDao().observeSearch("04999").first()
        val searchMillis = SystemClock.elapsedRealtime() - searchStarted

        Log.i(
            "ThorPerf",
            "fixture_count=$count import_ms=$importMillis first_page_ms=$pagingMillis search_ms=$searchMillis",
        )
        assertThat(firstPage.data).hasSize(64)
        assertThat(firstPage.data.first().gameId).isEqualTo("fixture-game-00000")
        assertThat(firstPage.itemsBefore).isEqualTo(0)
        assertThat(firstPage.itemsAfter).isEqualTo(count - 64)
        assertThat(search.map { it.gameId }).containsExactly("fixture-game-04999")
        assertThat(search.single().availableInstallationCount).isEqualTo(1)
        // Wide emulator guardrails catch algorithmic regressions without
        // pretending to certify target-hardware performance.
        assertThat(importMillis).isLessThan(60_000L)
        assertThat(pagingMillis).isLessThan(10_000L)
        assertThat(searchMillis).isLessThan(10_000L)
    }

    private suspend fun seedPlayableData() {
        database.libraryDao().importLibrary(TestFixtures.baseImport())
        database.launcherDao().upsert(TestFixtures.launcher())
        database.playMethodDao().upsert(TestFixtures.playMethod())
    }
}
