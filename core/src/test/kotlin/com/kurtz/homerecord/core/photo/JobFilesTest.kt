package com.kurtz.homerecord.core.photo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F23 (folders, move, rename, search by name)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobFilesTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<String>()
    private var n = 0
    private lateinit var files: JobFiles

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        files = JobFiles(db, { 5_000L + n }, { "f${n++}" }, { _, id -> changes += id })
    }

    @After fun tearDown() = db.close()

    private suspend fun add(name: String, folder: String = "") = files.add("j1", folder, name, "/m/$name", "application/pdf", 10, "Sam")

    @Test fun rootListsFoldersAndFilesSorted() = runBlocking<Unit> {
        add("Plans.pdf"); add("permit.pdf", "Permits"); add("final.pdf", "Permits/2026"); add("Bid.pdf")
        val root = files.list("j1", "")
        assertThat(root.folders).containsExactly("Permits")
        assertThat(root.files.map { it.name }).containsExactly("Bid.pdf", "Plans.pdf").inOrder()
        val permits = files.list("j1", "Permits")
        assertThat(permits.folders).containsExactly("2026")
        assertThat(permits.files.map { it.name }).containsExactly("permit.pdf")
    }

    @Test fun emptyFolderSurvivesAndDuplicateIsRefused() = runBlocking<Unit> {
        assertThat(files.createFolder("j1", "", "Photos to print")).isTrue()
        assertThat(files.createFolder("j1", "", "Photos to print")).isFalse()
        assertThat(files.createFolder("j1", "", "  ")).isFalse()
        val root = files.list("j1", "")
        assertThat(root.folders).containsExactly("Photos to print")
        assertThat(root.files).isEmpty()
    }

    @Test fun moveAndRenameQueueSync() = runBlocking<Unit> {
        val f = add("Plan.pdf")
        changes.clear()
        assertThat(files.rename(f.id, " Plan v2.pdf ")).isTrue()
        assertThat(files.rename(f.id, " ")).isFalse()
        assertThat(files.move(f.id, "/Permits/")).isTrue()
        val row = db.jobFileDao().get(f.id)!!
        assertThat(row.name).isEqualTo("Plan v2.pdf")
        assertThat(row.folder).isEqualTo("Permits")
        assertThat(changes).hasSize(2)
        assertThat(files.move("missing", "x")).isFalse()
    }

    @Test fun searchMatchesNameAcrossFoldersIgnoringCase() = runBlocking<Unit> {
        add("Panel schedule.pdf"); add("Permit.pdf", "Permits"); files.createFolder("j1", "", "Panel photos")
        assertThat(files.search("j1", "PANEL").map { it.name }).containsExactly("Panel schedule.pdf")
        assertThat(files.search("j1", "")).isEmpty()
    }

    @Test fun offlineOffClearsLocalPathAndDeleteHidesFile() = runBlocking<Unit> {
        val f = add("Plan.pdf")
        files.setOffline(f.id, false)
        assertThat(db.jobFileDao().get(f.id)!!.path).isNull()
        files.setOffline(f.id, true, "/m/new.pdf")
        assertThat(db.jobFileDao().get(f.id)!!.path).isEqualTo("/m/new.pdf")
        assertThat(files.delete(f.id)).isTrue()
        assertThat(files.list("j1", "").files).isEmpty()
        assertThat(files.delete(f.id)).isFalse()
    }
}
