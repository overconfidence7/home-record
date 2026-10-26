package com.kurtz.homerecord.core.photo

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobFileEntity
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef

/** One mark on a PDF page (AC-F21). Points are fractions of the page width and height (0..1). */
sealed interface PdfMark {
    val page: Int

    data class Pen(override val page: Int, val points: List<NormPoint>) : PdfMark
    data class Arrow(override val page: Int, val from: NormPoint, val to: NormPoint) : PdfMark
    data class Rect(override val page: Int, val topLeft: NormPoint, val bottomRight: NormPoint) : PdfMark
    data class Text(override val page: Int, val at: NormPoint, val text: String) : PdfMark
    data class Highlight(override val page: Int, val topLeft: NormPoint, val bottomRight: NormPoint) : PdfMark
}

/** Writes the pages of the PDF at [sourcePath] with [marks] drawn on them to a new PDF at [destPath]. False when it could not. */
fun interface MarkedPdfWriter {
    suspend fun write(sourcePath: String, marks: List<PdfMark>, destPath: String): Boolean
}

/**
 * Marks drawn on a PDF page are saved as a new version of the file with the author and time; the original is never
 * modified (AC-F21). Each version points at the one before it through `parentFileId`.
 */
class PdfMarkup(
    private val db: HomeRecordDatabase,
    private val writer: MarkedPdfWriter,
    private val queueUpload: suspend (TenantRef, entity: String, entityId: String, filename: String, path: String, mime: String) -> Unit,
    private val companyId: () -> String?,
    private val sizeOf: (String) -> Long = { java.io.File(it).length() },
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    /** Returns the new version's id, or null for no marks, a file that is gone, not a PDF, not on this phone, or a failed write. */
    suspend fun saveVersion(fileId: String, marks: List<PdfMark>, author: String?): String? {
        if (marks.isEmpty()) return null
        val file = db.jobFileDao().get(fileId) ?: return null
        val source = file.path ?: return null
        if (file.mime != PDF_MIME) return null
        val id = Ids.newId()
        val dir = source.substringBeforeLast('/', "")
        val dest = (if (dir.isEmpty()) "" else "$dir/") + "$id.pdf"
        if (!writer.write(source, marks, dest)) return null
        val t = now()
        db.jobFileDao().upsert(
            file.copy(
                id = id, path = dest, r2Key = null, size = sizeOf(dest), version = file.version + 1, parentFileId = file.id,
                author = author, offline = true, createdAt = t, updatedAt = t,
            ),
        )
        onChange("job_file", id)
        companyId()?.let { queueUpload(TenantRef(TenantKind.Company, it), "job_file", id, "$id.pdf", dest, PDF_MIME) }
        return id
    }

    /** All versions of the file that [fileId] belongs to, newest first (AC-F21). */
    suspend fun history(fileId: String): List<JobFileEntity> {
        val any = db.jobFileDao().get(fileId) ?: return emptyList()
        val rows = db.jobFileDao().listFor(any.jobId).associateBy { it.id }
        var root: JobFileEntity = any
        while (true) root = root.parentFileId?.let { rows[it] } ?: break
        val chain = generateSequence(root) { cur -> rows.values.firstOrNull { it.parentFileId == cur.id } }
        return chain.toList().reversed()
    }

    companion object {
        const val PDF_MIME = "application/pdf"
    }
}
