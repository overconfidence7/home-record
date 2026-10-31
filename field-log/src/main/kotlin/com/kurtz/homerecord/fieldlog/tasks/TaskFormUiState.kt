package com.kurtz.homerecord.fieldlog.tasks

import androidx.compose.runtime.Immutable
import com.kurtz.homerecord.core.model.JobTask
import com.kurtz.homerecord.core.model.TaskPlanPin
import com.kurtz.homerecord.core.task.TaskDraft
import com.kurtz.homerecord.housemodel.blueprint.RoomHitTester
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** The task form as typed; kept in the saved state so rotation and process death keep it (rule 33). */
@Serializable
data class TaskFormDraft(
    val title: String = "",
    val assigneeId: String? = null,
    /** ISO yyyy-MM-dd. */
    val due: String? = null,
    val roomId: String? = null,
    val photoId: String? = null,
    val note: String = "",
    val punch: Boolean = false,
    /** Where the work is on the floor plan (AC-F35). */
    val planPin: TaskPlanPin? = null,
) {
    val titleOk: Boolean get() = title.isNotBlank()

    fun toDraft(): TaskDraft = TaskDraft(
        title = title.trim(), assigneeId = assigneeId,
        due = due?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        roomId = roomId, photoId = photoId, note = note.trim().takeIf { it.isNotEmpty() }, punch = punch, planPin = planPin,
    )

    companion object {
        fun of(t: JobTask) = TaskFormDraft(t.title, t.assigneeId, t.due?.toString(), t.roomId, t.photoId, t.note.orEmpty(), t.punch, t.planPin)
    }
}

/** Someone a task can go to; people on the job's crew come first in the picker. */
data class PersonOption(val id: String, val name: String, val onJob: Boolean = false)

data class RoomOption(val id: String, val name: String)

/** A job photo that can go on a task; [path] is "" when the photo is not on this phone yet. */
data class PhotoOption(val id: String, val path: String)

/** One floor of the job's house with its placed rooms, for pinning a task on the plan (AC-F35). */
data class PinFloor(val id: String, val name: String, val rooms: List<PlanRoom>)

enum class TaskFormSheet { ASSIGNEE, ROOM, PHOTO }

/** FL-Tasks form: title, assignee, due, room, photo, note, punch (AC-F34, rule 15: full screen with Close and Save). */
@Immutable
data class TaskFormUiState(
    val isNew: Boolean = true,
    val loading: Boolean = true,
    /** The task is gone, or this person may not change tasks on this job (AC-F3). */
    val failed: Boolean = false,
    val draft: TaskFormDraft = TaskFormDraft(),
    val initial: TaskFormDraft = TaskFormDraft(),
    val people: List<PersonOption> = emptyList(),
    val rooms: List<RoomOption> = emptyList(),
    val photos: List<PhotoOption> = emptyList(),
    val sheet: TaskFormSheet? = null,
    val confirmDelete: Boolean = false,
    val working: Boolean = false,
    val done: Boolean = false,
    /** Floors of the job's house that have rooms; empty hides "Pin on plan" (nothing to pin on yet). */
    val plan: List<PinFloor> = emptyList(),
    /** The plan picker is open over the form. */
    val pinning: Boolean = false,
    /** Floor shown in the picker. */
    val pinFloorId: String? = null,
) {
    val dirty: Boolean get() = draft != initial
    /** The name error shows once someone has typed and cleared it (rule 21). */
    val titleError: Boolean get() = !draft.titleOk && draft.title != initial.title
    val assigneeName: String? get() = draft.assigneeId?.let { id -> people.firstOrNull { it.id == id }?.name }
    val roomName: String? get() = draft.roomId?.let { id -> rooms.firstOrNull { it.id == id }?.name }
    val pinFloor: PinFloor? get() = plan.firstOrNull { it.id == pinFloorId } ?: plan.firstOrNull()
    /** The floor the pin is on and the room it falls in, for the "Pin on plan" field; null when not pinned. */
    val pinPlace: Pair<String, String?>? get() = draft.planPin?.let { pin ->
        val floor = plan.firstOrNull { it.id == pin.floorId } ?: return@let null
        floor.name to RoomHitTester.roomAt(floor.rooms, pin.xIn.toFloat(), pin.yIn.toFloat())?.name?.takeIf { it.isNotBlank() }
    }
    val photo: PhotoOption? get() = draft.photoId?.let { id -> photos.firstOrNull { it.id == id } ?: PhotoOption(id, "") }
}

sealed interface TaskFormEvent {
    data class Title(val text: String) : TaskFormEvent
    data class Assignee(val id: String?) : TaskFormEvent
    data class Due(val iso: String?) : TaskFormEvent
    data class Room(val id: String?) : TaskFormEvent
    data class Photo(val id: String?) : TaskFormEvent
    data class Note(val text: String) : TaskFormEvent
    data class Punch(val on: Boolean) : TaskFormEvent
    data class OpenSheet(val sheet: TaskFormSheet) : TaskFormEvent
    data object CloseSheet : TaskFormEvent
    /** Opens the plan picker (AC-F35). */
    data object OpenPin : TaskFormEvent
    data object ClosePin : TaskFormEvent
    data class PinFloor(val floorId: String) : TaskFormEvent
    /** A tap on the plan in the picker, in plan inches. */
    data class PlacePin(val xIn: Int, val yIn: Int) : TaskFormEvent
    data object RemovePin : TaskFormEvent
    data object Save : TaskFormEvent
    data object AskDelete : TaskFormEvent
    data object DismissDelete : TaskFormEvent
    data object Delete : TaskFormEvent
    data object Retry : TaskFormEvent
    /** Close (X); the route pops. */
    data object Close : TaskFormEvent
}
