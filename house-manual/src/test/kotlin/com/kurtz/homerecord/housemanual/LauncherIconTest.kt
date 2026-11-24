package com.kurtz.homerecord.housemanual

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * S9: the adaptive launcher icon's themed (monochrome) layer is its own one-path drawable with the door cut out.
 * The system tints every opaque pixel of that layer one colour, so reusing the two-colour foreground drew a blank shape.
 */
class LauncherIconTest {
    private val res = File("src/main/res")

    @Test
    fun everyAdaptiveIconHasForegroundBackgroundAndItsOwnMonochrome() {
        for (name in listOf("ic_launcher", "ic_launcher_round")) {
            val xml = File(res, "mipmap-anydpi/$name.xml").readText()
            assertThat(xml).contains("<background")
            assertThat(xml).contains("@drawable/ic_launcher_foreground")
            assertThat(xml).contains("<monochrome android:drawable=\"@drawable/ic_launcher_monochrome\"")
        }
    }

    @Test
    fun monochromeIsOneEvenOddShapeSoTheInnerMarkIsAHole() {
        val xml = File(res, "drawable/ic_launcher_monochrome.xml").readText()
        assertThat(Regex("<path").findAll(xml).count()).isEqualTo(1)
        assertThat(xml).contains("android:fillType=\"evenOdd\"")
        // Outer shape and inner mark: two closed sub-paths.
        assertThat(Regex("Z").findAll(xml).count()).isEqualTo(2)
    }
}
