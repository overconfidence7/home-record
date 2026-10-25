package com.kurtz.homerecord.fieldlog.timesheets

import com.kurtz.homerecord.core.model.ShiftFlag
import com.kurtz.homerecord.core.punch.PunchEdit
import com.kurtz.homerecord.fieldlog.clock.WeekSheet
import java.time.LocalDate

/** One punch in a person's week; [edits] is the audit trail shown on the punch (AC-F14). */
data class PunchRow(
    val id: String,
    val kind: String,
    val at: Long,
    val jobId: String?,
    val jobName: String?,
    val farFromJob: Boolean,
    val edits: List<PunchEdit> = emptyList(),
) {
    val edited: Boolean get() = edits.isNotEmpty()
}

data class PersonWeek(
    val memberId: String,
    val name: String,
    val sheet: WeekSheet,
    val flags: Set<ShiftFlag>,
    val approved: Boolean,
    val punches: List<PunchRow>,
)

data class JobChoice(val id: String, val name: String)

/** The edit-a-punch sheet: time and job may change, and a reason is required (AC-F14). */
data class PunchEditSheet(
    val punchId: String,
    val personName: String,
    val kind: String,
    val dateLabel: String,
    val timeText: String,
    val jobId: String?,
    val jobs: List<JobChoice>,
    val reason: String = "",
    val error: EditError? = null,
    val audit: List<PunchEdit> = emptyList(),
) {
    val canSave: Boolean get() = reason.isNotBlank() && TimeText.parse(timeText) != null
}

enum class EditError { ReasonRequired, BadTime, Locked }

data class TimesheetsUiState(
    val loading: Boolean = true,
    val weekStart: LocalDate = LocalDate.of(2026, 1, 5),
    val people: List<PersonWeek> = emptyList(),
    val expandedId: String? = null,
    val edit: PunchEditSheet? = null,
    val confirmApproveAll: Boolean = false,
    val isCurrentWeek: Boolean = true,
) {
    val approvable: Int get() = people.count { !it.approved && it.punches.isNotEmpty() }
}

sealed interface TimesheetsEvent {
    data object PreviousWeek : TimesheetsEvent
    data object NextWeek : TimesheetsEvent
    data class Toggle(val memberId: String) : TimesheetsEvent
    data class Approve(val memberId: String) : TimesheetsEvent
    data object AskApproveAll : TimesheetsEvent
    data object ApproveAll : TimesheetsEvent
    data object CancelApproveAll : TimesheetsEvent
    data class EditPunch(val punchId: String) : TimesheetsEvent
    data class EditTime(val text: String) : TimesheetsEvent
    data class EditJob(val jobId: String?) : TimesheetsEvent
    data class EditReason(val text: String) : TimesheetsEvent
    data object SaveEdit : TimesheetsEvent
    data object CancelEdit : TimesheetsEvent
}
