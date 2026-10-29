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
import com.kurtz.homerecord.core.photo.PhotoEdits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
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

// AC-F25: caption, room and phase tags, Before mark and delete on one job photo
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PhotoDetailViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<String>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            db.homeDao().upsert(HomeEntity(id = "h1", name = "Ortiz house", createdAt = 1, updatedAt = 1))
            db.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", sortOrder = 0, name = "Main", createdAt = 1, updatedAt = 1))
            db.roomDao().upsert(RoomEntity(id = "r1", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", homeId = "h1", name = "Ortiz rewire", phasesJson = "[\"Rough-in\",\"Finish\"]", createdAt = 1, updatedAt = 1))
            db.photoDao().upsert(PhotoEntity(id = "p1", homeId = "h1", jobId = "j1", path = "/m/p1.jpg", takenAt = 5, caption = "Panel", createdAt = 5, updatedAt = 5))
        }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(id: String = "p1", gates: com.kurtz.homerecord.core.company.RoleGates = com.kurtz.homerecord.core.company.RoleGates(com.kurtz.homerecord.core.company.CompanyRole.Owner)) =
        PhotoDetailViewModel(
            PhotoDetailDeps(id, db.photoDao(), db.jobDao(), db.roomDao(), PhotoEdits(db, { 100 }) { e, i -> changes.add("$e:$i") }, kotlinx.coroutines.flow.flowOf(gates)),
        )

    private suspend fun loaded(v: PhotoDetailViewModel) = v.state.filter { !it.loading }.first()

    private suspend fun row(until: (PhotoEntity?) -> Boolean): PhotoEntity? = kotlinx.coroutines.withTimeout(5_000) {
        var r = db.photoDao().get("p1")
        while (!until(r)) { kotlinx.coroutines.delay(20); r = db.photoDao().get("p1") }
        r
    }

    @Test fun `loads caption rooms and phases of the job`() = runBlocking {
        val s = vm().state.filter { !it.loading }.first()
        assertThat(s.caption).isEqualTo("Panel")
        assertThat(s.rooms.map { it.name }).containsExactly("Kitchen")
        assertThat(s.phases).containsExactly("Rough-in", "Finish").inOrder()
    }

    @Test fun `unknown photo is reported missing`() = runBlocking {
        assertThat(vm("nope").state.filter { !it.loading }.first().missing).isTrue()
    }

    @Test fun `caption and tags are saved and queued for sync`() = runBlocking {
        val v = vm()
        loaded(v)
        v.onEvent(PhotoDetailEvent.Caption("Panel, labeled"))
        v.onEvent(PhotoDetailEvent.SaveCaption)
        row { it?.caption == "Panel, labeled" }
        v.onEvent(PhotoDetailEvent.Room("r1"))
        v.onEvent(PhotoDetailEvent.Phase("Finish"))
        val row = row { it?.roomId == "r1" && it.stage == "Finish" }!!
        assertThat(row.caption).isEqualTo("Panel, labeled")
        assertThat(row.roomId).isEqualTo("r1")
        assertThat(row.stage).isEqualTo("Finish")
        assertThat(changes).contains("photo:p1")
    }

    // AC-F25, cr:fl-before-phase: the Before mark is its own tag; the phase survives it and it can be undone.
    @Test fun `before toggles on and off and keeps the phase`() = runBlocking {
        val v = vm()
        loaded(v)
        v.onEvent(PhotoDetailEvent.Phase("Finish"))
        v.onEvent(PhotoDetailEvent.ToggleBefore)
        assertThat(v.state.value.isBefore).isTrue()
        assertThat(v.state.value.phase).isEqualTo("Finish")
        assertThat(row { it?.pairRole == PhotoEdits.BEFORE && it.stage == "Finish" }).isNotNull()
        v.onEvent(PhotoDetailEvent.Phase("Rough-in"))
        assertThat(v.state.value.isBefore).isTrue()
        assertThat(row { it?.pairRole == PhotoEdits.BEFORE && it.stage == "Rough-in" }).isNotNull()
        v.onEvent(PhotoDetailEvent.ToggleBefore)
        assertThat(v.state.value.isBefore).isFalse()
        assertThat(row { it != null && it.pairRole == null && it.stage == "Rough-in" }).isNotNull()
    }

    @Test fun `delete asks first then removes the photo`() = runBlocking {
        val v = vm()
        loaded(v)
        v.onEvent(PhotoDetailEvent.AskDelete)
        assertThat(v.state.value.confirmDelete).isTrue()
        v.onEvent(PhotoDetailEvent.CancelDelete)
        assertThat(db.photoDao().get("p1")).isNotNull()
        v.onEvent(PhotoDetailEvent.AskDelete)
        v.onEvent(PhotoDetailEvent.ConfirmDelete)
        row { it == null }
        assertThat(v.state.filter { it.deleted }.first().deleted).isTrue()
    }

    // AC-F3: Crew cannot delete; a Lead can only on a job they are on
    @Test fun `crew and an off-job lead cannot delete`() = runBlocking {
        for (gates in listOf(
            com.kurtz.homerecord.core.company.RoleGates(com.kurtz.homerecord.core.company.CompanyRole.Crew, setOf("j1")),
            com.kurtz.homerecord.core.company.RoleGates(com.kurtz.homerecord.core.company.CompanyRole.Lead, setOf("j2")),
        )) {
            val v = vm(gates = gates)
            assertThat(loaded(v).canDelete).isFalse()
            v.onEvent(PhotoDetailEvent.AskDelete)
            assertThat(v.state.value.confirmDelete).isFalse()
            v.onEvent(PhotoDetailEvent.ConfirmDelete)
            kotlinx.coroutines.delay(100)
            assertThat(db.photoDao().get("p1")).isNotNull()
        }
        val lead = vm(gates = com.kurtz.homerecord.core.company.RoleGates(com.kurtz.homerecord.core.company.CompanyRole.Lead, setOf("j1")))
        assertThat(lead.state.filter { !it.loading && it.canDelete }.first().canDelete).isTrue()
    }

    // AC-F55: a photo from another phone has no bytes here; the page shows the download, says why it stopped
    // (offline), and Retry fetches it by key and shows it (cr:sync-local-columns, judge:fl-photo-detail-no-image).
    @Test fun `photo from another phone downloads with offline and retry`() = runBlocking<Unit> {
        db.photoDao().upsert(PhotoEntity(id = "p2", homeId = "h1", jobId = "j1", path = "", r2Key = "c1/photo/p2/IMG.jpg", takenAt = 6, createdAt = 6, updatedAt = 6))
        val answers = ArrayDeque(listOf(com.kurtz.homerecord.core.photo.OnPhone.NoNetwork, com.kurtz.homerecord.core.photo.OnPhone.Ready))
        val v = PhotoDetailViewModel(
            PhotoDetailDeps(
                "p2", db.photoDao(), db.jobDao(), db.roomDao(), PhotoEdits(db, { 100 }) { e, i -> changes.add("$e:$i") },
                kotlinx.coroutines.flow.flowOf(com.kurtz.homerecord.core.company.RoleGates(com.kurtz.homerecord.core.company.CompanyRole.Owner)),
                isOnPhone = { it.isNotEmpty() },
                download = { id ->
                    val r = answers.removeFirst()
                    if (r == com.kurtz.homerecord.core.photo.OnPhone.Ready) db.photoDao().setLocalPath(id, "/cache/p2.jpg")
                    r
                },
            ),
        )
        val offline = v.state.filter { !it.loading && it.image != PhotoImage.Downloading }.first()
        assertThat(offline.image).isEqualTo(PhotoImage.NoNetwork)
        assertThat(offline.imagePath).isEmpty()
        v.onEvent(PhotoDetailEvent.RetryDownload)
        val shown = v.state.filter { it.image == PhotoImage.Ready }.first()
        assertThat(shown.imagePath).isEqualTo("/cache/p2.jpg")
        assertThat(changes).isEmpty()
    }

    @Test fun `photo on this phone is shown without a download`() = runBlocking<Unit> {
        var asked = false
        val v = PhotoDetailViewModel(
            PhotoDetailDeps(
                "p1", db.photoDao(), db.jobDao(), db.roomDao(), PhotoEdits(db, { 100 }) { e, i -> changes.add("$e:$i") },
                kotlinx.coroutines.flow.flowOf(com.kurtz.homerecord.core.company.RoleGates(com.kurtz.homerecord.core.company.CompanyRole.Owner)),
                isOnPhone = { true }, download = { asked = true; com.kurtz.homerecord.core.photo.OnPhone.Ready },
            ),
        )
        val s = loaded(v)
        assertThat(s.image).isEqualTo(PhotoImage.Ready)
        assertThat(s.imagePath).isEqualTo("/m/p1.jpg")
        assertThat(asked).isFalse()
    }
}
