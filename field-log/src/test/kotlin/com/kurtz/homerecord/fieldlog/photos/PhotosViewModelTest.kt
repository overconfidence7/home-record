package com.kurtz.homerecord.fieldlog.photos

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.photo.Annotation
import com.kurtz.homerecord.core.photo.AnnotationCodec
import com.kurtz.homerecord.core.photo.NormPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
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
import java.time.LocalDate
import java.time.ZoneId

// AC-F24: photos grouped by day with room and phase; AC-F25: before/after pair side by side; AC-F26: annotated copy flagged
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PhotosViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private val zone = ZoneId.of("UTC")
    private fun at(day: Int, hour: Int) = LocalDate.of(2026, 10, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            db.homeDao().upsert(HomeEntity(id = "h1", name = "Ortiz house", createdAt = 1, updatedAt = 1))
            db.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", sortOrder = 0, name = "Main", createdAt = 1, updatedAt = 1))
            db.roomDao().upsert(RoomEntity(id = "r1", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", homeId = "h1", name = "Ortiz rewire", createdAt = 1, updatedAt = 1))
        }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun photo(id: String, takenAt: Long, roomId: String? = null, before: String? = null, marks: String = "[]", thumb: String? = null) =
        PhotoEntity(
            id = id, homeId = "h1", roomId = roomId, jobId = "j1", stage = "Rough-in", path = "/m/$id.jpg", thumbPath = thumb,
            takenAt = takenAt, beforeOfPhotoId = before, annotationsJson = marks, createdAt = takenAt, updatedAt = takenAt,
        )

    private fun vm() = PhotosViewModel(PhotosDeps("j1", db.photoDao(), db.jobDao(), db.roomDao(), zone))

    @Test fun `empty job shows no days once loaded`() = runBlocking {
        val s = vm().state.filter { !it.loading }.first()
        assertThat(s.days).isEmpty()
        assertThat(s.count).isEqualTo(0)
    }

    @Test fun `photos group by day newest first with room names`() = runBlocking {
        db.photoDao().upsert(photo("a", at(20, 9), roomId = "r1"))
        db.photoDao().upsert(photo("b", at(21, 9), thumb = "/m/b_t.jpg"))
        val s = vm().state.filter { !it.loading }.first()
        assertThat(s.days.map { it.day.dayOfMonth }).containsExactly(21, 20).inOrder()
        val b = (s.days[0].cells.single() as PhotoCell.Single).tile
        assertThat(b.imagePath).isEqualTo("/m/b_t.jpg")
        val a = (s.days[1].cells.single() as PhotoCell.Single).tile
        assertThat(a.roomName).isEqualTo("Kitchen")
        assertThat(a.phase).isEqualTo("Rough-in")
    }

    @Test fun `after shot pairs with its before and annotated copy is flagged`() = runBlocking {
        val marks = AnnotationCodec.encode(listOf(Annotation.Arrow(NormPoint(0.1f, 0.1f), NormPoint(0.5f, 0.5f))))
        db.photoDao().upsert(photo("before", at(20, 9)))
        db.photoDao().upsert(photo("after", at(21, 9), before = "before", marks = marks))
        val s = vm().state.filter { !it.loading }.first()
        assertThat(s.days).hasSize(1)
        val pair = s.days[0].cells.single() as PhotoCell.Pair
        assertThat(pair.before.id).isEqualTo("before")
        assertThat(pair.after.id).isEqualTo("after")
        assertThat(pair.after.annotated).isTrue()
        assertThat(pair.before.annotated).isFalse()
        assertThat(s.count).isEqualTo(2)
    }
}
