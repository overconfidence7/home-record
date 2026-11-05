package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.importing.ImportConflict
import com.kurtz.homerecord.housemanual.importing.ImportCount
import com.kurtz.homerecord.housemanual.importing.ImportEvent
import com.kurtz.homerecord.housemanual.importing.ImportKind
import com.kurtz.homerecord.housemanual.importing.ImportScreen
import com.kurtz.homerecord.housemanual.importing.ImportUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H17: HM-Import shows the counts, Keep mine / Use imported per conflict, Import; nothing new disables Import.
@AcceptanceCriterion("AC-H17")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ImportScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun countsConflictPickAndImport() {
        val events = mutableListOf<ImportEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                ImportScreen(
                    ImportUiState(
                        loading = false, homeName = "Ortiz house",
                        counts = listOf(ImportCount(ImportKind.WallPhotos, 6), ImportCount(ImportKind.Overlays, 3)),
                        conflicts = listOf(ImportConflict("room", "r1", ImportKind.Rooms, "Kitchen")),
                    ),
                ) { events += it }
            }
        }
        rule.onNodeWithTag("hm_import_count_wall_photo").assertIsDisplayed()
        rule.onNodeWithTag("hm_import_count_overlay").assertIsDisplayed()
        rule.onNodeWithTag("hm_import_use_imported_r1").performScrollTo().performClick()
        rule.onNodeWithTag("hm_import_confirm").performClick()
        assertThat(events).containsExactly(ImportEvent.Choose("room", "r1", true), ImportEvent.Import).inOrder()
    }

    @Test fun nothingNewDisablesImport() {
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                ImportScreen(ImportUiState(loading = false, homeName = "Ortiz house")) {}
            }
        }
        rule.onNodeWithTag("hm_import_nothing_new").assertIsDisplayed()
        rule.onNodeWithTag("hm_import_confirm").assertIsNotEnabled()
    }

    @Test fun doneOffersToShowTheHome() {
        val events = mutableListOf<ImportEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                ImportScreen(ImportUiState(loading = false, done = true)) { events += it }
            }
        }
        rule.onNodeWithTag("hm_import_finish").performClick()
        assertThat(events).containsExactly(ImportEvent.Finish)
    }
}
