package com.kurtz.homerecord.fieldlog.settings

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.photo.FileStorage
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.core.units.UnitsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

class FakeUnitsStore(start: UnitSystem = UnitSystem.IMPERIAL) : UnitsStore {
    private val state = MutableStateFlow(start)
    override val units: StateFlow<UnitSystem> = state
    override fun set(system: UnitSystem) { state.value = system }
}

// AC-F20: Settings lists what job files hold on this phone and "Free up" drops the cached copies, then counts again.
// AC-C4: the units choice is made here.
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private var stored = FileStorage(offlineBytes = 2_400_000, cachedBytes = 318_000)
    private var freed = 0
    private var notificationsOn = true
    private val units = FakeUnitsStore()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SettingsViewModel(
        SettingsDeps(
            units,
            notificationsEnabled = { notificationsOn },
            storage = { stored },
            freeUp = { freed++; stored = stored.copy(cachedBytes = 0) },
            io = dispatcher,
        ),
    )

    // AC-F20
    @Test fun countsStorageWhenShown() {
        val vm = vm()
        assertThat(vm.state.value.storage).isNull()
        vm.refresh()
        assertThat(vm.state.value.storage).isEqualTo(FileStorage(2_400_000, 318_000))
    }

    // AC-F20
    @Test fun freeUpDropsCachedFilesAndRecounts() {
        val vm = vm()
        vm.refresh()
        vm.freeUp()
        assertThat(freed).isEqualTo(1)
        assertThat(vm.state.value.storage).isEqualTo(FileStorage(2_400_000, 0))
    }

    // AC-C4
    @Test fun pickingMetricStoresIt() {
        val vm = vm()
        assertThat(vm.state.value.units).isEqualTo(UnitSystem.IMPERIAL)
        vm.setUnits(UnitSystem.METRIC)
        assertThat(units.units.value).isEqualTo(UnitSystem.METRIC)
        assertThat(vm.state.value.units).isEqualTo(UnitSystem.METRIC)
    }

    // Coming back from phone settings reads the notification switch again.
    @Test fun refreshReadsTheNotificationSwitch() {
        val vm = vm()
        assertThat(vm.state.value.notificationsOn).isTrue()
        notificationsOn = false
        vm.refresh()
        assertThat(vm.state.value.notificationsOn).isFalse()
    }
}
