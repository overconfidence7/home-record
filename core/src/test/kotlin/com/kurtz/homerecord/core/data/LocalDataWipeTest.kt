package com.kurtz.homerecord.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

// AC-F4 (sign-out clears cached company data)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LocalDataWipeTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: HomeRecordDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private suspend fun seed() {
        db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Kitchen remodel", createdAt = 1, updatedAt = 1))
        db.outboxDao().add(OutboxEntity(tenantKind = "company", tenantId = "c1", entity = "job", entityId = "j1", op = "upsert", payload = "{}", updatedAt = 1, deviceId = "d1"))
    }

    @Test fun clearsTablesMediaAndPrefs() = runBlocking<Unit> {
        seed()
        val media = tmp.newFolder("media").also { File(it, "a.jpg").writeText("jpg") }
        val offline = tmp.newFolder("offline-files").also { File(it, "plan.pdf").writeText("pdf") }
        val keep = tmp.newFolder("keep").also { File(it, "device").writeText("id") }
        val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("capture", Context.MODE_PRIVATE)
        prefs.edit().putString("room_j1", "Kitchen").commit()

        LocalDataWipe({ db.clearAllTables() }, listOf(media, offline), listOf({ prefs.edit().clear().commit(); Unit })).run()

        assertThat(db.jobDao().listFor("c1")).isEmpty()
        assertThat(db.outboxDao().observePendingCount().first()).isEqualTo(0)
        assertThat(media.exists()).isFalse()
        assertThat(offline.exists()).isFalse()
        assertThat(File(keep, "device").exists()).isTrue()
        assertThat(prefs.all).isEmpty()
    }

    @Test fun missingDirectoryIsFine() = runBlocking<Unit> {
        LocalDataWipe({ db.clearAllTables() }, listOf(File(tmp.root, "never-made")), emptyList()).run()
        assertThat(db.outboxDao().observePendingCount().first()).isEqualTo(0)
    }

    @Test fun runsInsideTheExclusiveBlock() = runBlocking<Unit> {
        seed()
        val order = mutableListOf<String>()
        val wipe = LocalDataWipe(
            clearTables = { order += "tables"; db.clearAllTables() },
            dirs = emptyList(),
            clearPrefs = listOf({ order += "prefs"; Unit }),
            exclusive = { block -> order += "lock"; block(); order += "unlock" },
        )
        wipe.run()
        assertThat(order).containsExactly("lock", "tables", "prefs", "unlock").inOrder()
    }
}
