package com.kurtz.homerecord.core.data

import androidx.room.testing.MigrationTestHelper
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
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HomeRecordDatabase::class.java)

    // cr:sync-global-cursor
    @Test fun v1ToV2DropsTheSingleCursorAndKeepsConfirmedRows() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL("INSERT INTO sync_state (id, lastServerSeq) VALUES (1, 42)")
            db.execSQL("INSERT INTO sync_base (`key`, rowJson) VALUES ('task/t1', '{\"id\":\"t1\"}')")
            db.execSQL("INSERT INTO task (id, jobId, title, done, punch, createdAt, updatedAt, source) VALUES ('t1', 'j1', 'Patch drywall', 0, 0, 1, 1, 'FIELD_LOG')")
        }
        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sync_state'").use { assertThat(it.count).isEqualTo(0) }
            db.query("SELECT COUNT(*) FROM sync_cursor").use { it.moveToFirst(); assertThat(it.getInt(0)).isEqualTo(0) }
            db.query("SELECT rowJson, tenant FROM sync_base WHERE `key` = 'task/t1'").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(1)).isEmpty()
            }
            db.query("SELECT title FROM task WHERE id = 't1'").use { it.moveToFirst(); assertThat(it.getString(0)).isEqualTo("Patch drywall") }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
