package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ContactEntity
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.contacts.ContactsEvent
import com.kurtz.homerecord.housemanual.contacts.ContactsScreen
import com.kurtz.homerecord.housemanual.contacts.ContactsUiState
import com.kurtz.homerecord.housemanual.documents.DocumentsEvent
import com.kurtz.homerecord.housemanual.documents.DocumentsScreen
import com.kurtz.homerecord.housemanual.documents.DocumentsUiState
import com.kurtz.homerecord.housemodel.documents.DocumentFolder
import com.kurtz.homerecord.housemodel.upkeep.ContactRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Contacts and Documents are list rows, not card stacks (cr:card-lists): the row opens, trailing buttons act. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RecordRowsTest {
    @get:Rule val rule = createComposeRule()


    @AcceptanceCriterion("AC-U3", "AC-C39")
    @Test fun contactRowEditsOnTapAndCallsFromItsButton() {
        val events = mutableListOf<ContactsEvent>()
        val dale = ContactEntity(id = "c1", homeId = "h1", name = "Dale Harlan", company = "Harlan Heating", trade = "HVAC", phone = "555-0142", email = "dale@harlanheating.example", createdAt = 1, updatedAt = 1)
        val maria = ContactEntity(id = "c2", homeId = "h1", name = "Maria Soto", trade = "Plumber", createdAt = 1, updatedAt = 1)
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                ContactsScreen(
                    ContactsUiState(loading = false, rows = listOf(ContactRow(dale, 2, listOf("i1"), listOf("Basement furnace")), ContactRow(maria, 0, emptyList(), emptyList()))),
                    { events += it },
                )
            }
        }
        val rows = rule.onAllNodesWithTag("hm_contacts_row")
        assertThat(rows.fetchSemanticsNodes()).hasSize(2)
        rows[1].performClick()
        assertThat(events).containsExactly(ContactsEvent.OpenForm("c2"))
        rule.onNodeWithContentDescription("Call Dale Harlan at 555-0142").performClick()
        rule.onNodeWithContentDescription("Email Dale Harlan").performClick()
        assertThat(events).containsExactly(
            ContactsEvent.OpenForm("c2"),
            ContactsEvent.Call("555-0142"),
            ContactsEvent.Email("dale@harlanheating.example"),
        ).inOrder()
        // Maria has no phone or email, so her row has no call or email button
        assertThat(rule.onAllNodes(hasTestTag("hm_contacts_call")).fetchSemanticsNodes()).hasSize(1)
    }

    @AcceptanceCriterion("AC-U3", "AC-C37")
    @Test fun documentRowOpensOnTapAndRenamesFromItsButton() {
        val events = mutableListOf<DocumentsEvent>()
        val manual = DocumentEntity(id = "d1", homeId = "h1", folder = "Appliances", name = "Furnace manual.pdf", path = "p1", mime = "application/pdf", size = 2_411_520, createdAt = 1, updatedAt = 1)
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                DocumentsScreen(DocumentsUiState(loading = false, folders = listOf(DocumentFolder("Appliances", listOf(manual)))), { events += it })
            }
        }
        rule.onAllNodesWithTag("hm_documents_row")[0].performClick()
        rule.onNodeWithContentDescription("Rename or move Furnace manual.pdf").performClick()
        assertThat(events).containsExactly(DocumentsEvent.Open("d1"), DocumentsEvent.OpenForm("d1")).inOrder()
    }
}
