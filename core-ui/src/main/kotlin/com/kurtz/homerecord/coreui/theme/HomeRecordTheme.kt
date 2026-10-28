package com.kurtz.homerecord.coreui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix

/** Shared 8 dp-grid corner radii. */
internal val HomeRecordShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Dynamic color on Android 12+, brand palette otherwise; light and dark; standard motion.
 * Also provides the brand's test tag prefix, so shared screens tag `hm_` or `fl_` by app.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeRecordTheme(
    brand: Brand,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> brandColorScheme(brand, darkTheme)
    }
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.standard(),
        shapes = HomeRecordShapes,
        typography = Typography(),
    ) {
        CompositionLocalProvider(LocalTagPrefix provides brand.tagPrefix, content = content)
    }
}
