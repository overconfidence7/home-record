package com.kurtz.homerecord.core.photo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.PhotoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F26 (annotate: saved as a copy, original kept)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PhotoAnnotatorTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<Pair<String, String>>()
    private val uploads = mutableListOf<String>()
    private val rendered = mutableListOf<Triple<String, Int, String>>()
    private var renderOk = true
    private var company: String? = "c1"
    private lateinit var annotator: PhotoAnnotator
    private val arrow = Annotation.Arrow(NormPoint(0.1f, 0.1f), NormPoint(0.6f, 0.5f))

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        annotator = PhotoAnnotator(
            db, { src, marks, dest -> rendered += Triple(src, marks.size, dest); renderOk },
            { _, _, id, _, path, _ -> uploads += "$id:$path" }, { company }, { 7_000L }, { e, id -> changes += e to id },
        )
        runBlocking {
            db.photoDao().upsert(
                PhotoEntity(
                    id = "p1", homeId = "h1", roomId = "r1", jobId = "j1", stage = "Rough-in", path = "/m/p1.jpg", caption = "Pipe",
                    takenAt = 100, createdAt = 100, updatedAt = 100,
                ),
            )
        }
    }

    @After fun tearDown() = db.close()

    @Test fun copyKeepsOriginalAndTagsAndCarriesMarks() = runBlocking<Unit> {
        val id = annotator.saveCopy("p1", listOf(arrow))!!
        val copy = db.photoDao().get(id)!!
        assertThat(copy.path).isEqualTo("/m/$id.jpg")
        assertThat(copy.jobId).isEqualTo("j1")
        assertThat(copy.roomId).isEqualTo("r1")
        assertThat(copy.stage).isEqualTo("Rough-in")
        assertThat(AnnotationCodec.decode(copy.annotationsJson)).containsExactly(arrow)
        val original = db.photoDao().get("p1")!!
        assertThat(original.path).isEqualTo("/m/p1.jpg")
        assertThat(original.annotationsJson).isNotEqualTo(copy.annotationsJson)
        assertThat(rendered).containsExactly(Triple("/m/p1.jpg", 1, "/m/$id.jpg"))
        assertThat(changes).containsExactly("photo" to id)
        assertThat(uploads).containsExactly("$id:/m/$id.jpg")
    }

    @Test fun nothingSavedWithoutMarksOrOriginalOrRender() = runBlocking<Unit> {
        assertThat(annotator.saveCopy("p1", emptyList())).isNull()
        assertThat(annotator.saveCopy("nope", listOf(arrow))).isNull()
        renderOk = false
        assertThat(annotator.saveCopy("p1", listOf(arrow))).isNull()
        assertThat(changes).isEmpty()
        assertThat(db.photoDao().observeForJob("j1").first()).hasSize(1)
    }

    @Test fun noUploadWithoutCompany() = runBlocking<Unit> {
        company = null
        assertThat(annotator.saveCopy("p1", listOf(arrow))).isNotNull()
        assertThat(uploads).isEmpty()
    }
}
