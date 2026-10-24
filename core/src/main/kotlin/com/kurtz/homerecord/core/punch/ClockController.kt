package com.kurtz.homerecord.core.punch

import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.location.LocationProvider
import com.kurtz.homerecord.core.model.GeoFix
import com.kurtz.homerecord.core.model.PunchDistance
import com.kurtz.homerecord.core.model.PunchKind

/** Where the person stands on the clock right now (AC-F10). */
sealed interface ClockStatus {
    data object Out : ClockStatus
    data class Working(val jobId: String?, val shiftStartAt: Long) : ClockStatus
    data class OnBreak(val jobId: String?, val shiftStartAt: Long, val breakStartAt: Long) : ClockStatus
}

/** A job as the clock needs it: id and where it is, when known. */
data class ClockJob(val id: String, val lat: Double?, val lng: Double?)

/** Who is punching and under which company rules. */
data class ClockContext(val companyId: String, val memberId: String, val settings: CompanySettings)

sealed interface ClockResult {
    data class Done(val punchId: String, val noLocation: Boolean) : ClockResult

    /** The punch is far from the job: ask for a reason, then call again with it (AC-F9). */
    data class NeedsReason(val distanceFt: Int) : ClockResult

    /** The company requires GPS and there is no fix: explain, and offer Settings when [permissionMissing] (AC-F18). */
    data class GpsRequired(val permissionMissing: Boolean) : ClockResult

    /** The action does not fit the clock state, for example Clock in while already in. */
    data object NotAllowed : ClockResult
}

/**
 * Clock in, break, switch and clock out on top of [PunchRepository] (AC-F8, AC-F10, AC-F18).
 * Clock in and Switch take a fix within [fixTimeoutMs]; a missing fix saves the punch marked "no location"
 * unless the company requires GPS.
 */
class ClockController(
    private val punches: PunchRepository,
    private val location: LocationProvider,
    private val fixTimeoutMs: Long = FIX_TIMEOUT_MS,
) {
    suspend fun status(companyId: String, memberId: String): ClockStatus {
        val shift = punches.openShift(companyId, memberId) ?: return ClockStatus.Out
        val last = punches.lastPunch(companyId, memberId)
        val job = shift.segments.lastOrNull()?.jobId
        return if (last?.kind == PunchKind.BREAK_START) ClockStatus.OnBreak(job, shift.startAt, last.at)
        else ClockStatus.Working(job, shift.startAt)
    }

    suspend fun clockIn(ctx: ClockContext, job: ClockJob?, reason: String? = null, note: String? = null): ClockResult {
        if (status(ctx.companyId, ctx.memberId) != ClockStatus.Out) return ClockResult.NotAllowed
        return located(ctx, job, PunchKind.IN, reason, note)
    }

    suspend fun switchJob(ctx: ClockContext, job: ClockJob, reason: String? = null, note: String? = null): ClockResult {
        if (status(ctx.companyId, ctx.memberId) !is ClockStatus.Working) return ClockResult.NotAllowed
        return located(ctx, job, PunchKind.SWITCH, reason, note)
    }

    suspend fun startBreak(ctx: ClockContext): ClockResult {
        val s = status(ctx.companyId, ctx.memberId) as? ClockStatus.Working ?: return ClockResult.NotAllowed
        return plain(ctx, s.jobId, PunchKind.BREAK_START)
    }

    suspend fun endBreak(ctx: ClockContext): ClockResult {
        val s = status(ctx.companyId, ctx.memberId) as? ClockStatus.OnBreak ?: return ClockResult.NotAllowed
        return plain(ctx, s.jobId, PunchKind.BREAK_END)
    }

    /** Clocking out while on break ends the break first so the shift closes cleanly. */
    suspend fun clockOut(ctx: ClockContext, note: String? = null): ClockResult {
        val s = status(ctx.companyId, ctx.memberId)
        val jobId = when (s) {
            ClockStatus.Out -> return ClockResult.NotAllowed
            is ClockStatus.Working -> s.jobId
            is ClockStatus.OnBreak -> s.jobId.also { plain(ctx, it, PunchKind.BREAK_END) }
        }
        return plain(ctx, jobId, PunchKind.OUT, note)
    }

    private suspend fun plain(ctx: ClockContext, jobId: String?, kind: String, note: String? = null): ClockResult {
        val id = punches.record(NewPunch(ctx.companyId, ctx.memberId, jobId, kind, note = note))
        return ClockResult.Done(id, noLocation = false)
    }

    private suspend fun located(ctx: ClockContext, job: ClockJob?, kind: String, reason: String?, note: String?): ClockResult {
        val permitted = location.hasPermission()
        val fix: GeoFix? = if (permitted) location.currentFix(fixTimeoutMs) else null
        if (fix == null && ctx.settings.requireGps) return ClockResult.GpsRequired(permissionMissing = !permitted)
        val far = PunchDistance.isFarFromJob(fix, job?.lat, job?.lng, ctx.settings.punchDistanceFt)
        if (far && reason.isNullOrBlank()) {
            val ft = PunchDistance.feetBetween(requireNotNull(fix), requireNotNull(job?.lat), requireNotNull(job?.lng))
            return ClockResult.NeedsReason(ft.toInt())
        }
        val saved = note?.takeIf { it.isNotBlank() }
        val noteText = if (fix == null) listOfNotNull(saved, NO_LOCATION_NOTE).joinToString(" · ") else saved
        val id = punches.record(
            NewPunch(ctx.companyId, ctx.memberId, job?.id, kind, fix = fix, farFromJob = far, reason = if (far) reason else null, note = noteText),
        )
        return ClockResult.Done(id, noLocation = fix == null)
    }

    companion object {
        const val FIX_TIMEOUT_MS = 5_000L
        const val NO_LOCATION_NOTE = "No location"
    }
}
