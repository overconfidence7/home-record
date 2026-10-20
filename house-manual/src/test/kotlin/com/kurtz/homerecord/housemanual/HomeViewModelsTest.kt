package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.housemanual.house.FloorsDialog
import com.kurtz.homerecord.housemanual.house.FloorsEvent
import com.kurtz.homerecord.housemanual.house.FloorsViewModel
import com.kurtz.homerecord.housemanual.onboarding.OnboardingEvent
import com.kurtz.homerecord.housemanual.onboarding.OnboardingViewModel
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HomeViewModelsTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: HomeRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = HomeRepository(db)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun drawPlanWithoutNameOrTypeShowsErrorsAndCreatesNothing() = runTest {
        val vm = OnboardingViewModel(repo)
        vm.onEvent(OnboardingEvent.DrawPlan)
        assertThat(vm.state.value.showErrors).isTrue()
        assertThat(repo.observeHome().first()).isNull()
    }

    @Test fun drawPlanCreatesHomeWithTemplateFloors() = runTest {
        val vm = OnboardingViewModel(repo)
        vm.onEvent(OnboardingEvent.NameChanged("Maple St"))
        vm.onEvent(OnboardingEvent.TypeChanged(HouseType.CAPE_COD))
        vm.onEvent(OnboardingEvent.FoundationChanged(Foundation.SLAB))
        vm.onEvent(OnboardingEvent.DrawPlan)
        val home = repo.observeHome().first { it != null }!!
        assertThat(home.houseType).isEqualTo("CAPE_COD")
        assertThat(repo.observeFloors(home.id).first { it.size == 2 }.map { it.name }).containsExactly("Main", "Upper").inOrder()
    }

    @Test fun skipCreatesSingleMainFloor() = runTest {
        val vm = OnboardingViewModel(repo)
        vm.onEvent(OnboardingEvent.NameChanged("Cabin"))
        vm.onEvent(OnboardingEvent.TypeChanged(HouseType.TWO_STORY))
        vm.onEvent(OnboardingEvent.Skip)
        val home = repo.observeHome().first { it != null }!!
        assertThat(repo.observeFloors(home.id).first { it.isNotEmpty() }.map { it.name }).containsExactly("Main")
    }

    @Test fun answersSurviveProcessDeathThroughSavedState() {
        val handle = androidx.lifecycle.SavedStateHandle()
        OnboardingViewModel(repo, handle).apply {
            onEvent(OnboardingEvent.NameChanged("Maple St"))
            onEvent(OnboardingEvent.TypeChanged(HouseType.COLONIAL))
            onEvent(OnboardingEvent.GarageChanged(true))
        }
        val restored = OnboardingViewModel(repo, handle).state.value
        assertThat(restored.name).isEqualTo("Maple St")
        assertThat(restored.type).isEqualTo(HouseType.COLONIAL)
        assertThat(restored.attachedGarage).isTrue()
    }

    private suspend fun FloorsViewModel.awaitFloors(vararg names: String) =
        state.first { s -> !s.loading && s.floors.map { it.name } == names.toList() }

    @Test fun floorsViewModelAddsMovesEditsAndDeletes() = runTest {
        val id = repo.createHome(com.kurtz.homerecord.housemodel.NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, true))
        val vm = FloorsViewModel(repo, id)
        vm.awaitFloors("Basement", "Main", "Upper")

        vm.onEvent(FloorsEvent.AddClicked)
        assertThat(vm.state.value.dialog).isEqualTo(FloorsDialog.Add)
        vm.onEvent(FloorsEvent.ConfirmAdd("Attic"))
        assertThat(vm.state.value.dialog).isNull()
        val attic = vm.awaitFloors("Basement", "Main", "Upper", "Attic").floors.last()

        vm.onEvent(FloorsEvent.MoveDown(attic))
        vm.awaitFloors("Basement", "Main", "Attic", "Upper")

        vm.onEvent(FloorsEvent.ConfirmEdit(attic.id, "Loft", 84))
        val loft = vm.awaitFloors("Basement", "Main", "Loft", "Upper").floors.single { it.id == attic.id }
        assertThat(loft.ceilingHeightIn).isEqualTo(84)

        val main = vm.state.value.floors.single { it.name == "Main" }
        vm.onEvent(FloorsEvent.DeleteClicked(main))
        assertThat((vm.state.value.dialog as FloorsDialog.Delete).floor.roomCount).isEqualTo(1)
        vm.onEvent(FloorsEvent.ConfirmDelete(main.id))
        vm.awaitFloors("Basement", "Loft", "Upper")
    }
}
