package com.kurtz.homerecord.coreui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.luminance

/**
 * True when the color scheme in use is dark. Layer and kind colors pick their light or dark tone from this, not from
 * the system setting, so they always match the surface they sit on (dynamic color, previews, a theme forced dark).
 */
@Composable
@ReadOnlyComposable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
