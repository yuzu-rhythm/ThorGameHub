package dev.thorhub.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 already contained the complete Phase 1A graph. v2 adds pinning as a
 * queryable library property, preserving all existing rows with a false
 * default. This is intentionally additive so an old install can be upgraded
 * without deleting history or falling back to destructive recreation.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE games ADD COLUMN is_pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_games_is_pinned ON games(is_pinned)")
    }
}

/** Adds field-level metadata provenance without rewriting or deleting catalog rows. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE games ADD COLUMN metadata_provenance_json TEXT NOT NULL DEFAULT '{}'",
        )
    }
}

/**
 * Removes transient Android display IDs from durable assignment records.
 *
 * Legacy rows are preserved as unverified historical evidence, but their
 * transient ID and raw build fingerprint are deliberately not copied. A new
 * user confirmation will replace them with signature/build hashes.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS display_assignments_v4 (
                id TEXT NOT NULL,
                device_id TEXT,
                display_signature_hash TEXT NOT NULL,
                display_role TEXT NOT NULL,
                width INTEGER,
                height INTEGER,
                density_dpi INTEGER,
                rotation INTEGER,
                refresh_rate_hz REAL,
                flags INTEGER NOT NULL,
                is_verified INTEGER NOT NULL DEFAULT 0,
                build_fingerprint_hash TEXT,
                assigned_at INTEGER NOT NULL,
                evidence_code TEXT,
                PRIMARY KEY(id),
                FOREIGN KEY(device_id) REFERENCES devices(id) ON UPDATE CASCADE ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO display_assignments_v4(
                id, device_id, display_signature_hash, display_role,
                width, height, density_dpi, rotation, refresh_rate_hz,
                flags, is_verified, build_fingerprint_hash, assigned_at, evidence_code
            )
            SELECT
                id, device_id, 'legacy-unresolved:' || id, display_role,
                width, height, density_dpi, rotation, refresh_rate_hz,
                flags, 0, NULL, assigned_at, 'LEGACY_TRANSIENT_ID_REMOVED'
            FROM display_assignments
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE display_assignments")
        db.execSQL("ALTER TABLE display_assignments_v4 RENAME TO display_assignments")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_display_assignments_device_id ON display_assignments(device_id)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_display_assignments_display_role ON display_assignments(display_role)",
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                index_display_assignments_device_id_display_signature_hash_build_fingerprint_hash
            ON display_assignments(device_id, display_signature_hash, build_fingerprint_hash)
            """.trimIndent(),
        )
    }
}

/**
 * Adds the boot-scoped monotonic checkpoint needed to accumulate only active
 * session intervals. Existing duration remains authoritative; legacy open
 * sessions have no comparable checkpoint and are recovered without guessing.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE game_sessions ADD COLUMN monotonic_boot_id TEXT")
        db.execSQL("ALTER TABLE game_sessions ADD COLUMN last_monotonic_nanos INTEGER")
    }
}
