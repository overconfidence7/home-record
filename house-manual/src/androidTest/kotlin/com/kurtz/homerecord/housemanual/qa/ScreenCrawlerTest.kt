package com.kurtz.homerecord.housemanual.qa

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Opens every ScreenCatalog entry, checks the top bar and 48 dp targets (AC-U1, AC-U2, AC-U4, AC-U8, AC-U10),
 * and saves light, dark, 200% font and landscape screenshots to /sdcard/Download/qa/ (AC-U9, AC-U12).
 */
class ScreenCrawlerTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val outDir = File("/sdcard/Download/qa").also { it.mkdirs() }

    private fun shell(cmd: String) = inst.uiAutomation.executeShellCommand(cmd).close()

    @After fun resetSystem() {
        shell("cmd uimode night no")
        shell("settings put system font_scale 1.0")
    }

    private fun open(id: String, landscape: Boolean = false): ActivityScenario<QaActivity> {
        val ctx: Context = inst.targetContext
        val intent = Intent("${ctx.packageName}.qa.OPEN_SCREEN").setPackage(ctx.packageName)
            .putExtra("screen", id).putExtra("landscape", landscape).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return ActivityScenario.launch(intent)
    }

    private fun shoot(id: String, variant: String) {
        compose.waitForIdle()
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: return
        File(outDir, "$id-$variant.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    @Test fun everyCatalogScreenOpensAndMeetsTheBasics() {
        assertTrue("catalog is empty", ScreenCatalog.entries.isNotEmpty())
        for (entry in ScreenCatalog.entries) {
            open(entry.id).use {
                compose.waitForIdle()
                if (entry.nonRoot) compose.onNodeWithTag("hm_back").assertIsDisplayed()
                val tooSmall = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter { n ->
                    n.size.width < 48 * inst.targetContext.resources.displayMetrics.density - 1 ||
                        n.size.height < 48 * inst.targetContext.resources.displayMetrics.density - 1
                }
                assertTrue("${entry.id}: ${tooSmall.size} clickable nodes under 48 dp", tooSmall.isEmpty())
                shoot(entry.id, "light")
            }
        }
    }

    @Test fun darkFontAndLandscapeVariants() {
        shell("cmd uimode night yes")
        for (entry in ScreenCatalog.entries) open(entry.id).use { shoot(entry.id, "dark") }
        shell("cmd uimode night no")
        shell("settings put system font_scale 2.0")
        for (entry in ScreenCatalog.entries) open(entry.id).use { shoot(entry.id, "font200") }
        shell("settings put system font_scale 1.0")
        for (entry in ScreenCatalog.entries) open(entry.id, landscape = true).use { shoot(entry.id, "landscape") }
    }
}
