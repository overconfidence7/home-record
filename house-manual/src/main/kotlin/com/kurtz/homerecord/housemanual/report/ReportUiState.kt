package com.kurtz.homerecord.housemanual.report

/** What the report will hold, counted from the same data the PDF reads (AC-H13). */
data class ReportContents(val floors: Int, val rooms: Int, val items: Int, val shutoffs: Int, val panels: Int)

/** HM-Report: what goes in, and the state of making the PDF. [uri] is a made report waiting for the share sheet. */
data class ReportUiState(
    val loading: Boolean = true,
    val homeName: String = "",
    val contents: ReportContents? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val uri: String? = null,
)

sealed interface ReportEvent {
    data object Back : ReportEvent
    /** Make the PDF and open the share sheet. */
    data object Make : ReportEvent
    data object ShareHandled : ReportEvent
}
