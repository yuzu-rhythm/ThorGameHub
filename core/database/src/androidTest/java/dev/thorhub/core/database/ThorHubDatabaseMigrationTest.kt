package dev.thorhub.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThorHubDatabaseMigrationTest {
    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ThorHubDatabase::class.java,
    )

    @Test
    fun migration1To2AddsGamePinColumnAndIndexWithoutDataLoss() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO games(
                    id, canonical_title, sort_title, lifecycle_status, description,
                    release_year, is_favorite, is_hidden, user_rating, created_at, updated_at
                ) VALUES ('legacy-game', 'Legacy Game', 'Legacy Game', 'BACKLOG', NULL,
                    NULL, 1, 0, NULL, 1000, 1000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            MIGRATION_1_2,
        )
        migrated.use { database: SupportSQLiteDatabase ->
            database.query("SELECT is_pinned FROM games WHERE id = 'legacy-game'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
            database.query("PRAGMA index_list('games')").use { cursor ->
                val indexNames = buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    }
                }
                assertThat(indexNames).contains("index_games_is_pinned")
            }
        }
    }

    @Test
    fun migration2To3AddsMetadataProvenanceWithoutDataLoss() {
        helper.createDatabase(DATABASE_NAME, 2).apply {
            execSQL(
                """
                INSERT INTO games(
                    id, canonical_title, sort_title, lifecycle_status, description,
                    release_year, is_favorite, is_hidden, is_pinned, user_rating,
                    created_at, updated_at
                ) VALUES ('v2-game', 'V2 Game', 'V2 Game', 'BACKLOG', 'kept',
                    2001, 0, 0, 1, NULL, 1000, 1000)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 3, true, MIGRATION_2_3).use { database ->
            database.query(
                "SELECT canonical_title, description, is_pinned, metadata_provenance_json FROM games WHERE id = 'v2-game'",
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("V2 Game")
                assertThat(cursor.getString(1)).isEqualTo("kept")
                assertThat(cursor.getInt(2)).isEqualTo(1)
                assertThat(cursor.getString(3)).isEqualTo("{}")
            }
        }
    }

    @Test
    fun migration1To3RunsEveryStep() {
        helper.createDatabase(DATABASE_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO games(
                    id, canonical_title, sort_title, lifecycle_status, description,
                    release_year, is_favorite, is_hidden, user_rating, created_at, updated_at
                ) VALUES ('v1-game', 'V1 Game', 'V1 Game', 'BACKLOG', NULL,
                    NULL, 0, 0, NULL, 1000, 1000)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 3, true, MIGRATION_1_2, MIGRATION_2_3).use { database ->
            database.query(
                "SELECT is_pinned, metadata_provenance_json FROM games WHERE id = 'v1-game'",
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(0)
                assertThat(cursor.getString(1)).isEqualTo("{}")
            }
        }
    }

    @Test
    fun migration3To4RemovesTransientDisplayIdsWithoutDroppingTheRecord() {
        helper.createDatabase(DATABASE_NAME, 3).apply {
            execSQL(
                """
                INSERT INTO display_assignments(
                    id, device_id, display_id, display_role, width, height,
                    density_dpi, rotation, refresh_rate_hz, flags, is_verified,
                    build_fingerprint, assigned_at, evidence_code
                ) VALUES (
                    'legacy-display', NULL, 42, 'LOWER', 800, 600,
                    240, 0, 60.0, 0, 1, 'raw-build-fingerprint', 1000, 'USER_CONFIRMED'
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 4, true, MIGRATION_3_4).use { database ->
            database.query("PRAGMA table_info('display_assignments')").use { cursor ->
                val columns = buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    }
                }
                assertThat(columns).doesNotContain("display_id")
                assertThat(columns).contains("display_signature_hash")
                assertThat(columns).contains("build_fingerprint_hash")
            }
            database.query(
                """
                SELECT id, display_signature_hash, is_verified,
                    build_fingerprint_hash, evidence_code
                FROM display_assignments
                WHERE id = 'legacy-display'
                """.trimIndent(),
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("legacy-display")
                assertThat(cursor.getString(1)).isEqualTo("legacy-unresolved:legacy-display")
                assertThat(cursor.getInt(2)).isEqualTo(0)
                assertThat(cursor.isNull(3)).isTrue()
                assertThat(cursor.getString(4)).isEqualTo("LEGACY_TRANSIENT_ID_REMOVED")
            }
        }
    }

    @Test
    fun migration1To4RunsEveryStep() {
        helper.createDatabase(DATABASE_NAME, 1).close()

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            4,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        ).close()
    }

    @Test
    fun migration4To5AddsBootScopedSessionCheckpointWithoutChangingDuration() {
        helper.createDatabase(DATABASE_NAME, 4).apply {
            execSQL(
                """
                INSERT INTO games(
                    id, canonical_title, sort_title, lifecycle_status, description,
                    release_year, is_favorite, is_hidden, is_pinned, user_rating,
                    metadata_provenance_json, created_at, updated_at
                ) VALUES (
                    'v4-game', 'V4 Game', 'V4 Game', 'PLAYING', NULL,
                    NULL, 0, 0, 0, NULL, '{}', 1000, 1000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO game_sessions(
                    id, game_id, wall_started_at, monotonic_started_nanos,
                    duration_millis, state, created_at, updated_at
                ) VALUES ('v4-session', 'v4-game', 1000, 5000000000, 2300, 'ACTIVE', 1000, 2000)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 5, true, MIGRATION_4_5).use { database ->
            database.query("PRAGMA table_info('game_sessions')").use { cursor ->
                val columns = buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    }
                }
                assertThat(columns).contains("monotonic_boot_id")
                assertThat(columns).contains("last_monotonic_nanos")
            }
            database.query(
                """
                SELECT duration_millis, state, monotonic_boot_id, last_monotonic_nanos
                FROM game_sessions WHERE id = 'v4-session'
                """.trimIndent(),
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getLong(0)).isEqualTo(2300L)
                assertThat(cursor.getString(1)).isEqualTo("ACTIVE")
                assertThat(cursor.isNull(2)).isTrue()
                assertThat(cursor.isNull(3)).isTrue()
            }
        }
    }

    @Test
    fun migration1To5RunsEveryStep() {
        helper.createDatabase(DATABASE_NAME, 1).close()

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            5,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
        ).close()
    }

    private companion object {
        const val DATABASE_NAME = "thorhub-migration-test.db"
    }
}
