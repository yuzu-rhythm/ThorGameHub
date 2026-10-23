package dev.thorhub.core.database

import androidx.room.TypeConverter

private inline fun <reified T : Enum<T>> decodeEnum(value: String?, fallback: T): T? {
    return value?.let { persisted ->
        enumValues<T>().firstOrNull { it.name == persisted } ?: fallback
    }
}

class DatabaseConverters {
    @TypeConverter
    fun gameLifecycleStatusToString(value: GameLifecycleStatus?): String? = value?.name

    @TypeConverter
    fun stringToGameLifecycleStatus(value: String?): GameLifecycleStatus? =
        decodeEnum(value, GameLifecycleStatus.UNKNOWN)

    @TypeConverter
    fun gameVersionClassificationToString(value: GameVersionClassification?): String? = value?.name

    @TypeConverter
    fun stringToGameVersionClassification(value: String?): GameVersionClassification? =
        decodeEnum(value, GameVersionClassification.UNKNOWN)

    @TypeConverter
    fun deviceAvailabilityStateToString(value: DeviceAvailabilityState?): String? = value?.name

    @TypeConverter
    fun stringToDeviceAvailabilityState(value: String?): DeviceAvailabilityState? =
        decodeEnum(value, DeviceAvailabilityState.UNKNOWN)

    @TypeConverter
    fun pairingStateToString(value: PairingState?): String? = value?.name

    @TypeConverter
    fun stringToPairingState(value: String?): PairingState? = decodeEnum(value, PairingState.UNKNOWN)

    @TypeConverter
    fun installationSourceTypeToString(value: InstallationSourceType?): String? = value?.name

    @TypeConverter
    fun stringToInstallationSourceType(value: String?): InstallationSourceType? =
        decodeEnum(value, InstallationSourceType.UNKNOWN)

    @TypeConverter
    fun installationAvailabilityToString(value: InstallationAvailability?): String? = value?.name

    @TypeConverter
    fun stringToInstallationAvailability(value: String?): InstallationAvailability? =
        decodeEnum(value, InstallationAvailability.UNKNOWN)

    @TypeConverter
    fun launcherAdapterTypeToString(value: LauncherAdapterType?): String? = value?.name

    @TypeConverter
    fun stringToLauncherAdapterType(value: String?): LauncherAdapterType? =
        decodeEnum(value, LauncherAdapterType.UNKNOWN)

    @TypeConverter
    fun verificationStatusToString(value: VerificationStatus?): String? = value?.name

    @TypeConverter
    fun stringToVerificationStatus(value: String?): VerificationStatus? =
        decodeEnum(value, VerificationStatus.UNKNOWN)

    @TypeConverter
    fun compatibilityStateToString(value: CompatibilityState?): String? = value?.name

    @TypeConverter
    fun stringToCompatibilityState(value: String?): CompatibilityState? =
        decodeEnum(value, CompatibilityState.UNKNOWN)

    @TypeConverter
    fun sessionStateToString(value: SessionState?): String? = value?.name

    @TypeConverter
    fun stringToSessionState(value: String?): SessionState? = decodeEnum(value, SessionState.PENDING)

    @TypeConverter
    fun noteTypeToString(value: NoteType?): String? = value?.name

    @TypeConverter
    fun stringToNoteType(value: String?): NoteType? = decodeEnum(value, NoteType.UNKNOWN)

    @TypeConverter
    fun saveAccessMethodToString(value: SaveAccessMethod?): String? = value?.name

    @TypeConverter
    fun stringToSaveAccessMethod(value: String?): SaveAccessMethod? =
        decodeEnum(value, SaveAccessMethod.UNKNOWN)

    @TypeConverter
    fun saveTypeToString(value: SaveType?): String? = value?.name

    @TypeConverter
    fun stringToSaveType(value: String?): SaveType? = decodeEnum(value, SaveType.UNKNOWN)

    @TypeConverter
    fun saveStateToString(value: SaveState?): String? = value?.name

    @TypeConverter
    fun stringToSaveState(value: String?): SaveState? = decodeEnum(value, SaveState.UNCONFIGURED)

    @TypeConverter
    fun snapshotReasonToString(value: SnapshotReason?): String? = value?.name

    @TypeConverter
    fun stringToSnapshotReason(value: String?): SnapshotReason? =
        decodeEnum(value, SnapshotReason.UNKNOWN)

    @TypeConverter
    fun snapshotStateToString(value: SnapshotState?): String? = value?.name

    @TypeConverter
    fun stringToSnapshotState(value: String?): SnapshotState? =
        decodeEnum(value, SnapshotState.UNKNOWN)

    @TypeConverter
    fun collectionTypeToString(value: CollectionType?): String? = value?.name

    @TypeConverter
    fun stringToCollectionType(value: String?): CollectionType? =
        decodeEnum(value, CollectionType.UNKNOWN)

    @TypeConverter
    fun libraryRootSourceTypeToString(value: LibraryRootSourceType?): String? = value?.name

    @TypeConverter
    fun stringToLibraryRootSourceType(value: String?): LibraryRootSourceType? =
        decodeEnum(value, LibraryRootSourceType.UNKNOWN)

    @TypeConverter
    fun libraryRootStateToString(value: LibraryRootState?): String? = value?.name

    @TypeConverter
    fun stringToLibraryRootState(value: String?): LibraryRootState? =
        decodeEnum(value, LibraryRootState.UNKNOWN)

    @TypeConverter
    fun artworkKindToString(value: ArtworkKind?): String? = value?.name

    @TypeConverter
    fun stringToArtworkKind(value: String?): ArtworkKind? = decodeEnum(value, ArtworkKind.UNKNOWN)

    @TypeConverter
    fun capabilityStateToString(value: CapabilityState?): String? = value?.name

    @TypeConverter
    fun stringToCapabilityState(value: String?): CapabilityState? =
        decodeEnum(value, CapabilityState.UNKNOWN)

    @TypeConverter
    fun diagnosticSeverityToString(value: DiagnosticSeverity?): String? = value?.name

    @TypeConverter
    fun stringToDiagnosticSeverity(value: String?): DiagnosticSeverity? =
        decodeEnum(value, DiagnosticSeverity.INFO)

    @TypeConverter
    fun displayRoleToString(value: DisplayRole?): String? = value?.name

    @TypeConverter
    fun stringToDisplayRole(value: String?): DisplayRole? = decodeEnum(value, DisplayRole.UNKNOWN)
}
