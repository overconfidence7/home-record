package com.kurtz.homerecord.housemanual.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.home.AttentionKind
import com.kurtz.homerecord.housemodel.home.AttentionRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H2: Needs attention rows open what they name; an overdue reminder (with or without an item) opens the Reminders tab.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HomeAttentionTest {
    @get:Rule val rule = createComposeRule()

    @Test
    @AcceptanceCriterion("AC-H2")
    fun overdueRemindersOpenRemindersAndWarrantyRowsOpenTheItem() {
        val events = mutableListOf<HomeEvent>()
        val rows = listOf(
            AttentionRow(AttentionKind.REMINDER_OVERDUE, "t1", "furnace", "HVAC service", "Oct 25, 2026"),
            AttentionRow(AttentionKind.REMINDER_OVERDUE, "t2", null, "Clean gutters", "Oct 30, 2026"),
            AttentionRow(AttentionKind.WARRANTY_EXPIRING, "w1", "dw", "Dishwasher", "Dec 14, 2026"),
        )
        rule.setContent { HomeScreen(HomeUiState(loading = false, homeName = "Maple St", itemCount = 3, attention = rows), onEvent = { events += it }) }
        rule.onNodeWithTag("hm_home_attention_t1").performScrollTo().performClick()
        rule.onNodeWithTag("hm_home_attention_t2").performScrollTo().performClick()
        rule.onNodeWithTag("hm_home_attention_w1").performScrollTo().performClick()
        assertThat(events).containsExactly(HomeEvent.OpenReminders, HomeEvent.OpenReminders, HomeEvent.OpenItem("dw")).inOrder()
    }
}
