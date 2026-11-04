package com.kurtz.homerecord.fieldlog.clients

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.JobStatus
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AC-F45: FL-Clients shows each client with tap-to-call and email and their jobs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ClientsScreenTest {
    @get:Rule val rule = createComposeRule()

    private val rows = listOf(
        ClientRow("c1", "Dana Hale", "(555) 014-2290", "dana@hale.test", listOf(ClientJob("j1", "Hale kitchen", JobStatus.ACTIVE))),
        ClientRow("c2", "Pat Smith", null, null, listOf(ClientJob("j2", "Smith deck", JobStatus.DONE))),
    )

    private fun show(state: ClientsUiState, events: MutableList<ClientsEvent>) = rule.setContent {
        HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { ClientsScreen(state, { events += it }, onBack = {}) }
    }

    // AC-F45
    @Test fun callEmailAndJobRowsSendTheirEvents() {
        val events = mutableListOf<ClientsEvent>()
        show(ClientsUiState(loading = false, clients = rows), events)
        rule.onNodeWithTag("fl_clients_call_c1").performClick()
        rule.onNodeWithTag("fl_clients_email_c1").performClick()
        rule.onNodeWithText("Hale kitchen").assertIsDisplayed().performClick()
        assertThat(events).containsExactly(
            ClientsEvent.Call("(555) 014-2290"), ClientsEvent.Email("dana@hale.test"), ClientsEvent.OpenJob("j1"),
        ).inOrder()
    }

    // AC-F45: no phone or email on file, no button that would do nothing.
    @Test fun clientWithoutDetailsHasNoCallOrEmail() {
        show(ClientsUiState(loading = false, clients = rows), mutableListOf())
        rule.onNodeWithText("Pat Smith").assertIsDisplayed()
        rule.onNodeWithTag("fl_clients_call_c2").assertDoesNotExist()
        rule.onNodeWithTag("fl_clients_email_c2").assertDoesNotExist()
    }

    @Test fun noClientsShowsTheEmptyState() {
        show(ClientsUiState(loading = false), mutableListOf())
        rule.onNodeWithText("No clients yet").assertIsDisplayed()
    }
}
