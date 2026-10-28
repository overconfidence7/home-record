package com.kurtz.homerecord.fieldlog.house

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsNode
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import com.kurtz.homerecord.housemodel.ui.PlanEditorScreen
import com.kurtz.homerecord.housemodel.ui.PlanEditorUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The plan editor lives in :house-model; in Field Log its tags carry `fl_`, never `hm_` (cr:shared-hm-tags). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SharedHouseTagsTest {
    @get:Rule val rule = createComposeRule()

    private val state = PlanEditorUiState(
        loading = false,
        floors = listOf(FloorSummary("f1", "Main", ceilingHeightIn = 96, halfLevel = false, roomCount = 1)),
        floorId = "f1",
        rooms = listOf(PlanRoom("k", "Kitchen", "KITCHEN", listOf(Pt(0, 0), Pt(168, 0), Pt(168, 150), Pt(0, 150)))),
        selectedId = "k",
        zoom = 1f, panX = 16f, panY = 16f,
    )

    private fun tags(node: SemanticsNode): List<String> =
        listOfNotNull(node.config.getOrNull(SemanticsProperties.TestTag)) + node.children.flatMap(::tags)

    @AcceptanceCriterion("AC-U12")
    @Test fun planEditorInFieldLogTagsWithFl() {
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { PlanEditorScreen(state, {}, onBack = {}) } }
        rule.onNodeWithTag("fl_plan_canvas").assertExists()
        rule.onNodeWithTag("fl_plan_room_k").assertExists()
        val all = tags(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        assertThat(all).isNotEmpty()
        assertThat(all.filter { it.startsWith("hm_") }).isEmpty()
    }
}
