package com.kurtz.homerecord.fieldlog.log

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.pages.PdfViewer
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Preview PDF: the day's log exactly as it will be sent, pages with pinch zoom and a page strip (AC-F33). */
@Composable
fun LogPreviewScreen(state: LogPreviewUiState, onEvent: (LogPreviewEvent) -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(
        title = stringResource(R.string.log_preview_title),
        subtitle = state.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
        onBack = { onEvent(LogPreviewEvent.Back) },
    ) { padding ->
        Box(modifier.padding(padding).fillMaxSize().testTag("fl_log_pdf")) {
            val pages = state.pages
            when {
                state.loading -> LoadingState(Modifier.fillMaxSize())
                pages != null -> PdfViewer(pages, tagPrefix = "fl_log_pdf", modifier = Modifier.fillMaxSize())
                else -> ErrorState(stringResource(R.string.log_preview_failed), onRetry = { onEvent(LogPreviewEvent.Retry) }, Modifier.fillMaxSize())
            }
        }
    }
}
