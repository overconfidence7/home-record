package com.kurtz.homerecord.fieldlog.photos

import java.time.LocalDate

/** One photo in the gallery grid. [imagePath] is the thumbnail when there is one, "" while it downloads (AC-F24, AC-F55). */
data class PhotoTile(
    val id: String,
    val imagePath: String,
    val roomName: String? = null,
    val phase: String? = null,
    val caption: String? = null,
    val annotated: Boolean = false,
)

/** A single photo, or a before/after pair shown side by side (AC-F25). */
sealed interface PhotoCell {
    data class Single(val tile: PhotoTile) : PhotoCell
    data class Pair(val before: PhotoTile, val after: PhotoTile) : PhotoCell
}

data class PhotoDayUi(val day: LocalDate, val cells: List<PhotoCell>)

data class PhotosUiState(
    val loading: Boolean = true,
    val days: List<PhotoDayUi> = emptyList(),
    val count: Int = 0,
    /** The job gallery's share link (AC-F28): Owner and Office, once the job has photos. */
    val link: com.kurtz.homerecord.fieldlog.share.ShareLinkUi = com.kurtz.homerecord.fieldlog.share.ShareLinkUi(),
)

sealed interface PhotosEvent {
    data object Capture : PhotosEvent
    data class Open(val photoId: String) : PhotosEvent
    data class Link(val event: com.kurtz.homerecord.fieldlog.share.ShareLinkEvent) : PhotosEvent
}
