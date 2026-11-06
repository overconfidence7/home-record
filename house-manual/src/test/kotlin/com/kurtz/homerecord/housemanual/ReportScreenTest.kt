package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.report.ReportContents
import com.kurtz.homerecord.housemanual.report.ReportEvent
import com.kurtz.homerecord.housemanual.report.ReportScreen
import com.kurtz.homerecord.housemanual.report.ReportUiState
import com.kurtz.homerecord.housemanual.report.ReportViewModel
import com.kurtz.homerecord.housemodel.pdf.HomeReportData
import com.kurtz.homerecord.housemodel.pdf.PdfFloor
import com.kurtz.homerecord.housemodel.pdf.ReportItem
import com.kurtz.homerecord.housemodel.pdf.ReportRoom
import com.kurtz.homerecord.housemodel.pdf.ReportShutoff
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

// AC-H13: HM-Report says what the PDF holds and makes it on one tap.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@AcceptanceCriterion("AC-H13")
class ReportScreenTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private val data = HomeReportData(
        "Maple Street house", "14 Maple St", "Two-story", listOf(PdfFloor("Main floor", emptyList()), PdfFloor("Upper floor", emptyList())),
        listOf(
            ReportRoom("Kitchen", "Main floor", listOf(ReportItem("Refrigerator", "Refrigerator", "GE", "GFE28", null, null, WarrantyStatus.Unknown))),
            ReportRoom("Whole house", "", listOf(ReportItem("Smoke detectors", "Smoke detector", null, null, null, null, WarrantyStatus.Unknown))),
        ),
        listOf(ReportShutoff("Water main shutoff", "Utility room", "", null)), emptyList(),
    )

    @Test fun countsComeFromTheReportDataAndMakeSharesTheFile() {
        val gate = CompletableDeferred<String?>()
        val vm = ReportViewModel({ data }, { gate.await() })
        assertThat(vm.state.value.contents).isEqualTo(ReportContents(floors = 2, rooms = 1, items = 2, shutoffs = 1, panels = 0))
        vm.onEvent(ReportEvent.Make)
        assertThat(vm.state.value.busy).isTrue()
        vm.onEvent(ReportEvent.Make) // a second tap while busy does not start another
        gate.complete("content://report.pdf")
        assertThat(vm.state.value.uri).isEqualTo("content://report.pdf")
        assertThat(vm.state.value.busy).isFalse()
        vm.onEvent(ReportEvent.ShareHandled)
        assertThat(vm.state.value.uri).isNull()
    }

    @Test fun aFailedExportSaysSoAndCanBeTriedAgain() {
        var calls = 0
        val vm = ReportViewModel({ data }, { calls++; if (calls == 1) null else "content://report.pdf" })
        vm.onEvent(ReportEvent.Make)
        assertThat(vm.state.value.failed).isTrue()
        vm.onEvent(ReportEvent.Make)
        assertThat(vm.state.value.failed).isFalse()
        assertThat(vm.state.value.uri).isEqualTo("content://report.pdf")
    }

    @Test fun screenListsThePartsAndTheMakeButtonSendsMake() {
        val events = mutableListOf<ReportEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                ReportScreen(ReportUiState(loading = false, homeName = "Maple Street house", contents = ReportContents(3, 11, 38, 2, 1)), events::add)
            }
        }
        rule.onNodeWithText("3 floors, drawn to scale").assertExists()
        rule.onNodeWithText("11 rooms · 38 items with make, model, serial and warranty").assertExists()
        rule.onNodeWithText("2 shutoffs with instructions and photos").assertExists()
        rule.onNodeWithText("1 panel legend").assertExists()
        rule.onNodeWithTag("hm_report_make").performScrollTo().assertHeightIsAtLeast(56.dp).performClick()
        assertThat(events).containsExactly(ReportEvent.Make)
    }

    @Test fun whileMakingTheButtonIsOffAndAFailureShowsTheNotice() {
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                ReportScreen(ReportUiState(loading = false, contents = ReportContents(1, 1, 0, 0, 0), busy = true, failed = true), {})
            }
        }
        rule.onNodeWithTag("hm_report_make").assertIsNotEnabled()
        rule.onNodeWithText("Making the report…").assertExists()
        rule.onNodeWithTag("hm_report_failed").assertExists()
        assertThat(rule.onAllNodesWithText("None recorded yet").fetchSemanticsNodes()).hasSize(2)
    }
}
