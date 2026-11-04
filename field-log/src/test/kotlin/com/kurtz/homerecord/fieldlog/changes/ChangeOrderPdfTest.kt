package com.kurtz.homerecord.fieldlog.changes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.changeorder.ChangeOrderDraft
import com.kurtz.homerecord.core.changeorder.ChangeOrderPdfLayout
import com.kurtz.homerecord.core.changeorder.JobChangeOrders
import com.kurtz.homerecord.core.data.CompanyEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.log.LogPdfOp
import com.kurtz.homerecord.core.model.ChangeOrderLineItem
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import com.kurtz.homerecord.core.model.JobChangeOrder
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.fieldlog.log.AndroidLogPdfWriter
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The change order PDF's words (AC-F39) and its drawing, checked on a bitmap canvas (PdfDocument has no JVM backing). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChangeOrderPdfTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val res = context.resources
    private val zone = ZoneId.of("America/New_York")
    private val company = CompanyEntity(
        id = "c1", name = "Ortiz Electric", phone = "(610) 555-0142", email = "office@ortizelectric.com", logoPath = "/files/media/logo.png",
        createdAt = 1, updatedAt = 1,
    )
    private val job = JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", address = "14 Maple Ave, Wayne, PA", createdAt = 1, updatedAt = 1)

    // 2026-11-03 19:15 UTC = 2:15 PM Eastern
    private val signedAt = 1_793_733_300_000L
    private val signed = JobChangeOrder(
        id = "co1", jobId = "j1", title = "Second outlet on the island",
        description = "GFCI outlet on the sink side of the island, fed from the small-appliance circuit.",
        lines = listOf(ChangeOrderLineItem("l1", "GFCI outlet, box and cover", 1_000, 18_500), ChangeOrderLineItem("l2", "Labor", 1_500, 9_600)),
        totalCents = 32_900, clientName = "Dana Hale", status = ChangeOrderStatus.SIGNED, signedAt = signedAt, signedBy = "Dana Hale",
        createdAt = signedAt - 3_600_000, updatedAt = signedAt, photoIds = listOf("p1"), signaturePath = "/files/media/sig.png",
    )

    @AcceptanceCriterion("AC-F39")
    @Test fun aSignedOrderCarriesCompanyJobDescriptionLinesTotalPhotosSignatureAndTime() {
        val c = ChangeOrderPdfContentBuilder.build(res, company, job, signed, listOf("/m/p1.jpg"), zone, fileExists = { true })
        assertThat(c.company).isEqualTo("Ortiz Electric")
        assertThat(c.companyLine).isEqualTo("(610) 555-0142 · office@ortizelectric.com")
        assertThat(c.logoPath).isEqualTo("/files/media/logo.png")
        assertThat(c.title).isEqualTo("Change order: Second outlet on the island")
        assertThat(c.job).isEqualTo("Hale kitchen")
        assertThat(c.jobLine).isEqualTo("14 Maple Ave, Wayne, PA")
        assertThat(c.facts).containsExactly("Client: Dana Hale", "Date: Nov 3, 2026", "Status: Signed").inOrder()
        assertThat(c.description).startsWith("GFCI outlet")
        assertThat(c.heads.amount).isEqualTo("Amount")
        assertThat(c.lines.map { listOf(it.item, it.qty, it.price, it.amount) }).containsExactly(
            listOf("GFCI outlet, box and cover", "1", "$185.00", "$185.00"),
            listOf("Labor", "1.5", "$96.00", "$144.00"),
        ).inOrder()
        assertThat(c.total).isEqualTo("$329.00")
        assertThat(c.photos).containsExactly("/m/p1.jpg")
        assertThat(c.signaturePath).isEqualTo("/files/media/sig.png")
        assertThat(c.signedLine).matches("Signed by Dana Hale, Nov 3, 2026,[\\s\\u202F]2:15[\\s\\u202F]PM")
    }

    @Test fun aDraftPrintsNotSignedYetWithoutASignature() {
        val draft = signed.copy(status = ChangeOrderStatus.DRAFT, signedAt = null, signedBy = null, signaturePath = null)
        val c = ChangeOrderPdfContentBuilder.build(res, null, job.copy(address = null), draft, emptyList(), zone, fileExists = { true })
        assertThat(c.company).isEqualTo(res.getString(com.kurtz.homerecord.fieldlog.R.string.app_name))
        assertThat(c.jobLine).isNull()
        assertThat(c.facts.last()).isEqualTo("Status: Draft")
        assertThat(c.signaturePath).isNull()
        assertThat(c.signedLine).isEqualTo("Not signed yet")
    }

    @AcceptanceCriterion("AC-F39")
    @Test fun theSignatureImageIsDrawnWholeOnThePage() {
        val sig = File(context.cacheDir, "sig.png")
        val ink = Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888)
        Canvas(ink).drawColor(Color.BLACK)
        sig.outputStream().use { ink.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val c = ChangeOrderPdfContentBuilder.build(res, company, job, signed.copy(signaturePath = sig.path), emptyList(), zone, fileExists = { it == sig.path })
        val writer = AndroidLogPdfWriter({ n, m -> "Page $n of $m" })
        val ops = ChangeOrderPdfLayout(writer.measurer, { n, m -> "Page $n of $m" }).layout(c).single()
        val box = ops.filterIsInstance<LogPdfOp.Image>().single { it.path == sig.path }
        assertThat(box.fit).isTrue()
        val page = Bitmap.createBitmap(612, 792, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page).apply { drawColor(Color.WHITE) }
        writer.draw(canvas, ops, c.logoPath)
        // the 3:1 ink fills the 240-wide box across and is not stretched down its full height
        assertThat(page.getPixel(box.left.toInt() + 4, box.top.toInt() + 4)).isEqualTo(Color.BLACK)
        assertThat(page.getPixel(box.right.toInt() - 4, box.top.toInt() + 4)).isEqualTo(Color.BLACK)
        assertThat(page.getPixel(box.left.toInt() + 4, box.bottom.toInt() - 2)).isNotEqualTo(Color.BLACK)
    }

    // the PDF file itself is written on a device (PdfDocument has no JVM backing): see ChangeOrderPdfAcceptanceTest
    @Test fun theMakerHasNothingToWriteForAnOrderOrJobThatIsGone() {
        val db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        try {
            val id = runBlocking {
                db.jobDao().upsert(job)
                checkNotNull(JobChangeOrders(db).add("j1", ChangeOrderDraft("Island outlet", lines = listOf(ChangeOrderLineItem("l1", "Outlet", 1_000, 18_500)))))
            }
            val maker = ChangeOrderPdfMaker(context, db)
            // the job is deleted: nothing to head the PDF with
            runBlocking { db.jobDao().upsert(job.copy(deletedAt = 5)) }
            assertThat(runBlocking { maker.make(id) }).isNull()
            assertThat(runBlocking { maker.make("gone") }).isNull()
        } finally {
            db.close()
        }
    }

    @Test fun fileNamesDropCharactersAFileCannotHold() {
        assertThat(ChangeOrderPdfMaker.fileName("Hale / kitchen", "Outlet: \"island\"")).isEqualTo("Change order Hale kitchen - Outlet island.pdf")
    }
}
