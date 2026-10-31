package com.kurtz.homerecord.core.task

import androidx.room.withTransaction
import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.data.TaskEntity
import com.kurtz.homerecord.core.model.JobTask
import com.kurtz.homerecord.core.model.TaskList
import com.kurtz.homerecord.core.model.TaskOrder
import com.kurtz.homerecord.core.model.TaskPlanPin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** What a person types into the task form (AC-F34). */
data class TaskDraft(
    val title: String,
    val assigneeId: String? = null,
    val due: LocalDate? = null,
    val roomId: String? = null,
    val photoId: String? = null,
    val note: String? = null,
    val punch: Boolean = false,
    /** Where the work is on the floor plan (AC-F35). */
    val planPin: TaskPlanPin? = null,
)

fun TaskEntity.toModel(): JobTask = JobTask(
    id = id, jobId = jobId, title = title, assigneeId = assigneeId,
    due = due?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
    roomId = roomId, photoId = photoId, note = note, done = done, punch = punch, sortOrder = sortOrder,
    doneAt = doneAt ?: if (done) updatedAt else null, createdAt = createdAt, planPin = TaskPlanPin.decode(planPinJson),
)

/**
 * A job's tasks and punch list (AC-F34): add and edit, tick done and Undo, drag to reorder, delete. Every write is one
 * outbox change through [onChange], so tasks work offline and sync (AC-F36, AC-F52).
 */
class JobTasks(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    private val dao get() = db.taskDao()

    fun observeForJob(jobId: String): Flow<List<JobTask>> = dao.observeFor(jobId).map { rows -> rows.map { it.toModel() } }

    /** Tasks assigned to [memberId] on the company's jobs; Today narrows them to due and overdue (MyTasksDue). */
    fun observeAssigned(companyId: String, memberId: String): Flow<List<JobTask>> =
        dao.observeAssigned(companyId, memberId).map { rows -> rows.map { it.toModel() } }

    suspend fun get(id: String): JobTask? = dao.get(id)?.toModel()

    /** Adds a task at the bottom of the job's list. Returns its id, or null when the title is blank or the job is gone. */
    suspend fun add(jobId: String, draft: TaskDraft): String? {
        val title = draft.title.trim()
        if (title.isEmpty() || db.jobDao().get(jobId) == null) return null
        val id = Ids.newId()
        val stamp = now()
        db.withTransaction {
            dao.upsert(
                TaskEntity(
                    id = id, jobId = jobId, title = title, assigneeId = draft.assigneeId, due = draft.due?.toString(),
                    roomId = draft.roomId, photoId = draft.photoId, note = draft.note.clean(), punch = draft.punch,
                    planPinJson = draft.planPin?.encode(), sortOrder = dao.maxSortOrder(jobId) + 1, createdAt = stamp, updatedAt = stamp, source = Sources.FIELD_LOG,
                ),
            )
        }
        onChange(ENTITY, id)
        return id
    }

    /** Saves the form over an existing task, plan pin included; done state, order and the file pin are kept. False when blank or gone. */
    suspend fun update(id: String, draft: TaskDraft): Boolean {
        val title = draft.title.trim()
        val row = dao.get(id) ?: return false
        if (title.isEmpty()) return false
        dao.upsert(
            row.copy(
                title = title, assigneeId = draft.assigneeId, due = draft.due?.toString(), roomId = draft.roomId,
                photoId = draft.photoId, note = draft.note.clean(), punch = draft.punch, planPinJson = draft.planPin?.encode(),
                updatedAt = now(),
            ),
        )
        onChange(ENTITY, id)
        return true
    }

    /** Ticks a task done (swipe) or open again (Undo). Returns false when nothing changed. */
    suspend fun setDone(id: String, done: Boolean): Boolean {
        val row = dao.get(id) ?: return false
        if (row.done == done) return false
        val stamp = now()
        dao.upsert(row.copy(done = done, doneAt = if (done) stamp else null, updatedAt = stamp))
        onChange(ENTITY, id)
        return true
    }

    /** Moves an open task from [from] to [to] in the job's open list (display order). */
    suspend fun move(jobId: String, from: Int, to: Int) {
        val changed = db.withTransaction {
            val open = TaskList.build(dao.listFor(jobId).map { it.toModel() }).open
            val moves = TaskOrder.move(open, from, to)
            val stamp = now()
            moves.forEach { (id, order) -> dao.get(id)?.let { dao.upsert(it.copy(sortOrder = order, updatedAt = stamp)) } }
            moves.keys
        }
        changed.forEach { onChange(ENTITY, it) }
    }

    suspend fun delete(id: String) {
        if (dao.get(id) == null) return
        dao.softDelete(id, now())
        onChange(ENTITY, id)
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val ENTITY = "task"
    }
}
