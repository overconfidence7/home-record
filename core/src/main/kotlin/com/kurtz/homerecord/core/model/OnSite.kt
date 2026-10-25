package com.kurtz.homerecord.core.model

import com.kurtz.homerecord.core.data.PunchEntity

/** One person currently clocked in, for the Owner's "Who's on site now" list (AC-F16). Pin is the clock-in fix, when there was one. */
data class OnSiteEntry(
    val memberId: String,
    val name: String,
    val jobId: String?,
    val jobName: String?,
    val since: Long,
    val onBreak: Boolean,
    val lat: Double?,
    val lng: Double?,
) {
    val hasPin: Boolean get() = lat != null && lng != null
}

/** Open shifts of the whole company as list rows, longest-on-site first (AC-F16). */
object OnSiteBuilder {
    fun build(punches: List<PunchEntity>, memberNames: Map<String, String>, jobNames: Map<String, String>, now: Long): List<OnSiteEntry> {
        val shifts = ShiftBuilder.build(punches.map { ShiftPunch(it.memberId, it.jobId, it.kind, it.at, it.farFromJob, it.editsJson != "[]") }, now)
        return shifts.filter { it.endAt == null }.map { shift ->
            val mine = punches.filter { it.memberId == shift.memberId && it.at >= shift.startAt }.sortedBy { it.at }
            val start = mine.firstOrNull { it.kind == PunchKind.IN && it.at == shift.startAt }
            val jobId = shift.segments.lastOrNull()?.jobId
            OnSiteEntry(
                memberId = shift.memberId,
                name = memberNames[shift.memberId] ?: "",
                jobId = jobId,
                jobName = jobId?.let(jobNames::get),
                since = shift.startAt,
                onBreak = mine.lastOrNull()?.kind == PunchKind.BREAK_START,
                lat = start?.lat,
                lng = start?.lng,
            )
        }.sortedBy { it.since }
    }
}
