package dev.thorhub.app.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map as mapPaging
import androidx.room.withTransaction
import dev.thorhub.app.platform.diagnostics.InstalledLaunchableApp
import dev.thorhub.app.platform.launcher.GameNativeSource
import dev.thorhub.app.platform.launcher.LauncherProfiles
import dev.thorhub.app.platform.launcher.TypedExtrasTemplateParser
import dev.thorhub.core.database.ArtworkAssetEntity
import dev.thorhub.core.database.ArtworkKind
import dev.thorhub.core.database.CompatibilityState
import dev.thorhub.core.database.ContinueGameRow
import dev.thorhub.core.database.DeviceEntity
import dev.thorhub.core.database.GameEntity
import dev.thorhub.core.database.GameLifecycleStatus
import dev.thorhub.core.database.GameVersionClassification
import dev.thorhub.core.database.GameVersionEntity
import dev.thorhub.core.database.InstallationAvailability
import dev.thorhub.core.database.InstallationEntity
import dev.thorhub.core.database.InstallationSourceType
import dev.thorhub.core.database.LibraryGameRow
import dev.thorhub.core.database.LauncherAdapterType
import dev.thorhub.core.database.LauncherEntity
import dev.thorhub.core.database.NoteChecklistItemEntity
import dev.thorhub.core.database.NoteEntity
import dev.thorhub.core.database.NoteType
import dev.thorhub.core.database.PlayMethodEntity
import dev.thorhub.core.database.SaveAccessMethod
import dev.thorhub.core.database.SaveProfileEntity
import dev.thorhub.core.database.SaveState
import dev.thorhub.core.database.SaveType
import dev.thorhub.core.database.SearchGameRow
import dev.thorhub.core.database.SessionWithGameRow
import dev.thorhub.core.database.ThorHubDatabase
import dev.thorhub.core.database.VerificationStatus
import dev.thorhub.core.model.DeterministicIds
import dev.thorhub.core.model.ContentUriGrantMode
import dev.thorhub.core.model.LaunchParameterPayload
import dev.thorhub.core.model.LauncherId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HubRepository @Inject constructor(
    private val database: ThorHubDatabase,
    private val localDeviceIdentity: LocalDeviceIdentityProvider,
) {
    fun pagedLibrary(
        query: String,
        platform: String,
        lifecycle: String,
        sortMode: String,
    ): Flow<PagingData<LibraryGameSummary>> = Pager(
        config = PagingConfig(
            pageSize = 48,
            prefetchDistance = 12,
            initialLoadSize = 96,
            enablePlaceholders = true,
            maxSize = 240,
        ),
        pagingSourceFactory = {
            database.gameDao().pagingLibrary(
                query = query.trim(),
                platform = platform,
                lifecycle = lifecycle,
                sortMode = sortMode,
                includeHidden = false,
            )
        },
    ).flow.map { page -> page.mapPaging { row -> row.toSummary() } }

    fun libraryPreview(): Flow<List<LibraryGameSummary>> = database.gameDao()
        .observeLibraryPreview(limit = 24)
        .map { rows -> rows.map { row -> row.toSummary() } }

    fun libraryCount(query: String): Flow<Int> =
        database.gameDao().observeVisibleLibraryCount(query.trim())

    fun unknownPlatformCount(query: String): Flow<Int> =
        database.gameDao().observeUnknownPlatformCount(query.trim())

    fun libraryPlatforms(): Flow<List<String>> = database.gameDao().observePlatforms()

    fun library(query: String): Flow<List<LibraryGameSummary>> = combine(
        database.gameDao().observeLibrary(query = query, includeHidden = false),
        database.gameDao().observeSearch(query = query, includeHidden = false),
        database.gameVersionDao().observeAll(),
    ) { games, searchRows, versions ->
        val search = searchRows.associateBy(SearchGameRow::gameId)
        val platformByGame = versions.groupBy(GameVersionEntity::gameId)
            .mapValues { (_, values) -> values.firstOrNull()?.platform ?: "UNKNOWN" }
        games.map { game ->
            val row = search[game.id]
            LibraryGameSummary(
                id = game.id,
                title = game.canonicalTitle,
                sortTitle = game.sortTitle,
                pinned = game.isPinned,
                favorite = game.isFavorite,
                hidden = game.isHidden,
                availableInstallations = row?.availableInstallationCount ?: 0,
                platform = platformByGame[game.id] ?: "UNKNOWN",
                lifecycle = game.lifecycleStatus.name,
            )
        }
    }

    fun continueGames(): Flow<List<LibraryGameSummary>> = combine(
        database.gameDao().observeContinue(20),
        database.gameVersionDao().observeAll(),
    ) { rows, versions ->
        val platformByGame = versions.groupBy(GameVersionEntity::gameId)
            .mapValues { (_, values) -> values.firstOrNull()?.platform ?: "UNKNOWN" }
        rows.map { it.toSummary().copy(platform = platformByGame[it.gameId] ?: "UNKNOWN") }
    }

    fun hiddenGames(): Flow<List<LibraryGameSummary>> = database.gameDao()
        .observeLibrary(query = "", includeHidden = true)
        .map { games ->
            games.filter(GameEntity::isHidden).map { game ->
                LibraryGameSummary(
                    id = game.id,
                    title = game.canonicalTitle,
                    sortTitle = game.sortTitle,
                    pinned = game.isPinned,
                    favorite = game.isFavorite,
                    hidden = true,
                    availableInstallations = 0,
                    lifecycle = game.lifecycleStatus.name,
                )
            }
        }

    fun libraryRoots(): Flow<List<LibraryRootSummary>> = database.libraryRootDao().observeAll().map { roots ->
        roots.map { root ->
            LibraryRootSummary(
                id = root.id,
                state = root.rootState.name,
                platformHint = root.platformHint,
                provider = root.providerAuthority,
                lastScanAt = root.lastSuccessfulScanAt,
            )
        }
    }

    fun recoverySessions(): Flow<List<SessionSummary>> = database.sessionDao().observeRecoveryRecords().map { sessions ->
        sessions.map { session ->
            SessionSummary(
                id = session.id,
                gameId = session.gameId,
                gameTitle = session.gameId,
                state = session.state,
                startedAt = session.wallStartedAt,
                endedAt = session.wallEndedAt,
                durationMillis = session.durationMillis,
            )
        }
    }

    fun activeSessions(): Flow<List<SessionSummary>> = database.sessionDao().observeActive().map { sessions ->
        sessions.map { session ->
            SessionSummary(
                id = session.id,
                gameId = session.gameId,
                gameTitle = session.gameId,
                state = session.state,
                startedAt = session.wallStartedAt,
                endedAt = session.wallEndedAt,
                durationMillis = session.durationMillis,
            )
        }
    }

    fun notes(query: String): Flow<List<NoteSummary>> = database.notesDao().search(query).map { notes ->
        notes.map { it.toSummary() }
    }

    fun detail(gameId: String): Flow<GameDetailSummary?> {
        val base = combine(
            database.gameDao().observeById(gameId),
            database.gameVersionDao().observeForGame(gameId),
            database.installationDao().observeForGame(gameId),
            database.playMethodDao().observeForGame(gameId),
            database.sessionDao().observeForGame(gameId),
        ) { game, versions, installations, methods, sessions ->
            DetailBase(game, versions, installations, methods, sessions)
        }
        val withNotes = combine(base, database.notesDao().observeForGame(gameId)) { partial, notes ->
            partial to notes
        }
        return combine(withNotes, database.artworkDao().observeForGame(gameId)) { (partial, notes), artwork ->
            val game = partial.game ?: return@combine null
            val preferredArtwork = artwork.firstOrNull { it.provenance == "USER_OVERRIDE" } ?: artwork.firstOrNull()
            GameDetailSummary(
                id = game.id,
                title = game.canonicalTitle,
                description = game.description,
                releaseYear = game.releaseYear,
                pinned = game.isPinned,
                favorite = game.isFavorite,
                versions = partial.versions.map { VersionSummary(it.id, it.platform, it.edition) },
                installations = partial.installations.map {
                    InstallationSummary(it.id, it.gameVersionId, it.sourceType.name, it.availabilityState)
                },
                playMethods = partial.methods.map {
                    PlayMethodSummary(it.id, it.displayName, it.compatibilityState, it.isPreferred)
                },
                sessions = partial.sessions.map { it.toSummary() },
                notes = notes.map { it.toSummary() },
                metadataProvenance = decodeStringMap(game.metadataProvenanceJson),
                artworkLocator = preferredArtwork?.locator,
                artworkProvenance = preferredArtwork?.provenance,
            )
        }
    }

    suspend fun setPinned(gameId: String, pinned: Boolean) {
        database.gameDao().setPinned(gameId, pinned, Instant.now().toEpochMilli())
    }

    suspend fun setFavorite(gameId: String, favorite: Boolean) {
        database.gameDao().setFavorite(gameId, favorite, Instant.now().toEpochMilli())
    }

    suspend fun setHidden(gameId: String, hidden: Boolean) {
        database.gameDao().setHidden(gameId, hidden, Instant.now().toEpochMilli())
    }

    suspend fun updateGameMetadata(
        gameId: String,
        title: String,
        description: String,
        releaseYear: Int?,
    ) {
        val normalizedTitle = title.trim()
        require(normalizedTitle.isNotBlank()) { "Title is required" }
        require(releaseYear == null || releaseYear in 1800..3000) { "Release year must be between 1800 and 3000" }
        val existing = database.gameDao().getById(gameId) ?: error("Unknown game")
        val provenance = decodeStringMap(existing.metadataProvenanceJson).toMutableMap().apply {
            put("title", "USER_OVERRIDE")
            put("description", "USER_OVERRIDE")
            put("releaseYear", "USER_OVERRIDE")
        }
        database.gameDao().upsert(
            existing.copy(
                canonicalTitle = normalizedTitle,
                sortTitle = normalizedTitle,
                description = description.trim().takeIf(String::isNotBlank),
                releaseYear = releaseYear,
                metadataProvenanceJson = JSONObject(provenance as Map<*, *>).toString(),
                updatedAt = Instant.now().toEpochMilli(),
            ),
        )
    }

    suspend fun setArtworkOverride(gameId: String, locator: String) {
        require(locator.startsWith("content://")) { "Artwork must come from a granted document" }
        val now = Instant.now().toEpochMilli()
        database.artworkDao().upsert(
            ArtworkAssetEntity(
                id = DeterministicIds.artworkAsset("user-override", gameId).value,
                gameId = gameId,
                artworkKind = ArtworkKind.HERO,
                locator = locator,
                provenance = "USER_OVERRIDE",
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun importAndroidApp(app: InstalledLaunchableApp): String {
        val now = Instant.now().toEpochMilli()
        val component = app.componentName.flattenToString()
        val gameId = DeterministicIds.game("android-app", component).value
        val versionId = DeterministicIds.gameVersion("android-app", "$component:${app.versionCode}").value
        val installationId = DeterministicIds.installation("android-app", component).value
        val launcherId = DeterministicIds.launcher("android-app", component).value
        val methodId = DeterministicIds.playMethod("android-app", component).value
        val device = localDevice(now)
        val profile = LauncherProfiles.androidNative(app.packageName, app.activityName)

        database.withTransaction {
            database.deviceDao().upsert(device)
            database.gameDao().upsert(
                GameEntity(
                    id = gameId,
                    canonicalTitle = app.label,
                    sortTitle = app.label,
                    lifecycleStatus = GameLifecycleStatus.BACKLOG,
                    metadataProvenanceJson = "{\"title\":\"LOCAL_APP_LABEL\"}",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.gameVersionDao().upsert(
                GameVersionEntity(
                    id = versionId,
                    gameId = gameId,
                    platform = "ANDROID",
                    edition = app.versionName,
                    versionClassification = GameVersionClassification.PORT,
                    externalId = app.packageName,
                    isPreferred = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.installationDao().upsert(
                InstallationEntity(
                    id = installationId,
                    gameVersionId = versionId,
                    deviceId = device.id,
                    sourceType = InstallationSourceType.ANDROID,
                    sourceLocator = component,
                    availabilityState = InstallationAvailability.AVAILABLE,
                    discoveredAt = now,
                    lastSeenAt = now,
                    updatedAt = now,
                ),
            )
            database.launcherDao().upsert(
                LauncherEntity(
                    id = launcherId,
                    displayName = "Android Native · ${app.label}",
                    adapterType = LauncherAdapterType.ANDROID_NATIVE,
                    packageCandidatesJson = LaunchConfigurationCodec.stringArray(listOf(app.packageName)),
                    componentCandidatesJson = LaunchConfigurationCodec.stringArray(listOf(component)),
                    launchAction = profile.launchParameters.action,
                    verificationStatus = VerificationStatus.UNVERIFIED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.playMethodDao().upsert(
                PlayMethodEntity(
                    id = methodId,
                    installationId = installationId,
                    deviceId = device.id,
                    launcherId = launcherId,
                    displayName = "Play installed Android app",
                    isPreferred = true,
                    compatibilityState = if (app.exported) CompatibilityState.READY else CompatibilityState.NEEDS_ACTION,
                    configurationJson = LaunchConfigurationCodec.encode(profile.launchParameters),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.artworkDao().upsert(
                ArtworkAssetEntity(
                    id = DeterministicIds.artworkAsset("android-app", component).value,
                    gameId = gameId,
                    artworkKind = ArtworkKind.ORIGINAL,
                    locator = "android-icon:${app.packageName}",
                    provenance = "LOCAL_ICON",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        return gameId
    }

    suspend fun addGameNativeTitle(title: String, source: GameNativeSource, appId: Int): String {
        require(title.isNotBlank()) { "Title is required" }
        require(appId > 0) { "App ID must be positive" }
        val now = Instant.now().toEpochMilli()
        val sourceKey = "${source.name}:$appId"
        val gameId = DeterministicIds.game("gamenative", sourceKey).value
        val versionId = DeterministicIds.gameVersion("gamenative", sourceKey).value
        val installationId = DeterministicIds.installation("gamenative", sourceKey).value
        // Preserve the existing deterministic key so previously entered titles
        // are updated in place rather than duplicated. It is not evidence that
        // the currently installed GameNative contract has been verified.
        val launcherId = DeterministicIds.launcher("gamenative", "verified-contract").value
        val methodId = DeterministicIds.playMethod("gamenative", sourceKey).value
        val device = localDevice(now)
        val profile = LauncherProfiles.gameNative(source, appId)

        database.withTransaction {
            database.deviceDao().upsert(device)
            database.gameDao().upsert(
                GameEntity(
                    id = gameId,
                    canonicalTitle = title.trim(),
                    sortTitle = title.trim(),
                    lifecycleStatus = GameLifecycleStatus.BACKLOG,
                    metadataProvenanceJson = "{\"title\":\"USER_OVERRIDE\"}",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.gameVersionDao().upsert(
                GameVersionEntity(
                    id = versionId,
                    gameId = gameId,
                    platform = "GAME_NATIVE",
                    externalId = sourceKey,
                    isPreferred = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.installationDao().upsert(
                InstallationEntity(
                    id = installationId,
                    gameVersionId = versionId,
                    deviceId = device.id,
                    sourceType = InstallationSourceType.GAMENATIVE,
                    sourceLocator = sourceKey,
                    availabilityState = InstallationAvailability.UNKNOWN,
                    discoveredAt = now,
                    updatedAt = now,
                ),
            )
            database.launcherDao().upsert(
                LauncherEntity(
                    id = launcherId,
                    displayName = "GameNative",
                    adapterType = LauncherAdapterType.GAMENATIVE,
                    packageCandidatesJson = LaunchConfigurationCodec.stringArray(listOf(LauncherProfiles.GAME_NATIVE_PACKAGE)),
                    componentCandidatesJson = LaunchConfigurationCodec.stringArray(profile.componentCandidates),
                    launchAction = LauncherProfiles.GAME_NATIVE_ACTION,
                    extrasSchemaJson = "{\"app_id\":\"INTEGER\",\"game_source\":\"TEXT\"}",
                    verificationStatus = VerificationStatus.UNVERIFIED,
                    verifiedAt = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.playMethodDao().upsert(
                PlayMethodEntity(
                    id = methodId,
                    installationId = installationId,
                    deviceId = device.id,
                    launcherId = launcherId,
                    displayName = "Launch with GameNative",
                    isPreferred = true,
                    compatibilityState = CompatibilityState.UNKNOWN,
                    configurationJson = LaunchConfigurationCodec.encode(profile.launchParameters),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.saveDao().upsertProfile(
                SaveProfileEntity(
                    id = DeterministicIds.saveProfile("gamenative", sourceKey).value,
                    gameVersionId = versionId,
                    installationId = installationId,
                    deviceId = device.id,
                    adapterId = "gamenative-managed-externally",
                    accessMethod = SaveAccessMethod.EXTERNAL_ADAPTER,
                    saveType = SaveType.NORMAL_SAVE,
                    saveState = SaveState.MANAGED_EXTERNALLY,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        return gameId
    }

    suspend fun addConfigurableExternalTitle(
        title: String,
        app: InstalledLaunchableApp,
        action: String?,
        data: String?,
        extrasTemplate: String,
    ): String {
        require(title.isNotBlank()) { "Title is required" }
        val parsedExtras = TypedExtrasTemplateParser.parse(extrasTemplate)
        require(parsedExtras.issues.isEmpty()) { parsedExtras.issues.joinToString { it.explanation } }
        val normalizedAction = action?.trim()?.takeIf(String::isNotBlank)
        val normalizedData = data?.trim()?.takeIf(String::isNotBlank)
        require(normalizedAction != null || normalizedData != null || parsedExtras.extras.isNotEmpty()) {
            "Configure an action, data value, or typed extra"
        }
        val now = Instant.now().toEpochMilli()
        val component = app.componentName.flattenToString()
        val sourceKey = "$component:${title.trim()}"
        val gameId = DeterministicIds.game("configurable-external", sourceKey).value
        val versionId = DeterministicIds.gameVersion("configurable-external", sourceKey).value
        val installationId = DeterministicIds.installation("configurable-external", sourceKey).value
        val launcherId = DeterministicIds.launcher("configurable-external", sourceKey).value
        val methodId = DeterministicIds.playMethod("configurable-external", sourceKey).value
        val device = localDevice(now)
        val payload = LaunchParameterPayload(
            action = normalizedAction,
            dataTemplate = normalizedData,
            componentTemplate = component,
            extras = parsedExtras.extras,
            contentUriGrant = if (
                normalizedData?.startsWith("content://") == true ||
                parsedExtras.extras.values.any { it is dev.thorhub.core.model.LaunchExtraValue.Text && it.value.contains("{contentUri}") }
            ) {
                ContentUriGrantMode.READ
            } else {
                ContentUriGrantMode.NONE
            },
        )
        val profile = LauncherProfiles.configurable(
            id = LauncherId.of(launcherId),
            displayName = "External · ${app.label}",
            packageName = app.packageName,
            activityName = app.activityName,
            payload = payload,
        )
        database.withTransaction {
            database.deviceDao().upsert(device)
            database.gameDao().upsert(
                GameEntity(
                    id = gameId,
                    canonicalTitle = title.trim(),
                    sortTitle = title.trim(),
                    lifecycleStatus = GameLifecycleStatus.BACKLOG,
                    metadataProvenanceJson = "{\"title\":\"USER_OVERRIDE\"}",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.gameVersionDao().upsert(
                GameVersionEntity(
                    id = versionId,
                    gameId = gameId,
                    platform = "PLAYSTATION",
                    externalId = component,
                    isPreferred = true,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.installationDao().upsert(
                InstallationEntity(
                    id = installationId,
                    gameVersionId = versionId,
                    deviceId = device.id,
                    sourceType = InstallationSourceType.MANUAL,
                    sourceLocator = component,
                    availabilityState = InstallationAvailability.UNKNOWN,
                    discoveredAt = now,
                    updatedAt = now,
                ),
            )
            database.launcherDao().upsert(
                LauncherEntity(
                    id = launcherId,
                    displayName = profile.displayName,
                    adapterType = LauncherAdapterType.CONFIGURABLE_EXTERNAL,
                    packageCandidatesJson = LaunchConfigurationCodec.stringArray(listOf(app.packageName)),
                    componentCandidatesJson = LaunchConfigurationCodec.stringArray(listOf(component)),
                    launchAction = normalizedAction,
                    launchDataTemplate = normalizedData,
                    extrasSchemaJson = extrasTemplate,
                    verificationStatus = VerificationStatus.UNVERIFIED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.playMethodDao().upsert(
                PlayMethodEntity(
                    id = methodId,
                    installationId = installationId,
                    deviceId = device.id,
                    launcherId = launcherId,
                    displayName = "Launch with ${app.label}",
                    isPreferred = true,
                    compatibilityState = CompatibilityState.UNKNOWN,
                    configurationJson = LaunchConfigurationCodec.encode(payload),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.saveDao().upsertProfile(
                SaveProfileEntity(
                    id = DeterministicIds.saveProfile("configurable-external", sourceKey).value,
                    gameVersionId = versionId,
                    installationId = installationId,
                    deviceId = device.id,
                    adapterId = "unconfigured",
                    accessMethod = SaveAccessMethod.UNKNOWN,
                    saveType = SaveType.UNKNOWN,
                    saveState = SaveState.UNCONFIGURED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        return gameId
    }

    suspend fun configureRetroArch(
        gameId: String,
        packageName: String,
        corePath: String,
    ): String {
        require(packageName in LauncherProfiles.retroArchPackages) { "Choose a supported RetroArch package candidate" }
        val normalizedCorePath = corePath.trim()
        require(normalizedCorePath.isNotBlank() && '\u0000' !in normalizedCorePath) {
            "Enter the core path reported by the installed RetroArch build"
        }
        val installation = database.installationDao().observeForGame(gameId).first().firstOrNull { candidate ->
            candidate.sourceType == InstallationSourceType.ROM &&
                candidate.sourceLocator?.startsWith("content://") == true &&
                candidate.availabilityState == InstallationAvailability.AVAILABLE
        } ?: error("This game has no available SAF ROM installation")
        val deviceId = installation.deviceId ?: error("The ROM installation has no local device identity")
        val version = database.gameVersionDao().getById(installation.gameVersionId)
            ?: error("The ROM installation has no game version")
        require(version.gameId == gameId) { "The selected ROM installation belongs to another game" }

        val now = Instant.now().toEpochMilli()
        val profile = LauncherProfiles.retroArch(
            contentUri = requireNotNull(installation.sourceLocator),
            corePath = normalizedCorePath,
            packageName = packageName,
        )
        val launcherId = DeterministicIds.launcher("retroarch", packageName).value
        val methodId = DeterministicIds.playMethod(
            "retroarch",
            "${installation.id}:$packageName:$normalizedCorePath",
        ).value
        database.withTransaction {
            database.launcherDao().upsert(
                LauncherEntity(
                    id = launcherId,
                    displayName = "RetroArch · $packageName",
                    adapterType = LauncherAdapterType.RETROARCH,
                    packageCandidatesJson = LaunchConfigurationCodec.stringArray(LauncherProfiles.retroArchPackages),
                    componentCandidatesJson = LaunchConfigurationCodec.stringArray(profile.componentCandidates),
                    verificationStatus = VerificationStatus.UNVERIFIED,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            database.playMethodDao().upsert(
                PlayMethodEntity(
                    id = methodId,
                    installationId = installation.id,
                    deviceId = deviceId,
                    launcherId = launcherId,
                    displayName = "RetroArch · ${version.platform}",
                    priority = 50,
                    isPreferred = true,
                    compatibilityState = CompatibilityState.UNKNOWN,
                    configurationJson = LaunchConfigurationCodec.encode(profile.launchParameters),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        return methodId
    }

    suspend fun saveNote(
        gameId: String,
        body: String,
        checklist: Boolean,
        pinned: Boolean,
        sessionId: String? = null,
        gameVersionId: String? = null,
        deduplicationKey: String? = null,
    ): String {
        require(body.isNotBlank()) { "Note cannot be blank" }
        require(deduplicationKey == null || deduplicationKey.isNotBlank()) {
            "Note deduplication key cannot be blank"
        }
        val detail = detail(gameId).first() ?: error("Unknown game")
        val now = Instant.now().toEpochMilli()
        val noteId = if (deduplicationKey == null) {
            DeterministicIds.note("local-note", "$gameId:$now:${body.hashCode()}").value
        } else {
            DeterministicIds.note("local-note-idempotent", "$gameId:$deduplicationKey").value
        }
        val localDevice = localDevice(now)
        val note = NoteEntity(
            id = noteId,
            gameId = gameId,
            gameVersionId = gameVersionId ?: detail.versions.firstOrNull()?.id,
            sessionId = sessionId,
            deviceId = localDevice.id,
            noteType = if (checklist) NoteType.CHECKLIST else NoteType.TEXT,
            body = body.trim(),
            isPinned = pinned,
            createdAt = now,
            updatedAt = now,
        )
        val items = if (checklist) {
            body.lineSequence().map(String::trim).filter(String::isNotBlank).mapIndexed { index, line ->
                NoteChecklistItemEntity(
                    id = DeterministicIds.note("checklist-item", "$noteId:$index").value,
                    noteId = noteId,
                    position = index,
                    text = line.removePrefix("- ").removePrefix("[ ] ").removePrefix("[x] "),
                    isCompleted = line.startsWith("[x]", ignoreCase = true),
                    createdAt = now,
                    updatedAt = now,
                )
            }.toList()
        } else {
            emptyList()
        }
        database.withTransaction {
            database.deviceDao().upsert(localDevice)
            database.notesDao().saveNoteWithChecklist(note, items)
        }
        return noteId
    }

    suspend fun updateNote(noteId: String, body: String) {
        require(body.isNotBlank()) { "Note cannot be blank" }
        database.notesDao().updateBody(noteId, body.trim(), Instant.now().toEpochMilli())
    }

    suspend fun setNotePinned(noteId: String, pinned: Boolean) {
        database.notesDao().setPinned(noteId, pinned, Instant.now().toEpochMilli())
    }

    suspend fun setNoteCompleted(noteId: String, completed: Boolean) {
        database.notesDao().setCompleted(noteId, completed, Instant.now().toEpochMilli())
    }

    suspend fun resolvePlayMethod(gameId: String): ResolvedPlayMethod? {
        val entity = database.playMethodDao().observeForGame(gameId).first()
            .sortedWith(compareByDescending<PlayMethodEntity> { it.isPreferred }.thenByDescending { it.priority })
            .firstOrNull() ?: return null
        return resolvePlayMethodEntity(entity)?.takeIf { it.gameId == gameId }
    }

    suspend fun resolvePlayMethodById(playMethodId: String): ResolvedPlayMethod? =
        database.playMethodDao().getById(playMethodId)?.let { resolvePlayMethodEntity(it) }

    private suspend fun resolvePlayMethodEntity(entity: PlayMethodEntity): ResolvedPlayMethod? {
        val installation = entity.installationId?.let { database.installationDao().getById(it) } ?: return null
        val version = database.gameVersionDao().getById(installation.gameVersionId) ?: return null
        val launcherId = entity.launcherId ?: return null
        val launcher = database.launcherDao().observeById(launcherId).first() ?: return null
        val method = LaunchConfigurationCodec.toDomain(entity)
        return ResolvedPlayMethod(
            method = method,
            profile = LaunchConfigurationCodec.toDomain(launcher, method.launchParameters),
            gameId = version.gameId,
            gameVersionId = version.id,
        )
    }

    private fun localDevice(now: Long): DeviceEntity = localDeviceIdentity.current(now)

    private fun ContinueGameRow.toSummary(): LibraryGameSummary = LibraryGameSummary(
        id = gameId,
        title = canonicalTitle,
        sortTitle = sortTitle,
        pinned = isPinned,
        favorite = false,
        hidden = false,
        availableInstallations = 0,
        lastSessionAt = lastSessionAt,
        sessionCount = sessionCount,
    )

    private fun LibraryGameRow.toSummary(): LibraryGameSummary = LibraryGameSummary(
        id = gameId,
        title = canonicalTitle,
        sortTitle = sortTitle,
        pinned = isPinned,
        favorite = isFavorite,
        hidden = isHidden,
        availableInstallations = availableInstallationCount,
        platform = platform,
        lifecycle = lifecycleStatus.name,
        lastSessionAt = lastSessionAt,
        sessionCount = sessionCount,
    )

    private fun SessionWithGameRow.toSummary(): SessionSummary = SessionSummary(
        id = sessionId,
        gameId = gameId,
        gameTitle = gameTitle,
        state = state,
        startedAt = wallStartedAt,
        endedAt = wallEndedAt,
        durationMillis = durationMillis,
    )

    private fun NoteEntity.toSummary(): NoteSummary = NoteSummary(
        id = id,
        gameId = gameId,
        body = body,
        type = noteType,
        pinned = isPinned,
        completed = isCompleted,
        updatedAt = updatedAt,
    )

    private fun decodeStringMap(json: String): Map<String, String> = runCatching {
        val objectValue = JSONObject(json)
        objectValue.keys().asSequence().associateWith { key -> objectValue.optString(key, "UNKNOWN") }
    }.getOrDefault(emptyMap())

    private data class DetailBase(
        val game: GameEntity?,
        val versions: List<GameVersionEntity>,
        val installations: List<InstallationEntity>,
        val methods: List<PlayMethodEntity>,
        val sessions: List<SessionWithGameRow>,
    )
}
