package com.kurtz.homerecord.fieldlog.jobs

import com.kurtz.homerecord.core.data.JobDao
import com.kurtz.homerecord.core.geo.Geocoder

/**
 * Gives jobs with an address but no coordinates a lat/lng (AC-F5). Called after a save and whenever the network
 * returns, so an address typed offline resolves later. A failed lookup leaves the job alone for the next pass.
 */
class JobGeocoder(
    private val jobs: JobDao,
    private val geocoder: Geocoder,
    private val recordChange: suspend (entity: String, id: String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Returns how many jobs were resolved. */
    suspend fun resolvePending(companyId: String): Int {
        var resolved = 0
        for (job in jobs.listFor(companyId)) {
            val address = job.address?.takeIf { it.isNotBlank() } ?: continue
            if (job.lat != null && job.lng != null) continue
            val point = geocoder.resolve(address) ?: continue
            // The address may have been edited while the lookup ran; only store coordinates for what was asked.
            val latest = jobs.get(job.id) ?: continue
            if (latest.address != job.address) continue
            jobs.upsert(latest.copy(lat = point.lat, lng = point.lng, updatedAt = clock()))
            recordChange("job", job.id)
            resolved++
        }
        return resolved
    }
}
