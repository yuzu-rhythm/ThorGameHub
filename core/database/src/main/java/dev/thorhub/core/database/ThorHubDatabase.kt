package dev.thorhub.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        GameEntity::class,
        GameVersionEntity::class,
        DeviceEntity::class,
        InstallationEntity::class,
        LauncherEntity::class,
        PlayMethodEntity::class,
        GameSessionEntity::class,
        NoteEntity::class,
        NoteChecklistItemEntity::class,
        SaveProfileEntity::class,
        SaveSnapshotEntity::class,
        SaveSnapshotFileEntity::class,
        CollectionEntity::class,
        CollectionMemberEntity::class,
        LibraryRootEntity::class,
        ContentFingerprintEntity::class,
        ArtworkAssetEntity::class,
        CapabilityRecordEntity::class,
        DiagnosticEventEntity::class,
        DisplayAssignmentEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class ThorHubDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    abstract fun gameVersionDao(): GameVersionDao

    abstract fun deviceDao(): DeviceDao

    abstract fun installationDao(): InstallationDao

    abstract fun launcherDao(): LauncherDao

    abstract fun playMethodDao(): PlayMethodDao

    abstract fun libraryDao(): LibraryDao

    abstract fun libraryRootDao(): LibraryRootDao

    abstract fun fingerprintDao(): FingerprintDao

    abstract fun artworkDao(): ArtworkDao

    abstract fun sessionDao(): SessionDao

    abstract fun notesDao(): NotesDao

    abstract fun saveDao(): SaveDao

    abstract fun collectionDao(): CollectionDao

    abstract fun capabilityDao(): CapabilityDao

    abstract fun diagnosticDao(): DiagnosticDao

    abstract fun displayAssignmentDao(): DisplayAssignmentDao
}
