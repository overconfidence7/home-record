package com.kurtz.homerecord.housemanual.billing

import android.app.Application
import android.content.Context
import com.kurtz.homerecord.core.billing.ProBilling
import com.kurtz.homerecord.core.billing.ProPlan
import com.kurtz.homerecord.core.billing.ProPlanKind
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException

/** RevenueCat behind [ProBilling] (docs/billing.md): entitlement `pro`, offering `default`. */
class RevenueCatBilling(app: Application, apiKey: String, private val activity: () -> Context?) : ProBilling {
    private val state = MutableStateFlow(false)
    override val isPro: StateFlow<Boolean> = state
    private var packages = emptyMap<String, Package>()

    init {
        Purchases.configure(PurchasesConfiguration.Builder(app, apiKey).build())
        Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { apply(it) }
    }

    private fun apply(info: CustomerInfo) {
        state.value = info.entitlements[ENTITLEMENT]?.isActive == true
    }

    /** Reads the current entitlement; called once at start so a returning buyer is Pro offline-first from RevenueCat's cache. */
    suspend fun refresh() {
        try { apply(Purchases.sharedInstance.awaitCustomerInfo()) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* stays as it was */ }
    }

    override suspend fun plans(): Result<List<ProPlan>> = runCatching {
        val offering = Purchases.sharedInstance.awaitOfferings().current ?: error("No current offering")
        val found = offering.availablePackages.mapNotNull { pkg ->
            val kind = when (pkg.packageType) {
                PackageType.ANNUAL -> ProPlanKind.Annual
                PackageType.LIFETIME -> ProPlanKind.Lifetime
                else -> return@mapNotNull null
            }
            pkg to ProPlan(pkg.identifier, kind, pkg.product.price.formatted)
        }
        packages = found.associate { it.second.id to it.first }
        found.map { it.second }.sortedBy { it.kind }.also { check(it.isNotEmpty()) { "No plans" } }
    }.rethrowCancellation()

    override suspend fun purchase(planId: String): Result<Unit> = runCatching {
        val pkg = packages[planId] ?: error("Unknown plan")
        val host = activity() as? android.app.Activity ?: error("No activity")
        apply(Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(host, pkg).build()).customerInfo)
    }.rethrowCancellation()

    override suspend fun restore(): Result<Unit> = runCatching {
        val info = Purchases.sharedInstance.awaitRestore()
        apply(info)
        check(state.value) { "Nothing to restore" }
    }.rethrowCancellation()

    private fun <T> Result<T>.rethrowCancellation(): Result<T> {
        exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return this
    }

    private companion object { const val ENTITLEMENT = "pro" }
}
