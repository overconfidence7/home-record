package com.kurtz.homerecord.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * A daily log's sections, one value per synced column (R09a, AC-F30, AC-F53). Sync merges a row field by field, so with
 * each section in its own column an office phone whose auto-assembly rewrites the crew hours no longer overwrites the
 * Visitors or delays a Lead typed offline: each phone pushes only the columns it changed.
 *
 * A null column has not been written by an app that stores sections this way; [toSections] then takes that section from
 * the row's older single `sectionsJson` value, so a log saved before the split still reads whole.
 */
data class DailyLogColumns(
    val crewJson: String? = null,
    val workDoneJson: String? = null,
    val photosJson: String? = null,
    val tasksJson: String? = null,
    val changeOrdersJson: String? = null,
    val materialsJson: String? = null,
    val loggedWeatherJson: String? = null,
    val leftOutJson: String? = null,
    val delaysJson: String? = null,
    val visitors: String? = null,
    val safety: String? = null,
    val clientNotes: String? = null,
) {
    /** The sections these columns hold; a null or unreadable column falls back to [legacyJson]'s value for it. */
    fun toSections(legacyJson: String? = null): DailyLogSections {
        val old = DailyLogSections.fromJson(legacyJson)
        return DailyLogSections(
            crew = read(crewJson, CREW) ?: old.crew,
            workDone = read(workDoneJson, WORK_DONE) ?: old.workDone,
            photoIds = read(photosJson, PHOTOS) ?: old.photoIds,
            tasksDone = read(tasksJson, TASKS) ?: old.tasksDone,
            changeOrders = read(changeOrdersJson, CHANGE_ORDERS) ?: old.changeOrders,
            materials = read(materialsJson, MATERIALS) ?: old.materials,
            weather = read(loggedWeatherJson, WEATHER) ?: old.weather,
            delays = read(delaysJson, DELAYS) ?: old.delays,
            visitors = visitors ?: old.visitors,
            safety = safety ?: old.safety,
            clientNotes = clientNotes ?: old.clientNotes,
            dropped = read(leftOutJson, DROPPED) ?: old.dropped,
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        private val CREW = LogSection.serializer(ListSerializer(CrewLine.serializer()))
        private val WORK_DONE = LogSection.serializer(ListSerializer(String.serializer()))
        private val PHOTOS = LogSection.serializer(ListSerializer(String.serializer()))
        private val TASKS = LogSection.serializer(ListSerializer(TaskLine.serializer()))
        private val CHANGE_ORDERS = LogSection.serializer(ListSerializer(ChangeOrderLine.serializer()))
        private val MATERIALS = LogSection.serializer(ListSerializer(MaterialLine.serializer()))
        private val WEATHER = LogSection.serializer(LogWeather.serializer().nullable)
        private val DELAYS = ListSerializer(LogDelay.serializer())
        private val DROPPED = MapSerializer(LogLineKind.serializer(), ListSerializer(String.serializer()))

        /** Every column written, so a saved log never depends on the legacy value again. */
        fun of(sections: DailyLogSections): DailyLogColumns = DailyLogColumns(
            crewJson = json.encodeToString(CREW, sections.crew),
            workDoneJson = json.encodeToString(WORK_DONE, sections.workDone),
            photosJson = json.encodeToString(PHOTOS, sections.photoIds),
            tasksJson = json.encodeToString(TASKS, sections.tasksDone),
            changeOrdersJson = json.encodeToString(CHANGE_ORDERS, sections.changeOrders),
            materialsJson = json.encodeToString(MATERIALS, sections.materials),
            loggedWeatherJson = json.encodeToString(WEATHER, sections.weather),
            leftOutJson = json.encodeToString(DROPPED, sections.dropped),
            delaysJson = json.encodeToString(DELAYS, sections.delays),
            visitors = sections.visitors,
            safety = sections.safety,
            clientNotes = sections.clientNotes,
        )

        private fun <T> read(text: String?, serializer: KSerializer<T>): T? =
            if (text.isNullOrBlank()) null else runCatching { json.decodeFromString(serializer, text) }.getOrNull()
    }
}
