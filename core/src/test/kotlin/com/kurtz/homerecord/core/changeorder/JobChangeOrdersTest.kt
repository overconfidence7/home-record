package com.kurtz.homerecord.core.changeorder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.model.ChangeOrderLineItem
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F37 (create from a job: title, description, line items, total in cents, client name), AC-F52 (each write queues a sync change)
@AcceptanceCriterion("AC-F37", "AC-F52")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobChangeOrdersTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var orders: JobChangeOrders
    private val changes = mutableListOf<String>()
    private var clock = 1_000L

    private val lines = listOf(
        ChangeOrderLineItem("l1", " Move outlet to island ", 1_000, 18_500),
        ChangeOrderLineItem("l2", "Drywall patch", 2_500, 6_000),
    )

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        orders = JobChangeOrders(db, now = { clock }, onChange = { e, id -> changes += "$e/$id" })
        runBlocking { db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1)) }
    }

    @After fun tearDown() = db.close()

    @Test fun addKeepsEveryFieldComputesTheTotalAndQueuesOneChange() = runBlocking<Unit> {
        val id = checkNotNull(orders.add("j1", ChangeOrderDraft(" Island outlet ", " Client asked for a second outlet. ", lines, " Dana Hale ")))
        val co = checkNotNull(orders.get(id))
        assertThat(co.title).isEqualTo("Island outlet")
        assertThat(co.description).isEqualTo("Client asked for a second outlet.")
        assertThat(co.clientName).isEqualTo("Dana Hale")
        assertThat(co.lines.map { it.description }).containsExactly("Move outlet to island", "Drywall patch").inOrder()
        assertThat(co.totalCents).isEqualTo(18_500 + 15_000)
        assertThat(db.changeOrderDao().get(id)?.totalCents).isEqualTo(33_500)
        assertThat(co.status).isEqualTo(ChangeOrderStatus.DRAFT)
        assertThat(changes).containsExactly("change_order/$id")
    }

    @Test fun blankTitleOrMissingJobSavesNothing() = runBlocking<Unit> {
        assertThat(orders.add("j1", ChangeOrderDraft("  "))).isNull()
        assertThat(orders.add("gone", ChangeOrderDraft("Island outlet"))).isNull()
        assertThat(changes).isEmpty()
    }

    @Test fun emptyLinesAreDroppedAndBlankFieldsCleared() = runBlocking<Unit> {
        val id = checkNotNull(orders.add("j1", ChangeOrderDraft("Island outlet", " ", lines + ChangeOrderLineItem("l3", "  ", 1_000, 0), "")))
        val co = checkNotNull(orders.get(id))
        assertThat(co.lines).hasSize(2)
        assertThat(co.description).isNull()
        assertThat(co.clientName).isNull()
    }

    @Test fun updateSavesADraftAndRecomputesTheTotal() = runBlocking<Unit> {
        val id = checkNotNull(orders.add("j1", ChangeOrderDraft("Island outlet", lines = lines)))
        clock = 2_000
        assertThat(orders.update(id, ChangeOrderDraft("Island outlet and USB", lines = lines.take(1)))).isTrue()
        val co = checkNotNull(orders.get(id))
        assertThat(co.title).isEqualTo("Island outlet and USB")
        assertThat(co.totalCents).isEqualTo(18_500)
        assertThat(co.updatedAt).isEqualTo(2_000)
        assertThat(changes).hasSize(2)
    }

    @Test fun aSignedOrderCannotBeEditedOrDeleted() = runBlocking<Unit> {
        val id = checkNotNull(orders.add("j1", ChangeOrderDraft("Island outlet", lines = lines)))
        val row = checkNotNull(db.changeOrderDao().get(id))
        db.changeOrderDao().upsert(row.copy(status = "SIGNED", signedAt = 5_000, signedBy = "Dana Hale"))
        assertThat(orders.update(id, ChangeOrderDraft("Changed"))).isFalse()
        assertThat(orders.delete(id)).isFalse()
        assertThat(orders.get(id)?.title).isEqualTo("Island outlet")
        assertThat(orders.get(id)?.status).isEqualTo(ChangeOrderStatus.SIGNED)
    }

    @Test fun deleteRemovesADraftAndListIsNewestFirst() = runBlocking<Unit> {
        val first = checkNotNull(orders.add("j1", ChangeOrderDraft("Island outlet")))
        clock = 3_000
        val second = checkNotNull(orders.add("j1", ChangeOrderDraft("Pantry shelf")))
        assertThat(orders.observeForJob("j1").first().map { it.id }).containsExactly(second, first).inOrder()
        assertThat(orders.delete(first)).isTrue()
        assertThat(orders.observeForJob("j1").first().map { it.id }).containsExactly(second)
        assertThat(changes.last()).isEqualTo("change_order/$first")
    }
}
