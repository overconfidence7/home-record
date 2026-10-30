package com.kurtz.homerecord.housemanual.items

import androidx.navigation3.runtime.NavKey
import com.kurtz.homerecord.core.billing.Entitlements
import com.kurtz.homerecord.core.billing.LimitGate
import com.kurtz.homerecord.core.billing.LimitedFeature
import com.kurtz.homerecord.housemanual.ItemEditKey
import com.kurtz.homerecord.housemanual.PaywallKey

/** Where "Add item" leads: the form, or HM-Paywall when the free tier already holds 15 items (AC-H18). */
class AddItemGate(private val count: suspend (homeId: String) -> Int, private val entitlements: Entitlements) {
    suspend fun destination(homeId: String, roomId: String? = null, subtypeId: String? = null, scanPlate: Boolean = false): NavKey =
        if (LimitGate.blocked(entitlements, LimitedFeature.SixteenthItem, count(homeId))) PaywallKey(LimitedFeature.SixteenthItem)
        else ItemEditKey(roomId = roomId, subtypeId = subtypeId, scanPlate = scanPlate)
}
