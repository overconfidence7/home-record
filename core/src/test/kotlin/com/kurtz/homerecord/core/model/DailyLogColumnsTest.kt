package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

/** R09a: one daily log section per synced column, older single-value rows still read whole. */
class DailyLogColumnsTest {
    private val sections = DailyLogSections(
        crew = LogSection(listOf(CrewLine("m1", "Maria Soto", 510, onSiteNow = true))),
        workDone = LogSection(listOf("Set the base cabinets"), edited = true),
        photoIds = LogSection(listOf("ph1", "ph2")),
        tasksDone = LogSection(listOf(TaskLine("t1", "Level the sink base"))),
        changeOrders = LogSection(listOf(ChangeOrderLine("co1", "Add pantry outlet", 18_500))),
        materials = LogSection(listOf(MaterialLine("ma1", "Home Depot", "Electrical", 4_212, "Wire nuts"))),
        weather = LogSection(LogWeather(61, 44, 0.2, "showers", 99), edited = true),
        delays = listOf(LogDelay(DelayReason.INSPECTION, "Inspector came at 2")),
        visitors = "Town inspector",
        safety = "Ladder tied off",
        clientNotes = "Pick a pantry light by Friday",
        dropped = mapOf(LogLineKind.PHOTO to listOf("ph3")),
    )

    @AcceptanceCriterion("AC-F30")
    @Test fun everySectionRoundTripsThroughItsOwnColumn() {
        assertThat(DailyLogColumns.of(sections).toSections()).isEqualTo(sections)
        assertThat(DailyLogColumns.of(DailyLogSections()).toSections()).isEqualTo(DailyLogSections())
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun aRowSavedBeforeTheSplitReadsFromItsSingleValue() {
        assertThat(DailyLogColumns().toSections(sections.toJson())).isEqualTo(sections)
        // a column written since wins over the old value; the rest still come from it
        val partly = DailyLogColumns(visitors = "Electrician from Ortiz", delaysJson = "[]")
        val read = partly.toSections(sections.toJson())
        assertThat(read.visitors).isEqualTo("Electrician from Ortiz")
        assertThat(read.delays).isEmpty()
        assertThat(read.crew).isEqualTo(sections.crew)
        assertThat(read.safety).isEqualTo("Ladder tied off")
    }

    @AcceptanceCriterion("AC-F30", "AC-F53")
    @Test fun aChangeToOneSectionChangesOnlyItsColumn() {
        val before = DailyLogColumns.of(sections)
        val crewMoved = DailyLogColumns.of(sections.copy(crew = LogSection(listOf(CrewLine("m1", "Maria Soto", 540)))))
        assertThat(changed(before, crewMoved)).containsExactly("crewJson")
        val typed = DailyLogColumns.of(sections.copy(visitors = "Town inspector, client", delays = emptyList()))
        assertThat(changed(before, typed)).containsExactly("visitors", "delaysJson")
    }

    @Test fun anUnreadableColumnFallsBackInsteadOfFailing() {
        val read = DailyLogColumns(crewJson = "{not json", visitors = "Neighbour").toSections(sections.toJson())
        assertThat(read.crew).isEqualTo(sections.crew)
        assertThat(read.visitors).isEqualTo("Neighbour")
    }

    private fun changed(a: DailyLogColumns, b: DailyLogColumns): List<String> = buildList {
        if (a.crewJson != b.crewJson) add("crewJson")
        if (a.workDoneJson != b.workDoneJson) add("workDoneJson")
        if (a.photosJson != b.photosJson) add("photosJson")
        if (a.tasksJson != b.tasksJson) add("tasksJson")
        if (a.changeOrdersJson != b.changeOrdersJson) add("changeOrdersJson")
        if (a.materialsJson != b.materialsJson) add("materialsJson")
        if (a.loggedWeatherJson != b.loggedWeatherJson) add("loggedWeatherJson")
        if (a.leftOutJson != b.leftOutJson) add("leftOutJson")
        if (a.delaysJson != b.delaysJson) add("delaysJson")
        if (a.visitors != b.visitors) add("visitors")
        if (a.safety != b.safety) add("safety")
        if (a.clientNotes != b.clientNotes) add("clientNotes")
    }
}
