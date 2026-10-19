package com.kurtz.homerecord.coreui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText

import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun errorStateRetryFires() {
        var retried = false
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { ErrorState("Can't load items.", { retried = true }) } }
        rule.onNodeWithText("Can't load items.").assertIsDisplayed()
        rule.onNodeWithText("Retry").performClick()
        assertThat(retried).isTrue()
    }

    @Test fun emptyStateActionFires() {
        var clicked = false
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                EmptyState(Icons.Rounded.Bolt, "No breakers yet", "Tap a slot.", "Add breaker", { clicked = true })
            }
        }
        rule.onNodeWithText("Add breaker").performClick()
        assertThat(clicked).isTrue()
    }

    @Test fun discardPromptAppearsWhenDirty() {
        var closed = false
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { FormScaffold("Edit item", dirty = true, saveEnabled = true, onClose = { closed = true }, onSave = {}) {} }
        }
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertThat(closed).isFalse()
        rule.onNodeWithText("Discard").performClick()
        assertThat(closed).isTrue()
    }

    @Test fun cleanFormClosesImmediately() {
        var closed = false
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { FormScaffold("Edit item", dirty = false, saveEnabled = false, onClose = { closed = true }, onSave = {}) {} }
        }
        rule.onNodeWithContentDescription("Close").performClick()
        assertThat(closed).isTrue()
    }

    @Test fun linkChipOpens() {
        var opened: String? = null
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { LinkChipRow(listOf(LinkChip("b14", "Breaker 14")), onOpen = { opened = it.id }) } }
        rule.onNodeWithText("Breaker 14").performClick()
        assertThat(opened).isEqualTo("b14")
    }
}
