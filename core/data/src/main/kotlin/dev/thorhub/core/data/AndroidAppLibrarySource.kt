package dev.thorhub.core.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
import dev.thorhub.core.model.LibrarySource
import dev.thorhub.core.model.LibrarySourceId
import dev.thorhub.core.model.ResolutionResult
import dev.thorhub.core.model.ResolutionState
import dev.thorhub.core.model.ScanEvent
import dev.thorhub.core.model.ScanRequest
import dev.thorhub.core.model.SourceAvailability
import dev.thorhub.core.model.SourceAvailabilityState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Instant

data class AndroidLauncherQuerySpec(
    val action: String = Intent.ACTION_MAIN,
    val category: String = Intent.CATEGORY_LAUNCHER,
)

/** Pure query contract kept separate from PackageManager for JVM assertions. */
object AndroidAppQuery {
    val spec: AndroidLauncherQuerySpec = AndroidLauncherQuerySpec()

    fun intent(): Intent = Intent(spec.action).addCategory(spec.category)

    fun shouldInclude(activityInfo: ActivityInfo?, selfPackageName: String): Boolean {
        val packageName = activityInfo?.packageName ?: return false
        return packageName != selfPackageName && activityInfo.exported
    }
}

data class AndroidIconReference(
    val packageName: String,
    val resourceId: Int?,
)

/**
 * Package/activity metadata is a source record, not a Drawable. The resource
 * ID remains resolvable through PackageManager and avoids copying icon bytes
 * into the data layer.
 */
data class AndroidAppDescriptor(
    val stableId: String,
    val packageName: String,
    val activityName: String,
    val componentName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long?,
    val icon: AndroidIconReference,
) {
    fun toDiscoveredItem(): DiscoveredItem = DiscoveredItem(
        rootId = null,
        sourceType = InstallationSourceType.ANDROID_APP,
        reference = InstallationReference.ExternalIdentifier(componentName),
        titleHint = label,
        platformHint = GamePlatform.ANDROID,
    )
}

class AndroidAppLibrarySource(
    private val packageManager: PackageManager,
    private val selfPackageName: String,
    private val deviceId: DeviceId? = null,
    private val scanDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: () -> Instant = { Instant.now() },
) : LibrarySource {
    constructor(
        context: Context,
        deviceId: DeviceId? = null,
        scanDispatcher: CoroutineDispatcher = Dispatchers.Default,
        clock: () -> Instant = { Instant.now() },
    ) : this(
        packageManager = context.packageManager,
        selfPackageName = context.packageName,
        deviceId = deviceId,
        scanDispatcher = scanDispatcher,
        clock = clock,
    )

    override val id: LibrarySourceId = LibrarySourceId.of("android-apps")

    override suspend fun availability(): SourceAvailability = withContext(scanDispatcher) {
        try {
            descriptors()
            SourceAvailability(SourceAvailabilityState.READY, checkedAt = clock())
        } catch (security: SecurityException) {
            SourceAvailability(
                state = SourceAvailabilityState.ERROR,
                detail = "Launcher activity query failed (${security::class.simpleName ?: "package visibility"}).",
                checkedAt = clock(),
            )
        } catch (failure: Throwable) {
            SourceAvailability(
                state = SourceAvailabilityState.ERROR,
                detail = "Android app discovery failed (${failure::class.simpleName ?: "package manager error"}).",
                checkedAt = clock(),
            )
        }
    }

    override fun scan(request: ScanRequest): Flow<ScanEvent> = flow {
        emit(ScanEvent.Started(clock()))
        if (request.rootId != null) {
            emit(
                ScanEvent.Failed(
                    code = "ROOT_UNSUPPORTED",
                    detail = "Android app discovery does not scan a SAF root.",
                    emittedAt = clock(),
                ),
            )
            return@flow
        }
        try {
            var count = 0
            for (descriptor in descriptors()) {
                emit(ScanEvent.ItemDiscovered(descriptor.toDiscoveredItem(), clock()))
                count += 1
            }
            emit(ScanEvent.Completed(count, clock()))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (security: SecurityException) {
            emit(
                ScanEvent.Failed(
                    code = "PACKAGE_VISIBILITY_ERROR",
                    detail = "Android package visibility prevented launcher discovery.",
                    emittedAt = clock(),
                ),
            )
        } catch (failure: Throwable) {
            emit(
                ScanEvent.Failed(
                    code = "ANDROID_APP_SCAN_FAILED",
                    detail = "Android app discovery failed (${failure::class.simpleName ?: "package manager error"}).",
                    emittedAt = clock(),
                ),
            )
        }
    }.flowOn(scanDispatcher)

    override suspend fun resolve(item: DiscoveredItem): ResolutionResult = withContext(scanDispatcher) {
        if (item.sourceType != InstallationSourceType.ANDROID_APP || item.rootId != null) {
            return@withContext ResolutionResult(
                state = ResolutionState.UNSUPPORTED,
                detail = "Item does not belong to the Android app source",
            )
        }
        val component = (item.reference as? InstallationReference.ExternalIdentifier)?.value
            ?: return@withContext ResolutionResult(
                state = ResolutionState.FAILED,
                detail = "Android app item has no component identifier",
            )
        val descriptor = descriptors().firstOrNull { it.componentName == component }
            ?: return@withContext ResolutionResult(
                state = ResolutionState.UNKNOWN,
                detail = "Android app activity is no longer visible",
            )
        val observedAt = clock()
        val game = Game(
            id = LibraryRecordIds.game(item),
            title = descriptor.label,
            sortTitle = descriptor.label,
            lifecycle = GameLifecycle.UNKNOWN,
            createdAt = observedAt,
            updatedAt = observedAt,
        )
        val version = GameVersion(
            id = LibraryRecordIds.version(item),
            gameId = game.id,
            platform = GamePlatform.ANDROID,
            kind = GameVersionKind.ORIGINAL,
            externalIdentifiers = mapOf(
                "android.package" to descriptor.packageName,
                "android.activity" to descriptor.activityName,
                "android.component" to descriptor.componentName,
            ),
            createdAt = observedAt,
            updatedAt = observedAt,
        )
        if (deviceId == null) {
            return@withContext ResolutionResult(
                state = ResolutionState.NEEDS_REVIEW,
                game = game,
                version = version,
                detail = "A local device identity is required before an app installation can be fully resolved.",
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
                sourceType = InstallationSourceType.ANDROID_APP,
                reference = InstallationReference.ExternalIdentifier(descriptor.componentName),
                availability = InstallationAvailability.AVAILABLE,
                discoveredAt = observedAt,
                lastSeenAt = observedAt,
            ),
        )
    }

    suspend fun discoverDescriptors(): List<AndroidAppDescriptor> = withContext(scanDispatcher) { descriptors() }

    suspend fun resolveDescriptor(componentName: String): AndroidAppDescriptor? = withContext(scanDispatcher) {
        descriptors().firstOrNull { it.componentName == componentName }
    }

    private fun descriptors(): List<AndroidAppDescriptor> = queryResolveInfos()
        .asSequence()
        .mapNotNull { info -> info.activityInfo?.takeIf { AndroidAppQuery.shouldInclude(it, selfPackageName) } }
        .mapNotNull(::descriptorFor)
        .distinctBy { it.stableId }
        .sortedWith(
            compareBy<AndroidAppDescriptor, String>(String.CASE_INSENSITIVE_ORDER) { it.label }
                .thenBy { it.componentName },
        )
        .toList()

    @Suppress("DEPRECATION")
    private fun queryResolveInfos(): List<ResolveInfo> = packageManager.queryIntentActivities(
        AndroidAppQuery.intent(),
        PackageManager.ResolveInfoFlags.of(0L),
    )

    private fun descriptorFor(activityInfo: ActivityInfo): AndroidAppDescriptor? {
        val packageName = activityInfo.packageName.takeIf(String::isNotBlank) ?: return null
        val activityName = activityInfo.name.takeIf(String::isNotBlank) ?: return null
        val componentName = ComponentName(packageName, activityName).flattenToString()
        val applicationInfo = activityInfo.applicationInfo ?: return null
        val label = runCatching { activityInfo.loadLabel(packageManager).toString() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: runCatching { packageManager.getApplicationLabel(applicationInfo).toString() }
                .getOrDefault(packageName)
        val packageInfo = runCatching {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        }.getOrNull()
        val activityIcon = activityInfo.icon.takeIf { it != 0 }
        val iconId = activityIcon ?: applicationInfo.icon.takeIf { it != 0 }
        return AndroidAppDescriptor(
            stableId = LibraryRecordIds.androidComponent(packageName, activityName),
            packageName = packageName,
            activityName = activityName,
            componentName = componentName,
            label = label,
            versionName = packageInfo?.versionName,
            versionCode = packageInfo?.longVersionCode,
            icon = AndroidIconReference(packageName, iconId),
        )
    }
}
