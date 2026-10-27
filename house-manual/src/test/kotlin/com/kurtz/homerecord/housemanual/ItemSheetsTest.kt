package com.kurtz.homerecord.housemanual

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.core.model.PlateField
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.items.AttachDocumentContent
import com.kurtz.homerecord.housemanual.items.ItemDetailEvent
import com.kurtz.homerecord.housemanual.items.ItemDetailUiState
import com.kurtz.homerecord.housemodel.ui.ItemEditEvent
import com.kurtz.homerecord.housemodel.ui.PlateScan
import com.kurtz.homerecord.housemodel.ui.PlateScanContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Item sheets with real-world content: repeated OCR lines (cr:plate-dup-key) and a long document list at 200% text (cr:sheet-scroll). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemSheetsTest {
    @get:Rule val rule = createComposeRule()

    @AcceptanceCriterion("AC-C31")
    @Test fun plateSheetShowsRepeatedLinesWithoutCrashing() {
        val events = mutableListOf<ItemEditEvent>()
        val scan = PlateScan(
            lines = listOf("WARNING", "120V", "MODEL NO: XG50T06EC36U1", "120V", "WARNING"),
            make = "Rheem", model = "XG50T06EC36U1", target = PlateField.SERIAL,
        )
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { PlateScanContent(scan) { events += it } } }
        assertThat(rule.onAllNodesWithTag("hm_item_plate_line").fetchSemanticsNodes()).hasSize(5)
        rule.onAllNodesWithTag("hm_item_plate_line")[3].performClick()
        assertThat(events.last()).isEqualTo(ItemEditEvent.PlateAssign("120V"))
    }

    @AcceptanceCriterion("AC-C6")
    @Test fun attachSheetReachesTheLastDocumentAt200PercentText() {
        val docs = (1..14).map { i -> doc("d$i", "Manual $i.pdf") }
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                    Box(Modifier.height(480.dp)) { AttachDocumentContent(ItemDetailUiState(showAttach = true, attachable = docs)) { events += it } }
                }
            }
        }
        rule.onNodeWithTag("hm_item_detail_attach_list").performScrollToNode(hasText("Manual 14.pdf"))
        rule.onNodeWithText("Manual 14.pdf").performClick()
        assertThat(events.last()).isEqualTo(ItemDetailEvent.AttachDocument("d14"))
    }
}

private fun doc(id: String, name: String) =
    DocumentEntity(id = id, homeId = "h1", name = name, path = "/media/$id.pdf", mime = "application/pdf", createdAt = 0, updatedAt = 0)
