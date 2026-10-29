package com.kurtz.homerecord.core.photo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.model.GeoFix
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

// AC-F24 (photo tagged with job, room, phase, time and GPS; grouped by day), AC-F25 (after links to its before), AC-F29 (lens limiter)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobPhotosTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<Pair<String, String>>()
    private val uploads = mutableListOf<String>()
    private var company: String? = "c1"
    private var clock = 5_000_000L
    private lateinit var photos: JobPhotos

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        photos = JobPhotos(db, { _, entity, id, name, _, _ -> uploads += "$entity/$id/$name" }, { company }, { clock }, { e, id -> changes += e to id })
        runBlocking { db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Kitchen", createdAt = 1, updatedAt = 1)) }
    }

    @After fun tearDown() = db.close()

    @Test fun shutterSavesTagsFixAndQueuesUpload() = runBlocking<Unit> {
        val id = checkNotNull(photos.save("/media/a.jpg", CaptureTags("j1", "r1", "Rough-in"), GeoFix(34.0, -118.0, 5f)))
        val row = checkNotNull(db.photoDao().get(id))
        assertThat(row.jobId).isEqualTo("j1")
        assertThat(row.roomId).isEqualTo("r1")
        assertThat(row.stage).isEqualTo("Rough-in")
        assertThat(row.takenAt).isEqualTo(5_000_000L)
        assertThat(row.lat).isEqualTo(34.0)
        assertThat(row.homeId).isEqualTo("j1")
        assertThat(changes).containsExactly("photo" to id)
        assertThat(uploads).containsExactly("photo/$id/a.jpg")
    }

    @Test fun noPermissionStillSavesWithoutGps() = runBlocking<Unit> {
        val id = checkNotNull(photos.save("/media/b.jpg", CaptureTags("j1"), null))
        assertThat(checkNotNull(db.photoDao().get(id)).lat).isNull()
    }

    // AC-F24: the fix found after the row was written is added without touching tags edited meanwhile.
    @Test fun lateFixFillsOnlyMissingLocationAndKeepsOtherEdits() = runBlocking<Unit> {
        val id = checkNotNull(photos.save("/media/late.jpg", CaptureTags("j1", "r1"), null))
        db.photoDao().upsert(checkNotNull(db.photoDao().get(id)).copy(stage = "Rough-in", pairRole = PhotoEdits.BEFORE))
        val located = checkNotNull(photos.save("/media/had.jpg", CaptureTags("j1"), GeoFix(1.0, 2.0, 5f)))
        changes.clear()
        clock = 6_000_000L
        photos.addFix(listOf(id, located), GeoFix(34.0, -118.0, 5f))
        val row = checkNotNull(db.photoDao().get(id))
        assertThat(row.lat).isEqualTo(34.0)
        assertThat(row.lng).isEqualTo(-118.0)
        assertThat(row.stage).isEqualTo("Rough-in")
        assertThat(row.pairRole).isEqualTo(PhotoEdits.BEFORE)
        assertThat(row.updatedAt).isEqualTo(6_000_000L)
        assertThat(checkNotNull(db.photoDao().get(located)).lat).isEqualTo(1.0)
        assertThat(changes).containsExactly("photo" to id)
    }

    @Test fun missingJobSavesNothing() = runBlocking<Unit> {
        assertThat(photos.save("/media/c.jpg", CaptureTags("gone"), null)).isNull()
        assertThat(changes).isEmpty()
    }

    @Test fun afterPhotoLinksToBefore() = runBlocking<Unit> {
        val before = checkNotNull(photos.save("/media/d.jpg", CaptureTags("j1"), null))
        val after = checkNotNull(photos.save("/media/e.jpg", CaptureTags("j1"), null, beforeOf = before))
        assertThat(checkNotNull(db.photoDao().get(after)).beforeOfPhotoId).isEqualTo(before)
    }

    @Test fun signedOutKeepsPhotoButQueuesNoUpload() = runBlocking<Unit> {
        company = null
        photos.save("/media/f.jpg", CaptureTags("j1"), null)
        assertThat(uploads).isEmpty()
        assertThat(photos.observeForJob("j1").first()).hasSize(1)
    }

    @Test fun groupsByDayNewestFirst() = runBlocking<Unit> {
        val utc = ZoneId.of("UTC")
        photos.save("/media/1.jpg", CaptureTags("j1"), null, takenAt = 1_700_000_000_000)
        photos.save("/media/2.jpg", CaptureTags("j1"), null, takenAt = 1_700_000_100_000)
        photos.save("/media/3.jpg", CaptureTags("j1"), null, takenAt = 1_700_200_000_000)
        val days = PhotoGrouping.byDay(photos.observeForJob("j1").first(), utc)
        assertThat(days.map { it.photos.size }).containsExactly(1, 2).inOrder()
        assertThat(days[1].photos.map { it.path }).containsExactly("/media/2.jpg", "/media/1.jpg").inOrder()
    }

    @Test fun lensLimiter() {
        assertThat(LensLimiter.choose(hasBack = true, hasFront = false, wantFront = true)).isEqualTo(LensChoice.Back)
        assertThat(LensLimiter.choose(hasBack = false, hasFront = true, wantFront = false)).isEqualTo(LensChoice.Front)
        assertThat(LensLimiter.choose(hasBack = true, hasFront = true, wantFront = true)).isEqualTo(LensChoice.Front)
        assertThat(LensLimiter.choose(hasBack = false, hasFront = false, wantFront = false)).isEqualTo(LensChoice.None)
        assertThat(LensLimiter.canFlip(true, false)).isFalse()
        assertThat(LensLimiter.canFlip(true, true)).isTrue()
    }
}
