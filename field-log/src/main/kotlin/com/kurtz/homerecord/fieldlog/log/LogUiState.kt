package com.kurtz.homerecord.fieldlog.log

import androidx.compose.runtime.Immutable
import com.kurtz.homerecord.core.log.WeatherStatus
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.model.LogDelay
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
    @Serializable data class RemoveDelay(val index: Int) : LogEditor
    @Serializable data object PickDay : LogEditor
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
    val crew: List<LogCrewRow> = emptyList(),
    val workDone: List<String> = emptyList(),
    val photos: List<LogPhotoThumb> = emptyList(),
    val tasksDone: List<LogLine> = emptyList(),
    val changeOrders: List<LogLine> = emptyList(),
    val materials: List<LogLine> = emptyList(),
    val weather: LogWeather? = null,
    val weatherStatus: WeatherStatus? = null,
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
}

sealed interface LogEvent {
    data object PreviousDay : LogEvent
    data object NextDay : LogEvent
    data object OpenDayPicker : LogEvent
    data class PickDay(val day: LocalDate) : LogEvent
    data class EditText(val section: LogTextSection) : LogEvent
    data class EditHours(val memberId: String) : LogEvent
    data object AddDelay : LogEvent
    data class AskRemoveDelay(val index: Int) : LogEvent
    data class EditorText(val text: String) : LogEvent
    data class EditorReason(val reason: DelayReason) : LogEvent
    data object SaveEditor : LogEvent
    data object DismissEditor : LogEvent
    data object Retry : LogEvent
}
