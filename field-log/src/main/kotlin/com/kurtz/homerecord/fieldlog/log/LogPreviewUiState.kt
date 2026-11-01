package com.kurtz.homerecord.fieldlog.log

import androidx.compose.runtime.Immutable
import com.kurtz.homerecord.coreui.pages.PageSource
import java.time.LocalDate

/** The daily log's PDF as the client gets it, in the in-app viewer (SPEC FL-Log Preview PDF, AC-F33). */
@Immutable
data class LogPreviewUiState(
    val day: LocalDate,
    val loading: Boolean = true,
    /** No saved log for the day, or the PDF could not be made or opened. */
    val failed: Boolean = false,
    val pages: PageSource? = null,
)

sealed interface LogPreviewEvent {
    data object Back : LogPreviewEvent
    data object Retry : LogPreviewEvent
}
