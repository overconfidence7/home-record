package com.kurtz.homerecord.core.schedule

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.AssignmentEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.model.ScheduleEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

fun AssignmentEntity.toEntry(): ScheduleEntry? =
    runCatching { LocalDate.parse(date) }.getOrNull()?.let { ScheduleEntry(id, jobId, memberId, it) }

/**
 * The company schedule (AC-F41): put a person on a job for a day, move that day, take it off. Every write is one outbox
 * change through [onChange], so the schedule works offline and the server sends the "schedule changed" push for today
 * and tomorrow (AC-F42, AC-B30).
 */
class JobSchedule(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    private val dao get() = db.assignmentDao()

    fun observeForCompany(companyId: String): Flow<List<ScheduleEntry>> =
        dao.observeForCompany(companyId).map { rows -> rows.sortedBy { it.createdAt }.mapNotNull { it.toEntry() } }

    /**
     * Schedules [memberId] on [jobId] for [date]. Returns the assignment id, the existing one when that person already
     * has that job that day, or null when the job is gone.
     */
    suspend fun assign(jobId: String, memberId: String, date: LocalDate): String? {
        if (db.jobDao().get(jobId) == null) return null
        dao.listFor(jobId).firstOrNull { it.memberId == memberId && it.date == date.toString() }?.let { return it.id }
        val id = Ids.newId()
        val stamp = now()
        dao.upsert(AssignmentEntity(id = id, jobId = jobId, memberId = memberId, date = date.toString(), createdAt = stamp, updatedAt = stamp, source = Sources.FIELD_LOG))
        onChange(ENTITY, id)
        return id
    }

    /**
     * Moves a scheduled day to [date] (and [memberId] when given). When the target person already has that job on that
     * day the moved one is removed instead, so a day never holds the same job twice. False when it is gone.
     */
    suspend fun move(id: String, date: LocalDate, memberId: String? = null): Boolean {
        val row = dao.get(id) ?: return false
        val member = memberId ?: row.memberId
        if (row.date == date.toString() && row.memberId == member) return false
        val twin = dao.listFor(row.jobId).firstOrNull { it.id != id && it.memberId == member && it.date == date.toString() }
        if (twin != null) return remove(id)
        dao.upsert(row.copy(date = date.toString(), memberId = member, updatedAt = now()))
        onChange(ENTITY, id)
        return true
    }

    /** Takes a scheduled day off the schedule. False when it was already gone. */
    suspend fun remove(id: String): Boolean {
        if (dao.get(id) == null) return false
        dao.softDelete(id, now())
        onChange(ENTITY, id)
        return true
    }

    companion object {
        const val ENTITY = "assignment"
    }
}
