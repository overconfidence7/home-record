package com.kurtz.homerecord.coreui.components

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AC-U4: link chips and their remove X are 48 dp targets (rule 30); the X removes that chip. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkChipTouchTargetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun chipsAndRemoveXAre48dp() {
        var density = 1f
        var removed: String? = null
        rule.setContent {
            density = LocalDensity.current.density
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                LinkChipRow(listOf(LinkChip("k", "Kitchen"), LinkChip("g", "Garage")), onOpen = {}, onRemove = { removed = it.id })
            }
        }
        val min = 48 * density - 1
        val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertThat(nodes).hasSize(4)
        assertThat(nodes.filter { it.size.height < min || it.size.width < min }).isEmpty()
        rule.onNodeWithContentDescription("Remove Garage").performClick()
        assertThat(removed).isEqualTo("g")
    }
}
