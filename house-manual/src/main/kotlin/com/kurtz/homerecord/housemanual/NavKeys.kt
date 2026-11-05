package com.kurtz.homerecord.housemanual

import androidx.navigation3.runtime.NavKey
import com.kurtz.homerecord.core.billing.LimitedFeature
import kotlinx.serialization.Serializable

@Serializable data object HomeKey : NavKey

@Serializable data object HouseKey : NavKey

@Serializable data object FloorsKey : NavKey

@Serializable data class PlanKey(val floorId: String? = null, val roomId: String? = null, val editShape: Boolean = false, val overlayId: String? = null) : NavKey

@Serializable data class RoomKey(val roomId: String) : NavKey

/** HM-3D opening on [floorId], the floor the House tab shows (AC-C20), standing inside [roomId] when given (AC-C22). */
@Serializable data class House3DKey(val floorId: String? = null, val roomId: String? = null) : NavKey

/** HM-WallOutline for the new photo at [photoPath] of [wallId] in [roomId] at [stage] (a `WallStage` name, AC-C18). */
@Serializable data class WallOutlineKey(val roomId: String, val wallId: String, val stage: String, val photoPath: String) : NavKey
/** HM-Wall: draw and edit overlays on one wall (AC-C25..C28). */
@Serializable data class RoomWallKey(val roomId: String, val wallId: String, val stage: String, val overlayId: String? = null) : NavKey

@Serializable data object ItemsKey : NavKey

@Serializable data class ItemDetailKey(val itemId: String) : NavKey
/** Place on plan for one item (AC-C30 position). */
@Serializable data class ItemPlaceKey(val itemId: String) : NavKey
@Serializable data class ItemEditKey(val itemId: String? = null, val roomId: String? = null, val subtypeId: String? = null, val scanPlate: Boolean = false) : NavKey

/** HM-Panel for the Breaker panel or Sub-panel item [itemId] (AC-C40). */
@Serializable data class PanelKey(val itemId: String, val highlightSlot: Int? = null) : NavKey

/** More → Breaker panels when the home has none or several (AC-C40). */
@Serializable data object PanelsKey : NavKey

@Serializable data object ContactsKey : NavKey
@Serializable data object DocumentsKey : NavKey
@Serializable data class DocumentViewerKey(val documentId: String) : NavKey
@Serializable data class PaywallKey(val feature: LimitedFeature? = null) : NavKey

/** Full-screen editors hide the tab bar so only Close/Save or back leaves them (ui-standards rule 3). */
fun isFullScreen(key: NavKey): Boolean = key is ItemEditKey || key is PaywallKey || key is WallOutlineKey || key is RoomWallKey
