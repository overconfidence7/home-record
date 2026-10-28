package com.kurtz.homerecord.coreui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Which app is themed; selects the fallback palette used when dynamic color is unavailable, and the test tag prefix. */
enum class Brand(val tagPrefix: String) { HouseManual("hm"), FieldLog("fl") }

private val HmPrimary = Color(0xFF1F5FA6)
private val HmOnPrimary = Color(0xFFFFFFFF)
private val HmPrimaryContainer = Color(0xFFD4E3FF)
private val HmOnPrimaryContainer = Color(0xFF001C3A)
private val HmSecondary = Color(0xFF00696B)
private val HmSecondaryContainer = Color(0xFF9DF1F2)
private val HmOnSecondaryContainer = Color(0xFF002020)
private val HmPrimaryDark = Color(0xFFA5C8FF)
private val HmOnPrimaryDark = Color(0xFF00315F)
private val HmPrimaryContainerDark = Color(0xFF004786)
private val HmSecondaryDark = Color(0xFF80D5D6)
private val HmSecondaryContainerDark = Color(0xFF004F51)

private val FlPrimary = Color(0xFFB04A00)
private val FlOnPrimary = Color(0xFFFFFFFF)
private val FlPrimaryContainer = Color(0xFFFFDBC8)
private val FlOnPrimaryContainer = Color(0xFF380D00)
private val FlSecondary = Color(0xFF7A5900)
private val FlSecondaryContainer = Color(0xFFFFDF9E)
private val FlOnSecondaryContainer = Color(0xFF261A00)
private val FlPrimaryDark = Color(0xFFFFB68F)
private val FlOnPrimaryDark = Color(0xFF591D00)
private val FlPrimaryContainerDark = Color(0xFF823700)
private val FlSecondaryDark = Color(0xFFF2BF48)
private val FlSecondaryContainerDark = Color(0xFF5B4300)

internal fun brandColorScheme(brand: Brand, dark: Boolean): ColorScheme = when (brand) {
    Brand.HouseManual -> if (dark) {
        darkColorScheme(
            primary = HmPrimaryDark, onPrimary = HmOnPrimaryDark,
            primaryContainer = HmPrimaryContainerDark, onPrimaryContainer = HmPrimaryContainer,
            secondary = HmSecondaryDark, secondaryContainer = HmSecondaryContainerDark,
            onSecondaryContainer = HmSecondaryContainer,
        )
    } else {
        lightColorScheme(
            primary = HmPrimary, onPrimary = HmOnPrimary,
            primaryContainer = HmPrimaryContainer, onPrimaryContainer = HmOnPrimaryContainer,
            secondary = HmSecondary, secondaryContainer = HmSecondaryContainer,
            onSecondaryContainer = HmOnSecondaryContainer,
        )
    }
    Brand.FieldLog -> if (dark) {
        darkColorScheme(
            primary = FlPrimaryDark, onPrimary = FlOnPrimaryDark,
            primaryContainer = FlPrimaryContainerDark, onPrimaryContainer = FlPrimaryContainer,
            secondary = FlSecondaryDark, secondaryContainer = FlSecondaryContainerDark,
            onSecondaryContainer = FlSecondaryContainer,
        )
    } else {
        lightColorScheme(
            primary = FlPrimary, onPrimary = FlOnPrimary,
            primaryContainer = FlPrimaryContainer, onPrimaryContainer = FlOnPrimaryContainer,
            secondary = FlSecondary, secondaryContainer = FlSecondaryContainer,
            onSecondaryContainer = FlOnSecondaryContainer,
        )
    }
}
