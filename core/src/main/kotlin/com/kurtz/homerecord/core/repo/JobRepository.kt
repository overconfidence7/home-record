package com.kurtz.homerecord.core.repo

import androidx.room.withTransaction
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.logic.JobOrdering
import com.kurtz.homerecord.core.logic.JobSummary
import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.model.Job
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Field Log jobs. Each job owns its own Home (SPEC §6). */
class JobRepository(
    private val db: HomeRecordDatabase,
    private val source: Source,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    /** All jobs (archived included), ordered by last activity (AC-53). */
    fun observeSummaries(): Flow<List<JobSummary>> =
        combine(db.jobDao().observeAll(), db.logEntryDao().observeAll()) { jobs, entries ->
            JobOrdering.summarize(jobs.map { it.toDomain() }, entries.map { it.toDomain() })
        }

    fun observeJob(id: String): Flow<Job?> = db.jobDao().observe(id).map { it?.toDomain() }

    suspend fun getJob(id: String): Job? = db.jobDao().get(id)?.toDomain()

    suspend fun getRooms(job: Job): List<Room> =
        db.roomDao().getAll().filter { it.homeId == job.homeId }.map { it.toDomain() }

    /** AC-52: creates the Home (same name), the Job and the chosen rooms in one transaction. */
    suspend fun createJob(
        name: String,
        address: String? = null,
        clientName: String? = null,
        clientContact: String? = null,
        roomNames: List<String> = emptyList(),
    ): Job {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Job name is required" }
        return db.withTransaction {
            val now = clock.now()
            val home = Home(ids.next(), trimmed, address.clean(), null, null, now, now, source)
            db.homeDao().upsert(home.toEntity())
            roomNames.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.forEach {
                db.roomDao().upsert(Room(ids.next(), home.id, it, null, now, now, source).toEntity())
            }
            val job = Job(ids.next(), home.id, trimmed, address.clean(), clientName.clean(), clientContact.clean(), false, now, now, source)
            db.jobDao().upsert(job.toEntity())
            job
        }
    }

    /** Updates the job and keeps its Home name/address in step. Blank name is rejected. */
    suspend fun updateJob(job: Job): Boolean {
        val trimmed = job.name.trim()
        if (trimmed.isEmpty()) return false
        db.withTransaction {
            val now = clock.now()
            val clean = job.copy(
                name = trimmed, address = job.address.clean(), clientName = job.clientName.clean(),
                clientContact = job.clientContact.clean(), updatedAt = now,
            )
            db.jobDao().upsert(clean.toEntity())
            db.homeDao().get(job.homeId)?.toDomain()?.let {
                db.homeDao().upsert(it.copy(name = trimmed, address = clean.address, updatedAt = now).toEntity())
            }
        }
        return true
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        val job = getJob(id) ?: return
        db.jobDao().upsert(job.copy(archived = archived, updatedAt = clock.now()).toEntity())
    }

    private fun String?.clean(): String? = this?.trim()?.ifEmpty { null }
}
