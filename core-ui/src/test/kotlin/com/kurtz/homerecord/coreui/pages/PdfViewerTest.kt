package com.kurtz.homerecord.coreui.pages

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
}
