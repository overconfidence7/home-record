package com.kurtz.homerecord.coreui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Rule 22 (AC-F34 due date): a tap anywhere on a read-only date or time field opens its picker (QA F11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PickerFieldTapTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tapOnDateFieldBodyOpensDatePicker() {
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                DateField("Due", null, {}, Modifier.testTag("due"))
            }
        }
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.onNodeWithTag("due").performClick()
        rule.onNode(isDialog()).assertExists()
    }

    @Test fun tapOnTimeFieldBodyOpensTimePicker() {
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                TimeField("Start", LocalTime.of(7, 30), {}, Modifier.testTag("start"), is24Hour = false)
            }
        }
        rule.onNode(isDialog()).assertDoesNotExist()
        rule.onNodeWithTag("start").performClick()
        rule.onNode(isDialog()).assertExists()
    }
}
