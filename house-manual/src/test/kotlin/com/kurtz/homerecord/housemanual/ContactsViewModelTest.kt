package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.contacts.ContactsEvent
import com.kurtz.homerecord.housemanual.contacts.ContactsViewModel
import com.kurtz.homerecord.housemodel.upkeep.ContactDraft
import com.kurtz.homerecord.housemodel.upkeep.UpkeepRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.withTimeout
import com.kurtz.homerecord.housemanual.contacts.ContactsUiState
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ContactsViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: UpkeepRepository
    private val vms = mutableListOf<ContactsViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = UpkeepRepository(db)
    }

    @After fun tearDown() {
        runBlocking { vms.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = ContactsViewModel(repo, "h1", saved).also {
        vms += it
        it.state.launchIn(it.viewModelScope) // keep the WhileSubscribed state hot
    }

    private fun ContactsViewModel.await(what: (ContactsUiState) -> Boolean) = runBlocking { withTimeout(5_000) { state.first(what) } }

    @AcceptanceCriterion("AC-C39")
    @Test fun saveNeedsNameThenListsContact() {
        val vm = vm()
        vm.onEvent(ContactsEvent.OpenForm(null))
        vm.onEvent(ContactsEvent.SaveForm)
        vm.await { it.showFormErrors }
        vm.onEvent(ContactsEvent.ChangeForm(ContactDraft(name = "Maria Soto", trade = "Plumber", phone = "555-0177")))
        vm.onEvent(ContactsEvent.SaveForm)
        val done = vm.await { it.form == null && it.rows.isNotEmpty() }
        assertThat(done.rows.map { it.contact.name }).containsExactly("Maria Soto")
    }

    @AcceptanceCriterion("AC-C39")
    @Test fun openFormSurvivesProcessDeath() {
        val saved = SavedStateHandle()
        val first = vm(saved)
        first.onEvent(ContactsEvent.OpenForm(null))
        first.onEvent(ContactsEvent.ChangeForm(ContactDraft(name = "Dale", email = "dale@x.co")))
        val second = vm(saved)
        second.await { it.form != null }
        assertThat(second.state.value.form?.name).isEqualTo("Dale")
        assertThat(second.state.value.form?.email).isEqualTo("dale@x.co")
    }

    @AcceptanceCriterion("AC-C39")
    @Test fun deleteAsksThenRemoves() {
        val vm = vm()
        vm.onEvent(ContactsEvent.OpenForm(null))
        vm.onEvent(ContactsEvent.ChangeForm(ContactDraft(name = "Dale")))
        vm.onEvent(ContactsEvent.SaveForm)
        val id = vm.await { it.rows.isNotEmpty() }.rows.single().contact.id
        vm.onEvent(ContactsEvent.AskDelete(id))
        assertThat(vm.await { it.confirmDeleteId == id }.confirmDeleteId).isEqualTo(id)
        vm.onEvent(ContactsEvent.ConfirmDelete)
        vm.await { it.rows.isEmpty() && it.confirmDeleteId == null }
    }

    @AcceptanceCriterion("AC-U6")
    @Test fun editFormIsDirtyOnlyAfterAChangeAndSurvivesProcessDeath() {
        // AC-C39 (cr:form-dialog): an opened contact is clean until edited; the original survives so dirty does too
        val first = vm()
        first.onEvent(ContactsEvent.OpenForm(null))
        first.onEvent(ContactsEvent.ChangeForm(ContactDraft(name = "Dale", phone = "555-0142")))
        first.onEvent(ContactsEvent.SaveForm)
        val id = first.await { it.rows.isNotEmpty() && it.form == null }.rows.single().contact.id

        val saved = SavedStateHandle()
        val vm = vm(saved)
        vm.onEvent(ContactsEvent.OpenForm(id))
        val opened = vm.await { it.form?.id == id }
        assertThat(opened.formDirty).isFalse()
        assertThat(opened.formCanSave).isFalse() // nothing changed, nothing to save
        vm.onEvent(ContactsEvent.ChangeForm(checkNotNull(opened.form).copy(trade = "HVAC")))
        val edited = vm.await { it.form?.trade == "HVAC" }
        assertThat(edited.formDirty).isTrue()
        assertThat(edited.formCanSave).isTrue()

        val restored = vm(saved).await { it.form != null }
        assertThat(restored.form?.trade).isEqualTo("HVAC")
        assertThat(restored.formDirty).isTrue()
    }
}
