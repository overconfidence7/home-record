package com.kurtz.homerecord.fieldlog.more

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.units.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

// AC-F4: More names who is signed in and reads the live unsent count the sign-out warning uses.
class MoreViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val unsynced = MutableStateFlow(0)
    private val units = MutableStateFlow(UnitSystem.IMPERIAL)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = MoreViewModel(
        MoreDeps("Kurtz Electric", CompanyRole.Owner, unsynced, units, userEmail = "nate@kurtzelectric.com", versionName = "0.1.0"),
    )

    // AC-F4
    @Test fun namesTheAccountAndVersion() {
        val state = vm().state.value
        assertThat(state.companyName).isEqualTo("Kurtz Electric")
        assertThat(state.userEmail).isEqualTo("nate@kurtzelectric.com")
        assertThat(state.versionName).isEqualTo("0.1.0")
    }

    // AC-F4: the sign-out warning reads the live unsent count.
    @Test fun unsentCountFollowsTheOutbox() {
        val vm = vm()
        unsynced.value = 3
        assertThat(vm.state.value.unsynced).isEqualTo(3)
    }

    // AC-C4: the Settings row shows the units in force
    @Test fun settingsRowFollowsTheUnits() {
        val vm = vm()
        units.value = UnitSystem.METRIC
        assertThat(vm.state.value.units).isEqualTo(UnitSystem.METRIC)
    }
}
