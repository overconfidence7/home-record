package com.kurtz.homerecord.housemanual.billing

import android.app.Activity
import android.content.Context
import com.kurtz.homerecord.core.billing.Entitlements
import com.kurtz.homerecord.core.billing.Offer
import com.kurtz.homerecord.core.billing.OffersResult
import com.kurtz.homerecord.core.billing.Plan
import com.kurtz.homerecord.core.billing.PurchaseResult
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesException
import com.revenuecat.purchases.awaitCustomerInfo
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The only class that imports the RevenueCat SDK (AC-95). Never throws; offline yields [OffersResult.Unavailable] (AC-96). */
class RevenueCatEntitlements(context: Context, apiKey: String) : Entitlements {
    private val _isPro = MutableStateFlow(false)
    override val isPro: StateFlow<Boolean> = _isPro

    private val packages = mutableMapOf<Plan, Package>()
    private val configured: Boolean = runCatching {
        Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())
        Purchases.sharedInstance.updatedCustomerInfoListener =
            com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener { apply(it) }
    }.isSuccess

    init {
        if (configured) refreshCustomer()
    }

    private fun refreshCustomer() {
        Purchases.sharedInstance.getCustomerInfo(
            object : com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) = apply(customerInfo)
                override fun onError(error: PurchasesError) = Unit
            },
        )
    }

    private fun apply(info: CustomerInfo) {
        _isPro.value = info.entitlements[PRO_ENTITLEMENT]?.isActive == true
    }

    override suspend fun loadOffers(): OffersResult {
        if (!configured) return OffersResult.Unavailable
        return try {
            val available = Purchases.sharedInstance.awaitOfferings().current?.availablePackages.orEmpty()
            packages.clear()
            val offers = available.mapNotNull { pkg ->
                val plan = planFor(pkg.packageType) ?: return@mapNotNull null
                packages[plan] = pkg
                Offer(plan, pkg.product.price.formatted)
            }.sortedBy { it.plan.ordinal }
            if (offers.isEmpty()) OffersResult.Unavailable else OffersResult.Loaded(offers)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OffersResult.Unavailable
        }
    }

    /** Purchases need a foreground Activity; set by MainActivity while it is resumed. */
    @Volatile var activity: Activity? = null

    override suspend fun purchase(plan: Plan): PurchaseResult {
        val host = activity
        val pkg = packages[plan]
        if (!configured || host == null || pkg == null) return PurchaseResult.Failed
        return try {
            val result = Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(host, pkg).build())
            apply(result.customerInfo)
            if (_isPro.value) PurchaseResult.Success else PurchaseResult.Failed
        } catch (e: CancellationException) {
            throw e
        } catch (e: PurchasesException) {
            if (e.error.code == PurchasesErrorCode.PurchaseCancelledError) PurchaseResult.Cancelled else PurchaseResult.Failed
        } catch (e: Exception) {
            PurchaseResult.Failed
        }
    }

    internal companion object {
        const val PRO_ENTITLEMENT = "pro"

        /** `$rc_annual` is the Annual plan, `$rc_lifetime` the Lifetime plan; other packages are ignored. */
        fun planFor(type: PackageType): Plan? = when (type) {
            PackageType.ANNUAL -> Plan.YEARLY
            PackageType.LIFETIME -> Plan.LIFETIME
            else -> null
        }
    }
}
