package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class JobsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `empty state shows exactly one New job button (AC-51)`() {
        val events = mutableListOf<JobsEvent>()
        rule.setContent { HomeRecordTheme { JobsScreen(JobsUiState(loaded = true), events::add) } }
        rule.onAllNodesWithText("New job").assertCountEquals(1)
        rule.onNodeWithText("No jobs yet. Tap New job to start logging.").assertIsDisplayed()
        rule.onNodeWithText("New job").performClick()
        assertEquals(listOf<JobsEvent>(JobsEvent.NewJob), events)
    }

    @Test fun `show archived toggle only appears when archived jobs exist`() {
        rule.setContent { HomeRecordTheme { JobsScreen(JobsUiState(loaded = true, archivedCount = 0)) {} } }
        rule.onAllNodesWithText("Show archived").assertCountEquals(0)
    }

    @Test fun `toggle is shown with archived jobs`() {
        rule.setContent { HomeRecordTheme { JobsScreen(JobsUiState(loaded = true, archivedCount = 2)) {} } }
        rule.onNodeWithText("Show archived").assertIsDisplayed()
    }
}
