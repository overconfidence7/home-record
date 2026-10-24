package com.kurtz.homerecord.core.punch

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.data.ShiftEntity
import com.kurtz.homerecord.core.model.GeoFix
import com.kurtz.homerecord.core.model.Shift
import com.kurtz.homerecord.core.model.ShiftBuilder
import com.kurtz.homerecord.core.model.ShiftPunch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A punch as the clock screens hand it over (AC-F8, AC-F9). [fix] is null when there was no location. */
data class NewPunch(
    val companyId: String,
    val memberId: String,
    val jobId: String?,
    val kind: String,
    val fix: GeoFix? = null,
    val farFromJob: Boolean = false,
    val reason: String? = null,
    val clockedInBy: String? = null,
    val note: String? = null,
    val at: Long? = null,
)

/** One audit entry kept on an edited punch: who, when, before and after (AC-F14). */
@Serializable
data class PunchEdit(
    val by: String,
    val byName: String,
    val at: Long,
    val reason: String,
    val beforeAt: Long,
    val afterAt: Long,
    val beforeJobId: String? = null,
    val afterJobId: String? = null,
)

/**
 * Punches save to Room first and queue for sync through [onChange] (AC-F52); shifts are derived from punches
 * and cached in the `shift` table, which is local only and rebuilt after every write.
 */
class PunchRepository(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Saves a punch offline and returns its id. */
    suspend fun record(p: NewPunch): String {
        val stamp = now()
        val id = Ids.newId()
        db.punchDao().upsert(
            PunchEntity(
                id = id, companyId = p.companyId, memberId = p.memberId, jobId = p.jobId, kind = p.kind, at = p.at ?: stamp,
                lat = p.fix?.lat, lng = p.fix?.lng, accuracyM = p.fix?.accuracyM, farFromJob = p.farFromJob,
                reason = p.reason?.takeIf { it.isNotBlank() }, clockedInBy = p.clockedInBy, note = p.note?.takeIf { it.isNotBlank() },
                createdAt = stamp, updatedAt = stamp,
            ),
        )
        onChange("punch", id)
        rebuildShifts(p.companyId, p.memberId)
        return id
    }

    /**
     * Changes a punch's time or job and appends an audit entry (AC-F14). Returns false when [reason] is blank
     * or the punch is missing: an edit without a reason is never saved.
     */
    suspend fun edit(punchId: String, editorId: String, editorName: String, newAt: Long, newJobId: String?, reason: String): Boolean {
        if (reason.isBlank()) return false
        val old = db.punchDao().get(punchId) ?: return false
        val stamp = now()
        val entry = PunchEdit(editorId, editorName, stamp, reason.trim(), old.at, newAt, old.jobId, newJobId)
        val edits = json.decodeFromString<List<PunchEdit>>(old.editsJson) + entry
        db.punchDao().upsert(old.copy(at = newAt, jobId = newJobId, editsJson = json.encodeToString(edits), updatedAt = stamp))
        onChange("punch", punchId)
        rebuildShifts(old.companyId, old.memberId)
        return true
    }

    fun auditOf(punch: PunchEntity): List<PunchEdit> = runCatching { json.decodeFromString<List<PunchEdit>>(punch.editsJson) }.getOrDefault(emptyList())

    fun observePunches(companyId: String, memberId: String? = null): Flow<List<PunchEntity>> =
        db.punchDao().observeFor(companyId).map { rows -> rows.filter { memberId == null || it.memberId == memberId }.sortedBy { it.at } }

    /** Shifts built from all punches of the company, oldest first (AC-F10). */
    fun observeShifts(companyId: String, memberId: String? = null): Flow<List<Shift>> =
        observePunches(companyId, memberId).map { rows -> ShiftBuilder.build(rows.map(::toShiftPunch), now()) }

    /** The member's open shift, if any (AC-F10). */
    suspend fun openShift(companyId: String, memberId: String): Shift? =
        ShiftBuilder.build(db.punchDao().listFor(companyId).filter { it.memberId == memberId }.map(::toShiftPunch), now())
            .lastOrNull { it.endAt == null }

    /** The member's most recent punch, or null before the first. */
    suspend fun lastPunch(companyId: String, memberId: String): PunchEntity? =
        db.punchDao().listFor(companyId).filter { it.memberId == memberId }.maxByOrNull { it.at }

    private suspend fun rebuildShifts(companyId: String, memberId: String) {
        val stamp = now()
        val shifts = ShiftBuilder.build(db.punchDao().listFor(companyId).filter { it.memberId == memberId }.map(::toShiftPunch), stamp)
        db.shiftDao().listFor(memberId).forEach { db.shiftDao().softDelete(it.id, stamp) }
        shifts.forEach { s ->
            db.shiftDao().upsert(
                ShiftEntity(
                    id = "$memberId-${s.startAt}", memberId = memberId, jobId = s.jobId, startAt = s.startAt, endAt = s.endAt,
                    breakMinutes = (s.breakMillis() / 60_000).toInt(), createdAt = stamp, updatedAt = stamp,
                ),
            )
        }
    }

    private fun toShiftPunch(p: PunchEntity) = ShiftPunch(p.memberId, p.jobId, p.kind, p.at, p.farFromJob, edited = p.editsJson != "[]")
}
