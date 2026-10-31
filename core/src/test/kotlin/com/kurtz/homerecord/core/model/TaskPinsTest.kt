package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-F35: a task pinned on the floor plan; the plan shows the open pins of the floor in view.
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
}
