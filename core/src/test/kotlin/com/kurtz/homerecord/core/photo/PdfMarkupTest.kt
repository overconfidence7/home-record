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

// AC-F21 (markup saves a new version with author and time; history lists each; original never modified)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PdfMarkupTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var files: JobFiles
    private lateinit var markup: PdfMarkup
    private val written = mutableListOf<Triple<String, List<PdfMark>, String>>()
    private val uploads = mutableListOf<String>()
    private var writeOk = true
    private var clock = 1_000L
    private var company: String? = "co1"

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        files = JobFiles(db, { clock })
        markup = PdfMarkup(
            db, { src, marks, dest -> written += Triple(src, marks, dest); writeOk }, { "/files/media/" },
            { _, _, id, _, _, _ -> uploads += id }, { company }, { 42L }, { clock },
        )
    }

    @After fun tearDown() = db.close()

    private val mark = listOf<PdfMark>(PdfMark.Arrow(0, NormPoint(0.1f, 0.1f), NormPoint(0.5f, 0.5f)))

    private suspend fun plan() = files.add("j1", "Plans", "Plan.pdf", "/m/plan.pdf", "application/pdf", 10, "Sam")

    @Test fun markupSavesNewVersionAndKeepsOriginal() = runBlocking<Unit> {
        val original = plan()
        clock = 2_000L
        val id = checkNotNull(markup.saveVersion(original.id, mark, "Maria"))
        val v2 = checkNotNull(db.jobFileDao().get(id))
        assertThat(v2.version).isEqualTo(2)
        assertThat(v2.parentFileId).isEqualTo(original.id)
        assertThat(v2.author).isEqualTo("Maria")
        assertThat(v2.createdAt).isEqualTo(2_000L)
        assertThat(v2.path).isNotEqualTo("/m/plan.pdf")
        assertThat(written.single().first).isEqualTo("/m/plan.pdf")
        val kept = checkNotNull(db.jobFileDao().get(original.id))
        assertThat(kept.path).isEqualTo("/m/plan.pdf")
        assertThat(kept.version).isEqualTo(1)
        assertThat(uploads).containsExactly(id)
    }

    // cr:pdfmarkup-cache-loss: a markup of a downloaded plan is this phone's own file, kept out of the download cache
    @Test fun markupOfADownloadIsWrittenToMediaNotBesideTheDownload() = runBlocking<Unit> {
        val downloaded = files.add("j1", "Plans", "Plan.pdf", "/cache/downloads/abc.pdf", "application/pdf", 10, "Sam")
        val id = markup.saveVersion(downloaded.id, mark, "Maria")
        val v2 = db.jobFileDao().get(requireNotNull(id))
        assertThat(v2?.path).isEqualTo("/files/media/$id.pdf")
        assertThat(written.single().third).isEqualTo("/files/media/$id.pdf")
        assertThat(v2?.offline).isTrue()
    }

    @Test fun historyListsEveryVersionNewestFirstAndListShowsOnlyTheLatest() = runBlocking<Unit> {
        val v1 = plan()
        val v2 = checkNotNull(markup.saveVersion(v1.id, mark, "Maria"))
        val v3 = checkNotNull(markup.saveVersion(v2, mark, "Sam"))
        assertThat(markup.history(v1.id).map { it.id }).containsExactly(v3, v2, v1.id).inOrder()
        assertThat(markup.history(v3).map { it.version }).containsExactly(3, 2, 1).inOrder()
        assertThat(files.list("j1", "Plans").files.map { it.id }).containsExactly(v3)
        assertThat(files.search("j1", "plan").map { it.id }).containsExactly(v3)
    }

    // cr:pdf-history-branch: two markups of one version both stay in the history, newest first
    @Test fun historyKeepsBothBranchesOfOneVersion() = runBlocking<Unit> {
        val v1 = plan()
        clock = 2_000L
        val maria = requireNotNull(markup.saveVersion(v1.id, mark, "Maria"))
        clock = 3_000L
        val sam = requireNotNull(markup.saveVersion(v1.id, mark, "Sam"))
        clock = 4_000L
        val next = requireNotNull(markup.saveVersion(maria, mark, "Maria"))
        val expected = listOf(next, sam, maria, v1.id)
        assertThat(markup.history(v1.id).map { it.id }).containsExactlyElementsIn(expected).inOrder()
        assertThat(markup.history(sam).map { it.id }).containsExactlyElementsIn(expected).inOrder()
        assertThat(files.list("j1", "Plans").files.map { it.id }).containsExactly(next, sam)
    }

    // cr:pdf-history-branch: a parent cycle from bad synced data ends instead of looping forever
    @Test fun historyStopsOnAParentCycle() = runBlocking<Unit> {
        val v1 = plan()
        clock = 2_000L
        val v2 = requireNotNull(markup.saveVersion(v1.id, mark, "Maria"))
        db.jobFileDao().upsert(requireNotNull(db.jobFileDao().get(v1.id)).copy(parentFileId = v2))
        assertThat(markup.history(v2).map { it.id }).containsExactly(v2, v1.id).inOrder()
    }

    @Test fun nothingSavedForNoMarksNotOnPhoneNotPdfOrFailedWrite() = runBlocking<Unit> {
        val f = plan()
        assertThat(markup.saveVersion(f.id, emptyList(), "Sam")).isNull()
        assertThat(markup.saveVersion("gone", mark, "Sam")).isNull()
        val photo = files.add("j1", "", "a.jpg", "/m/a.jpg", "image/jpeg", 1, null)
        assertThat(markup.saveVersion(photo.id, mark, "Sam")).isNull()
        files.setLocal(f.id, false, null)
        assertThat(markup.saveVersion(f.id, mark, "Sam")).isNull()
        files.setLocal(f.id, true, "/m/plan.pdf")
        writeOk = false
        assertThat(markup.saveVersion(f.id, mark, "Sam")).isNull()
        assertThat(markup.history(f.id)).hasSize(1)
    }
}
