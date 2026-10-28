package com.kurtz.homerecord.core.photo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AC-F26: the marked-up copy matches what was drawn, so the stored image's EXIF turn is applied before the marks.
class ImageOrientationTest {
    @Test
    fun exifTagsMapToTurnAndMirror() {
        assertThat(ImageOrientation.fromExif(1)).isEqualTo(ImageOrientation.Upright)
        assertThat(ImageOrientation.fromExif(0)).isEqualTo(ImageOrientation.Upright)
        assertThat(ImageOrientation.fromExif(6)).isEqualTo(ImageOrientation(90, false))
        assertThat(ImageOrientation.fromExif(3)).isEqualTo(ImageOrientation(180, false))
        assertThat(ImageOrientation.fromExif(8)).isEqualTo(ImageOrientation(270, false))
        assertThat(ImageOrientation.fromExif(2)).isEqualTo(ImageOrientation(0, true))
        assertThat(ImageOrientation.fromExif(5)).isEqualTo(ImageOrientation(90, true))
        assertThat(ImageOrientation.fromExif(42)).isEqualTo(ImageOrientation.Upright)
    }

    @Test
    fun portraitShotStoredSidewaysIsTallUpright() {
        val o = ImageOrientation.fromExif(6)
        assertThat(o.uprightSize(4000, 3000)).isEqualTo(3000 to 4000)
        assertThat(o.uprightAspect(4000, 3000)).isWithin(0.001f).of(0.75f)
        assertThat(ImageOrientation.fromExif(3).uprightAspect(4000, 3000)).isWithin(0.001f).of(4f / 3f)
        assertThat(o.uprightAspect(0, 3000)).isNull()
    }

    @Test
    fun sampleSizeKeepsDecodeUnderCap() {
        assertThat(ImageOrientation.sampleSize(4000, 3000, 8_000_000)).isEqualTo(2)
        assertThat(ImageOrientation.sampleSize(2000, 1500, 8_000_000)).isEqualTo(1)
        assertThat(ImageOrientation.sampleSize(8160, 6120, 8_000_000)).isEqualTo(4)
        assertThat(ImageOrientation.sampleSize(0, 0, 8_000_000)).isEqualTo(1)
    }
}
