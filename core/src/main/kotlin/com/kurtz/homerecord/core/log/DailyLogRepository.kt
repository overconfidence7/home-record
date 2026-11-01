package com.kurtz.homerecord.core.log

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.DailyLogEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.data.columns
import com.kurtz.homerecord.core.data.sections
import com.kurtz.homerecord.core.data.withColumns
import com.kurtz.homerecord.core.lookup.WeatherApi
import com.kurtz.homerecord.core.model.DailyLogColumns
import com.kurtz.homerecord.core.model.DailyLogSections
import com.kurtz.homerecord.core.model.LogAssembler
import com.kurtz.homerecord.core.model.LogChangeOrderInput
import com.kurtz.homerecord.core.model.LogInputs
import com.kurtz.homerecord.core.model.LogMaterialInput
import com.kurtz.homerecord.core.model.LogNoteInput
import com.kurtz.homerecord.core.model.LogPhotoInput
import com.kurtz.homerecord.core.model.LogTaskInput
import com.kurtz.homerecord.core.model.LogWeather
import com.kurtz.homerecord.core.model.ShiftBuilder
import com.kurtz.homerecord.core.model.ShiftPunch
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Why the weather section reads the way it does (AC-F31). Only [Shown] has a snapshot to show. */
enum class WeatherStatus {
    /** A snapshot from the backend, fresh or cached on the row. */
    Shown,

    /** No network or the lookup failed, and no earlier snapshot: "No weather (offline)". */
    Offline,

    /** The job has no map location to look the weather up for. */
    NoLocation,

    /** The day is further ahead than any forecast. */
    NotYet,

    /** The lookup worked but Open-Meteo has no values for the day: "No weather recorded.", not offline. */
    NoData,
}

/** A job's log for one day as the screen reads it. */
data class DailyLog(
    val id: String,
    val jobId: String,
    val day: LocalDate,
    val sections: DailyLogSections,
    val status: String,
    val sentAt: Long?,
    val updatedAt: Long,
    /** Who the last send went to by email; empty when it went through the share sheet (AC-F33). */
    val recipients: List<String> = emptyList(),
    val pdfPath: String? = null,
) {
    val sent: Boolean get() = status == DailyLogRepository.STATUS_SENT
}

data class AssembleResult(val log: DailyLog, val weather: WeatherStatus)

/**
 * Loads, assembles and saves a job's daily log (AC-F30, AC-F31). The row for a job and day has a fixed id derived from
 * both, so two phones that open the same day's draft offline write the same row and sync merges it instead of
 * leaving two logs. Every save goes to Room first and queues for sync through [onChange] (AC-F52).
 *
 * The weather snapshot is kept on the row: it is fetched when there is none, or when the one kept is older than
 * [WEATHER_REFRESH_MS] and was taken before the day ended (a snapshot the backend fetched after the day is over is
 * final; its [LogWeather.fetchedAt] is the backend's fetch time, so a cached evening forecast is not). When the
 * fetch fails the kept snapshot stays; with none, the status is [WeatherStatus.Offline].
 */
class DailyLogRepository(
    private val db: HomeRecordDatabase,
    private val weatherApi: WeatherApi,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Every read-change-write of a log row runs one at a time, so two quick edits, or an edit during assembly, both land. */
    private val writes = Mutex()

    /** The saved log for [jobId] on [day], or null before anyone opened it. */
    fun observe(jobId: String, day: LocalDate): Flow<DailyLog?> =
        db.dailyLogDao().observeFor(jobId).map { rows -> pick(rows, jobId, day)?.toModel() }

    /** Every saved day of [jobId], newest first (for the day picker and the job timeline). */
    fun observeDays(jobId: String): Flow<List<DailyLog>> =
        db.dailyLogDao().observeFor(jobId).map { rows ->
            rows.groupBy { it.day }.mapNotNull { (day, sameDay) -> pick(sameDay, jobId, LocalDate.parse(day))?.toModel() }
                .sortedByDescending { it.day }
        }

    /** Every job's saved log from [from] to [to] inclusive, one per job and day (for the delay timeline, AC-F32). */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DailyLog>> =
        db.dailyLogDao().observeBetween(from.toString(), to.toString()).map { rows ->
            rows.groupBy { it.jobId to it.day }.mapNotNull { (key, sameDay) -> pick(sameDay, key.first, LocalDate.parse(key.second))?.toModel() }
        }

    /**
     * Builds the day's draft from the job's records and saves it; re-running keeps every hand-edited and typed-only
     * section (AC-F30). Returns null when the job is gone.
     */
    suspend fun assemble(jobId: String, day: LocalDate): AssembleResult? {
        val job = db.jobDao().get(jobId) ?: return null
        val before = pick(db.dailyLogDao().listFor(jobId), jobId, day)?.let { it.sections() }
        // the weather lookup can take seconds; it runs outside the lock so an edit made meanwhile is not held up
        val (fresh, weatherStatus) = weatherFor(job, day, before)
        val (row, sections) = writes.withLock {
            // read again under the lock: an edit saved during the lookup must not be overwritten
            val existing = pick(db.dailyLogDao().listFor(jobId), jobId, day)
            val previous = existing?.let { it.sections() }
            val stamp = now()
            val sections = LogAssembler.assemble(inputs(job, day, fresh), stamp, previous)
            save(existing, jobId, day, sections, stamp) to sections
        }
        val status = when {
            sections.weather.value != null -> WeatherStatus.Shown
            weatherStatus == WeatherStatus.Shown -> WeatherStatus.Offline
            else -> weatherStatus
        }
        return AssembleResult(row.toModel(), status)
    }

    /**
     * Saves a change to the log's sections. The caller marks what it changed as edited (see [DailyLogSections]); the
     * row is only written, and queued, when the sections actually changed.
     */
    suspend fun update(logId: String, change: (DailyLogSections) -> DailyLogSections): DailyLog? = writes.withLock {
        val row = db.dailyLogDao().get(logId) ?: return@withLock null
        val before = row.sections()
        val after = change(before)
        if (after == before) return@withLock row.toModel()
        save(row, row.jobId, LocalDate.parse(row.day), after, now()).toModel()
    }

    /**
     * Marks the log Sent now, to [recipients] (empty for the share sheet), with the PDF that went out (AC-F33). Sending
     * again replaces the time, recipients and PDF, so the log always shows its latest send. Null when the log is gone.
     */
    suspend fun markSent(logId: String, recipients: List<String>, pdfPath: String): DailyLog? = writes.withLock {
        val row = db.dailyLogDao().get(logId) ?: return@withLock null
        val stamp = now()
        val sent = row.copy(
            status = STATUS_SENT, sentAt = stamp, recipientsJson = json.encodeToString(recipients), pdfPath = pdfPath, updatedAt = stamp,
        )
        db.dailyLogDao().upsert(sent)
        onChange("daily_log", sent.id)
        sent.toModel()
    }

    /**
     * Writes [sections] one column per section (R09a). Nothing is written, or queued, when no column changed, so opening a
     * day whose records have not moved saves nothing; sync then pushes only the columns that did change, and a section
     * typed on another phone is not overwritten by this phone's copy of it.
     */
    private suspend fun save(existing: DailyLogEntity?, jobId: String, day: LocalDate, sections: DailyLogSections, stamp: Long): DailyLogEntity {
        val columns = DailyLogColumns.of(sections)
        val weatherJson = sections.weather.value?.let { json.encodeToString(LogWeather.serializer(), it) }
        if (existing != null && existing.columns() == columns && existing.weatherJson == weatherJson) return existing
        val row = (
            existing?.copy(weatherJson = weatherJson, updatedAt = stamp)
                ?: DailyLogEntity(
                    id = idFor(jobId, day), jobId = jobId, day = day.toString(), weatherJson = weatherJson,
                    createdAt = stamp, updatedAt = stamp, source = Sources.FIELD_LOG,
                )
            ).withColumns(columns)
        db.dailyLogDao().upsert(row)
        onChange("daily_log", row.id)
        return row
    }

    /** A fresh snapshot when one is due and the fetch worked, else null (the assembler then keeps the saved one). */
    private suspend fun weatherFor(job: JobEntity, day: LocalDate, previous: DailyLogSections?): Pair<LogWeather?, WeatherStatus> {
        val kept = previous?.weather
        if (kept?.edited == true) return null to WeatherStatus.Shown
        val lat = job.lat
        val lng = job.lng
        if (lat == null || lng == null) return null to WeatherStatus.NoLocation
        val dayEnd = day.plusDays(1).atStartOfDay(zone()).toInstant().toEpochMilli()
        val snapshot = kept?.value
        val due = snapshot == null || (snapshot.fetchedAt < dayEnd && now() - snapshot.fetchedAt >= WEATHER_REFRESH_MS)
        if (!due) return null to WeatherStatus.Shown
        return when (val r = weatherApi.day(lat, lng, day.toString())) {
            is ApiResult.Ok -> r.value?.let { it to WeatherStatus.Shown } ?: (null to WeatherStatus.NoData)
            is ApiResult.Err -> null to if (r.failure == ApiFailure.NotFound) WeatherStatus.NotYet else WeatherStatus.Offline
        }
    }

    private suspend fun inputs(job: JobEntity, day: LocalDate, weather: LogWeather?): LogInputs {
        val stamp = now()
        val punches = db.punchDao().listFor(job.companyId).map {
            ShiftPunch(it.memberId, it.jobId, it.kind, it.at, it.farFromJob, edited = it.editsJson != "[]")
        }
        val names = db.memberDao().listFor(job.companyId).associate { it.id to it.name }
        return LogInputs(
            jobId = job.id,
            day = day,
            zone = zone(),
            shifts = ShiftBuilder.build(punches, stamp),
            memberNames = names,
            notes = db.noteDao().listFor(job.id).map { LogNoteInput(it.id, it.createdAt, it.text, it.transcript) },
            photos = db.photoDao().observeForJob(job.id).first().map { LogPhotoInput(it.id, it.takenAt) },
            // doneAt since v5; a done row synced from an older app falls back to its last change
            tasks = db.taskDao().listFor(job.id).map { LogTaskInput(it.id, it.title, if (it.done) it.doneAt ?: it.updatedAt else null) },
            changeOrders = db.changeOrderDao().listFor(job.id).map { LogChangeOrderInput(it.id, it.title, it.totalCents, it.signedAt) },
            materials = db.materialDao().listFor(job.id).map { LogMaterialInput(it.id, it.date, it.vendor, it.category, it.amountCents, it.note) },
            weather = weather,
        )
    }

    /** The day's row: the one with the derived id, else (rows from before it, or a merge) the oldest. */
    private fun pick(rows: List<DailyLogEntity>, jobId: String, day: LocalDate): DailyLogEntity? {
        val iso = day.toString()
        val sameDay = rows.filter { it.day == iso }
        return sameDay.firstOrNull { it.id == idFor(jobId, day) } ?: sameDay.minByOrNull { it.createdAt }
    }

    private fun DailyLogEntity.toModel() = DailyLog(
        id = id, jobId = jobId, day = LocalDate.parse(day), sections = sections(),
        status = status, sentAt = sentAt, updatedAt = updatedAt,
        recipients = runCatching { json.decodeFromString<List<String>>(recipientsJson) }.getOrDefault(emptyList()), pdfPath = pdfPath,
    )

    companion object {
        /** A saved snapshot is looked up again after six hours, matching the backend's cache (AC-B32). */
        const val WEATHER_REFRESH_MS = 6 * 3_600_000L

        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_SENT = "SENT"

        /** The same job and day always get the same id, on every phone. */
        fun idFor(jobId: String, day: LocalDate): String =
            UUID.nameUUIDFromBytes("daily_log:$jobId:$day".toByteArray(StandardCharsets.UTF_8)).toString()
    }
}
