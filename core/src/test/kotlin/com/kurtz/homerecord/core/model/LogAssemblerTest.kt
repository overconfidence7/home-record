package com.kurtz.homerecord.core.model

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// AC-F30: LogAssembler builds the day's draft and never overwrites a hand-edited section
class LogAssemblerTest {
    private val zone = ZoneId.of("America/New_York")
    private val day = LocalDate.of(2026, 10, 30)
    private val hour = 3_600_000L
    private val minute = 60_000L
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atStartOfDay(zone).toInstant().toEpochMilli() + h * hour + m * minute
    private val endOfDay = at(23, 59)
    private val names = mapOf("m1" to "Maria Soto", "m2" to "Dev Patel", "m3" to "Ana Lee")

    private fun punch(member: String, kind: String, at: Long, job: String? = "kitchen") = ShiftPunch(member, job, kind, at)

    private fun inputs(
        punches: List<ShiftPunch> = emptyList(),
        now: Long = endOfDay,
        notes: List<LogNoteInput> = emptyList(),
        photos: List<LogPhotoInput> = emptyList(),
        tasks: List<LogTaskInput> = emptyList(),
        changeOrders: List<LogChangeOrderInput> = emptyList(),
        materials: List<LogMaterialInput> = emptyList(),
        weather: LogWeather? = null,
    ) = LogInputs(
        jobId = "kitchen", day = day, zone = zone, shifts = ShiftBuilder.build(punches, now), memberNames = names,
        notes = notes, photos = photos, tasks = tasks, changeOrders = changeOrders, materials = materials, weather = weather,
    )

    @Test fun crewHoursComeFromPunchesOnThisJobLessBreaks() { // AC-F30
        val punches = listOf(
            punch("m1", PunchKind.IN, at(7)),
            punch("m1", PunchKind.BREAK_START, at(12)),
            punch("m1", PunchKind.BREAK_END, at(12, 30)),
            punch("m1", PunchKind.OUT, at(15, 30)),
            // Dev starts on the deck job, switches to the kitchen at 10:00, leaves at 14:00
            punch("m2", PunchKind.IN, at(8), job = "deck"),
            punch("m2", PunchKind.SWITCH, at(10), job = "kitchen"),
            punch("m2", PunchKind.OUT, at(14), job = "kitchen"),
            // Ana worked only the deck
            punch("m3", PunchKind.IN, at(8), job = "deck"),
            punch("m3", PunchKind.OUT, at(16), job = "deck"),
            // Maria the day before
            punch("m1", PunchKind.IN, at(7, d = day.minusDays(1))),
            punch("m1", PunchKind.OUT, at(15, d = day.minusDays(1))),
        )
        val crew = LogAssembler.assemble(inputs(punches), now = endOfDay).crew.value
        assertEquals(listOf("Dev Patel" to 240, "Maria Soto" to 480), crew.map { it.name to it.minutes })
        assertFalse(crew.any { it.onSiteNow })
    }

    @Test fun shiftsAcrossMidnightCountOnlyTheirPartOfTheDay() { // AC-F30
        val punches = listOf(punch("m1", PunchKind.IN, at(22)), punch("m1", PunchKind.OUT, at(2, d = day.plusDays(1))))
        val crew = LogAssembler.assemble(inputs(punches, now = at(3, d = day.plusDays(1))), now = at(3, d = day.plusDays(1))).crew.value
        assertEquals(120, crew.single().minutes)
    }

    @Test fun someoneStillClockedInCountsUntilNowAndIsMarkedOnSite() { // AC-F30
        val now = at(11)
        val punches = listOf(punch("m1", PunchKind.IN, at(7)), punch("m1", PunchKind.BREAK_START, at(10, 30)))
        val line = LogAssembler.assemble(inputs(punches, now = now), now = now).crew.value.single()
        assertEquals(210, line.minutes) // 7:00–11:00 less the open 30 min break
        assertTrue(line.onSiteNow)
    }

    @Test fun workDoneIsNotesAndTranscriptsInOrder() { // AC-F30
        val notes = listOf(
            LogNoteInput("n2", at(14), "", "Set the sink base and leveled the run"),
            LogNoteInput("n1", at(9), "Demo of the old soffit done", null),
            LogNoteInput("n3", at(16), "Rough-in inspection booked", "Rough-in inspection booked"),
            LogNoteInput("n4", at(10), "  ", null),
            LogNoteInput("old", at(9, d = day.minusDays(1)), "Yesterday's note", null),
        )
        val work = LogAssembler.assemble(inputs(notes = notes), now = endOfDay).workDone.value
        assertEquals(listOf("Demo of the old soffit done", "Set the sink base and leveled the run", "Rough-in inspection booked"), work)
    }

    @Test fun photosTasksChangeOrdersAndMaterialsAreTheDays() { // AC-F30
        val s = LogAssembler.assemble(
            inputs(
                photos = listOf(LogPhotoInput("p2", at(13)), LogPhotoInput("p1", at(9)), LogPhotoInput("px", at(9, d = day.plusDays(1)))),
                tasks = listOf(
                    LogTaskInput("t1", "Patch drywall at the window", at(15)),
                    LogTaskInput("t2", "Order cabinet pulls", null),
                    LogTaskInput("t3", "Cap the gas line", at(8, d = day.minusDays(1))),
                ),
                changeOrders = listOf(
                    LogChangeOrderInput("co1", "Add pantry outlet", 34_500, at(11)),
                    LogChangeOrderInput("co2", "Upgrade faucet", 21_000, null),
                ),
                materials = listOf(
                    LogMaterialInput("mat1", "2026-10-30", "Home Depot", "Lumber", 18_742, "2x4s for the soffit"),
                    LogMaterialInput("mat2", "2026-10-29", "Ferguson", "Plumbing", 9_900, null),
                ),
            ),
            now = endOfDay,
        )
        assertEquals(listOf("p1", "p2"), s.photoIds.value)
        assertEquals(listOf(TaskLine("t1", "Patch drywall at the window")), s.tasksDone.value)
        assertEquals(listOf(ChangeOrderLine("co1", "Add pantry outlet", 34_500)), s.changeOrders.value)
        assertEquals(listOf("mat1"), s.materials.value.map { it.materialId })
    }

    @Test fun reassemblingKeepsEditedSectionsAndTypedOnesButRefreshesTheRest() { // AC-F30
        val first = LogAssembler.assemble(
            inputs(punches = listOf(punch("m1", PunchKind.IN, at(7)), punch("m1", PunchKind.OUT, at(15))),
                notes = listOf(LogNoteInput("n1", at(9), "Demo done", null))),
            now = endOfDay,
        )
        val edited = first.copy(
            workDone = LogSection(listOf("Demo of the soffit done, debris hauled"), edited = true),
            delays = listOf(LogDelay(DelayReason.INSPECTION, "Inspector came at 2 instead of 10")),
            visitors = "Homeowner at 4 pm",
            safety = "Ladder tie-off reminder",
            clientNotes = "Pick tile color by Monday",
        )
        val again = LogAssembler.assemble(
            inputs(
                punches = listOf(punch("m1", PunchKind.IN, at(7)), punch("m1", PunchKind.OUT, at(15)),
                    punch("m2", PunchKind.IN, at(12)), punch("m2", PunchKind.OUT, at(14))),
                notes = listOf(LogNoteInput("n1", at(9), "Demo done", null), LogNoteInput("n2", at(13), "Framing started", null)),
            ),
            now = endOfDay, previous = edited,
        )
        assertEquals(listOf("Demo of the soffit done, debris hauled"), again.workDone.value)
        assertTrue(again.workDone.edited)
        assertEquals(listOf("Dev Patel", "Maria Soto"), again.crew.value.map { it.name })
        assertEquals(edited.delays, again.delays)
        assertEquals("Homeowner at 4 pm", again.visitors)
        assertEquals("Ladder tie-off reminder", again.safety)
        assertEquals("Pick tile color by Monday", again.clientNotes)
    }

    @Test fun editedCrewHoursSurviveNewPunches() { // AC-F30
        val first = LogAssembler.assemble(inputs(listOf(punch("m1", PunchKind.IN, at(7)), punch("m1", PunchKind.OUT, at(15)))), now = endOfDay)
        val edited = first.copy(crew = LogSection(listOf(first.crew.value.single().copy(minutes = 450)), edited = true))
        val again = LogAssembler.assemble(
            inputs(listOf(punch("m1", PunchKind.IN, at(7)), punch("m1", PunchKind.OUT, at(16)))), now = endOfDay, previous = edited,
        )
        assertEquals(450, again.crew.value.single().minutes)
    }

    @Test fun weatherIsKeptWhenOfflineAndRefreshedWhenOnline() { // AC-F30, AC-F31
        val rain = LogWeather(highF = 62, lowF = 44, precipIn = 0.2, conditions = "rain", fetchedAt = at(8))
        assertNull(LogAssembler.assemble(inputs(), now = endOfDay).weather.value)
        val first = LogAssembler.assemble(inputs(weather = rain), now = endOfDay)
        assertEquals(rain, first.weather.value)
        val offline = LogAssembler.assemble(inputs(weather = null), now = endOfDay, previous = first)
        assertEquals(rain, offline.weather.value)
        val clear = rain.copy(conditions = "clear", precipIn = 0.0, fetchedAt = at(15))
        assertEquals(clear, LogAssembler.assemble(inputs(weather = clear), now = endOfDay, previous = offline).weather.value)
        val hand = offline.copy(weather = LogSection(rain.copy(conditions = "showers"), edited = true))
        assertEquals("showers", LogAssembler.assemble(inputs(weather = clear), now = endOfDay, previous = hand).weather.value?.conditions)
    }

    @Test fun sectionsRoundTripThroughTheStoredJson() { // AC-F30
        val s = DailyLogSections(
            crew = LogSection(listOf(CrewLine("m1", "Maria Soto", 480)), edited = true),
            workDone = LogSection(listOf("Demo done")),
            weather = LogSection(LogWeather(62, 44, 0.2, "rain", 1L)),
            delays = listOf(LogDelay(DelayReason.MATERIALS, "Cabinets back-ordered")),
            visitors = "Inspector",
        )
        assertEquals(s, DailyLogSections.fromJson(s.toJson()))
        assertEquals(DailyLogSections(), DailyLogSections.fromJson("{}"))
        assertEquals(DailyLogSections(), DailyLogSections.fromJson("not json"))
        assertEquals(DailyLogSections(), DailyLogSections.fromJson(null))
        assertEquals(480, s.totalCrewMinutes)
    }

    @Test fun linesLeftOutStayOutOnReassemblyWhileNewOnesArriveAndPutBackRestoresThem() { // AC-F30
        val photos = listOf(LogPhotoInput("p1", at(9)), LogPhotoInput("p2", at(13)))
        val tasks = listOf(LogTaskInput("t1", "Patch drywall at the window", at(15)), LogTaskInput("t2", "Hang the door", at(16)))
        val cos = listOf(LogChangeOrderInput("co1", "Add pantry outlet", 34_500, at(11)))
        val mats = listOf(LogMaterialInput("mat1", "2026-10-30", "Home Depot", "Lumber", 18_742, null))
        val first = LogAssembler.assemble(inputs(photos = photos, tasks = tasks, changeOrders = cos, materials = mats), now = endOfDay)

        val dropped = first.drop(LogLineKind.PHOTO, "p1").drop(LogLineKind.TASK, "t2")
            .drop(LogLineKind.CHANGE_ORDER, "co1").drop(LogLineKind.MATERIAL, "mat1").drop(LogLineKind.PHOTO, "p1")
        assertEquals(listOf("p2"), dropped.photoIds.value)
        assertEquals(listOf("t1"), dropped.tasksDone.value.map { it.taskId })
        assertTrue(dropped.changeOrders.value.isEmpty())
        assertTrue(dropped.materials.value.isEmpty())
        assertEquals(listOf("p1"), dropped.dropped[LogLineKind.PHOTO])
        // dropping is not a hand edit of the section: later records still come in
        assertFalse(dropped.photoIds.edited)

        val later = photos + LogPhotoInput("p3", at(17))
        val again = LogAssembler.assemble(inputs(photos = later, tasks = tasks, changeOrders = cos, materials = mats), now = endOfDay, previous = dropped)
        assertEquals(listOf("p2", "p3"), again.photoIds.value)
        assertEquals(listOf("t1"), again.tasksDone.value.map { it.taskId })
        assertTrue(again.changeOrders.value.isEmpty())
        assertTrue(again.materials.value.isEmpty())
        assertEquals(again, DailyLogSections.fromJson(again.toJson()))

        val back = LogAssembler.assemble(
            inputs(photos = later, tasks = tasks, changeOrders = cos, materials = mats), now = endOfDay, previous = again.restore(LogLineKind.PHOTO),
        )
        assertEquals(listOf("p1", "p2", "p3"), back.photoIds.value)
        assertEquals(listOf("t1"), back.tasksDone.value.map { it.taskId })
        assertNull(back.dropped[LogLineKind.PHOTO])
    }
}
