package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** FL-Jobs empty states follow rule 10 (judge:fl-empty-states-rule10): New job on empty, Clear search or New job on no matches. */
@AcceptanceCriterion("AC-F6")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobsEmptyStateTest {
    @get:Rule val rule = createComposeRule()

    private fun show(state: JobsUiState, events: MutableList<JobsEvent>) =
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { JobsScreen(state, { events += it }) } }

    @Test fun emptyOffersNewJobForOwners() {
        val events = mutableListOf<JobsEvent>()
        show(JobsUiState(loading = false), events)
        rule.onNodeWithTag("fl_jobs_empty_new").performClick()
        assertThat(events).containsExactly(JobsEvent.New)
    }

    @Test fun crewGetNoNewJobOnEmpty() {
        show(JobsUiState(loading = false, canAddJob = false), mutableListOf())
        rule.onNodeWithTag("fl_jobs_empty_new").assertDoesNotExist()
    }

    @Test fun noMatchesOffersClearAndNew() {
        val events = mutableListOf<JobsEvent>()
        show(JobsUiState(loading = false, query = "zzz", noMatches = true), events)
        rule.onNodeWithTag("fl_jobs_no_matches_clear").performClick()
        rule.onNodeWithTag("fl_jobs_no_matches_new").performClick()
        assertThat(events).containsExactly(JobsEvent.Query(""), JobsEvent.New).inOrder()
    }
}
