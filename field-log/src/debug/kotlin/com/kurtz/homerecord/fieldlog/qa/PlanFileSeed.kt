package com.kurtz.homerecord.fieldlog.qa

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.JobFileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Debug seed for the files walkthrough (F7, AC-F19, AC-F21): the job "Garage subpanel" with a two-sheet plan PDF in
 * its Plans folder, on this phone and uploaded by the owner, so a flow can open it, mark it up and read the version
 * history without the system file picker. Written to this phone only, like [LongListSeed]; nothing is queued for sync.
 */
object PlanFileSeed {
    const val JOB_ID = "qa-job-garage"
    const val FILE_ID = "qa-file-garage-plan"

    suspend fun seed(db: HomeRecordDatabase, companyId: String, ownerId: String, mediaDir: File, now: Long = System.currentTimeMillis()) {
        db.jobDao().upsert(
            JobEntity(
                id = JOB_ID, companyId = companyId, name = "Garage subpanel", address = "412 Maple Ave",
                startDate = LocalDate.now().toString(), createdAt = now, updatedAt = now,
            ),
        )
        val pdf = File(mediaDir.apply { mkdirs() }, "$FILE_ID.pdf")
        withContext(Dispatchers.IO) { writePlan(pdf) }
        db.jobFileDao().upsert(
            JobFileEntity(
                id = FILE_ID, jobId = JOB_ID, folder = "Plans", name = "Garage plan.pdf", path = pdf.path, mime = "application/pdf",
                size = pdf.length(), author = ownerId, offline = true, createdAt = now, updatedAt = now,
            ),
        )
    }

    private fun writePlan(file: File) {
        val doc = PdfDocument()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
        listOf("E-1 Garage subpanel plan" to "100 A subpanel on the north wall, 20 circuits", "E-2 Panel schedule" to "Feeder: 3 AWG copper in 1 in conduit")
            .forEachIndexed { i, (title, note) ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(612, 792, i + 1).create())
                page.canvas.drawText(title, 72f, 96f, text)
                page.canvas.drawText(note, 72f, 132f, text)
                page.canvas.drawRect(72f, 170f, 540f, 700f, line)
                doc.finishPage(page)
            }
        try {
            file.outputStream().use(doc::writeTo)
        } finally {
            doc.close()
        }
    }
}
