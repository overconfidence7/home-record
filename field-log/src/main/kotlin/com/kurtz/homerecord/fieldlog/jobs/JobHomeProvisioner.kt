package com.kurtz.homerecord.fieldlog.jobs

import com.kurtz.homerecord.core.data.FloorDao
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeDao
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.JobDao
import com.kurtz.homerecord.core.data.Sources
import java.util.UUID

/**
 * A job gets its own home the first time someone types a room or opens the House tab. This creates it
 * (with a Main floor) when it is missing and queues the new rows for sync (AC-F48).
 */
class JobHomeProvisioner(
    private val jobs: JobDao,
    private val homes: HomeDao,
    private val floors: FloorDao,
    private val recordChange: suspend (entity: String, id: String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Returns the job's homeId, or null when the job does not exist. */
    suspend fun ensure(jobId: String): String? {
        val job = jobs.get(jobId) ?: return null
        job.homeId?.let { return it }
        val now = clock()
        val homeId = newId()
        homes.upsert(HomeEntity(id = homeId, name = job.name, address = job.address, createdAt = now, updatedAt = now, source = Sources.FIELD_LOG))
        recordChange("home", homeId)
        val floor = FloorEntity(id = newId(), homeId = homeId, sortOrder = 0, name = "Main", createdAt = now, updatedAt = now, source = Sources.FIELD_LOG)
        floors.upsert(floor)
        recordChange("floor", floor.id)
        jobs.upsert(job.copy(homeId = homeId, updatedAt = now))
        recordChange("job", jobId)
        return homeId
    }
}
