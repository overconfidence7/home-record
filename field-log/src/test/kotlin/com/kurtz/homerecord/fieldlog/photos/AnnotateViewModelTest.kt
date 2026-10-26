package com.kurtz.homerecord.fieldlog.photos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.photo.Annotation
import com.kurtz.homerecord.core.photo.AnnotationTool
import com.kurtz.homerecord.core.photo.NormPoint
import com.kurtz.homerecord.core.photo.PhotoAnnotator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F26: arrow, circle, rectangle, freehand, text and measure marks, undo, saved as a copy
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AnnotateViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private var renderOk = true

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            db.homeDao().upsert(HomeEntity(id = "h1", name = "Ortiz house", createdAt = 1, updatedAt = 1))
            db.photoDao().upsert(PhotoEntity(id = "p1", homeId = "h1", path = "/m/p1.jpg", takenAt = 5, createdAt = 5, updatedAt = 5))
        }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(id: String = "p1") = AnnotateViewModel(
        AnnotateDeps(
            id, db.photoDao(),
            PhotoAnnotator(db, { _, _, _ -> renderOk }, { _, _, _, _, _, _ -> }, { null }),
            aspectOf = { 2f },
        ),
    )

    private fun p(x: Float, y: Float) = NormPoint(x, y)
    private fun AnnotateViewModel.draw(tool: AnnotationTool, vararg pts: NormPoint) {
        onEvent(AnnotateEvent.Tool(tool))
        onEvent(AnnotateEvent.Gesture(pts.toList()))
    }

    @Test fun `loads the photo with its aspect and reports a missing one`() = runBlocking {
        val s = vm().state.first { !it.loading }
        assertThat(s.aspect).isEqualTo(2f)
        assertThat(vm("nope").state.first { !it.loading }.missing).isTrue()
    }

    @Test fun `drags add marks and undo removes the last`() = runBlocking {
        val v = vm()
        v.state.first { !it.loading }
        v.draw(AnnotationTool.Arrow, p(.1f, .1f), p(.4f, .4f))
        v.draw(AnnotationTool.Rectangle, p(.5f, .5f), p(.8f, .8f))
        v.draw(AnnotationTool.Freehand, p(.1f, .9f), p(.2f, .8f), p(.3f, .9f))
        assertThat(v.state.value.marks).hasSize(3)
        v.onEvent(AnnotateEvent.Undo)
        assertThat(v.state.value.marks.map { it::class }).containsExactly(Annotation.Arrow::class, Annotation.Rectangle::class).inOrder()
        assertThat(v.state.value.canUndo).isTrue()
    }

    @Test fun `text and measure ask for a value before adding`() = runBlocking {
        val v = vm()
        v.state.first { !it.loading }
        v.draw(AnnotationTool.Text, p(.3f, .3f))
        assertThat(v.state.value.pending).isInstanceOf(PendingMark.Text::class.java)
        assertThat(v.state.value.marks).isEmpty()
        v.onEvent(AnnotateEvent.PendingText("Move panel"))
        v.onEvent(AnnotateEvent.ConfirmPending)
        assertThat(v.state.value.marks).containsExactly(Annotation.Text(p(.3f, .3f), "Move panel"))
        v.draw(AnnotationTool.Measure, p(.1f, .5f), p(.6f, .5f))
        v.onEvent(AnnotateEvent.PendingText("36 in"))
        v.onEvent(AnnotateEvent.ConfirmPending)
        assertThat(v.state.value.marks.last()).isEqualTo(Annotation.Measure(p(.1f, .5f), p(.6f, .5f), "36 in"))
        assertThat(v.state.value.pending).isNull()
    }

    @Test fun `save writes a copy and keeps the original`() = runBlocking {
        val v = vm()
        v.state.first { !it.loading }
        v.draw(AnnotationTool.Circle, p(.5f, .5f), p(.6f, .5f))
        v.onEvent(AnnotateEvent.Save)
        kotlinx.coroutines.withTimeout(5_000) { v.state.first { it.saved || it.failed } }
        assertThat(v.state.value.saved).isTrue()
        val all = db.photoDao().get("p1")
        assertThat(all?.annotationsJson).isEqualTo("[]")
    }

    @Test fun `nothing to save and a failed render are reported`() = runBlocking {
        val v = vm()
        v.state.first { !it.loading }
        assertThat(v.state.value.canSave).isFalse()
        v.onEvent(AnnotateEvent.Save)
        assertThat(v.state.value.saved).isFalse()
        renderOk = false
        v.draw(AnnotationTool.Arrow, p(.1f, .1f), p(.4f, .4f))
        v.onEvent(AnnotateEvent.Save)
        kotlinx.coroutines.withTimeout(5_000) { v.state.first { it.saved || it.failed } }
        assertThat(v.state.value.failed).isTrue()
        assertThat(v.state.value.saved).isFalse()
    }
}
