package dev.thorhub.app.data

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.thorhub.core.data.LibraryRecordIds
import dev.thorhub.core.data.LibraryRepository
import dev.thorhub.core.data.SafRomLibrarySource
import dev.thorhub.core.data.SafUriGrants
import dev.thorhub.core.database.LibraryRootEntity
import dev.thorhub.core.database.LibraryRootSourceType
import dev.thorhub.core.database.LibraryRootState
import dev.thorhub.core.database.ThorHubDatabase
import dev.thorhub.core.model.GamePlatform
import dev.thorhub.core.model.DeviceId
import dev.thorhub.core.model.InstallationReference
import dev.thorhub.core.model.InstallationSourceType
import dev.thorhub.core.model.LibraryRoot
import dev.thorhub.core.model.LibraryRootAccessState
import dev.thorhub.core.model.LibraryRootId
import dev.thorhub.core.model.ScanEvent
import dev.thorhub.core.model.ScanRequest
import kotlinx.coroutines.flow.collect
import java.time.Instant
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

data class SafImportResult(
    val rootId: String,
    val discoveredCount: Int,
    val completed: Boolean,
    val failureCode: String? = null,
)

@Singleton
class SafLibraryManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: ThorHubDatabase,
    private val localDeviceIdentity: LocalDeviceIdentityProvider,
) {
    suspend fun persistAndScan(treeUri: Uri, platformHint: GamePlatform = GamePlatform.UNKNOWN): SafImportResult {
        val flags = SafUriGrants.persistReadGrant(context.contentResolver, treeUri)
        val now = Instant.now()
        val device = localDeviceIdentity.current(now.toEpochMilli())
        val rootId = "root-${LibraryRecordIds.sha256Hex(treeUri.toString()).take(40)}"
        val entity = LibraryRootEntity(
            id = rootId,
            deviceId = device.id,
            treeUri = treeUri.toString(),
            platformHint = platformHint.name,
            sourceType = LibraryRootSourceType.ROM,
            accessFlags = flags,
            providerAuthority = treeUri.authority,
            rootState = LibraryRootState.UNKNOWN,
            createdAt = now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
        )
        val root = LibraryRoot(
            id = LibraryRootId.of(rootId),
            displayName = "ROM library",
            sourceType = InstallationSourceType.ROM,
            reference = InstallationReference.GrantedDocument(treeUri.toString()),
            platformHint = platformHint,
            accessState = LibraryRootAccessState.READY,
            providerAuthority = treeUri.authority,
        )
        val repository = LibraryRepository(database)
        database.deviceDao().upsert(device)
        repository.persistSelectedRoot(entity)
        val source = SafRomLibrarySource(context, root, database, DeviceId.of(device.id))
        var discovered = 0
        var completed = false
        var failureCode: String? = null
        repository.scanAndImport(
            source = source,
            root = entity,
            device = device,
            request = ScanRequest(rootId = root.id, incremental = true, allowFullHash = false, requestedAt = now),
        ).collect { event ->
            when (event) {
                is ScanEvent.ItemDiscovered -> discovered += 1
                is ScanEvent.Completed -> completed = true
                is ScanEvent.Failed -> failureCode = event.code
                else -> Unit
            }
        }
        scheduleIncrementalRescan(rootId)
        return SafImportResult(rootId, discovered, completed, failureCode)
    }

    private fun scheduleIncrementalRescan(rootId: String) {
        val request = PeriodicWorkRequestBuilder<SafLibraryRescanWorker>(Duration.ofHours(12))
            .setInputData(workDataOf(SafLibraryRescanWorker.ROOT_ID to rootId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(15))
            .addTag("thor-library-rescan")
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "thor-library-rescan-$rootId",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
