package com.kurtz.homerecord.fieldlog.changes

import android.content.Context
import android.content.res.Resources
import com.kurtz.homerecord.core.changeorder.ChangeOrderPdfContent
import com.kurtz.homerecord.core.changeorder.ChangeOrderPdfLayout
import com.kurtz.homerecord.core.changeorder.ChangeOrderPdfRow
import com.kurtz.homerecord.core.changeorder.toModel
import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.data.CompanyEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import com.kurtz.homerecord.core.model.JobChangeOrder
import com.kurtz.homerecord.core.model.MoneyMath
import com.kurtz.homerecord.coreui.platform.Intents
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.fieldlog.log.AndroidLogPdfWriter
import com.kurtz.homerecord.fieldlog.log.LogPdfCompany
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Makes a change order's PDF and opens the share sheet with it (AC-F39). Tests use a fake. */
interface ChangeOrderSharer {
    /** The PDF for a saved change order, or null when it or its job is gone or the file could not be written. */
    suspend fun make(changeOrderId: String): File?

    /** The share sheet with the PDF; false when the file is gone. */
    fun share(pdf: File): Boolean
}

class AndroidChangeOrderSharer(private val context: Context, private val maker: ChangeOrderPdfMaker) : ChangeOrderSharer {
    override suspend fun make(changeOrderId: String): File? = maker.make(changeOrderId)

    override fun share(pdf: File): Boolean {
        if (!pdf.exists()) return false
        Intents.shareFile(context, pdf, "application/pdf", context.getString(R.string.co_pdf_share_title))
        return true
    }
}

/**
 * Writes a saved change order's PDF into `cache/change-orders/` (shared through the app's FileProvider): company,
 * job, description, photos, line items, total, and the signature with who signed and when (AC-F39).
 */
class ChangeOrderPdfMaker(
    private val context: Context,
    private val db: HomeRecordDatabase,
    private val membership: () -> CompanyMembership? = { null },
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** The signature PNG, drawn from the synced strokes when it was signed elsewhere (T058b). */
    private val signatureImage: suspend (changeOrderId: String) -> String? = { null },
) {
    suspend fun make(changeOrderId: String): File? = withContext(Dispatchers.IO) {
        val stored = db.changeOrderDao().get(changeOrderId)?.toModel() ?: return@withContext null
        val order = if (stored.status == ChangeOrderStatus.SIGNED) stored.copy(signaturePath = signatureImage(changeOrderId) ?: stored.signaturePath) else stored
        val job = db.jobDao().get(order.jobId) ?: return@withContext null
        val company = LogPdfCompany.of(job.companyId, db.companyDao().get(job.companyId), membership())
        // the full photo when it is on this phone, else its thumbnail; neither means it is still on another phone
        val photos = order.photoIds.mapNotNull { id ->
            db.photoDao().get(id)?.let { p -> p.path.takeIf { it.isNotBlank() && File(it).exists() } ?: p.thumbPath?.takeIf { File(it).exists() } }
        }
        val res = context.resources
        val content = ChangeOrderPdfContentBuilder.build(res, company, job, order, photos, zone())
        val writer = AndroidLogPdfWriter({ n, m -> res.getString(R.string.log_pdf_page, n, m) })
        val pages = ChangeOrderPdfLayout(writer.measurer, { n, m -> res.getString(R.string.log_pdf_page, n, m) }).layout(content)
        val dir = File(context.cacheDir, "change-orders").apply { mkdirs() }
        val file = File(dir, fileName(job.name, order.title))
        val ok = runCatching { file.outputStream().use { writer.write(pages, it, content.logoPath) } }.isSuccess
        if (!ok) {
            file.delete()
            return@withContext null
        }
        file
    }

    companion object {
        /** "Change order Hale kitchen - Second outlet.pdf", with characters a file name cannot hold dropped. */
        fun fileName(job: String, title: String): String {
            fun clean(s: String, max: Int) = s.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").replace(Regex("\\s+"), " ").trim().take(max)
            val parts = listOf(clean(job, 40), clean(title, 40)).filter { it.isNotEmpty() }
            return "Change order ${parts.joinToString(" - ").ifEmpty { "job" }}.pdf"
        }
    }
}

/** Words a saved change order for its PDF (AC-F39). */
object ChangeOrderPdfContentBuilder {
    fun build(
        res: Resources,
        company: CompanyEntity?,
        job: JobEntity,
        order: JobChangeOrder,
        photoPaths: List<String>,
        zone: ZoneId,
        fileExists: (String) -> Boolean = { File(it).exists() },
    ): ChangeOrderPdfContent {
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US)
        val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.US)
        fun at(ms: Long, f: DateTimeFormatter) = f.format(Instant.ofEpochMilli(ms).atZone(zone))
        val status = res.getString(
            when (order.status) {
                ChangeOrderStatus.DRAFT -> R.string.changes_status_draft
                ChangeOrderStatus.SIGNED -> R.string.changes_status_signed
                ChangeOrderStatus.DECLINED -> R.string.changes_status_declined
            },
        )
        val signedAt = order.signedAt
        val signedLine = if (order.status == ChangeOrderStatus.SIGNED && signedAt != null) {
            res.getString(R.string.co_form_signed_by, order.signedBy.orEmpty().ifBlank { order.clientName.orEmpty() }, at(signedAt, dateTime))
        } else {
            res.getString(R.string.co_pdf_not_signed)
        }
        return ChangeOrderPdfContent(
            company = company?.name?.takeIf { it.isNotBlank() } ?: res.getString(R.string.app_name),
            companyLine = listOfNotNull(company?.phone, company?.email).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null },
            logoPath = company?.logoPath?.takeIf { it.isNotBlank() && fileExists(it) },
            title = res.getString(R.string.co_pdf_title, order.title),
            job = job.name,
            jobLine = job.address?.trim()?.takeIf { it.isNotEmpty() },
            facts = listOfNotNull(
                order.clientName?.trim()?.takeIf { it.isNotEmpty() }?.let { res.getString(R.string.co_pdf_client, it) },
                res.getString(R.string.co_pdf_date, at(order.createdAt, date)),
                res.getString(R.string.co_pdf_status, status),
            ),
            descriptionTitle = res.getString(R.string.co_form_description),
            description = order.description,
            linesTitle = res.getString(R.string.co_form_lines),
            heads = ChangeOrderPdfRow(
                res.getString(R.string.co_form_line_description), res.getString(R.string.co_form_line_qty),
                res.getString(R.string.co_form_line_price), res.getString(R.string.co_pdf_amount),
            ),
            lines = order.lines.map { l ->
                ChangeOrderPdfRow(l.description, MoneyMath.formatQuantity(l.qtyMilli), dollars(l.unitCents), dollars(l.amountCents))
            },
            totalLabel = res.getString(R.string.co_form_total),
            total = dollars(order.totalCents),
            photosTitle = res.getString(R.string.co_form_photos),
            photos = photoPaths,
            signatureTitle = res.getString(R.string.co_form_signature),
            signaturePath = order.signaturePath?.takeIf { order.status == ChangeOrderStatus.SIGNED && it.isNotBlank() && fileExists(it) },
            signedLine = signedLine,
        )
    }
}
