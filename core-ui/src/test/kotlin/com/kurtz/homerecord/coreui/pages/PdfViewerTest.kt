package com.kurtz.homerecord.coreui.pages

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AC-C37, AC-F19: the shared viewer shows every page, and a thumbnail strip that jumps to a page when there is more than one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfViewerTest {
    @get:Rule val rule = createComposeRule()

    private class BlankPages(override val pageCount: Int) : PageSource {
        override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = null // placeholders carry the same labels
    }

    @Test fun multiPageDocumentHasThumbnailsThatJumpToAPage() {
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { PdfViewer(BlankPages(12), tagPrefix = "fl_file") }
        }
        rule.onNodeWithTag("fl_file_pages").assertIsDisplayed()
        rule.onNodeWithTag("fl_file_thumbnails").assertIsDisplayed()
        rule.onNodeWithContentDescription("Page 4 of 12").assertDoesNotExist()
        rule.onNodeWithContentDescription("Go to page 4").performClick()
        rule.onNodeWithContentDescription("Page 4 of 12").assertIsDisplayed()
    }

    @Test fun singlePageHasNoThumbnailStrip() {
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { PdfViewer(BlankPages(1), tagPrefix = "hm_document") }
        }
        rule.onNodeWithContentDescription("Page 1 of 1").assertIsDisplayed()
        rule.onNodeWithTag("hm_document_thumbnails").assertDoesNotExist()
    }

    /** A page that renders only when [ready] completes, as a slow PdfRenderer does. */
    private class SlowPages(val ready: CompletableDeferred<ImageBitmap>) : PageSource {
        override val pageCount = 1
        override suspend fun render(index: Int, widthPx: Int): ImageBitmap = ready.await()
    }

    // AC-F35 (R09g): a tap on a page still drawing its placeholder drops no pin; once the page is drawn the tap reports the spot
    @Test fun aTapBeforeThePageHasRenderedReportsNothing() {
        val ready = CompletableDeferred<ImageBitmap>()
        val taps = mutableListOf<Triple<Int, Float, Float>>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                PdfViewer(SlowPages(ready), tagPrefix = "fl_file", onPageTap = { p, x, y -> taps += Triple(p, x, y) })
            }
        }
        rule.onNodeWithTag("fl_file_page_1").performTouchInput { click(percentOffset(0.25f, 0.5f)) }
        rule.onNodeWithTag("fl_file_page_1").performClick()
        rule.runOnIdle { assertThat(taps).isEmpty() }
        ready.complete(android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap())
        rule.waitForIdle()
        rule.onNodeWithTag("fl_file_page_1").performTouchInput { click(percentOffset(0.25f, 0.5f)) }
        rule.runOnIdle {
            assertThat(taps).hasSize(1)
            assertThat(taps.single().first).isEqualTo(0)
            assertThat(taps.single().second).isWithin(0.02f).of(0.25f)
        }
    }
}
