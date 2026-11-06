package com.kurtz.homerecord.housemanual

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffGroups
import com.kurtz.homerecord.housemanual.emergency.EmergencyViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

// AC-C44: HM-Emergency's ViewModel shows the repository's grouped shutoffs and leaves loading once they arrive.
@OptIn(ExperimentalCoroutinesApi::class)
class EmergencyViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun showsTheSectionsWithTheMainFirst() {
        val source = MutableStateFlow(
            ShutoffGroups.sections(
                listOf(
                    Shutoff("k", "Kitchen sink cold", "fixture_shutoff", "Kitchen", "Left valve.", null, 1L),
                    Shutoff("w", "Water main shutoff", "water_main", "Utility room", "Quarter turn.", null, 2L),
                ),
            ),
        )
        val vm = EmergencyViewModel(source)
        vm.state.launchIn(vm.viewModelScope)
        assertThat(vm.state.value.loading).isFalse()
        assertThat(vm.state.value.count).isEqualTo(2)
        assertThat(vm.state.value.sections.single().shutoffs.first().itemId).isEqualTo("w")
        source.value = emptyList()
        assertThat(vm.state.value.sections).isEmpty()
        vm.viewModelScope.cancel()
    }
}
