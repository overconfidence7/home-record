package com.kurtz.homerecord.core.data

import com.kurtz.homerecord.core.model.DailyLogColumns
import com.kurtz.homerecord.core.model.DailyLogSections

/** The row's per-section columns (R09a). */
fun DailyLogEntity.columns(): DailyLogColumns = DailyLogColumns(
    crewJson = crewJson, workDoneJson = workDoneJson, photosJson = photosJson, tasksJson = tasksJson,
    changeOrdersJson = changeOrdersJson, materialsJson = materialsJson, loggedWeatherJson = loggedWeatherJson,
    leftOutJson = leftOutJson, delaysJson = delaysJson, visitors = visitors, safety = safety, clientNotes = clientNotes,
)

/** The log's sections, a column not yet written falling back to the row's older `sectionsJson`. */
fun DailyLogEntity.sections(): DailyLogSections = columns().toSections(sectionsJson)

/** This row with [c] in its section columns; `sectionsJson` is left as it was. */
fun DailyLogEntity.withColumns(c: DailyLogColumns): DailyLogEntity = copy(
    crewJson = c.crewJson, workDoneJson = c.workDoneJson, photosJson = c.photosJson, tasksJson = c.tasksJson,
    changeOrdersJson = c.changeOrdersJson, materialsJson = c.materialsJson, loggedWeatherJson = c.loggedWeatherJson,
    leftOutJson = c.leftOutJson, delaysJson = c.delaysJson, visitors = c.visitors, safety = c.safety, clientNotes = c.clientNotes,
)
