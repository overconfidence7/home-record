package com.kurtz.homerecord.housemanual

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object HomeKey : NavKey

@Serializable data object HouseKey : NavKey

@Serializable data object FloorsKey : NavKey

@Serializable data class PlanKey(val floorId: String? = null, val roomId: String? = null, val editShape: Boolean = false) : NavKey

@Serializable data class RoomKey(val roomId: String) : NavKey

@Serializable data object ItemsKey : NavKey

@Serializable data class ItemDetailKey(val itemId: String) : NavKey
@Serializable data class ItemEditKey(val itemId: String? = null, val roomId: String? = null) : NavKey

@Serializable data object ContactsKey : NavKey
@Serializable data object DocumentsKey : NavKey
@Serializable data class DocumentViewerKey(val documentId: String) : NavKey
