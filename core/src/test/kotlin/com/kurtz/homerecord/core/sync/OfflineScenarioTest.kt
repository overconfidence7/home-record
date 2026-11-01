package com.kurtz.homerecord.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.log.DailyLogRepository
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.photo.CaptureTags
import com.kurtz.homerecord.core.photo.JobFiles
import com.kurtz.homerecord.core.photo.JobNotes
import com.kurtz.homerecord.core.photo.JobPhotos
import com.kurtz.homerecord.core.photo.SpeechTranscriber
import com.kurtz.homerecord.core.punch.NewPunch
import com.kurtz.homerecord.core.punch.PunchRepository
import com.kurtz.homerecord.core.task.JobTasks
import com.kurtz.homerecord.core.task.TaskDraft
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T046b: the airplane-mode pass on the JVM (AC-F52) and the two-phone timing (AC-F55), with the real repositories behind
 * every Field Log write and the real [SyncEngine] against the Worker's contract in [FakeSyncServer].
 *
 * Each phone's [SyncCoordinator] runs on a shared virtual clock ([clock]) and only records when it would sync; [step]
 * moves the clock a second at a time and runs the real pass for every phone that asked, at that moment. Room keeps its
 * own threads, so the clock never runs ahead while a database call is in flight and the 60 s figures are exact.
 * The device halves are F16-offline and TwoPhoneSyncAcceptanceTest.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// AC-F52 AC-F53 AC-F55
class OfflineScenarioTest {
    private val company = TenantRef(TenantKind.Company, "c-ortiz")
    private val server = FakeSyncServer()
    private val clock = TestCoroutineScheduler()
    private val coordinators = TestScope(StandardTestDispatcher(clock))
    private val phones = mutableListOf<Phone>()
    private val noSpeech = SpeechTranscriber { null }

    private class Net(online: Boolean) : ConnectivityMonitor {
        val state = MutableStateFlow(online)
        override val online = state
    }

    /** One phone: its own database, engine and coordinator; every repository write queues and asks for a sync, as the app does. */
    private inner class Phone(device: String, online: Boolean, foreground: Boolean = true) {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        val engine = SyncEngine(
            server, RoomRowStore(db), db.outboxDao(), db.syncCursorDao(), db.syncBaseDao(), device,
            conflictLog = SyncConflictLog(db.syncConflictDao(), RoomRowStore(db)),
        )
        val net = Net(online)
        var wantsSync = 0
        val coordinator = SyncCoordinator(
            coordinators, { wantsSync++; ApiResult.Ok(Unit) }, flowOf(true), flowOf(company), net, intervalMs = 30_000, writeDebounceMs = 1_000,
        )
        val uploads = mutableListOf<String>()
        var weatherCalls = 0
        private val onChange: suspend (String, String) -> Unit = { entity, id ->
            engine.recordChange(company, entity, id)
            coordinator.requestSync()
        }
        private val queueUpload: suspend (TenantRef, String, String, String, String, String) -> Unit = { _, _, id, _, _, _ -> uploads += id }
        val punches = PunchRepository(db, onChange = onChange)
        val photos = JobPhotos(db, queueUpload, companyId = { company.id }, onChange = onChange)
        val tasks = JobTasks(db, onChange = onChange)
        val files = JobFiles(db, onChange = onChange)
        val notes = JobNotes(db, noSpeech, queueUpload, companyId = { company.id }, onChange = onChange)
        val logs = DailyLogRepository(db, { _, _, _ -> weatherCalls++; ApiResult.Err(ApiFailure.Offline) }, onChange = onChange)

        init {
            phones += this
            coordinator.setForeground(foreground)
            coordinator.start()
        }

        suspend fun pending() = db.outboxDao().pending().size

        suspend fun seedJob() {
            db.jobDao().upsert(
                JobEntity(id = "j-harlan", companyId = company.id, name = "Harlan kitchen remodel", lat = 41.88, lng = -87.63, createdAt = 1, updatedAt = 1),
            )
        }

        fun goOnline() {
            server.online = true
            net.state.value = true
        }
    }

    @After fun tearDown() {
        coordinators.cancel()
        phones.forEach { it.db.close() }
    }

    /** Moves the virtual clock by [ms], a second at a time, running the real sync pass of each phone whose coordinator fired. */
    private suspend fun step(ms: Long, until: suspend () -> Boolean = { false }): Long? {
        val start = clock.currentTime
        while (clock.currentTime - start < ms) {
            clock.advanceTimeBy(1_000)
            clock.runCurrent()
            for (phone in phones) {
                repeat(phone.wantsSync) { phone.engine.sync(company) }
                phone.wantsSync = 0
            }
            if (until()) return clock.currentTime - start
        }
        return null
    }

    /** Counts the outbox rows one write adds; the write must finish on its own, without the network. */
    private suspend fun queued(phone: Phone, write: suspend () -> Any?): Int {
        val before = phone.pending()
        assertThat(write()).isNotNull()
        return phone.pending() - before
    }

    @Test fun inAirplaneModeEveryFieldWriteSavesAndQueuesExactlyOneChange() = runBlocking {
        server.online = false
        val phone = Phone("phone-crew", online = false)
        phone.seedJob()
        step(1_000)
        val day = LocalDate.now()

        assertThat(queued(phone) { phone.punches.record(NewPunch(company.id, "m-dee", "j-harlan", PunchKind.IN)) }).isEqualTo(1)
        var photoId: String? = null
        assertThat(queued(phone) { phone.photos.save("/media/IMG_0412.jpg", CaptureTags("j-harlan", phase = "Rough-in"), fix = null).also { photoId = it } }).isEqualTo(1)
        // The bytes wait in the upload queue; the photo row does not wait for them.
        assertThat(phone.uploads).containsExactly(photoId)
        var taskId: String? = null
        assertThat(queued(phone) { phone.tasks.add("j-harlan", TaskDraft("Set the kitchen outlets", punch = true)).also { taskId = it } }).isEqualTo(1)
        assertThat(queued(phone) { phone.tasks.setDone(checkNotNull(taskId), true).takeIf { it } }).isEqualTo(1)
        assertThat(queued(phone) { phone.notes.addText("j-harlan", "Inspector moved to Thursday") }).isEqualTo(1)
        assertThat(queued(phone) { phone.files.add("j-harlan", "/Plans", "Kitchen elevations.pdf", "/media/elev.pdf", "application/pdf", 48_213, "Dee") }).isEqualTo(1)
        var logId: String? = null
        assertThat(queued(phone) { phone.logs.assemble("j-harlan", day)?.also { logId = it.log.id } }).isEqualTo(1)
        assertThat(queued(phone) { phone.logs.update(checkNotNull(logId)) { it.copy(visitors = "Inspector Ruiz, 10:15") } }).isEqualTo(1)

        // punch, photo, task add, task done, note, file, log draft, log edit
        assertThat(phone.pending()).isEqualTo(8)
        // The weather lookup was tried once and failed fast; nothing else reached the network.
        assertThat(phone.weatherCalls).isEqualTo(1)
        assertThat(server.pushCalls).isEqualTo(0)
        // The coordinator's tries while offline lose nothing, and the chip reads offline.
        step(65_000)
        assertThat(phone.pending()).isEqualTo(8)
        assertThat(phone.engine.phase.value).isEqualTo(SyncPhase.Offline)
    }

    @Test fun reconnectingSendsEverythingWithin60SecondsWithNoDuplicates() = runBlocking {
        server.online = false
        val crew = Phone("phone-crew", online = false)
        crew.seedJob()
        crew.punches.record(NewPunch(company.id, "m-dee", "j-harlan", PunchKind.IN))
        crew.photos.save("/media/IMG_0412.jpg", CaptureTags("j-harlan"), fix = null)
        val task = checkNotNull(crew.tasks.add("j-harlan", TaskDraft("Patch drywall by the panel")))
        crew.tasks.setDone(task, true)
        crew.notes.addText("j-harlan", "Inspector moved to Thursday")
        step(40_000)
        val queued = crew.pending()
        assertThat(queued).isEqualTo(5)
        assertThat(crew.engine.phase.value).isEqualTo(SyncPhase.Offline)

        // Back online: the first push lands on the server but its answer is lost, so the phone sends the same rows again.
        server.loseNextResponse = true
        crew.goOnline()
        val took = step(60_000) { crew.pending() == 0 && crew.engine.phase.value == SyncPhase.Idle }
        assertThat(took).isNotNull()
        assertThat(checkNotNull(took)).isAtMost(60_000L)
        // Each change was applied once; the retry was answered from the (device, outbox) dedupe.
        assertThat(server.appliedOps).isEqualTo(queued)
        assertThat(server.rows(company).groupingBy { it.entity }.eachCount())
            .containsExactly("punch", 1, "photo", 1, "task", 1, "note", 1)

        // Another phone pulls one copy of each.
        val office = Phone("phone-office", online = true)
        assertThat(office.engine.sync(company)).isInstanceOf(ApiResult.Ok::class.java)
        assertThat(office.db.punchDao().observeFor(company.id).first()).hasSize(1)
        assertThat(office.db.photoDao().observeForJob("j-harlan").first()).hasSize(1)
        assertThat(office.db.taskDao().get(task)?.done).isTrue()
    }

    @Test fun twoPhonesSeeEachOthersPhotoAndTheOwnersPunchEditWithin60Seconds() = runBlocking {
        val crew = Phone("phone-crew", online = true)
        val owner = Phone("phone-owner", online = true)
        crew.seedJob()
        owner.seedJob()
        step(5_000)

        // A photo on the crew phone reaches the owner's job gallery on the owner's next pass.
        val photo = checkNotNull(crew.photos.save("/media/IMG_0420.jpg", CaptureTags("j-harlan", phase = "Rough-in"), fix = null))
        val photoTook = step(60_000) { owner.db.photoDao().get(photo) != null }
        assertThat(photoTook).isNotNull()
        assertThat(checkNotNull(photoTook)).isAtMost(60_000L)
        assertThat(owner.db.photoDao().get(photo)?.stage).isEqualTo("Rough-in")

        // The crew clocks in; the owner fixes the time with a reason; the crew phone shows the new time and the note.
        val punch = crew.punches.record(NewPunch(company.id, "m-dee", "j-harlan", PunchKind.IN, at = 1_793_196_000_000L))
        assertThat(step(60_000) { owner.db.punchDao().get(punch) != null }).isNotNull()
        assertThat(owner.punches.edit(punch, "m-nate", "Nate Kurtz", 1_793_194_200_000L, "j-harlan", "Forgot to clock in at 7:30")).isTrue()
        val editTook = step(60_000) { crew.db.punchDao().get(punch)?.at == 1_793_194_200_000L }
        assertThat(editTook).isNotNull()
        assertThat(checkNotNull(editTook)).isAtMost(60_000L)
        val audit = crew.punches.auditOf(checkNotNull(crew.db.punchDao().get(punch)))
        assertThat(audit.map { it.reason to it.byName }).containsExactly("Forgot to clock in at 7:30" to "Nate Kurtz")
        step(2_000)
        assertThat(crew.pending()).isEqualTo(0)
        assertThat(owner.pending()).isEqualTo(0)
    }

    @Test fun inTheBackgroundAWriteStillGoesOutAndAPhoneOnlyPullsWhenItSyncs() = runBlocking {
        // With the app in the background there is no 30 s tick; the write's own request still sends it.
        val crew = Phone("phone-crew", online = true, foreground = false)
        val owner = Phone("phone-owner", online = true, foreground = false)
        crew.seedJob()
        step(2_000)
        val task = checkNotNull(crew.tasks.add("j-harlan", TaskDraft("Label the panel")))
        assertThat(step(5_000) { crew.pending() == 0 }).isNotNull()
        assertThat(owner.db.taskDao().get(task)).isNull()
        // Opening the app (foreground) starts the ticks and the task arrives on the next one.
        owner.coordinator.setForeground(true)
        val took = step(60_000) { owner.db.taskDao().get(task) != null }
        assertThat(took).isNotNull()
        assertThat(checkNotNull(took)).isAtMost(31_000L)
    }
}
