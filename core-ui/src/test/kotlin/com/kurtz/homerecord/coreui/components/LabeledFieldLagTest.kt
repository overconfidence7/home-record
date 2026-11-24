package com.kurtz.homerecord.coreui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AC-F44 (QA auto F18): on a slow emulator the ViewModel's text reaches the field several keys late. The field must
 * keep what was typed while its value lags, instead of showing the stale value and typing over it ("123.71" saved as $1.00).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LabeledFieldLagTest {
    @get:Rule val rule = createComposeRule()

    @Test fun keysTypedWhileTheValueLagsAllLand() { // AC-F44
        var value by mutableStateOf("")
        val sent = mutableListOf<String>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                MoneyField("Amount", value, { sent += it }, Modifier.testTag("amount"))
            }
        }
        val field = rule.onNodeWithTag("amount")
        field.performClick()
        "123.71".forEach { field.performTextInput(it.toString()) }
        rule.waitForIdle()
        assertThat(sent.last()).isEqualTo("123.71")
        // The held-back values arrive late and in order; none of them may reset the field.
        sent.toList().forEach { value = it; rule.waitForIdle() }
        field.assertTextContains("123.71")
        assertThat(sent.last()).isEqualTo("123.71")
    }

    @Test fun aValueSetFromOutsideReplacesTheText() { // AC-F44 "Use $… from the receipt"
        var value by mutableStateOf("")
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                MoneyField("Amount", value, { value = it }, Modifier.testTag("amount"))
            }
        }
        val field = rule.onNodeWithTag("amount")
        field.performClick()
        field.performTextInput("12")
        rule.waitForIdle()
        value = "123.71"
        rule.waitForIdle()
        field.assertTextContains("123.71")
        field.performTextInput("5")
        rule.waitForIdle()
        assertThat(value).isEqualTo("123.715")
    }

    @Test fun aFilteredKeyIsDroppedFromTheField() { // breaker amps take digits only
        var value by mutableStateOf("")
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                LabeledField("Amps", value, { value = it }, Modifier.testTag("amps"), filter = { it.filter(Char::isDigit).take(3) })
            }
        }
        val field = rule.onNodeWithTag("amps")
        field.performClick()
        "2a0".forEach { field.performTextInput(it.toString()) }
        rule.waitForIdle()
        assertThat(value).isEqualTo("20")
        field.assertTextEquals("Amps", "20")
    }
}
