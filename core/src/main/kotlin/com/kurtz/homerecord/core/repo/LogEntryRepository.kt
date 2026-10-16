package com.kurtz.homerecord.core.repo

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.model.EntryKind
import com.kurtz.homerecord.core.model.LogEntry
import com.kurtz.homerecord.core.model.Source
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Field Log entries (photo, note, voice) attached to a job and the current day. */
class LogEntryRepository(
    private val db: HomeRecordDatabase,
    private val source: Source,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    fun observeForJob(jobId: String): Flow<List<LogEntry>> =
        db.logEntryDao().observeForJob(jobId).map { list -> list.map { it.toDomain() } }

    /** AC-56: a PHOTO entry for [jobId] dated today; [mediaPath] is relative to the files dir. */
    suspend fun addPhoto(jobId: String, mediaPath: String, roomId: String? = null): LogEntry =
        add(LogEntryDraft(jobId, EntryKind.PHOTO, mediaPath = mediaPath, roomId = roomId))

    /** AC-58: a NOTE entry dated today; blank text is not saved and returns null. */
    suspend fun addNote(jobId: String, text: String, roomId: String? = null): LogEntry? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return add(LogEntryDraft(jobId, EntryKind.NOTE, text = trimmed, roomId = roomId))
    }

    /** AC-59: a VOICE entry dated today with its recorded [durationMs]. */
    suspend fun addVoice(jobId: String, mediaPath: String, durationMs: Long, roomId: String? = null): LogEntry =
        add(LogEntryDraft(jobId, EntryKind.VOICE, mediaPath = mediaPath, durationMs = durationMs, roomId = roomId))

    suspend fun get(id: String): LogEntry? = db.logEntryDao().get(id)?.toDomain()

    fun observeEntry(id: String): Flow<LogEntry?> =
        db.logEntryDao().observeById(id).map { it?.toDomain() }

    /** FL-Entry edit: new note/caption text and room tag. Returns null if the entry is gone. */
    suspend fun update(id: String, text: String?, roomId: String?): LogEntry? {
        val current = db.logEntryDao().get(id)?.toDomain() ?: return null
        val cleaned = text?.trim()?.takeIf { it.isNotEmpty() }
        val updated = current.copy(
            text = if (current.kind == EntryKind.NOTE) cleaned ?: current.text else cleaned,
            roomId = roomId,
            updatedAt = clock.now(),
        )
        db.logEntryDao().upsert(updated.toEntity())
        return updated
    }

    /** Removes the row and returns it so the caller can delete its media file. */
    suspend fun delete(id: String): LogEntry? {
        val entity = db.logEntryDao().get(id) ?: return null
        db.logEntryDao().delete(entity)
        // A behind-the-wall Photo shares the entry's file; drop it so wall counts stay honest.
        entity.mediaPath?.let { db.photoDao().deleteByPath(it) }
        return entity.toDomain()
    }

    private suspend fun add(draft: LogEntryDraft): LogEntry {
        val now = clock.now()
        val entry = LogEntry(
            id = ids.next(), jobId = draft.jobId, day = dayOf(now), kind = draft.kind, text = draft.text,
            mediaPath = draft.mediaPath, durationMs = draft.durationMs, roomId = draft.roomId,
            createdAt = now, updatedAt = now, source = source,
        )
        db.logEntryDao().upsert(entry.toEntity())
        return entry
    }

    private fun dayOf(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone()).toLocalDate().toString()

    private data class LogEntryDraft(
        val jobId: String,
        val kind: EntryKind,
        val text: String? = null,
        val mediaPath: String? = null,
        val durationMs: Long? = null,
        val roomId: String? = null,
    )
}
