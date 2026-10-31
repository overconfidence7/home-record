package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-F35: a task pinned on the floor plan or a file page; the plan and the file show the open pins in view.
@AcceptanceCriterion("AC-F35")
class TaskPinsTest {
    private fun task(id: String, floor: String? = "f1", who: String? = "m1", done: Boolean = false, order: Int = 0) = JobTask(
        id = id, jobId = "j1", title = id, assigneeId = who, done = done, sortOrder = order,
        planPin = floor?.let { TaskPlanPin(it, 60, 90) },
    )

    @Test fun pinRoundTripsThroughItsStoredJson() {
        val pin = TaskPlanPin("f1", 126, 48)
        assertThat(TaskPlanPin.decode(pin.encode())).isEqualTo(pin)
    }

    @Test fun missingOrUnreadablePinReadsAsNoPin() {
        assertThat(TaskPlanPin.decode(null)).isNull()
        assertThat(TaskPlanPin.decode("")).isNull()
        assertThat(TaskPlanPin.decode("{not json")).isNull()
        assertThat(TaskPlanPin.decode("""{"floorId":"","xIn":1,"yIn":2}""")).isNull()
        // A newer app version may add fields; the pin still reads.
        assertThat(TaskPlanPin.decode("""{"floorId":"f2","xIn":1,"yIn":2,"page":3}""")).isEqualTo(TaskPlanPin("f2", 1, 2))
    }

    @Test fun floorShowsOnlyOpenPinsOnThatFloorInCrewOrder() {
        val tasks = listOf(task("b", order = 1), task("a", order = 0), task("done", done = true), task("upstairs", floor = "f2"), task("unpinned", floor = null))
        assertThat(TaskPins.onFloor(tasks, "f1", canManage = true, me = "m9").map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(TaskPins.onFloor(tasks, "f2", canManage = true, me = null).map { it.id }).containsExactly("upstairs")
        assertThat(TaskPins.onFloor(tasks, null, canManage = true, me = null)).isEmpty()
    }

    @Test fun crewSeeOnlyTheirOwnPins() {
        val tasks = listOf(task("mine", who = "m1"), task("theirs", who = "m2"), task("nobody", who = null))
        assertThat(TaskPins.onFloor(tasks, "f1", canManage = false, me = "m1").map { it.id }).containsExactly("mine")
        assertThat(TaskPins.onFloor(tasks, "f1", canManage = false, me = null)).isEmpty()
    }

    private fun filed(id: String, file: String? = "v1", who: String? = "m1", done: Boolean = false, order: Int = 0) = JobTask(
        id = id, jobId = "j1", title = id, assigneeId = who, done = done, sortOrder = order,
        filePin = file?.let { TaskFilePin(it, 0, 0.5f, 0.5f) },
    )

    @Test fun filePinReadsFromItsColumnsOnlyWhenWhole() {
        assertThat(TaskFilePin.of("f", 2, 0.1f, 0.9f)).isEqualTo(TaskFilePin("f", 2, 0.1f, 0.9f))
        assertThat(TaskFilePin.of(null, 0, 0.1f, 0.1f)).isNull()
        assertThat(TaskFilePin.of("f", null, 0.1f, 0.1f)).isNull()
        assertThat(TaskFilePin.of("f", 0, null, 0.1f)).isNull()
        assertThat(TaskFilePin.of("f", -1, 0.1f, 0.1f)).isNull()
        assertThat(TaskFilePin.of("f", 0, 1.5f, 0.1f)).isNull()
        assertThat(TaskFilePin.of("f", 0, Float.NaN, 0.1f)).isNull()
        // A tap at the very edge is clamped onto the page.
        assertThat(TaskFilePin.at("f", 0, -0.01f, 1.02f)).isEqualTo(TaskFilePin("f", 0, 0f, 1f))
    }

    @Test fun fileShowsOpenPinsOnAnyVersionInCrewOrder() {
        val tasks = listOf(filed("b", order = 1), filed("a", file = "v2", order = 0), filed("done", done = true), filed("other", file = "x"), filed("none", file = null))
        assertThat(TaskPins.onFile(tasks, setOf("v1", "v2"), canManage = true, me = null).map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(TaskPins.onFile(tasks, setOf("v1"), canManage = false, me = "m1").map { it.id }).containsExactly("b")
        assertThat(TaskPins.onFile(listOf(filed("theirs", who = "m2")), setOf("v1"), canManage = false, me = "m1")).isEmpty()
        assertThat(TaskPins.onFile(tasks, emptySet(), canManage = true, me = null)).isEmpty()
    }
}

