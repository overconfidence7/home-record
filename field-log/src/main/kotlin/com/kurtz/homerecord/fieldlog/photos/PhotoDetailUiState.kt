package com.kurtz.homerecord.fieldlog.photos

data class PhotoRoomOption(val id: String, val name: String)

/** One photo opened full screen: its caption and tags are editable, share and delete sit in the bar (AC-F25). */
data class PhotoDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val imagePath: String = "",
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
}
