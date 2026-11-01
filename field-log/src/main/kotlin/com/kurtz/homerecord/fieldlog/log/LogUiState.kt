package com.kurtz.homerecord.fieldlog.log

import androidx.compose.runtime.Immutable
import com.kurtz.homerecord.core.log.WeatherStatus
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.model.LogDelay
import com.kurtz.homerecord.core.model.LogLineKind
import com.kurtz.homerecord.core.model.LogWeather
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** One person on the day's crew. [minutes] already less breaks; [onSiteNow] while their shift runs. */
data class LogCrewRow(val memberId: String, val name: String, val minutes: Int, val onSiteNow: Boolean)

/** A photo of the day as a thumbnail; [path] is "" while it is still on another phone. */
data class LogPhotoThumb(val id: String, val path: String)

data class LogLine(val id: String, val headline: String, val supporting: String? = null)

/** The typed sections, edited one at a time in a sheet with a single text field (ui-standards rule 15). */
enum class LogTextSection { WORK_DONE, VISITORS, SAFETY, CLIENT_NOTES }

/** The open sheet or dialog. Kept in the saved state so a rotation or process death keeps the typing (rule 33). */
@Serializable
sealed interface LogEditor {
    @Serializable data class Text(val section: LogTextSection, val text: String) : LogEditor
    @Serializable data class Hours(val memberId: String, val name: String, val text: String) : LogEditor
    @Serializable data class Delay(val reason: DelayReason? = null, val note: String = "") : LogEditor
    /** "Remove delay?" for the delay with [key] ([LogDelay.key]). */
    @Serializable data class RemoveDelay(val key: String) : LogEditor
    /** "Leave out of the log?" for one photo, task, change order or material line; [label] names it (null for a photo). */
    @Serializable data class Drop(val kind: LogLineKind, val id: String, val label: String? = null) : LogEditor
    @Serializable data object PickDay : LogEditor
    /** The Send sheet; [recipients] are the job's client and office emails when it opened (AC-F33). */
    @Serializable data class Send(val recipients: List<String>) : LogEditor
    /** "Nothing logged for this day yet" before the Send sheet, so an empty page never goes to a client unasked. */
    @Serializable data object SendEmpty : LogEditor
}

/**
 * FL-Log, a job's daily log for one day (SPEC FL-Log; AC-F30, AC-F31, AC-F32). [canEdit] is false for Crew and Sub
 * and for a Lead on a job they are not on: they read the saved log but never start or change one (AC-F3).
 */
@Immutable
data class LogUiState(
    val day: LocalDate,
    val today: LocalDate,
    val loading: Boolean = true,
    /** The job is gone or the draft could not be built. */
    val failed: Boolean = false,
    val canEdit: Boolean = false,
    /** True once a log exists for the day; Crew see an empty state until the lead starts one. */
    val exists: Boolean = false,
    val sentAt: Long? = null,
    /** Who the last send went to by email; empty when it went through the share sheet (AC-F33). */
    val sentTo: List<String> = emptyList(),
    /** The log was edited or re-assembled after its last send, so the client's copy is out of date (R09d). */
    val changedSinceSent: Boolean = false,
    /** The PDF is being made for a send. */
    val sending: Boolean = false,
    /** The last send could not make the PDF or found no app to take it. */
    val sendFailed: Boolean = false,
    val crew: List<LogCrewRow> = emptyList(),
    val workDone: List<String> = emptyList(),
    val photos: List<LogPhotoThumb> = emptyList(),
    val tasksDone: List<LogLine> = emptyList(),
    val changeOrders: List<LogLine> = emptyList(),
    val materials: List<LogLine> = emptyList(),
    val weather: LogWeather? = null,
    val weatherStatus: WeatherStatus? = null,
    /** The weather was typed in by hand, so it is no longer looked up. */
    val weatherEdited: Boolean = false,
    /** How many lines of each kind were left out of this day's log; each kind with any offers "Put back". */
    val dropped: Map<LogLineKind, Int> = emptyMap(),
    val delays: List<LogDelay> = emptyList(),
    val visitors: String = "",
    val safety: String = "",
    val clientNotes: String = "",
    val editor: LogEditor? = null,
    /** The hours typed in the crew sheet do not read as a time. */
    val hoursInvalid: Boolean = false,
) {
    val totalMinutes: Int get() = crew.sumOf { it.minutes }
    val canGoForward: Boolean get() = day < today

    /** Owner, Office and the job's Lead send a log once it exists; Crew and Sub only read it (AC-F3, AC-F33). */
    val canSend: Boolean get() = canEdit && exists

    /**
     * Nothing a client would read is on the day yet: no crew time, work done, photos, tasks, change orders, materials,
     * delays, visitors, safety or client notes. Weather alone does not count; the PDF would be the header and nothing else.
     */
    val nothingLogged: Boolean get() =
        crew.isEmpty() && workDone.isEmpty() && photos.isEmpty() && tasksDone.isEmpty() && changeOrders.isEmpty() &&
            materials.isEmpty() && delays.isEmpty() && visitors.isBlank() && safety.isBlank() && clientNotes.isBlank()
}

sealed interface LogEvent {
    data object PreviousDay : LogEvent
    data object NextDay : LogEvent
    data object OpenDayPicker : LogEvent
    data class PickDay(val day: LocalDate) : LogEvent
    data class EditText(val section: LogTextSection) : LogEvent
    data class EditHours(val memberId: String) : LogEvent
    data object AddDelay : LogEvent
    data class AskRemoveDelay(val key: String) : LogEvent
    data class AskDrop(val kind: LogLineKind, val id: String) : LogEvent
    data class PutBack(val kind: LogLineKind) : LogEvent
    /** Opens the full-screen weather form for the shown day; the route handles it (rule 15). */
    data object EditWeather : LogEvent
    data class EditorText(val text: String) : LogEvent
    data class EditorReason(val reason: DelayReason) : LogEvent
    data object SaveEditor : LogEvent
    data object DismissEditor : LogEvent
    data object Retry : LogEvent
    /** The screen came back to the front: the calendar day may have turned while it was away. */
    data object CheckToday : LogEvent
    /** Opens the log's PDF in the in-app viewer; the route handles it. */
    data object Preview : LogEvent
    data object OpenSend : LogEvent
    /** From the empty-log warning: go on to the Send sheet. */
    data object SendAnyway : LogEvent
    /** Email the PDF to the job's client and office addresses. */
    data object SendEmail : LogEvent
    /** Hand the PDF to the share sheet (text, another email account, a drive). */
    data object SendShare : LogEvent
}
