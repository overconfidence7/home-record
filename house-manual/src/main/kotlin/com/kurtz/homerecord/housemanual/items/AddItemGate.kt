package com.kurtz.homerecord.housemanual.items

import androidx.navigation3.runtime.NavKey
import com.kurtz.homerecord.core.billing.Entitlements
import com.kurtz.homerecord.core.billing.LimitGate
import com.kurtz.homerecord.core.billing.LimitedFeature
import com.kurtz.homerecord.housemanual.ItemEditKey
import com.kurtz.homerecord.housemanual.PaywallKey
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.housemodel.panel.isPanelSubtype

/**
 * Where "Add item" leads (AC-H18): the form, or HM-Paywall when the free tier already holds 15 items, or one panel and
 * the new item is a panel. [panels] counts the home's panels, leaving out the item id it is given.
 */
class AddItemGate(
    private val count: suspend (homeId: String) -> Int,
    private val entitlements: Entitlements,
    private val panels: suspend (homeId: String, exceptItemId: String?) -> Int = { _, _ -> 0 },
) {
    suspend fun destination(homeId: String, roomId: String? = null, subtypeId: String? = null, scanPlate: Boolean = false): NavKey =
        when {
            LimitGate.blocked(entitlements, LimitedFeature.SixteenthItem, count(homeId)) -> PaywallKey(LimitedFeature.SixteenthItem)
            isPanelSubtype(subtypeId) && secondPanel(homeId, null) -> PaywallKey(LimitedFeature.SecondPanel)
            else -> ItemEditKey(roomId = roomId, subtypeId = subtypeId, scanPlate = scanPlate)
        }

    /** The item form's save check: a free home with a panel cannot make another item a panel, new or edited. */
    suspend fun saveBlocked(homeId: String, draft: ItemDraft): Boolean =
        isPanelSubtype(draft.subtypeId) && secondPanel(homeId, draft.id)

    private suspend fun secondPanel(homeId: String, exceptItemId: String?): Boolean =
        LimitGate.blocked(entitlements, LimitedFeature.SecondPanel, panels(homeId, exceptItemId))
}
