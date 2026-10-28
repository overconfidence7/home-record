package com.kurtz.homerecord.housemanual

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
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

/** House Manual's full-screen form (contacts, consumables, service): Save follows dirty, Close asks before discarding (cr:form-dialog). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FormScreenDialogTest {
    @get:Rule val rule = createComposeRule()

    private var closed = 0

    private fun show(dirty: Boolean) = rule.setContent {
        HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
            FormScreenDialog("New contact", "hm_contacts_save", dirty = dirty, saveEnabled = dirty, onClose = { closed++ }, onSave = {}) { Text("Name") }
        }
    }

    @AcceptanceCriterion("AC-U6")
    @Test fun cleanFormClosesAtOnceWithSaveOff() {
        show(dirty = false)
        rule.onNodeWithTag("hm_contacts_save").assertIsNotEnabled()
        rule.onNodeWithTag("hm_close").performClick()
        assertThat(closed).isEqualTo(1)
        assertThat(rule.onAllNodes(hasTestTag("hm_discard")).fetchSemanticsNodes()).isEmpty()
    }

    @AcceptanceCriterion("AC-U6")
    @Test fun dirtyFormAsksBeforeDiscarding() {
        show(dirty = true)
        rule.onNodeWithTag("hm_contacts_save").assertIsEnabled()
        rule.onNodeWithTag("hm_close").performClick()
        assertThat(closed).isEqualTo(0)
        rule.onNodeWithTag("hm_keep_editing").performClick()
        assertThat(closed).isEqualTo(0)
        rule.onNodeWithTag("hm_close").performClick()
        rule.onNodeWithTag("hm_discard").performClick()
        assertThat(closed).isEqualTo(1)
    }
}
