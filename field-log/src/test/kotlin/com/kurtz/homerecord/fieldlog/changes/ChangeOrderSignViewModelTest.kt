package com.kurtz.homerecord.fieldlog.changes

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.changeorder.ChangeOrderDraft
import com.kurtz.homerecord.core.changeorder.JobChangeOrders
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.model.ChangeOrderLineItem
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import com.kurtz.homerecord.core.model.SignatureStroke
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F38: sign on phone: signature pad, Clear, Sign; image, signer name and time stored; status Signed
@AcceptanceCriterion("AC-F38")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ChangeOrderSignViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var orders: JobChangeOrders
    private val store = ViewModelStore()
    private var made = 0
    private var coId = ""
    private val written = mutableListOf<List<SignatureStroke>>()
    private var writeOk = true

    private val line = SignatureStroke(listOf(0.1f, 0.5f, 0.4f, 0.6f, 0.7f, 0.4f))

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        orders = JobChangeOrders(db, now = { 7_000L })
        coId = runBlocking {
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1))
            checkNotNull(orders.add("j1", ChangeOrderDraft("Island outlet", lines = listOf(ChangeOrderLineItem("l1", "Outlet", 1_000, 18_500)), clientName = "Dana Hale")))
        }
    }

    @After fun tearDown() {
        store.clear()
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(manage: Boolean = true, saved: SavedStateHandle = SavedStateHandle(), id: String = coId): ChangeOrderSignViewModel {
        val deps = ChangeOrderSignDeps("j1", id, orders, flowOf(manage), writeImage = { s -> written += s; if (writeOk) "/m/sig-${written.size}.png" else null })
        return ViewModelProvider.create(store, viewModelFactory { initializer { ChangeOrderSignViewModel(deps, saved) } })["vm${made++}", ChangeOrderSignViewModel::class]
    }

    private fun ChangeOrderSignViewModel.current(until: (ChangeOrderSignUiState) -> Boolean = { true }) = runBlocking {
        withTimeout(20_000) { state.first { !it.loading && until(it) } }
    }

    @Test fun signerStartsAsTheClientAndSignNeedsANameAndInk() {
        val m = vm()
        val st = m.current()
        assertThat(st.signerName).isEqualTo("Dana Hale")
        assertThat(st.totalCents).isEqualTo(18_500)
        assertThat(st.canSign).isFalse()
        m.onEvent(ChangeOrderSignEvent.Stroke(SignatureStroke(listOf(0.5f, 0.5f, 0.5f, 0.5f))))
        assertThat(m.state.value.canSign).isFalse()
        m.onEvent(ChangeOrderSignEvent.Stroke(line))
        assertThat(m.state.value.canSign).isTrue()
        m.onEvent(ChangeOrderSignEvent.SignerName("  "))
        assertThat(m.state.value.canSign).isFalse()
        m.onEvent(ChangeOrderSignEvent.SignerName("Dana Hale"))
        m.onEvent(ChangeOrderSignEvent.Clear)
        assertThat(m.state.value.strokes).isEmpty()
        assertThat(m.state.value.canSign).isFalse()
    }

    @Test fun signStoresImageSignerAndTimeAndMakesItSigned() {
        val m = vm()
        m.current()
        m.onEvent(ChangeOrderSignEvent.SignerName("Dana R. Hale"))
        m.onEvent(ChangeOrderSignEvent.Stroke(line))
        m.onEvent(ChangeOrderSignEvent.Sign)
        assertThat(m.current { it.signedId != null }.signedId).isEqualTo(coId)
        assertThat(written).containsExactly(listOf(line))
        val co = runBlocking { checkNotNull(orders.get(coId)) }
        assertThat(co.status).isEqualTo(ChangeOrderStatus.SIGNED)
        assertThat(co.signedBy).isEqualTo("Dana R. Hale")
        assertThat(co.signedAt).isEqualTo(7_000L)
        assertThat(co.signaturePath).isEqualTo("/m/sig-1.png")
    }

    @Test fun aFailedImageKeepsTheDrawingAndStaysDraft() {
        writeOk = false
        val m = vm()
        m.current()
        m.onEvent(ChangeOrderSignEvent.Stroke(line))
        m.onEvent(ChangeOrderSignEvent.Sign)
        val st = m.current { it.saveFailed }
        assertThat(st.strokes).containsExactly(line)
        assertThat(st.signedId).isNull()
        assertThat(runBlocking { orders.get(coId) }?.status).isEqualTo(ChangeOrderStatus.DRAFT)
    }

    @Test fun nameAndStrokesSurviveProcessDeath() {
        val saved = SavedStateHandle()
        val first = vm(saved = saved)
        first.current()
        first.onEvent(ChangeOrderSignEvent.SignerName("Sam Hale"))
        first.onEvent(ChangeOrderSignEvent.Stroke(line))
        val again = vm(saved = saved).current()
        assertThat(again.signerName).isEqualTo("Sam Hale")
        assertThat(again.strokes).containsExactly(line)
    }

    @Test fun signedOrCrewCannotSign() {
        assertThat(vm(manage = false).current().failed).isTrue()
        runBlocking { check(orders.sign(coId, "Dana Hale", "/m/x.png")) }
        assertThat(vm().current().failed).isTrue()
        assertThat(vm(id = "nope").current().failed).isTrue()
    }
}
