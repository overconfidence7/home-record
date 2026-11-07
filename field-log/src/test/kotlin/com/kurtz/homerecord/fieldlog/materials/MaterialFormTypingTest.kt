package com.kurtz.homerecord.fieldlog.materials

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.material.JobMaterials
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

// AC-F44: QA auto F18 saved "123.71" typed after a failed save as $1.00; every typed character must land
@AcceptanceCriterion("AC-F44")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MaterialFormTypingTest {
    @get:Rule val rule = createComposeRule()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
        .allowMainThreadQueries().build()

    @After fun tearDown() = db.close()

    @Test fun amountTypedOneKeyAtATimeAfterAFailedSaveKeepsEveryDigit() { // AC-F44
        val deps = MaterialFormDeps(
            null, JobMaterials(db), flowOf(listOf(JobOption("j1", "Hale kitchen"))), receipts = null,
            today = { LocalDate.of(2026, 11, 4) }, fetchReceipt = { null },
        )
        val vm = MaterialFormViewModel(deps, SavedStateHandle())
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                val state by vm.state.collectAsState()
                MaterialFormScreen(state, vm::onEvent, onClose = {})
            }
        }
        rule.waitUntil(10_000) { !vm.state.value.loading }
        rule.onNodeWithTag("fl_save").performClick()
        rule.waitForIdle()
        assertThat(vm.state.value.showErrors).isTrue()
        val amount = rule.onNodeWithTag("fl_material_form_amount")
        amount.performClick()
        "123.71".forEach { amount.performTextInput(it.toString()) }
        rule.waitForIdle()
        assertThat(vm.state.value.draft.amount).isEqualTo("123.71")
        assertThat(vm.state.value.amountCents).isEqualTo(12_371L)
    }
}
