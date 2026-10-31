package com.kurtz.homerecord.core.model

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One section of a daily log. [edited] is set once someone changes it by hand; from then on
 * re-assembling keeps [value] as they left it (AC-F30).
 */
@Serializable
data class LogSection<T>(val value: T, val edited: Boolean = false)

@Serializable
data class CrewLine(val memberId: String, val name: String, val minutes: Int, val onSiteNow: Boolean = false)

@Serializable
data class TaskLine(val taskId: String, val title: String)

@Serializable
data class ChangeOrderLine(val changeOrderId: String, val title: String, val totalCents: Long)

@Serializable
data class MaterialLine(val materialId: String, val vendor: String?, val category: String?, val amountCents: Long, val note: String?)

/** The record lines a lead can leave out of a day's log (AC-F30); the record itself stays on the job. */
enum class LogLineKind { PHOTO, TASK, CHANGE_ORDER, MATERIAL }

/** Delay reasons for the picker (AC-F32). Words come from string resources in the app. */
enum class DelayReason { WEATHER, MATERIALS, INSPECTION, CLIENT, OTHER }

@Serializable
data class LogDelay(val reason: DelayReason, val note: String = "")

/**
 * The day's weather at the job (AC-F31, from the backend proxy AC-B32). [conditions] is the backend's key
 * (`clear`, `rain`, …), turned into words in the app.
 */
@Serializable
data class LogWeather(
    val highF: Int? = null,
    val lowF: Int? = null,
    val precipIn: Double? = null,
    val conditions: String = "unknown",
    val fetchedAt: Long = 0,
)

/**
 * The daily log's sections as stored in `daily_log.sectionsJson`. Assembled sections come from the day's
 * records; [delays], [visitors], [safety] and [clientNotes] are only ever typed by hand.
 */
@Serializable
data class DailyLogSections(
    val crew: LogSection<List<CrewLine>> = LogSection(emptyList()),
    val workDone: LogSection<List<String>> = LogSection(emptyList()),
    val photoIds: LogSection<List<String>> = LogSection(emptyList()),
    val tasksDone: LogSection<List<TaskLine>> = LogSection(emptyList()),
    val changeOrders: LogSection<List<ChangeOrderLine>> = LogSection(emptyList()),
    val materials: LogSection<List<MaterialLine>> = LogSection(emptyList()),
    val weather: LogSection<LogWeather?> = LogSection(null),
    val delays: List<LogDelay> = emptyList(),
    val visitors: String = "",
    val safety: String = "",
    val clientNotes: String = "",
    /**
     * Record ids left out of the log by hand, per kind. Re-assembling skips them, so a photo dropped from the log
     * stays out while photos taken later that day still arrive (AC-F30).
     */
    val dropped: Map<LogLineKind, List<String>> = emptyMap(),
) {
    val totalCrewMinutes: Int get() = crew.value.sumOf { it.minutes }

    /** Leaves one line out of the log: gone from its section now and skipped by every later re-assembly. */
    fun drop(kind: LogLineKind, id: String): DailyLogSections {
        val ids = dropped[kind].orEmpty()
        val next = copy(dropped = dropped + (kind to (if (id in ids) ids else ids + id)))
        return when (kind) {
            LogLineKind.PHOTO -> next.copy(photoIds = photoIds.copy(value = photoIds.value.filter { it != id }))
            LogLineKind.TASK -> next.copy(tasksDone = tasksDone.copy(value = tasksDone.value.filter { it.taskId != id }))
            LogLineKind.CHANGE_ORDER -> next.copy(changeOrders = changeOrders.copy(value = changeOrders.value.filter { it.changeOrderId != id }))
            LogLineKind.MATERIAL -> next.copy(materials = materials.copy(value = materials.value.filter { it.materialId != id }))
        }
    }

    /** Forgets what was left out of [kind]; the next re-assembly puts those lines back from the records. */
    fun restore(kind: LogLineKind): DailyLogSections = copy(dropped = dropped - kind)

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        /** An unreadable or empty column reads as a fresh draft rather than failing the screen. */
        fun fromJson(text: String?): DailyLogSections =
            if (text.isNullOrBlank()) DailyLogSections()
            else runCatching { json.decodeFromString(serializer(), text) }.getOrDefault(DailyLogSections())
    }
}

/** A job note or voice memo for the day; [transcript] is the memo's speech-to-text. */
data class LogNoteInput(val id: String, val createdAt: Long, val text: String, val transcript: String?)

data class LogPhotoInput(val id: String, val takenAt: Long)

/** A task with the time it was ticked done ([doneAt] null while open). */
data class LogTaskInput(val id: String, val title: String, val doneAt: Long?)

data class LogChangeOrderInput(val id: String, val title: String, val totalCents: Long, val signedAt: Long?)

data class LogMaterialInput(val id: String, val date: String, val vendor: String?, val category: String?, val amountCents: Long, val note: String?)

/** Everything recorded on [jobId] that the day's draft is built from. Lists may hold other days; the assembler filters. */
data class LogInputs(
    val jobId: String,
    val day: LocalDate,
    val zone: ZoneId,
    val shifts: List<Shift> = emptyList(),
    val memberNames: Map<String, String> = emptyMap(),
    val notes: List<LogNoteInput> = emptyList(),
    val photos: List<LogPhotoInput> = emptyList(),
    val tasks: List<LogTaskInput> = emptyList(),
    val changeOrders: List<LogChangeOrderInput> = emptyList(),
    val materials: List<LogMaterialInput> = emptyList(),
    /** Null when the weather could not be fetched (offline); an earlier snapshot is then kept. */
    val weather: LogWeather? = null,
)

/**
 * Builds a job's daily log draft from the day's punches, notes, photos, tasks, change orders, materials and
 * weather (AC-F30). Pass the saved sections as `previous` to re-assemble: hand-edited sections and the typed-only
 * sections are kept exactly; the rest are rebuilt from the records.
 */
object LogAssembler {

    fun assemble(inputs: LogInputs, now: Long, previous: DailyLogSections? = null): DailyLogSections {
        val start = inputs.day.atStartOfDay(inputs.zone).toInstant().toEpochMilli()
        val end = inputs.day.plusDays(1).atStartOfDay(inputs.zone).toInstant().toEpochMilli()
        val inDay = { t: Long -> t in start until end }
        val iso = inputs.day.toString()
        val prev = previous ?: DailyLogSections()
        fun kept(kind: LogLineKind, id: String) = id !in prev.dropped[kind].orEmpty()

        val crew = crewLines(inputs, start, end, now)
        val workDone = inputs.notes.filter { inDay(it.createdAt) }.sortedWith(compareBy({ it.createdAt }, { it.id }))
            .mapNotNull { n -> listOf(n.text, n.transcript.orEmpty()).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n").ifEmpty { null } }
        val photos = inputs.photos.filter { inDay(it.takenAt) && kept(LogLineKind.PHOTO, it.id) }.sortedWith(compareBy({ it.takenAt }, { it.id })).map { it.id }
        val tasks = inputs.tasks.filter { it.doneAt?.let(inDay) == true && kept(LogLineKind.TASK, it.id) }.sortedWith(compareBy({ it.doneAt }, { it.id }))
            .map { TaskLine(it.id, it.title) }
        val changeOrders = inputs.changeOrders.filter { it.signedAt?.let(inDay) == true && kept(LogLineKind.CHANGE_ORDER, it.id) }.sortedWith(compareBy({ it.signedAt }, { it.id }))
            .map { ChangeOrderLine(it.id, it.title, it.totalCents) }
        val materials = inputs.materials.filter { it.date == iso && kept(LogLineKind.MATERIAL, it.id) }.sortedBy { it.id }
            .map { MaterialLine(it.id, it.vendor, it.category, it.amountCents, it.note) }

        fun <T> keepOr(old: LogSection<T>, fresh: T): LogSection<T> = if (old.edited) old else LogSection(fresh)
        val weather: LogSection<LogWeather?> = when {
            prev.weather.edited -> prev.weather
            else -> LogSection<LogWeather?>(inputs.weather ?: prev.weather.value)
        }
        return prev.copy(
            crew = keepOr(prev.crew, crew),
            workDone = keepOr(prev.workDone, workDone),
            photoIds = keepOr(prev.photoIds, photos),
            tasksDone = keepOr(prev.tasksDone, tasks),
            changeOrders = keepOr(prev.changeOrders, changeOrders),
            materials = keepOr(prev.materials, materials),
            weather = weather,
        )
    }

    /** Minutes each person worked on the job inside the day: job segments clipped to the day, less breaks inside them. */
    private fun crewLines(inputs: LogInputs, start: Long, end: Long, now: Long): List<CrewLine> {
        val minutes = linkedMapOf<String, Long>()
        val open = mutableSetOf<String>()
        for (shift in inputs.shifts) {
            for (seg in shift.segments) {
                if (seg.jobId != inputs.jobId) continue
                // a running shift's last segment and open break (ShiftBuilder marks both as zero length) run until now
                val running = shift.endAt == null && seg === shift.segments.last()
                val segEnd = if (running) now else seg.endAt
                val from = maxOf(seg.startAt, start)
                val to = minOf(segEnd, end)
                if (to <= from) continue
                val breaks = shift.breaks.sumOf { b ->
                    val bEnd = if (shift.endAt == null && b.first == b.last) now else b.last
                    (minOf(bEnd, to) - maxOf(b.first, from)).coerceAtLeast(0)
                }
                minutes[shift.memberId] = (minutes[shift.memberId] ?: 0) + (to - from - breaks).coerceAtLeast(0)
                if (running && now in start until end) open += shift.memberId
            }
        }
        return minutes.map { (member, ms) ->
            CrewLine(member, inputs.memberNames[member].orEmpty(), (ms / 60_000).toInt(), onSiteNow = member in open)
        }.filter { it.minutes > 0 || it.onSiteNow }.sortedWith(compareBy({ it.name.lowercase() }, { it.memberId }))
    }
}
