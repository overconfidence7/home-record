package com.kurtz.homerecord.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AC-C6, ui-standards 17 and 32: the last of 18 room kinds is reachable in the options sheet at 200% text (cr:sheet-scroll). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OptionsSheetScrollTest {
    @get:Rule val rule = createComposeRule()

    @Test fun lastOptionIsReachableAt200PercentText() {
        val kinds = listOf(
            "Kitchen", "Living room", "Dining room", "Family room", "Primary bedroom", "Bedroom", "Bathroom", "Half bath",
            "Laundry", "Mudroom", "Office", "Hallway", "Closet", "Pantry", "Garage", "Basement", "Attic", "Utility room",
        )
        val options = kinds.mapIndexed { i, label -> SheetOption("k$i", label) }
        var picked: SheetOption? = null
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                    Box(Modifier.height(480.dp)) { OptionsSheetContent("Room kind", options) { picked = it } }
                }
            }
        }
        rule.onNodeWithTag("options_sheet_list").performScrollToNode(hasText("Utility room"))
        rule.onNodeWithText("Utility room").performClick()
        assertThat(picked?.id).isEqualTo("k17")
    }
}
