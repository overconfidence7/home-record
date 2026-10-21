package com.kurtz.homerecord.coreui.components

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AC-U4: segmented options are at least 48 dp tall. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EnumPickerTouchTargetTest {
    @get:Rule val rule = createComposeRule()

    @Test fun segmentedOptionsAre48dp() {
        var density = 1f
        rule.setContent {
            density = LocalDensity.current.density
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { EnumPicker(listOf("Create", "Join"), "Create", {}, { it }) }
        }
        val small = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { it.size.height < 48 * density - 1 }
        assertThat(small).isEmpty()
    }
}
