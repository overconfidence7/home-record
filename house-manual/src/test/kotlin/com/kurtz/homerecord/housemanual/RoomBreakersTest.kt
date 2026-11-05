package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.LinkConfidence
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import androidx.compose.runtime.CompositionLocalProvider
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.panel.FeedingBreaker
import com.kurtz.homerecord.housemodel.ui.RoomEvent
import com.kurtz.homerecord.housemodel.ui.RoomScreen
import com.kurtz.homerecord.housemodel.ui.RoomUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C41: the room page lists the breakers feeding it with Confirmed/Likely/Unknown; a row opens the panel at it (AC-H7).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomBreakersTest {
    @get:Rule val rule = createComposeRule()

    private val kitchen = PlanRoom(id = "k", name = "Kitchen", points = listOf(Pt(0, 0), Pt(168, 0), Pt(168, 144), Pt(0, 144)))

    private fun fed(slot: Int, label: String, c: LinkConfidence) =
        FeedingBreaker(Breaker("b$slot", "p1", listOf(slot), 1, 20, BreakerType.STANDARD, label, c), "panel", "Main panel", c)

    @Test
    @AcceptanceCriterion("AC-C41")
    fun eachBreakerShowsItsNumberLabelPanelAndConfidenceAndOpensThePanel() {
        val events = mutableListOf<RoomEvent>()
        val likely = fed(4, "Refrigerator", LinkConfidence.LIKELY)
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                RoomScreen(RoomUiState(loading = false, room = kitchen, breakers = listOf(fed(2, "Kitchen counter", LinkConfidence.CONFIRMED), likely, fed(12, "Dishwasher", LinkConfidence.UNKNOWN))), onEvent = { events += it })
            }
        }
        rule.onNodeWithText("Main panel · 20 A · Confirmed").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Main panel · 20 A · Unknown").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("hm_room_breaker_4").performScrollTo().performClick()
        assertThat(events).contains(RoomEvent.OpenBreaker(likely))
    }

    @Test
    fun aRoomNoBreakerFeedsSaysSo() {
        rule.setContent { RoomScreen(RoomUiState(loading = false, room = kitchen, breakers = emptyList()), onEvent = {}) }
        rule.onNodeWithText("No breakers linked to this room yet.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Breakers feeding this room").assertExists()
    }
}
