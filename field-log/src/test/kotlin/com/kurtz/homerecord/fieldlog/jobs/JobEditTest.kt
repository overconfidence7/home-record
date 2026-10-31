package com.kurtz.homerecord.fieldlog.jobs

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F5 (phases, office emails), AC-F48 (map link): a new job with client and address saves offline and is queued for sync; coordinates resolve later.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobEditTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<Pair<String, String>>()
    private val created = mutableListOf<JobEditViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        // A load or save still in flight when the database closes would throw into the next test class.
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    /** Room hops to its own executor, so wait for the form to finish loading, as the screen does. */
    private suspend fun vm(jobId: String? = null, saved: SavedStateHandle = SavedStateHandle(), refresh: suspend () -> Unit = {}): JobEditViewModel {
        val vm = JobEditViewModel("c1", jobId, db.jobDao(), db.clientDao(), db.homeDao(), db.floorDao(), db.roomDao(), db.jobCrewDao(), db.memberDao(), refresh, { e, id -> changes += e to id }, "Main", saved, { "2026-10-22" }, { 1000L }).also { created += it }
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first { !it.loading } } }
        return vm
    }

    private suspend fun JobEditViewModel.save() {
        onEvent(JobEditEvent.Save)
        withContext(Dispatchers.Default) { withTimeout(5000) { state.first { it.saved } } }
    }

    @Test fun nameIsRequiredAndDatesMustBeInOrder() {
        assertThat(JobDraft().canSave).isFalse()
        assertThat(JobDraft(name = "Panel").canSave).isTrue()
        assertThat(JobDraft(name = "Panel", startDate = "2026-11-02", endDate = "2026-11-01").canSave).isFalse()
        assertThat(JobDraft(name = "Panel", budgetHoursText = "12x").canSave).isFalse()
    }

    @Test fun statusFollowsTheDates() {
        assertThat(JobDraft(startDate = "2026-11-01").statusFor("2026-10-22")).isEqualTo("UPCOMING")
        assertThat(JobDraft(endDate = "2026-10-01").statusFor("2026-10-22")).isEqualTo("DONE")
        assertThat(JobDraft(startDate = "2026-10-01").statusFor("2026-10-22")).isEqualTo("ACTIVE")
    }

    @Test fun savingANewJobCreatesTheClientAndQueuesBoth() = runTest {
        val vm = vm()
        vm.onEvent(JobEditEvent.Name("Panel upgrade"))
        vm.onEvent(JobEditEvent.Client("Rivera family"))
        vm.onEvent(JobEditEvent.Address("418 Maple St"))
        vm.onEvent(JobEditEvent.Budget("40"))
        vm.save()
        val job = db.jobDao().listFor("c1").single()
        assertThat(job.name).isEqualTo("Panel upgrade")
        assertThat(job.budgetHours).isEqualTo(40)
        assertThat(job.lat).isNull()
        assertThat(db.clientDao().get(checkNotNull(job.clientId))?.name).isEqualTo("Rivera family")
        assertThat(changes.map { it.first }).containsExactly("client", "job").inOrder()
    }

    @Test fun anExistingClientIsReusedIgnoringCase() = runTest {
        repeat(2) { n ->
            val vm = vm()
            vm.onEvent(JobEditEvent.Name("Job $n"))
            vm.onEvent(JobEditEvent.Client(if (n == 0) "Okafor" else "okafor"))
            vm.save()
        }
        assertThat(db.clientDao().listFor("c1")).hasSize(1)
        assertThat(db.jobDao().listFor("c1")).hasSize(2)
    }

    @Test fun editingKeepsCoordinatesUntilTheAddressChanges() = runTest {
        val first = vm()
        first.onEvent(JobEditEvent.Name("Deck"))
        first.onEvent(JobEditEvent.Address("1 Elm"))
        first.save()
        val id = db.jobDao().listFor("c1").single().id
        db.jobDao().upsert(checkNotNull(db.jobDao().get(id)).copy(lat = 39.7, lng = -84.2))

        val same = vm(id)
        same.onEvent(JobEditEvent.Notes("Bring ladder"))
        same.save()
        assertThat(db.jobDao().get(id)?.lat).isEqualTo(39.7)

        val moved = vm(id)
        moved.onEvent(JobEditEvent.Address("2 Oak"))
        moved.save()
        assertThat(db.jobDao().get(id)?.lat).isNull()
    }

    @Test fun mapLinkShowsOnlyWhileTheAddressMatchesTheResolvedOne() = runTest {
        val first = vm()
        first.onEvent(JobEditEvent.Name("Deck"))
        first.onEvent(JobEditEvent.Address("1 Elm"))
        first.save()
        assertThat(first.state.value.mapPoint).isNull()
        val id = db.jobDao().listFor("c1").single().id
        db.jobDao().upsert(checkNotNull(db.jobDao().get(id)).copy(lat = 39.7, lng = -84.2))

        val again = vm(id)
        assertThat(again.state.value.mapPoint).isEqualTo(39.7 to -84.2)
        again.onEvent(JobEditEvent.Address("2 Oak"))
        assertThat(again.state.value.mapPoint).isNull()
    }

    // AC-F33, AC-F45: the client's phone and email are typed on the job and saved on the client row.
    @Test fun clientPhoneAndEmailSaveOnTheClientAndReload() = runTest {
        val vm = vm()
        vm.onEvent(JobEditEvent.Name("Panel upgrade"))
        vm.onEvent(JobEditEvent.Client("Rivera family"))
        vm.onEvent(JobEditEvent.ClientPhone("(937) 555-0142"))
        vm.onEvent(JobEditEvent.ClientEmail(" ana.rivera@example.com "))
        vm.save()
        val job = db.jobDao().listFor("c1").single()
        val client = db.clientDao().get(job.clientId ?: error("no client"))
        assertThat(client?.phone).isEqualTo("(937) 555-0142")
        assertThat(client?.email).isEqualTo("ana.rivera@example.com")
        val again = vm(job.id).state.value
        assertThat(again.draft.clientPhone).isEqualTo("(937) 555-0142")
        assertThat(again.draft.clientEmail).isEqualTo("ana.rivera@example.com")
        assertThat(again.dirty).isFalse()
    }

    // AC-F45: editing the job's own client updates its details once; clearing a field clears it.
    @Test fun editingTheJobsClientUpdatesOrClearsItsDetails() = runTest {
        val first = vm()
        first.onEvent(JobEditEvent.Name("Panel upgrade"))
        first.onEvent(JobEditEvent.Client("Okafor"))
        first.onEvent(JobEditEvent.ClientPhone("937-555-0100"))
        first.onEvent(JobEditEvent.ClientEmail("okafor@example.com"))
        first.save()
        val id = db.jobDao().listFor("c1").single().id
        changes.clear()
        val edit = vm(id)
        edit.onEvent(JobEditEvent.ClientPhone("937-555-0199"))
        edit.onEvent(JobEditEvent.ClientEmail(""))
        edit.save()
        val client = db.clientDao().listFor("c1").single()
        assertThat(client.phone).isEqualTo("937-555-0199")
        assertThat(client.email).isNull()
        assertThat(changes.map { it.first }).containsExactly("client", "job").inOrder()
    }

    // AC-F45: naming a known client on a new job with blank details keeps the ones on file; unchanged details write nothing.
    @Test fun aKnownClientKeepsItsDetailsWhenLeftBlank() = runTest {
        val first = vm()
        first.onEvent(JobEditEvent.Name("Panel upgrade"))
        first.onEvent(JobEditEvent.Client("Okafor"))
        first.onEvent(JobEditEvent.ClientPhone("937-555-0100"))
        first.save()
        changes.clear()
        val second = vm()
        second.onEvent(JobEditEvent.Name("Kitchen circuits"))
        second.onEvent(JobEditEvent.Client("okafor"))
        second.save()
        assertThat(db.clientDao().listFor("c1").single().phone).isEqualTo("937-555-0100")
        assertThat(changes.map { it.first }).containsExactly("job")
    }

    // AC-F33: a mistyped client email or phone blocks Save with a message.
    @Test fun badClientEmailOrPhoneBlocksSave() {
        val ok = JobDraft(name = "Panel", clientName = "Rivera", clientPhone = "+1 937 555 0142", clientEmail = "ana@example.com")
        assertThat(ok.canSave).isTrue()
        assertThat(ok.copy(clientEmail = "ana@example").let { it.clientEmailInvalid && !it.canSave }).isTrue()
        assertThat(ok.copy(clientPhone = "555").let { it.clientPhoneInvalid && !it.canSave }).isTrue()
        assertThat(ok.copy(clientPhone = "", clientEmail = "").canSave).isTrue()
    }

    @Test fun clientDetailsSurviveProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val vm = vm(saved = handle)
        vm.onEvent(JobEditEvent.ClientPhone("937-555-0142"))
        vm.onEvent(JobEditEvent.ClientEmail("ana@example.com"))
        val draft = vm(saved = handle).state.value.draft
        assertThat(draft.clientPhone).isEqualTo("937-555-0142")
        assertThat(draft.clientEmail).isEqualTo("ana@example.com")
    }

    @Test fun unsavedTextSurvivesProcessDeath() = runTest {
        val handle = SavedStateHandle()
        vm(saved = handle).onEvent(JobEditEvent.Name("Half typed"))
        assertThat(vm(saved = handle).state.value.draft.name).isEqualTo("Half typed")
    }

    @Test fun roomsBecomeUnplacedRoomsOnTheJobsHome() = runTest {
        val vm = vm()
        vm.onEvent(JobEditEvent.Name("Kitchen remodel"))
        vm.onEvent(JobEditEvent.AddRoom("Kitchen"))
        vm.onEvent(JobEditEvent.AddRoom("kitchen "))
        vm.onEvent(JobEditEvent.AddRoom("Pantry"))
        assertThat(vm.state.value.draft.rooms).containsExactly("Kitchen", "Pantry").inOrder()
        vm.save()
        val job = db.jobDao().listFor("c1").single()
        val floor = db.floorDao().listFor(checkNotNull(job.homeId)).single()
        val rooms = db.roomDao().listFor(floor.id)
        assertThat(rooms.map { it.name }).containsExactly("Kitchen", "Pantry")
        assertThat(rooms.map { it.polygonJson }.distinct()).containsExactly("[]")
        // AC-F5: the job's home, floor and rooms are queued for sync
        assertThat(changes.map { it.first }.filter { it != "job" && it != "client" }).containsExactly("home", "floor", "room", "room").inOrder()

        val edit = vm(job.id)
        assertThat(edit.state.value.draft.rooms).containsExactly("Kitchen", "Pantry")
        edit.onEvent(JobEditEvent.RemoveRoom("Pantry"))
        // an unplaced, empty room comes off at once, with no confirm
        edit.awaitDraft { "Pantry" !in it.rooms }
        assertThat(edit.state.value.removing).isNull()
        edit.onEvent(JobEditEvent.AddRoom("Garage"))
        edit.save()
        assertThat(db.roomDao().listFor(floor.id).map { it.name }).containsExactly("Kitchen", "Garage")
        assertThat(db.jobDao().get(job.id)?.homeId).isEqualTo(job.homeId)
        assertThat(changes.count { it.first == "room" }).isEqualTo(2 + 2)
    }

    private suspend fun JobEditViewModel.awaitDraft(check: (JobDraft) -> Boolean) {
        withContext(Dispatchers.Default) { withTimeout(5000) { state.first { check(it.draft) } } }
    }

    // AC-F48, ui-standards rule 16 (cr:fl-jobedit-room-delete): a room on the plan or with photos asks first and names what goes.
    @Test fun removingARoomInUseAsksFirstAndCancelKeepsIt() = runTest {
        val vm = vm()
        vm.onEvent(JobEditEvent.Name("Kitchen remodel"))
        vm.onEvent(JobEditEvent.AddRoom("Kitchen"))
        vm.onEvent(JobEditEvent.AddRoom("Pantry"))
        vm.save()
        val job = db.jobDao().listFor("c1").single()
        val kitchen = db.roomDao().listForHome(checkNotNull(job.homeId)).first { it.name == "Kitchen" }
        db.roomDao().upsert(kitchen.copy(polygonJson = """[{"x":0,"y":0},{"x":144,"y":0},{"x":144,"y":120},{"x":0,"y":120}]"""))
        repeat(2) { n ->
            db.photoDao().upsert(com.kurtz.homerecord.core.data.PhotoEntity(id = "p$n", homeId = checkNotNull(job.homeId), roomId = kitchen.id, jobId = job.id, path = "", takenAt = 1, createdAt = 1, updatedAt = 1))
        }
        db.taskDao().upsert(com.kurtz.homerecord.core.data.TaskEntity(id = "t1", jobId = job.id, title = "Patch drywall", roomId = kitchen.id, createdAt = 1, updatedAt = 1))

        val edit = vm(job.id)
        edit.onEvent(JobEditEvent.RemoveRoom("Kitchen"))
        val asked = withContext(Dispatchers.Default) { withTimeout(5000) { edit.state.first { it.removing != null } } }
        assertThat(asked.removing).isEqualTo(RoomRemoval("Kitchen", placed = true, photos = 2, items = 0, tasks = 1))
        assertThat(asked.draft.rooms).contains("Kitchen")
        edit.onEvent(JobEditEvent.CancelRemoveRoom)
        assertThat(edit.state.value.removing).isNull()
        assertThat(edit.state.value.draft.rooms).containsExactly("Kitchen", "Pantry")
        assertThat(edit.state.value.dirty).isFalse()
    }

    @Test fun confirmingTheRemovalTakesTheRoomOffOnSave() = runTest {
        val vm = vm()
        vm.onEvent(JobEditEvent.Name("Bath refresh"))
        vm.onEvent(JobEditEvent.AddRoom("Hall bath"))
        vm.save()
        val job = db.jobDao().listFor("c1").single()
        val bath = db.roomDao().listForHome(checkNotNull(job.homeId)).single()
        db.photoDao().upsert(com.kurtz.homerecord.core.data.PhotoEntity(id = "p1", homeId = checkNotNull(job.homeId), roomId = bath.id, jobId = job.id, path = "", takenAt = 1, createdAt = 1, updatedAt = 1))

        val edit = vm(job.id)
        edit.onEvent(JobEditEvent.RemoveRoom("Hall bath"))
        withContext(Dispatchers.Default) { withTimeout(5000) { edit.state.first { it.removing != null } } }
        assertThat(edit.state.value.removing?.placed).isFalse()
        assertThat(edit.state.value.removing?.photos).isEqualTo(1)
        edit.onEvent(JobEditEvent.ConfirmRemoveRoom)
        assertThat(edit.state.value.removing).isNull()
        assertThat(edit.state.value.draft.rooms).isEmpty()
        edit.save()
        assertThat(db.roomDao().listForHome(checkNotNull(job.homeId))).isEmpty()
        assertThat(changes.last { it.first == "room" }.second).isEqualTo(bath.id)
    }

    @Test fun phasesAndOfficeEmailsSaveAndReload() = runTest {
        val vm = vm()
        vm.onEvent(JobEditEvent.Name("Deck"))
        vm.onEvent(JobEditEvent.AddPhase("Demo"))
        vm.onEvent(JobEditEvent.AddPhase(" demo "))
        vm.onEvent(JobEditEvent.AddPhase("Framing"))
        vm.onEvent(JobEditEvent.AddOfficeEmail("office@qa.test"))
        vm.onEvent(JobEditEvent.AddOfficeEmail("not an email"))
        vm.onEvent(JobEditEvent.AddOfficeEmail("OFFICE@qa.test"))
        assertThat(vm.state.value.draft.phases).containsExactly("Demo", "Framing").inOrder()
        assertThat(vm.state.value.draft.officeEmails).containsExactly("office@qa.test")
        vm.save()
        val job = db.jobDao().listFor("c1").single()
        assertThat(job.phasesJson).isEqualTo("[\"Demo\",\"Framing\"]")
        assertThat(job.officeEmailsJson).isEqualTo("[\"office@qa.test\"]")

        val edit = vm(job.id)
        assertThat(edit.state.value.draft.phases).containsExactly("Demo", "Framing").inOrder()
        edit.onEvent(JobEditEvent.RemovePhase("Demo"))
        edit.onEvent(JobEditEvent.RemoveOfficeEmail("office@qa.test"))
        edit.save()
        assertThat(db.jobDao().get(job.id)?.phasesJson).isEqualTo("[\"Framing\"]")
        assertThat(db.jobDao().get(job.id)?.officeEmailsJson).isEqualTo("[]")
    }

    @Test fun phasesAndEmailsSurviveProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val first = vm(saved = handle)
        first.onEvent(JobEditEvent.AddPhase("Rough-in"))
        first.onEvent(JobEditEvent.AddOfficeEmail("a@b.co"))
        val again = vm(saved = handle).state.value.draft
        assertThat(again.phases).containsExactly("Rough-in")
        assertThat(again.officeEmails).containsExactly("a@b.co")
    }

    // AC-F5: crew is picked from the team list (cached offline), saved as job_crew rows and queued for sync.
    @Test fun crewIsPickedSavedAndQueued() = runTest {
        val refreshed = mutableListOf<Boolean>()
        db.memberDao().upsert(com.kurtz.homerecord.core.data.MemberEntity("u1", "c1", "u1", "Dana Ortiz", createdAt = 1, updatedAt = 1))
        val vm = vm(refresh = {
            refreshed += true
            db.memberDao().upsert(com.kurtz.homerecord.core.data.MemberEntity("u2", "c1", "u2", "Marcus Bell", createdAt = 1, updatedAt = 1))
        })
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first { it.members.size == 2 } } }
        assertThat(vm.state.value.members.map { it.name }).containsExactly("Dana Ortiz", "Marcus Bell").inOrder()
        vm.onEvent(JobEditEvent.Name("Panel"))
        vm.onEvent(JobEditEvent.ToggleCrew("u1"))
        vm.onEvent(JobEditEvent.ToggleCrew("u2"))
        vm.onEvent(JobEditEvent.ToggleCrew("u2"))
        vm.save()
        val job = db.jobDao().listFor("c1").single()
        assertThat(db.jobCrewDao().listFor(job.id).map { it.memberId }).containsExactly("u1")
        assertThat(changes.count { it.first == "job_crew" }).isEqualTo(1)

        val edit = vm(job.id)
        assertThat(edit.state.value.draft.crew).containsExactly("u1")
        edit.onEvent(JobEditEvent.ToggleCrew("u1"))
        edit.onEvent(JobEditEvent.ToggleCrew("u2"))
        edit.save()
        assertThat(db.jobCrewDao().listFor(job.id).map { it.memberId }).containsExactly("u2")
        assertThat(changes.count { it.first == "job_crew" }).isEqualTo(3)
        assertThat(refreshed).isNotEmpty()
    }
}
