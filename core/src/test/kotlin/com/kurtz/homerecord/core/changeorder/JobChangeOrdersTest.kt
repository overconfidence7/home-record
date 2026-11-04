package com.kurtz.homerecord.core.changeorder

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ChangeOrderEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.model.ChangeOrderLineItem
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import com.kurtz.homerecord.core.model.Signature
import com.kurtz.homerecord.core.model.SignatureStroke
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F37 (create from a job: title, description, line items, total in cents, client name), AC-F52 (each write queues a sync change),
// AC-F38 (sign stores the image, signer and time, makes it Signed and read only; duplicate to change)
@AcceptanceCriterion("AC-F37", "AC-F38", "AC-F40", "AC-F52")
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

    // T056b: photos on a change order are the job's own, once each, in the order added; an edit replaces them.
    @Test fun photosKeepTheJobsOwnInOrderOnce() = runBlocking<Unit> {
        fun photo(id: String, job: String, deleted: Long? = null) =
            PhotoEntity(id = id, homeId = "h1", jobId = job, path = "/m/$id.jpg", takenAt = 1, createdAt = 1, updatedAt = 1, deletedAt = deleted)
        db.photoDao().upsert(photo("p1", "j1"))
        db.photoDao().upsert(photo("p2", "j1"))
        db.photoDao().upsert(photo("other", "j2"))
        db.photoDao().upsert(photo("gone", "j1", deleted = 5))
        val id = checkNotNull(orders.add("j1", ChangeOrderDraft("Island outlet", photoIds = listOf("p2", "other", "p1", "p2", "gone", "nope"))))
        assertThat(checkNotNull(orders.get(id)).photoIds).containsExactly("p2", "p1").inOrder()
        assertThat(orders.update(id, ChangeOrderDraft("Island outlet", photoIds = listOf("p1")))).isTrue()
        assertThat(checkNotNull(orders.get(id)).photoIds).containsExactly("p1")
        assertThat(orders.update(id, ChangeOrderDraft("Island outlet"))).isTrue()
        assertThat(checkNotNull(orders.get(id)).photoIds).isEmpty()
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

    // AC-F38: signing stores the signature image, the signer and the time, freezes the total and locks the order.
    @Test fun signStoresSignerTimeAndImageAndLocksTheOrder() = runBlocking<Unit> {
        val id = checkNotNull(orders.add("j1", ChangeOrderDraft("Island outlet", lines = lines, clientName = "Dana Hale")))
        changes.clear()
        clock = 5_000
        assertThat(orders.sign(id, "  ", "/m/sig.png")).isFalse()
        assertThat(orders.sign(id, " Dana Hale ", "/m/sig.png")).isTrue()
        val co = checkNotNull(orders.get(id))
        assertThat(co.status).isEqualTo(ChangeOrderStatus.SIGNED)
        assertThat(co.signedBy).isEqualTo("Dana Hale")
        assertThat(co.signedAt).isEqualTo(5_000)
        assertThat(co.signaturePath).isEqualTo("/m/sig.png")
        assertThat(co.totalCents).isEqualTo(33_500)
        assertThat(changes).containsExactly("change_order/$id")
        // Read only from here: no edit, no delete, no second signature.
        assertThat(orders.update(id, ChangeOrderDraft("Changed"))).isFalse()
        assertThat(orders.delete(id)).isFalse()
        assertThat(orders.sign(id, "Someone else", "/m/other.png")).isFalse()
        assertThat(checkNotNull(orders.get(id)).signedBy).isEqualTo("Dana Hale")
        assertThat(orders.sign("nope", "Dana", "/m/x.png")).isFalse()
    }

    // AC-F38: a Signed order is changed by duplicating it into a new Draft with the same content and no signature.
    @Test fun duplicateMakesADraftCopyWithoutTheSignature() = runBlocking<Unit> {
        db.photoDao().upsert(PhotoEntity(id = "p1", homeId = "h1", jobId = "j1", path = "/m/p1.jpg", takenAt = 1, createdAt = 1, updatedAt = 1))
        val id = checkNotNull(orders.add("j1", ChangeOrderDraft("Island outlet", "Second outlet", lines, "Dana Hale", listOf("p1"))))
        assertThat(orders.sign(id, "Dana Hale", "/m/sig.png")).isTrue()
        var n = 0
        val copyId = checkNotNull(orders.duplicate(id) { "new${n++}" })
        assertThat(copyId).isNotEqualTo(id)
        val copy = checkNotNull(orders.get(copyId))
        assertThat(copy.status).isEqualTo(ChangeOrderStatus.DRAFT)
        assertThat(copy.title).isEqualTo("Island outlet")
        assertThat(copy.description).isEqualTo("Second outlet")
        assertThat(copy.clientName).isEqualTo("Dana Hale")
        assertThat(copy.lines.map { it.id }).containsExactly("new0", "new1").inOrder()
        assertThat(copy.totalCents).isEqualTo(33_500)
        assertThat(copy.photoIds).containsExactly("p1")
        assertThat(copy.signedBy).isNull()
        assertThat(copy.signaturePath).isNull()
        assertThat(checkNotNull(orders.get(id)).status).isEqualTo(ChangeOrderStatus.SIGNED)
        assertThat(orders.duplicate("nope")).isNull()
    }

    // T058b (AC-F40): the strokes are stored with the signature so every phone can draw it; an order signed on the
    // share page arrives as strokes only and this phone draws its PNG once, as a local column with no sync change.
    @Test fun signatureStrokesSyncAndAWebSignedOrderGetsItsImageDrawnOnce() = runBlocking<Unit> {
        val strokes = listOf(SignatureStroke(listOf(0.1f, 0.5f, 0.6f, 0.4f)))
        val id = checkNotNull(orders.add("j1", ChangeOrderDraft("Island outlet", lines = lines)))
        assertThat(orders.sign(id, "Dana Hale", "/m/sig.png", strokes)).isTrue()
        assertThat(checkNotNull(orders.get(id)).signature).isEqualTo(strokes)

        val web = "web-co"
        db.changeOrderDao().upsert(
            ChangeOrderEntity(
                id = web, jobId = "j1", title = "Fridge circuit", status = "SIGNED", signedBy = "Dana Hale", signedAt = 9_000,
                signatureJson = Signature.encode(strokes), signedVia = SIGNED_VIA_WEB, createdAt = 1, updatedAt = 9_000,
            ),
        )
        val pulled = checkNotNull(orders.get(web))
        assertThat(pulled.signedOnWeb).isTrue()
        assertThat(pulled.signaturePath).isNull()
        changes.clear()
        val file = java.io.File.createTempFile("sig", ".png")
        var drawn = 0
        val draw: suspend (List<SignatureStroke>) -> String? = { s -> drawn++; assertThat(s).isEqualTo(strokes); file.absolutePath }
        assertThat(orders.signatureImage(web, draw)).isEqualTo(file.absolutePath)
        assertThat(orders.signatureImage(web, draw)).isEqualTo(file.absolutePath)
        assertThat(drawn).isEqualTo(1)
        assertThat(checkNotNull(orders.get(web)).signaturePath).isEqualTo(file.absolutePath)
        assertThat(changes).isEmpty()
        // A Draft has nothing to draw.
        val draft = checkNotNull(orders.add("j1", ChangeOrderDraft("Another")))
        assertThat(orders.signatureImage(draft, draw)).isNull()
        file.delete()
    }
}
