package com.kurtz.homerecord.fieldlog.acceptance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.changeorder.ChangeOrderDraft
import com.kurtz.homerecord.core.changeorder.JobChangeOrders
import com.kurtz.homerecord.core.data.CompanyEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.model.ChangeOrderLineItem
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.fieldlog.changes.ChangeOrderPdfMaker
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The change order PDF on a device (AC-F39): the real PdfDocument writes a readable one-page file into the shared cache
 * folder with the order's photo and the signature drawn on it. Rules: ChangeOrderPdfLayoutTest, ChangeOrderPdfTest.
 */
@RunWith(AndroidJUnit4::class)
class ChangeOrderPdfAcceptanceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: HomeRecordDatabase
    private val dir by lazy { File(context.filesDir, "acceptance-co").apply { deleteRecursively(); mkdirs() } }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).build()
    }

    @After fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @AcceptanceCriterion("AC-F39")
    @Test fun aSignedOrderWritesAReadablePdfWithItsPhotoAndSignature() = runBlocking<Unit> {
        val photo = File(dir, "island.jpg")
        Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(210, 110, 30)) }
            .compress(Bitmap.CompressFormat.JPEG, 90, photo.outputStream())
        // blue ink so the signature can be told apart from the black text
        val sig = File(dir, "sig.png")
        Bitmap.createBitmap(600, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(20, 40, 220)) }
            .compress(Bitmap.CompressFormat.PNG, 100, sig.outputStream())
        db.companyDao().upsert(CompanyEntity(id = "c1", name = "Ortiz Electric", phone = "(610) 555-0142", createdAt = 1, updatedAt = 1))
        db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", address = "14 Maple Ave, Wayne, PA", createdAt = 1, updatedAt = 1))
        db.photoDao().upsert(PhotoEntity(id = "p1", homeId = "h1", jobId = "j1", path = photo.path, takenAt = 10, createdAt = 10, updatedAt = 10))
        val orders = JobChangeOrders(db)
        val id = checkNotNull(
            orders.add(
                "j1",
                ChangeOrderDraft(
                    "Second outlet on the island", description = "GFCI on the sink side.", clientName = "Dana Hale",
                    lines = listOf(ChangeOrderLineItem("l1", "GFCI outlet, box and cover", 1_000, 18_500), ChangeOrderLineItem("l2", "Labor", 1_500, 9_600)),
                    photoIds = listOf("p1"),
                ),
            ),
        )
        check(orders.sign(id, "Dana Hale", sig.path))

        val file = checkNotNull(ChangeOrderPdfMaker(context, db).make(id))
        assertThat(file.parentFile?.name).isEqualTo("change-orders")
        assertThat(file.name).isEqualTo("Change order Hale kitchen - Second outlet on the island.pdf")
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                assertThat(pdf.pageCount).isEqualTo(1)
                pdf.openPage(0).use { page ->
                    assertThat(page.width).isEqualTo(612)
                    val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    var orange = false
                    var blue = false
                    for (y in 0 until bmp.height step 4) for (x in 0 until bmp.width step 4) {
                        val p = bmp.getPixel(x, y)
                        if (Color.red(p) > 180 && Color.green(p) in 80..140 && Color.blue(p) < 70) orange = true
                        if (Color.blue(p) > 180 && Color.red(p) < 70 && Color.green(p) < 90) blue = true
                    }
                    assertThat(orange).isTrue()
                    assertThat(blue).isTrue()
                }
            }
        }
    }
}
