package com.kurtz.homerecord.core.photo

/**
 * How a stored image must be turned to look the way the camera saw it, read from its EXIF orientation tag (1..8).
 * Marks are drawn in that upright frame, so the marked-up copy is written upright too (AC-F26).
 */
data class ImageOrientation(val rotationDegrees: Int, val flipHorizontal: Boolean) {
    /** True when the upright picture is the stored one turned on its side. */
    val swapsSides: Boolean get() = rotationDegrees == 90 || rotationDegrees == 270

    /** Width and height of the upright picture for a stored image of [width] by [height]. */
    fun uprightSize(width: Int, height: Int): Pair<Int, Int> = if (swapsSides) height to width else width to height

    /** Upright width over height, or null when the size is unknown. */
    fun uprightAspect(width: Int, height: Int): Float? {
        if (width <= 0 || height <= 0) return null
        val (w, h) = uprightSize(width, height)
        return w.toFloat() / h
    }

    val isIdentity: Boolean get() = rotationDegrees == 0 && !flipHorizontal

    companion object {
        val Upright = ImageOrientation(0, false)

        /** Maps the EXIF `Orientation` value; unknown and missing values mean upright. */
        fun fromExif(tag: Int): ImageOrientation = when (tag) {
            2 -> ImageOrientation(0, true)
            3 -> ImageOrientation(180, false)
            4 -> ImageOrientation(180, true)
            5 -> ImageOrientation(90, true)
            6 -> ImageOrientation(90, false)
            7 -> ImageOrientation(270, true)
            8 -> ImageOrientation(270, false)
            else -> Upright
        }

        /**
         * The power-of-two decode step that keeps a [width] by [height] image at or under [maxPixels], so a 50 MP shot
         * does not need hundreds of megabytes to mark up.
         */
        fun sampleSize(width: Int, height: Int, maxPixels: Long): Int {
            if (width <= 0 || height <= 0 || maxPixels <= 0) return 1
            var step = 1
            while ((width.toLong() / step) * (height.toLong() / step) > maxPixels) step *= 2
            return step
        }
    }
}
