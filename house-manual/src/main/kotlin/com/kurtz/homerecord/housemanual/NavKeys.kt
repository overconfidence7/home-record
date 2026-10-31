package com.kurtz.homerecord.housemanual

import androidx.navigation3.runtime.NavKey
import com.kurtz.homerecord.core.billing.LimitedFeature
import kotlinx.serialization.Serializable

@Serializable data object HomeKey : NavKey

@Serializable data object HouseKey : NavKey

@Serializable data object FloorsKey : NavKey

@Serializable data class PlanKey(val floorId: String? = null, val roomId: String? = null, val editShape: Boolean = false) : NavKey

@Serializable data class RoomKey(val roomId: String) : NavKey

@Serializable data object ItemsKey : NavKey

@Serializable data class ItemDetailKey(val itemId: String) : NavKey
@Serializable data class ItemEditKey(val itemId: String? = null, val roomId: String? = null, val subtypeId: String? = null, val scanPlate: Boolean = false) : NavKey

@Serializable data object ContactsKey : NavKey
@Serializable data object DocumentsKey : NavKey
@Serializable data class DocumentViewerKey(val documentId: String) : NavKey
@Serializable data class PaywallKey(val feature: LimitedFeature? = null) : NavKey

/** Full-screen editors hide the tab bar so only Close/Save or back leaves them (ui-standards rule 3). */
fun isFullScreen(key: NavKey): Boolean = key is ItemEditKey || key is PaywallKey
