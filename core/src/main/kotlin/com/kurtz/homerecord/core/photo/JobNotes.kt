package com.kurtz.homerecord.core.photo

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.NoteEntity
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId

/** Records a voice memo to a file. Implementations sit behind the microphone permission. */
interface VoiceRecorder {
    fun start(path: String): Boolean

    /** Stops and returns the recorded length in milliseconds, or null when nothing was recorded. */
    fun stop(): Long?
}

/** On-device speech to text, best effort (AC-F27): null when the device cannot transcribe. */
fun interface SpeechTranscriber {
    suspend fun transcribe(path: String): String?
}

/**
 * Typed notes and voice memos that attach to a job and day (AC-F27). A voice memo is kept even when the device
 * cannot transcribe it; the transcript is editable afterwards. Voice memos queue for sync and upload like photos.
 */
class JobNotes(
    private val db: HomeRecordDatabase,
    private val transcriber: SpeechTranscriber,
    private val queueUpload: suspend (TenantRef, entity: String, entityId: String, filename: String, path: String, mime: String) -> Unit,
    private val companyId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    /** A typed note for today; blank text saves nothing. Returns the note id or null. */
    suspend fun addText(jobId: String, text: String): String? {
        val body = text.trim()
        if (body.isEmpty()) return null
        return insert(jobId, text = body, voicePath = null, transcript = null)
    }

    /**
     * Saves a recorded memo and tries to transcribe it; the recorder stops at [MAX_VOICE_MS]. Returns the note id,
     * or null when the job is gone.
     */
    suspend fun addVoice(jobId: String, path: String): String? {
        val id = insert(jobId, text = "", voicePath = path, transcript = null) ?: return null
        val heard = runCatching { transcriber.transcribe(path) }.getOrNull()?.trim().orEmpty()
        if (heard.isNotEmpty()) setTranscript(id, heard)
        return id
    }

    /** Edits the transcript (or the text of a typed note). */
    suspend fun setTranscript(noteId: String, text: String) {
        val row = db.noteDao().get(noteId) ?: return
        val stamp = now()
        db.noteDao().upsert(
            if (row.voicePath == null) row.copy(text = text.trim(), updatedAt = stamp) else row.copy(transcript = text.trim(), updatedAt = stamp),
        )
        onChange("note", noteId)
    }

    suspend fun delete(noteId: String) {
        db.noteDao().softDelete(noteId, now())
        onChange("note", noteId)
    }

    fun observeForJob(jobId: String): Flow<List<NoteEntity>> = db.noteDao().observeFor(jobId)

    private suspend fun insert(jobId: String, text: String, voicePath: String?, transcript: String?): String? {
        if (db.jobDao().get(jobId) == null) return null
        val stamp = now()
        val id = Ids.newId()
        val day = Instant.ofEpochMilli(stamp).atZone(zone()).toLocalDate().toString()
        db.noteDao().upsert(
            NoteEntity(
                id = id, jobId = jobId, day = day, text = text, voicePath = voicePath, transcript = transcript,
                createdAt = stamp, updatedAt = stamp, source = Sources.FIELD_LOG,
            ),
        )
        onChange("note", id)
        if (voicePath != null) {
            companyId()?.let { queueUpload(TenantRef(TenantKind.Company, it), "note", id, voicePath.substringAfterLast('/'), voicePath, "audio/mp4") }
        }
        return id
    }

    companion object {
        /** Voice memos stop at three minutes (AC-F27). */
        const val MAX_VOICE_MS = 3 * 60 * 1000L
    }
}
