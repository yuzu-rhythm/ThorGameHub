package dev.thorhub.core.database

internal object TestFixtures {
    const val gameId = "game-1"
    const val versionId = "version-1"
    const val deviceId = "device-1"
    const val rootId = "root-1"
    const val fingerprintId = "fingerprint-1"
    const val installationId = "installation-1"
    const val launcherId = "launcher-1"
    const val playMethodId = "play-method-1"
    const val sessionId = "session-1"
    const val profileId = "profile-1"

    fun game(
        id: String = gameId,
        pinned: Boolean = false,
    ) = GameEntity(
        id = id,
        canonicalTitle = "Thor Quest",
        sortTitle = "Thor Quest",
        lifecycleStatus = GameLifecycleStatus.PLAYING,
        isFavorite = true,
        isPinned = pinned,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    fun version(
        id: String = versionId,
        game: String = gameId,
    ) = GameVersionEntity(
        id = id,
        gameId = game,
        platform = "SNES",
        region = "JP",
        versionClassification = GameVersionClassification.ORIGINAL,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    fun device(id: String = deviceId) = DeviceEntity(
        id = id,
        displayName = "Test Thor",
        platform = "Android",
        osVersion = "16",
        hardwareFingerprint = "test-build",
        availabilityState = DeviceAvailabilityState.AVAILABLE,
        pairingState = PairingState.TRUSTED,
        lastSeenAt = 1_000L,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    fun root(id: String = rootId) = LibraryRootEntity(
        id = id,
        deviceId = deviceId,
        treeUri = "content://com.example.documents/tree/test",
        platformHint = "SNES",
        sourceType = LibraryRootSourceType.ROM,
        accessFlags = 3,
        providerAuthority = "com.example.documents",
        rootState = LibraryRootState.ACTIVE,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    fun fingerprint(id: String = fingerprintId) = ContentFingerprintEntity(
        id = id,
        algorithm = "SHA-256",
        value = "hash-$id",
        identityKey = "document-$id",
        sizeBytes = 128L,
        modifiedAt = 1_000L,
        createdAt = 1_000L,
    )

    fun installation(
        id: String = installationId,
        gameVersion: String = versionId,
        state: InstallationAvailability = InstallationAvailability.AVAILABLE,
    ) = InstallationEntity(
        id = id,
        gameVersionId = gameVersion,
        deviceId = deviceId,
        libraryRootId = rootId,
        sourceType = InstallationSourceType.ROM,
        sourceLocator = "content://com.example.documents/document/test-$id",
        contentFingerprintId = fingerprintId,
        availabilityState = state,
        discoveredAt = 1_000L,
        lastSeenAt = 1_000L,
        updatedAt = 1_000L,
    )

    fun launcher(id: String = launcherId) = LauncherEntity(
        id = id,
        displayName = "Test Launcher",
        adapterType = LauncherAdapterType.RETROARCH,
        packageCandidatesJson = "[\"com.example.launcher\"]",
        launchAction = "android.intent.action.VIEW",
        verificationStatus = VerificationStatus.VERIFIED,
        verifiedAt = 1_000L,
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    fun playMethod(id: String = playMethodId) = PlayMethodEntity(
        id = id,
        installationId = installationId,
        deviceId = deviceId,
        launcherId = launcherId,
        displayName = "RetroArch",
        priority = 10,
        isPreferred = true,
        compatibilityState = CompatibilityState.READY,
        configurationJson = "{\"schema\":1}",
        createdAt = 1_000L,
        updatedAt = 1_000L,
    )

    fun session(
        id: String = sessionId,
        state: SessionState = SessionState.PENDING,
    ) = GameSessionEntity(
        id = id,
        gameId = gameId,
        gameVersionId = versionId,
        installationId = installationId,
        deviceId = deviceId,
        playMethodId = playMethodId,
        wallStartedAt = 2_000L,
        monotonicStartedNanos = 10_000L,
        state = state,
        createdAt = 2_000L,
        updatedAt = 2_000L,
    )

    fun profile(id: String = profileId) = SaveProfileEntity(
        id = id,
        gameVersionId = versionId,
        installationId = installationId,
        deviceId = deviceId,
        adapterId = launcherId,
        accessMethod = SaveAccessMethod.SAF_DIRECTORY,
        rootLocator = "content://com.example.documents/tree/saves",
        relativeIncludeJson = "[\"save.dat\"]",
        saveType = SaveType.NORMAL_SAVE,
        compatibilityFingerprint = "engine-v1",
        saveState = SaveState.LOCAL_ONLY,
        createdAt = 3_000L,
        updatedAt = 3_000L,
    )

    fun baseImport(
        installationState: InstallationAvailability = InstallationAvailability.AVAILABLE,
    ) = LibraryImport(
        games = listOf(game()),
        versions = listOf(version()),
        devices = listOf(device()),
        roots = listOf(root()),
        fingerprints = listOf(fingerprint()),
        installations = listOf(installation(state = installationState)),
    )
}
