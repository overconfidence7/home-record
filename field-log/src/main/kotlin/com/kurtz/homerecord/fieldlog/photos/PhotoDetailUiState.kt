package com.kurtz.homerecord.fieldlog.photos

data class PhotoRoomOption(val id: String, val name: String)

/** Whether the photo's bytes are here; a photo pulled from another phone downloads by its R2 key first (cr:sync-local-columns). */
enum class PhotoImage { Ready, Downloading, NoNetwork, NotUploaded, Failed }

/** One photo opened full screen: its caption and tags are editable, share and delete sit in the bar (AC-F25). */
data class PhotoDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val imagePath: String = "",
    val image: PhotoImage = PhotoImage.Ready,
    val caption: String = "",
    val rooms: List<PhotoRoomOption> = emptyList(),
    val roomId: String? = null,
    val phases: List<String> = emptyList(),
    val phase: String? = null,
    val takenAt: Long = 0,
    val annotated: Boolean = false,
    val isBefore: Boolean = false,
    val confirmDelete: Boolean = false,
    val deleted: Boolean = false,
    /** Delete is for Owner, Office and the job's Lead (AC-F3). */
    val canDelete: Boolean = true,
)

sealed interface PhotoDetailEvent {
    data class Caption(val text: String) : PhotoDetailEvent
    data object SaveCaption : PhotoDetailEvent
    data class Room(val id: String?) : PhotoDetailEvent
    data class Phase(val phase: String?) : PhotoDetailEvent
    /** Marks or unmarks the photo as a Before shot; the phase stays as it is (cr:fl-before-phase). */
    data object ToggleBefore : PhotoDetailEvent
    data object AskDelete : PhotoDetailEvent
    data object CancelDelete : PhotoDetailEvent
    data object ConfirmDelete : PhotoDetailEvent
    /** Tries the download again after no network or a failure. */
    data object RetryDownload : PhotoDetailEvent

    /** The route answers these: share the file, open mark-up, or leave (cr:fl-screen-signature). */
    data object Share : PhotoDetailEvent
    data object Annotate : PhotoDetailEvent
    data object Back : PhotoDetailEvent
}
