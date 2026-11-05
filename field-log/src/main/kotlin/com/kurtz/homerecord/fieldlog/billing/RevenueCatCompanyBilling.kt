package com.kurtz.homerecord.fieldlog.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import com.kurtz.homerecord.core.billing.CompanyBilling
import com.kurtz.homerecord.core.billing.CompanyBillingRules
import com.kurtz.homerecord.core.billing.CompanyPlanOffer
import com.kurtz.homerecord.core.billing.FieldLogPlanKind
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.awaitLogIn
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * RevenueCat behind [CompanyBilling] (docs/billing.md, AC-F56): the Field Log RevenueCat app, entitlements
 * `fieldlog_solo` / `fieldlog_crew`, the current offering's monthly packages. Every call first logs RevenueCat in as
 * `company:<id>` so the purchase belongs to the company and reaches the backend webhook under that id (AC-B50).
 */
class RevenueCatCompanyBilling(app: Application, apiKey: String, private val activity: () -> Context?) : CompanyBilling {
    private val login = Mutex()
    private var packages = emptyMap<String, Package>()

    init {
        Purchases.configure(PurchasesConfiguration.Builder(app, apiKey).build())
    }

    private suspend fun logIn(companyId: String) = login.withLock {
        val id = CompanyBillingRules.appUserId(companyId)
        if (Purchases.sharedInstance.appUserID != id) Purchases.sharedInstance.awaitLogIn(id)
    }

    private fun activePlan(info: CustomerInfo): FieldLogPlanKind? =
        CompanyBillingRules.activePlan(info.entitlements.active.keys)

    override suspend fun plans(companyId: String): Result<List<CompanyPlanOffer>> = runCatching {
        logIn(companyId)
        val offering = Purchases.sharedInstance.awaitOfferings().current ?: error("No current offering")
        val found = offering.availablePackages.mapNotNull { pkg ->
            val kind = CompanyBillingRules.planOf(pkg.identifier, pkg.product.id) ?: return@mapNotNull null
            pkg to CompanyPlanOffer(pkg.identifier, kind, pkg.product.price.formatted)
        }
        packages = found.associate { it.second.id to it.first }
        CompanyBillingRules.order(found.map { it.second }).also { check(it.isNotEmpty()) { "No plans" } }
    }.rethrowCancellation()

    override suspend fun purchase(companyId: String, planId: String): Result<FieldLogPlanKind> = runCatching {
        logIn(companyId)
        val pkg = packages[planId] ?: error("Unknown plan")
        val host = activity() as? Activity ?: error("No activity")
        val info = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(host, pkg).build()).customerInfo
        activePlan(info) ?: error("Purchase not active yet")
    }.rethrowCancellation()

    override suspend fun restore(companyId: String): Result<FieldLogPlanKind> = runCatching {
        logIn(companyId)
        activePlan(Purchases.sharedInstance.awaitRestore()) ?: error("Nothing to restore")
    }.rethrowCancellation()

    private fun <T> Result<T>.rethrowCancellation(): Result<T> {
        exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return this
    }
}
