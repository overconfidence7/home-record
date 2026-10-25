package com.kurtz.homerecord.core.punch

import com.kurtz.homerecord.core.location.LocationProvider
import com.kurtz.homerecord.core.model.GeoFix
import com.kurtz.homerecord.core.model.PunchDistance
import com.kurtz.homerecord.core.model.PunchKind

sealed interface CrewResult {
    /** [punched] got a clock-in; [skipped] were already on the clock (AC-F11). */
    data class Done(val punched: List<String>, val skipped: List<String>) : CrewResult
    data class NeedsReason(val distanceFt: Int) : CrewResult
    data class GpsRequired(val permissionMissing: Boolean) : CrewResult
    data object NothingSelected : CrewResult
}

/**
 * Lead crew clock-in (AC-F11): one fix from the lead's phone, one IN punch per selected member, each marked
 * `clockedInBy` the lead. Members already on the clock are skipped, never double-punched.
 */
class CrewClock(
    private val punches: PunchRepository,
    private val clock: ClockController,
    private val location: LocationProvider,
    private val fixTimeoutMs: Long = ClockController.FIX_TIMEOUT_MS,
) {
    suspend fun clockInCrew(ctx: ClockContext, leadId: String, memberIds: Set<String>, job: ClockJob?, reason: String? = null): CrewResult {
        if (memberIds.isEmpty()) return CrewResult.NothingSelected
        val permitted = location.hasPermission()
        val fix: GeoFix? = if (permitted) location.currentFix(fixTimeoutMs) else null
        if (fix == null && ctx.settings.requireGps) return CrewResult.GpsRequired(permissionMissing = !permitted)
        val far = PunchDistance.isFarFromJob(fix, job?.lat, job?.lng, ctx.settings.punchDistanceFt)
        if (far && reason.isNullOrBlank()) {
            val ft = PunchDistance.feetBetween(requireNotNull(fix), requireNotNull(job?.lat), requireNotNull(job?.lng))
            return CrewResult.NeedsReason(ft.toInt())
        }
        val noteText = if (fix == null) ClockController.NO_LOCATION_NOTE else null
        val punched = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        for (id in memberIds) {
            if (clock.status(ctx.companyId, id) != ClockStatus.Out) { skipped += id; continue }
            punches.record(
                NewPunch(ctx.companyId, id, job?.id, PunchKind.IN, fix = fix, farFromJob = far, reason = if (far) reason else null, clockedInBy = leadId, note = noteText),
            )
            punched += id
        }
        return CrewResult.Done(punched, skipped)
    }
}
