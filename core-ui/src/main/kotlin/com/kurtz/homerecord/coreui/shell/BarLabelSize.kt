package com.kurtz.homerecord.coreui.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One label size for the whole bar ([judge:nav-label-200-uneven], rules 25 and 32): the largest size from [maxSp]
 * down to [minSp], in [stepSp] steps, at which every label fits [availablePx] on one line. [widthPx] measures a
 * label at a size in sp. If none fits even at [minSp], that size is used and the longest label ellipsizes.
 */
object BarLabelSize {
    fun pick(
        labels: List<String>,
        availablePx: Float,
        widthPx: (label: String, sizeSp: Float) -> Float,
        maxSp: Float = 12f,
        minSp: Float = 6f,
        stepSp: Float = 0.5f,
    ): Float {
        var size = maxSp
        while (size > minSp) {
            if (labels.all { widthPx(it, size) <= availablePx }) return size
            size -= stepSp
        }
        return minSp
    }
}

/** Room each bar or rail item leaves its label: the bar splits the window width, a rail item is 80 dp wide. */
private val ItemLabelPadding: Dp = 8.dp
private val RailItemWidth: Dp = 80.dp

@Composable
internal fun rememberBarLabelSize(labels: List<String>, rail: Boolean): TextUnit {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val containerPx = LocalWindowInfo.current.containerSize.width
    val windowWidthPx = if (containerPx > 0) containerPx else with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val style: TextStyle = MaterialTheme.typography.labelMedium
    return remember(labels, rail, windowWidthPx, density, style) {
        val itemPx = with(density) {
            if (rail || windowWidthPx <= 0) RailItemWidth.toPx() else windowWidthPx.toFloat() / labels.size.coerceAtLeast(1)
        }
        val available = itemPx - with(density) { ItemLabelPadding.toPx() }
        BarLabelSize.pick(labels, available, { label, sp ->
            measurer.measure(label, style.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width.toFloat()
        }).sp
    }
}
