package com.kurtz.homerecord.housemanual.documents

import androidx.compose.ui.graphics.ImageBitmap

/** One document's pages, rendered on demand so a 40-page manual never sits in memory at once. */
interface PageSource {
    val pageCount: Int
    suspend fun render(index: Int, widthPx: Int): ImageBitmap?
    /** A page already in memory, shown at once while [render] runs; previews use it. */
    fun peek(index: Int): ImageBitmap? = null
    fun close() = Unit
}

data class DocumentViewerUiState(
    val loading: Boolean = true,
    val name: String = "",
    val path: String = "",
    val mime: String = "",
    /** True when the record or its file is gone. */
    val missing: Boolean = false,
    val pages: PageSource? = null,
)

sealed interface DocumentViewerEvent {
    data object Back : DocumentViewerEvent
    data object Share : DocumentViewerEvent
}
