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
