package com.kurtz.homerecord.housemodel.ui

/**
 * Picks one font size for every label of a segmented row: the largest size, on a [stepSp] grid from
 * [maxSp] down to [minSp], at which [fits] holds. When nothing fits, [minSp] (the labels then ellipsize).
 */
internal object SegmentLabelSize {
    fun largestFitting(minSp: Float, maxSp: Float, stepSp: Float, fits: (Float) -> Boolean): Float {
        require(stepSp > 0f) { "stepSp must be positive" }
        if (maxSp <= minSp) return minSp
        var size = maxSp
        while (size > minSp) {
            if (fits(size)) return size
            size -= stepSp
        }
        return minSp
    }
}
