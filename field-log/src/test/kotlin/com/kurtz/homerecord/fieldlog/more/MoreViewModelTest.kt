package com.kurtz.homerecord.fieldlog.more

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.photo.FileStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

// AC-F20: More lists what job files hold on this phone and "Free up" drops the cached copies, then counts again.
class MoreViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private var stored = FileStorage(offlineBytes = 2_400_000, cachedBytes = 318_000)
    private var freed = 0
    private val unsynced = MutableStateFlow(0)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = MoreViewModel(
        MoreDeps(
            "Kurtz Electric", CompanyRole.Owner, unsynced,
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
        assertThat(vm.state.value.companyName).isEqualTo("Kurtz Electric")
    }

    // AC-F20
    @Test fun freeUpDropsCachedFilesAndRecounts() {
        val vm = vm()
        vm.refresh()
        vm.freeUp()
        assertThat(freed).isEqualTo(1)
        assertThat(vm.state.value.storage).isEqualTo(FileStorage(2_400_000, 0))
    }

    // AC-F4: the sign-out warning reads the live unsent count.
    @Test fun unsentCountFollowsTheOutbox() {
        val vm = vm()
        unsynced.value = 3
        assertThat(vm.state.value.unsynced).isEqualTo(3)
    }
}
