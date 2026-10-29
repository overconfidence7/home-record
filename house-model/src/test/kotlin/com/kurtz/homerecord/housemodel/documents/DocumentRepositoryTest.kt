package com.kurtz.homerecord.housemodel.documents

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.DocumentFiles
import com.kurtz.homerecord.core.media.StoredDocument
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeDocumentFiles : DocumentFiles {
    val known = mutableMapOf<String, StoredDocument>()
    val discarded = mutableListOf<String>()
    override suspend fun import(uri: String) = known[uri]
    override fun discard(path: String) { discarded += path }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var files: FakeDocumentFiles
    private lateinit var repo: DocumentRepository
    private val changes = mutableListOf<String>()
    private var clock = 1_000L

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        files = FakeDocumentFiles().apply {
            known["u/manual.pdf"] = StoredDocument("media/a.pdf", "Furnace manual.pdf", "application/pdf", 2048)
            known["u/receipt.jpg"] = StoredDocument("media/b.jpg", "receipt.jpg", "image/jpeg", 512)
            known["u/notes.txt"] = StoredDocument("media/c.txt", "notes.txt", "text/plain", 10)
        }
        repo = DocumentRepository(db, files, now = { clock++ }, onChange = { e, id -> changes += "$e/$id" })
    }

    @After fun tearDown() = db.close()

    @AcceptanceCriterion("AC-C37")
    @Test fun addsPdfAndImageAndRefusesOtherTypes() = runTest {
        assertThat(repo.add("h1", DocumentScope.Home, "u/manual.pdf")).isNotNull()
        assertThat(repo.add("h1", DocumentScope.Home, "u/receipt.jpg", name = " Receipt ")).isNotNull()
        assertThat(repo.add("h1", DocumentScope.Home, "u/notes.txt")).isNull()
        assertThat(repo.add("h1", DocumentScope.Home, "u/missing.pdf")).isNull()
        assertThat(files.discarded).containsExactly("media/c.txt")
        val names = repo.observeFolders("h1").first().single().documents.map { it.name }
        assertThat(names).containsExactly("Receipt", "Furnace manual.pdf")
        assertThat(changes).hasSize(2)
    }

    @AcceptanceCriterion("AC-C37")
    @Test fun scopesSeparateHomeRoomAndItemDocuments() = runTest {
        repo.add("h1", DocumentScope.Home, "u/manual.pdf")
        repo.add("h1", DocumentScope.Room("r1"), "u/receipt.jpg")
        repo.add("h1", DocumentScope.Item("i1"), "u/manual.pdf")
        assertThat(repo.observeFolders("h1", DocumentScope.Home).first().flatMap { it.documents }).hasSize(1)
        assertThat(repo.observeFolders("h1", DocumentScope.Room("r1")).first().flatMap { it.documents }.single().mime).isEqualTo("image/jpeg")
        assertThat(repo.observeFolders("h1", DocumentScope.Item("i1")).first().flatMap { it.documents }).hasSize(1)
        assertThat(repo.observeFolders("h1").first().flatMap { it.documents }).hasSize(3)
    }

    @AcceptanceCriterion("AC-C37")
    @Test fun foldersGroupRenameMoveAttachAndDelete() = runTest {
        val a = checkNotNull(repo.add("h1", DocumentScope.Home, "u/manual.pdf", folder = "Manuals"))
        val b = checkNotNull(repo.add("h1", DocumentScope.Home, "u/receipt.jpg"))
        var folders = repo.observeFolders("h1").first()
        assertThat(folders.map { it.name }).containsExactly("", "Manuals").inOrder()

        repo.moveToFolder(b, " Receipts ")
        assertThat(repo.observeFolders("h1").first().map { it.name }).containsExactly("Manuals", "Receipts").inOrder()

        assertThat(repo.rename(a, "  ")).isFalse()
        assertThat(repo.rename(a, "Boiler manual")).isTrue()
        repo.attach(a, DocumentScope.Item("i9"))
        assertThat(repo.observeFolders("h1", DocumentScope.Item("i9")).first().single().documents.single().name).isEqualTo("Boiler manual")

        repo.delete(b)
        folders = repo.observeFolders("h1").first()
        assertThat(folders.flatMap { it.documents }.map { it.id }).containsExactly(a)
        assertThat(files.discarded).contains("media/b.jpg")
    }
}
