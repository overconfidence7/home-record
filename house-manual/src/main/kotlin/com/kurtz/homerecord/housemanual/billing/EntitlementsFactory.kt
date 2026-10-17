package com.kurtz.homerecord.housemanual.billing

import android.content.Context
import com.kurtz.homerecord.core.billing.Entitlements
import com.kurtz.homerecord.core.billing.UnavailableEntitlements

object EntitlementsFactory {
    /** No SDK key (local builds without `revenuecat.apiKey`) means the free tier with a Retry paywall, never a crash. */
    fun create(context: Context, apiKey: String): Entitlements =
        if (apiKey.isBlank()) UnavailableEntitlements() else RevenueCatEntitlements(context.applicationContext, apiKey)
}
