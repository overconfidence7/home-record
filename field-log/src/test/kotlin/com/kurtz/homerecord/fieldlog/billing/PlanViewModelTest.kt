package com.kurtz.homerecord.fieldlog.billing

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.billing.CompanyBilling
import com.kurtz.homerecord.core.billing.CompanyPlanOffer
import com.kurtz.homerecord.core.billing.FieldLogPlan
import com.kurtz.homerecord.core.billing.FieldLogPlanKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** AC-F56: FL-Paywall buys Solo or Crew for the company and re-reads `/me` once the store says yes. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeCompanyBilling(
        var offers: Result<List<CompanyPlanOffer>> = Result.success(
            listOf(
                CompanyPlanOffer("fieldlog_solo_monthly", FieldLogPlanKind.Solo, "$39.00"),
                CompanyPlanOffer("fieldlog_crew_monthly", FieldLogPlanKind.Crew, "$79.00"),
            ),
        ),
        var purchaseResult: Result<FieldLogPlanKind> = Result.success(FieldLogPlanKind.Crew),
        var restoreResult: Result<FieldLogPlanKind> = Result.failure(IllegalStateException("Nothing to restore")),
    ) : CompanyBilling {
        val calls = mutableListOf<String>()
        override suspend fun plans(companyId: String) = offers.also { calls += "plans:$companyId" }
        override suspend fun purchase(companyId: String, planId: String) = purchaseResult.also { calls += "buy:$companyId:$planId" }
        override suspend fun restore(companyId: String) = restoreResult.also { calls += "restore:$companyId" }
    }

    private val expired = FieldLogPlan(FieldLogPlanKind.Expired, null, 0)
    private var refreshes = 0
    private fun vm(billing: CompanyBilling, plan: FieldLogPlan = expired, lapsed: Boolean = true) =
        PlanViewModel(billing, "co-1", plan, lapsed) { refreshes++ }

    // AC-F56
    @Test fun loadsSoloThenCrewWithStorePrices() {
        val vm = vm(FakeCompanyBilling(), FieldLogPlan(FieldLogPlanKind.Trial, 9, 10), lapsed = false)
        val s = vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.offers.map { it.price }).containsExactly("$39.00", "$79.00").inOrder()
        assertThat(s.plan.trialDaysLeft).isEqualTo(9)
        assertThat(s.lapsed).isFalse()
    }

    // AC-F56
    @Test fun noPricesShowsRetryAndRetryRecovers() {
        val billing = FakeCompanyBilling(offers = Result.failure(IllegalStateException("No billing key")))
        val vm = vm(billing)
        assertThat(vm.state.value.loadFailed).isTrue()
        billing.offers = FakeCompanyBilling().offers
        vm.onEvent(PlanEvent.Retry)
        assertThat(vm.state.value.loadFailed).isFalse()
        assertThat(vm.state.value.offers).hasSize(2)
    }

    // AC-F56: bought as the company, then /me is read again so the webhook's grant takes over.
    @Test fun purchaseBuysForTheCompanyAndRefreshesMembership() {
        val billing = FakeCompanyBilling()
        val vm = vm(billing)
        vm.onEvent(PlanEvent.Buy("fieldlog_crew_monthly"))
        assertThat(billing.calls).contains("buy:co-1:fieldlog_crew_monthly")
        assertThat(vm.state.value.bought).isEqualTo(FieldLogPlanKind.Crew)
        assertThat(vm.state.value.busy).isFalse()
        assertThat(refreshes).isEqualTo(1)
        vm.onPlan(FieldLogPlan(FieldLogPlanKind.Crew, null, 10), lapsed = false)
        assertThat(vm.state.value.plan.kind).isEqualTo(FieldLogPlanKind.Crew)
        assertThat(vm.state.value.lapsed).isFalse()
    }

    // AC-F56
    @Test fun failedPurchaseAndEmptyRestoreSaySoWithoutRefreshing() {
        val billing = FakeCompanyBilling(purchaseResult = Result.failure(IllegalStateException("cancelled")))
        val vm = vm(billing)
        vm.onEvent(PlanEvent.Buy("fieldlog_solo_monthly"))
        assertThat(vm.state.value.actionFailed).isEqualTo(PlanFailure.Purchase)
        vm.onEvent(PlanEvent.Restore)
        assertThat(vm.state.value.actionFailed).isEqualTo(PlanFailure.NothingToRestore)
        assertThat(vm.state.value.bought).isNull()
        assertThat(refreshes).isEqualTo(0)
    }

    // AC-F56
    @Test fun restoreFindsTheCompanyPlan() {
        val vm = vm(FakeCompanyBilling(restoreResult = Result.success(FieldLogPlanKind.Solo)))
        vm.onEvent(PlanEvent.Restore)
        assertThat(vm.state.value.bought).isEqualTo(FieldLogPlanKind.Solo)
        assertThat(refreshes).isEqualTo(1)
    }
}
