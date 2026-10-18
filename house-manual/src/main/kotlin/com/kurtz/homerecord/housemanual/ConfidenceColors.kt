package com.kurtz.homerecord.housemanual

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kurtz.homerecord.core.model.Confidence

/** Green Confirmed, amber Likely, grey Unknown (AC-15); lighter tones on dark surfaces. */
fun confidenceColor(confidence: Confidence, dark: Boolean): Color = when (confidence) {
    Confidence.CONFIRMED -> if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    Confidence.LIKELY -> if (dark) Color(0xFFFFCA28) else Color(0xFFF9A825)
    Confidence.UNKNOWN -> if (dark) Color(0xFFBDBDBD) else Color(0xFF757575)
}

@Composable
fun confidenceColor(confidence: Confidence): Color =
    confidenceColor(confidence, MaterialTheme.colorScheme.background.luminance() < 0.5f)
