package com.kurtz.homerecord.fieldlog.materials

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.material.Material
import com.kurtz.homerecord.core.material.MaterialCategory
import com.kurtz.homerecord.core.material.MaterialsReport
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F44: list per job with totals; CSV export; the receipt form offers the scanned total
@AcceptanceCriterion("AC-F44")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MaterialsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val list = listOf(
        Material("m1", "j1", "City Electric Supply", 12_371, MaterialCategory.MATERIALS, "2026-11-03", null, null),
        Material("m2", "j1", "Sunbelt Rentals", 9_500, MaterialCategory.EQUIPMENT_RENTAL, "2026-11-04", null, null),
        Material("m3", "j2", "Home Depot", 41_288, MaterialCategory.MATERIALS, "2026-11-02", null, null),
    )
    private val names = mapOf("j1" to "Hale kitchen", "j2" to "Smith deck")

    @Test fun showsEachJobsTotalAndTheTotalForAllJobs() {
        val events = mutableListOf<MaterialsEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                MaterialsScreen(MaterialsUiState(loading = false, groups = MaterialsReport.group(list, names)), { events += it }, onBack = {})
            }
        }
        rule.onNodeWithText("$631.59").assertIsDisplayed()
        rule.onNodeWithText("$218.71").assertIsDisplayed()
        rule.onNodeWithTag("fl_materials_row_m1").performClick()
        rule.onNodeWithTag("fl_materials_export").performClick()
        rule.onNodeWithTag("fl_materials_add").performClick()
        assertThat(events).containsExactly(MaterialsEvent.Open("m1"), MaterialsEvent.Export, MaterialsEvent.Add).inOrder()
    }

    @Test fun exportSharesTheCsvOfTheList() {
        val shared = mutableListOf<Pair<String, String>>()
        val vm = MaterialsViewModel(
            MaterialsDeps(
                materials = flowOf(list),
                jobs = flowOf(names.map { JobOption(it.key, it.value) }),
                exporter = { name, csv -> shared += name to csv; true },
                categoryLabel = { it.name },
                today = { java.time.LocalDate.of(2026, 11, 4) },
            ),
        )
        assertThat(vm.state.value.totalCents).isEqualTo(63_159L)
        vm.export()
        assertThat(shared.single().first).isEqualTo("materials-2026-11-04.csv")
        assertThat(shared.single().second).contains(",All jobs,,Total,631.59,")
        assertThat(vm.state.value.exportFailed).isFalse()
    }

    @Test fun emptyListOffersAddReceipt() {
        val events = mutableListOf<MaterialsEvent>()
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { MaterialsScreen(MaterialsUiState(loading = false), { events += it }, onBack = {}) } }
        rule.onNodeWithText("No receipts yet").assertIsDisplayed()
        rule.onNodeWithText("Add receipt").performClick()
        assertThat(events).containsExactly(MaterialsEvent.Add)
    }

    @Test fun scannedReceiptOffersItsTotal() {
        val events = mutableListOf<MaterialFormEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                MaterialFormScreen(
                    MaterialFormUiState(loading = false, canScan = true, jobs = listOf(JobOption("j1", "Hale kitchen")), suggestedCents = 12_371,
                        draft = MaterialFormDraft(jobId = "j1", date = "2026-11-04", receiptPath = "/none.jpg")),
                    { events += it }, onClose = {},
                )
            }
        }
        rule.onNodeWithText("Use $123.71 from the receipt").performClick()
        rule.onNodeWithTag("fl_material_form_rescan").performClick()
        assertThat(events).containsExactly(MaterialFormEvent.UseTotal, MaterialFormEvent.Scan).inOrder()
    }
}
