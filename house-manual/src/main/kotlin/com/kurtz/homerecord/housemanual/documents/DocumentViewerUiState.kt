package com.kurtz.homerecord.housemanual.documents

import com.kurtz.homerecord.coreui.pages.PageSource
import androidx.compose.ui.graphics.ImageBitmap

data class DocumentViewerUiState(
    val loading: Boolean = true,
    val name: String = "",
    val path: String = "",
    val mime: String = "",
    /** True when the record or its file is gone. */
    val missing: Boolean = false,
    /** True when the file is only in the cloud and this phone has no connection to fetch it (T075a2). */
    val offline: Boolean = false,
    val pages: PageSource? = null,
)

sealed interface DocumentViewerEvent {
    data object Back : DocumentViewerEvent
    data object Share : DocumentViewerEvent
    data object Retry : DocumentViewerEvent
}
