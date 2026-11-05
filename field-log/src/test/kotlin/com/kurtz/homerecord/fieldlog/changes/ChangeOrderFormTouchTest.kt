package com.kurtz.homerecord.fieldlog.changes

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.fieldlog.ChangeOrderFormEditPreview
import com.kurtz.homerecord.fieldlog.ChangeOrderFormSignedPreview
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** auto:instrumented:field-log 2026-11-05: Remove line was 40 dp with no touch padding on the device (rule 30). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ChangeOrderFormTouchTest {
    @get:Rule val rule = createComposeRule()

    @Test fun removeLineIs48dpInLayout() {
        rule.setContent { ChangeOrderFormEditPreview() }
        rule.onNodeWithTag("fl_co_form_line_0_remove").performScrollTo().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    /** judge:fl-co-signed-edit-title: a signed order opens as a view named for the order, with Back and no Save. */
    @Test fun aSignedOrderIsAViewNotAnEditor() {
        rule.setContent { ChangeOrderFormSignedPreview() }
        rule.onNodeWithTag("fl_back").assertExists()
        rule.onNodeWithTag("fl_save").assertDoesNotExist()
        rule.onNodeWithTag("fl_close").assertDoesNotExist()
        rule.onAllNodesWithText("Pantry light on a motion switch").onFirst().assertExists()
        rule.onNodeWithText("Edit change order").assertDoesNotExist()
    }
}
