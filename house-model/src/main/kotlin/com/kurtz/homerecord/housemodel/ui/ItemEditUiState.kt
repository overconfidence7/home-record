package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.housemodel.RoomChoice
import com.kurtz.homerecord.core.media.CaptureTarget
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.housemodel.items.ItemField
import com.kurtz.homerecord.housemodel.items.PhotoKind

/** Which picker sheet the item form has open. */
enum class ItemPicker { SUBTYPE, ROOM }

/** One request for the route to open the camera ([target] set) or the gallery; answered with PhotoCaptured or PhotoPicked. */
data class PhotoLaunch(val kind: PhotoKind, val target: CaptureTarget?)

data class ItemEditUiState(
    val loading: Boolean = true,
    val draft: ItemDraft = ItemDraft(),
    val rooms: List<RoomChoice> = emptyList(),
    val dirty: Boolean = false,
    val showErrors: Boolean = false,
    val picker: ItemPicker? = null,
    /** Photo kind whose "take or choose" sheet is open. */
    val photoSheetFor: PhotoKind? = null,
    val photoLaunch: PhotoLaunch? = null,
    val saving: Boolean = false,
    /** Set once the item is stored; the route closes the form. */
    val savedItemId: String? = null,
    val missing: Boolean = false,
) {
    val isNew: Boolean get() = draft.id == null
    val errors: Set<ItemField> get() = if (showErrors) draft.errors() else emptySet()
}

sealed interface ItemEditEvent {
    data class Change(val draft: ItemDraft) : ItemEditEvent
    data class PickSubtype(val subtypeId: String) : ItemEditEvent
    data class PickRoom(val roomId: String) : ItemEditEvent
    data class OpenPicker(val picker: ItemPicker) : ItemEditEvent
    data object ClosePicker : ItemEditEvent
    data class OpenPhotoSheet(val kind: PhotoKind) : ItemEditEvent
    data object ClosePhotoSheet : ItemEditEvent
    data class TakePhoto(val kind: PhotoKind) : ItemEditEvent
    data class ChoosePhoto(val kind: PhotoKind) : ItemEditEvent
    data object PhotoLaunchHandled : ItemEditEvent
    data class PhotoCaptured(val kind: PhotoKind, val path: String) : ItemEditEvent
    data class PhotoPicked(val kind: PhotoKind, val uri: String) : ItemEditEvent
    data class RemovePhoto(val path: String) : ItemEditEvent
    data object Save : ItemEditEvent
}
