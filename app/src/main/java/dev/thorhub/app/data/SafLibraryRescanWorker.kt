package dev.thorhub.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.InstallIn
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.components.SingletonComponent
import dev.thorhub.core.data.LibraryRepository
import dev.thorhub.core.data.SafRomLibrarySource
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

/** Durable, constrained incremental rescan for roots already granted by the user. */
class SafLibraryRescanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val rootId = inputData.getString(ROOT_ID) ?: return Result.failure(workDataOf(ERROR to "MISSING_ROOT_ID"))
        val dependencies = EntryPoints.get(applicationContext, Dependencies::class.java)
        val database = dependencies.database()
        val storedEntity = database.libraryRootDao().getById(rootId)
            ?: return Result.failure(workDataOf(ERROR to "ROOT_NOT_FOUND"))
        val now = Instant.now()
        val device = dependencies.localDeviceIdentity().current(now.toEpochMilli())
        database.deviceDao().upsert(device)
        val entity = storedEntity.copy(deviceId = device.id, updatedAt = now.toEpochMilli())
        if (entity != storedEntity) {
            database.libraryRootDao().upsert(entity)
        }
        val root = LibraryRoot(
            id = LibraryRootId.of(entity.id),
            displayName = "ROM library",
            sourceType = InstallationSourceType.ROM,
            reference = InstallationReference.GrantedDocument(entity.treeUri),
            platformHint = runCatching { GamePlatform.valueOf(entity.platformHint.orEmpty()) }
                .getOrDefault(GamePlatform.UNKNOWN),
            accessState = when (entity.rootState) {
                LibraryRootState.REVOKED -> LibraryRootAccessState.REVOKED
                LibraryRootState.ERROR -> LibraryRootAccessState.ERROR
                LibraryRootState.ACTIVE -> LibraryRootAccessState.READY
                else -> LibraryRootAccessState.UNKNOWN
            },
            providerAuthority = entity.providerAuthority,
        )
        val repository = LibraryRepository(database)
        val source = SafRomLibrarySource(applicationContext, root, database, DeviceId.of(device.id))
        var completed = false
        var failureCode: String? = null
        return runCatching {
            repository.scanAndImport(
                source = source,
                root = entity,
                device = device,
                request = ScanRequest(
                    rootId = root.id,
                    incremental = true,
                    allowFullHash = false,
                    requestedAt = now,
                ),
            ).collect { event ->
                when (event) {
                    is ScanEvent.Completed -> completed = true
                    is ScanEvent.Failed -> failureCode = event.code
                    else -> Unit
                }
            }
            when {
                completed -> Result.success()
                failureCode in permissionFailures -> Result.failure(workDataOf(ERROR to failureCode))
                else -> Result.retry()
            }
        }.getOrElse { failure ->
            Result.failure(workDataOf(ERROR to (failure::class.simpleName ?: "WORKER_ERROR")))
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun database(): ThorHubDatabase
        fun localDeviceIdentity(): LocalDeviceIdentityProvider
    }

    companion object {
        const val ROOT_ID = "root_id"
        const val ERROR = "error"
        private val permissionFailures = setOf("SAF_PERMISSION_REVOKED", "SAF_NEEDS_SETUP")
    }
}
