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
 * (with a Main floor, named [mainFloorName] in the device's language) when it is missing and queues the new rows for
 * sync (AC-F48).
 */
class JobHomeProvisioner(
    private val jobs: JobDao,
    private val homes: HomeDao,
    private val floors: FloorDao,
    private val recordChange: suspend (entity: String, id: String) -> Unit,
    private val mainFloorName: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Returns the job's homeId, or null when the job does not exist. */
    suspend fun ensure(jobId: String): String? {
        val job = jobs.get(jobId) ?: return null
        job.homeId?.let { return it }
        val now = clock()
        val homeId = newHome(job.name, job.address, now)
        jobs.upsert(job.copy(homeId = homeId, updatedAt = now))
        recordChange("job", jobId)
        return homeId
    }

    /** Creates a job's home with its Main floor and queues both; the caller links it to the job. */
    suspend fun newHome(name: String, address: String?, now: Long = clock()): String {
        val homeId = newId()
        homes.upsert(HomeEntity(id = homeId, name = name, address = address, createdAt = now, updatedAt = now, source = Sources.FIELD_LOG))
        recordChange("home", homeId)
        mainFloor(homeId, now)
        return homeId
    }

    /** The home's first floor, created as Main when the home has none. */
    suspend fun mainFloor(homeId: String, now: Long = clock()): FloorEntity =
        floors.listFor(homeId).firstOrNull() ?: FloorEntity(
            id = newId(), homeId = homeId, sortOrder = 0, name = mainFloorName, createdAt = now, updatedAt = now, source = Sources.FIELD_LOG,
        ).also { floors.upsert(it); recordChange("floor", it.id) }
}
