package com.kurtz.homerecord.core.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Installing a new APK over an older one keeps the data (cr:room-schema-v1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeRecordMigrationTest {
    // The driver-based helper opens the file by its full path. The name-based helper compares the part after the
    // last '/', which a Windows temp path does not have, so it failed there (QA 2026-10-26).
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = ApplicationProvider.getApplicationContext<Context>().getDatabasePath(DB),
        driver = AndroidSQLiteDriver(),
        databaseClass = HomeRecordDatabase::class,
    )

    // cr:sync-global-cursor
    @Test fun v1ToV2DropsTheSingleCursorAndKeepsConfirmedRows() {
        helper.createDatabase(1).use { db ->
            db.execSQL("INSERT INTO sync_state (id, lastServerSeq) VALUES (1, 42)")
            db.execSQL("INSERT INTO sync_base (`key`, rowJson) VALUES ('task/t1', '{\"id\":\"t1\"}')")
            db.execSQL("INSERT INTO task (id, jobId, title, done, punch, createdAt, updatedAt, source) VALUES ('t1', 'j1', 'Patch drywall', 0, 0, 1, 1, 'FIELD_LOG')")
        }
        helper.runMigrationsAndValidate(2).use { db ->
            assertThat(db.count("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'sync_state'")).isEqualTo(0)
            assertThat(db.count("SELECT COUNT(*) FROM sync_cursor")).isEqualTo(0)
            assertThat(db.texts("SELECT tenant FROM sync_base WHERE `key` = 'task/t1'")).containsExactly("")
            assertThat(db.texts("SELECT title FROM task WHERE id = 't1'")).containsExactly("Patch drywall")
        }
    }

    // cr:sync-local-columns
    @Test fun v2ToV3AddsR2KeyToPhotoAndNote() {
        helper.createDatabase(2).use { db ->
            db.execSQL("INSERT INTO photo (id, homeId, path, takenAt, tagsJson, annotationsJson, createdAt, updatedAt, source) VALUES ('p1', 'j1', '/m/p1.jpg', 1, '[]', '[]', 1, 1, 'FIELD_LOG')")
            db.execSQL("INSERT INTO note (id, jobId, day, text, createdAt, updatedAt, source) VALUES ('n1', 'j1', '2026-10-27', 'Panel moved', 1, 1, 'FIELD_LOG')")
        }
        helper.runMigrationsAndValidate(3).use { db ->
            assertThat(db.texts("SELECT path FROM photo WHERE id = 'p1' AND r2Key IS NULL")).containsExactly("/m/p1.jpg")
            assertThat(db.texts("SELECT text FROM note WHERE id = 'n1' AND r2Key IS NULL")).containsExactly("Panel moved")
        }
    }

    // cr:fl-before-phase: a photo whose phase was "Before" keeps being a Before shot, now with no phase.
    @Test fun v3ToV4MovesBeforeOutOfThePhase() {
        helper.createDatabase(3).use { db ->
            db.execSQL("INSERT INTO photo (id, homeId, stage, path, takenAt, tagsJson, annotationsJson, createdAt, updatedAt, source) VALUES ('p1', 'j1', 'Before', '/m/p1.jpg', 1, '[]', '[]', 1, 1, 'FIELD_LOG')")
            db.execSQL("INSERT INTO photo (id, homeId, stage, path, takenAt, tagsJson, annotationsJson, createdAt, updatedAt, source) VALUES ('p2', 'j1', 'Rough-in', '/m/p2.jpg', 1, '[]', '[]', 1, 1, 'FIELD_LOG')")
        }
        helper.runMigrationsAndValidate(4).use { db ->
            assertThat(db.texts("SELECT id FROM photo WHERE pairRole = 'Before' AND stage IS NULL")).containsExactly("p1")
            assertThat(db.texts("SELECT stage FROM photo WHERE id = 'p2' AND pairRole IS NULL")).containsExactly("Rough-in")
        }
    }

    // T044a: tasks gain an order and a done time; a task done before v5 keeps its last change as the done time.
    @Test fun v4ToV5AddsTaskOrderAndDoneTime() {
        helper.createDatabase(4).use { db ->
            db.execSQL("INSERT INTO task (id, jobId, title, done, punch, createdAt, updatedAt, source) VALUES ('t1', 'j1', 'Hang door', 1, 0, 1, 77, 'FIELD_LOG')")
            db.execSQL("INSERT INTO task (id, jobId, title, done, punch, createdAt, updatedAt, source) VALUES ('t2', 'j1', 'Trim out', 0, 1, 1, 88, 'FIELD_LOG')")
        }
        helper.runMigrationsAndValidate(5).use { db ->
            assertThat(db.count("SELECT doneAt FROM task WHERE id = 't1' AND sortOrder = 0")).isEqualTo(77L)
            assertThat(db.texts("SELECT title FROM task WHERE id = 't2' AND doneAt IS NULL AND sortOrder = 0")).containsExactly("Trim out")
        }
    }

    // AC-F53 (T046a): v6 adds the device-local conflict log and keeps every row.
    @Test fun v5ToV6AddsTheConflictLog() {
        helper.createDatabase(5).use { db ->
            db.execSQL("INSERT INTO task (id, jobId, title, done, punch, createdAt, updatedAt, source, sortOrder) VALUES ('t1', 'j1', 'Hang door', 0, 0, 1, 1, 'FIELD_LOG', 0)")
        }
        helper.runMigrationsAndValidate(6).use { db ->
            assertThat(db.texts("SELECT title FROM task WHERE id = 't1'")).containsExactly("Hang door")
            db.execSQL("INSERT INTO sync_conflict (tenantKey, entity, entityId, field, reason, label, at) VALUES ('company:c1', 'task', 't1', 'title', 'older', 'Hang door', 5)")
            assertThat(db.count("SELECT COUNT(*) FROM sync_conflict")).isEqualTo(1L)
        }
    }

    // R09a: v7 adds one column per daily log section; a log saved before keeps its single value and reads from it.
    @Test fun v6ToV7AddsTheDailyLogSectionColumns() {
        helper.createDatabase(6).use { db ->
            db.execSQL("INSERT INTO daily_log (id, jobId, day, sectionsJson, status, recipientsJson, createdAt, updatedAt, source) VALUES ('l1', 'j1', '2026-10-30', '{\"visitors\":\"Inspector\"}', 'DRAFT', '[]', 1, 1, 'FIELD_LOG')")
        }
        helper.runMigrationsAndValidate(7).use { db ->
            assertThat(db.texts("SELECT sectionsJson FROM daily_log WHERE id = 'l1' AND visitors IS NULL AND crewJson IS NULL AND delaysJson IS NULL"))
                .containsExactly("{\"visitors\":\"Inspector\"}")
        }
    }

    // T048d: v8 adds the R2 keys of a wall photo's two files; photos taken before keep their paths and have no keys yet.
    @Test fun v7ToV8AddsTheWallPhotoKeys() {
        helper.createDatabase(7).use { db ->
            db.execSQL("INSERT INTO wall_photo (id, wallId, stage, originalPath, texturePath, cornersJson, takenAt, createdAt, updatedAt, source) VALUES ('wp1', 'w1', 'ROUGH_IN', '/m/a.jpg', '/m/a-wall.jpg', '[]', 1, 1, 1, 'FIELD_LOG')")
        }
        helper.runMigrationsAndValidate(8).use { db ->
            assertThat(db.texts("SELECT originalPath FROM wall_photo WHERE id = 'wp1' AND r2Key IS NULL AND textureR2Key IS NULL")).containsExactly("/m/a.jpg")
        }
    }

    // cr:room-schema-v1: a phone on any earlier testing APK upgrades straight to this one.
    @Test fun everyReleasedVersionMigratesToTheLatest() {
        for (from in 1 until LATEST) {
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(DB)
            helper.createDatabase(from).use { db ->
                db.execSQL("INSERT INTO task (id, jobId, title, done, punch, createdAt, updatedAt, source) VALUES ('t$from', 'j1', 'Hang door', 0, 0, 1, 1, 'FIELD_LOG')")
            }
            helper.runMigrationsAndValidate(LATEST).use { db ->
                assertThat(db.texts("SELECT title FROM task WHERE id = 't$from'")).containsExactly("Hang door")
            }
        }
    }

    // The latest version is the one Room exports for the current code (see SchemaVersionGuardTest).
    @Test fun latestIsTheCompiledVersion() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(DB)
        helper.createDatabase(LATEST).use { db ->
            assertThat(db.count("PRAGMA user_version")).isEqualTo(LATEST.toLong())
        }
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            assertThat(db.openHelper.readableDatabase.version).isEqualTo(LATEST)
        } finally {
            db.close()
        }
    }

    private fun SQLiteConnection.count(sql: String): Long = prepare(sql).use { it.step(); it.getLong(0) }

    private fun SQLiteConnection.texts(sql: String): List<String> = prepare(sql).use { st ->
        buildList { while (st.step()) add(st.getText(0)) }
    }

    private companion object {
        const val DB = "migration-test.db"

        /** HomeRecordDatabase.version; raise it with each new schema. */
        const val LATEST = 8
    }
}
