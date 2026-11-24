package com.kurtz.homerecord.coreui.theme

import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** S6: layer and kind colors follow the scheme in use, not the system night setting (a theme forced dark is dark). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "notnight")
class DarkSchemeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun followsTheThemeNotTheSystemSetting() {
        var forcedDark: Boolean? = null
        var forcedLight: Boolean? = null
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, darkTheme = true, dynamicColor = false) { forcedDark = isDarkScheme() }
            HomeRecordTheme(Brand.FieldLog, darkTheme = false, dynamicColor = false) { forcedLight = isDarkScheme() }
        }
        rule.waitForIdle()
        assertThat(forcedDark).isTrue()
        assertThat(forcedLight).isFalse()
    }
}
