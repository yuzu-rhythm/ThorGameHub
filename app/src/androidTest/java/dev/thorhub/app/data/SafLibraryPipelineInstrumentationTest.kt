package dev.thorhub.app.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.thorhub.core.data.LibraryRepository
import dev.thorhub.core.data.SafDocument
import dev.thorhub.core.data.SafDocumentProvider
import dev.thorhub.core.data.SafRomLibrarySource
import dev.thorhub.core.database.DeviceEntity
import dev.thorhub.core.database.InstallationAvailability
import dev.thorhub.core.database.LibraryRootEntity
import dev.thorhub.core.database.LibraryRootSourceType
import dev.thorhub.core.database.LibraryRootState
import dev.thorhub.core.database.ThorHubDatabase
import dev.thorhub.core.model.DeviceId
import dev.thorhub.core.model.GamePlatform
import dev.thorhub.core.model.InstallationReference
import dev.thorhub.core.model.InstallationSourceType
import dev.thorhub.core.model.LibraryRoot
import dev.thorhub.core.model.LibraryRootAccessState
import dev.thorhub.core.model.LibraryRootId
import dev.thorhub.core.model.ScanEvent
import dev.thorhub.core.model.ScanRequest
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafLibraryPipelineInstrumentationTest {
    private lateinit var context: Context
    private lateinit var database: ThorHubDatabase

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, ThorHubDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.deviceDao().upsert(DEVICE)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun providerBackedScanFingerprintsImportsAndFailsClosedAfterGrantLoss() {
        runBlocking {
            val authority = "${context.packageName}.launch-fixture"
            val treeUri = DocumentsContract.buildTreeDocumentUri(authority, "rom-root")
            val romUri = Uri.parse("content://$authority/document/fixture-game")
            val ignoredUri = Uri.parse("content://$authority/document/readme")
            val tree = FixtureDocument(
                uri = treeUri,
                name = "ROMs",
                isDirectory = true,
                children = listOf(
                    FixtureDocument(
                        uri = romUri,
                        name = "Fixture_Game.nes",
                        lengthBytes = FIXTURE_PAYLOAD_SIZE,
                        lastModifiedMillis = 1_700_000_000_000L,
                    ),
                    FixtureDocument(
                        uri = ignoredUri,
                        name = "README.txt",
                        lengthBytes = 5L,
                    ),
                ),
            )
            val entity = rootEntity(treeUri)
            val root = domainRoot(treeUri)
            val repository = LibraryRepository(database, ioDispatcher = Dispatchers.IO, clock = { NOW })
            database.libraryRootDao().upsert(entity)

            val importedEvents = repository.scanAndImport(
                source = source(root, tree, hasGrant = true),
                root = entity,
                device = DEVICE,
                request = ScanRequest(rootId = root.id, requestedAt = NOW),
            ).toList()

            assertThat(importedEvents.filterIsInstance<ScanEvent.Completed>().single().itemCount).isEqualTo(1)
            val games = database.gameDao().observeLibrary().first()
            assertThat(games.map { it.canonicalTitle }).containsExactly("Fixture Game")
            val installation = database.installationDao().observeForRoot(ROOT_ID).first().single()
            assertThat(installation.availabilityState).isEqualTo(InstallationAvailability.AVAILABLE)
            assertThat(installation.sourceLocator).isEqualTo(romUri.toString())
            assertThat(installation.contentFingerprintId).isNotNull()

            val revokedEvents = repository.scanAndImport(
                source = source(root, tree, hasGrant = false),
                root = entity,
                device = DEVICE,
                request = ScanRequest(rootId = root.id, requestedAt = NOW.plusSeconds(60)),
            ).toList()

            assertThat(revokedEvents.filterIsInstance<ScanEvent.Failed>().single().code)
                .isEqualTo("SAF_PERMISSION_REVOKED")
            assertThat(database.libraryRootDao().getById(ROOT_ID)?.rootState).isEqualTo(LibraryRootState.REVOKED)
            val preserved = database.installationDao().observeForRoot(ROOT_ID).first().single()
            assertThat(preserved.id).isEqualTo(installation.id)
            assertThat(preserved.availabilityState).isEqualTo(InstallationAvailability.AVAILABLE)
            assertThat(database.gameDao().observeLibrary().first().map { it.id }).containsExactly(games.single().id)
            Unit
        }
    }

    private fun source(root: LibraryRoot, tree: SafDocument, hasGrant: Boolean) = SafRomLibrarySource(
        contentResolver = context.contentResolver,
        documentProvider = object : SafDocumentProvider {
            override fun fromTreeUri(treeUri: Uri): SafDocument = tree
        },
        root = root,
        deviceId = DeviceId.of(DEVICE_ID),
        scanDispatcher = Dispatchers.IO,
        clock = { NOW },
        hasReadGrant = { _: ContentResolver, _: Uri -> hasGrant },
    )

    private fun rootEntity(treeUri: Uri) = LibraryRootEntity(
        id = ROOT_ID,
        deviceId = DEVICE_ID,
        treeUri = treeUri.toString(),
        platformHint = GamePlatform.NINTENDO.name,
        sourceType = LibraryRootSourceType.ROM,
        rootState = LibraryRootState.UNKNOWN,
        createdAt = NOW.toEpochMilli(),
        updatedAt = NOW.toEpochMilli(),
    )

    private fun domainRoot(treeUri: Uri) = LibraryRoot(
        id = LibraryRootId.of(ROOT_ID),
        displayName = "Fixture ROM root",
        sourceType = InstallationSourceType.ROM,
        reference = InstallationReference.GrantedDocument(treeUri.toString()),
        platformHint = GamePlatform.NINTENDO,
        accessState = LibraryRootAccessState.READY,
        providerAuthority = treeUri.authority,
    )

    private data class FixtureDocument(
        override val uri: Uri,
        override val name: String?,
        override val lengthBytes: Long = 0L,
        override val lastModifiedMillis: Long? = null,
        override val isDirectory: Boolean = false,
        override val exists: Boolean = true,
        val children: List<SafDocument> = emptyList(),
    ) : SafDocument {
        override fun listFiles(): List<SafDocument> = children
    }

    private companion object {
        const val ROOT_ID = "instrumented-saf-root"
        const val DEVICE_ID = "instrumented-saf-device"
        // LaunchContentProvider serves exactly "thor-launch-fixture".
        const val FIXTURE_PAYLOAD_SIZE = 19L
        val NOW: Instant = Instant.parse("2026-08-24T00:00:00Z")
        val DEVICE = DeviceEntity(
            id = DEVICE_ID,
            displayName = "Instrumented SAF device",
            platform = "ANDROID",
            createdAt = NOW.toEpochMilli(),
            updatedAt = NOW.toEpochMilli(),
        )
    }
}
