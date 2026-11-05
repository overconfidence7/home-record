package com.kurtz.homerecord.fieldlog.photos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// [judge:fl-photo-marked-up-orphan]: "Marked up" is a badge on the photo itself, not a bare header line above the Before shot switch.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PhotoDetailMarkedBadgeTest {
    @get:Rule val rule = createComposeRule()

    private fun show(annotated: Boolean) = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "fl") {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                PhotoDetailScreen(
                    PhotoDetailUiState(loading = false, image = PhotoImage.NotUploaded, caption = "Kitchen north wall, rough-in", annotated = annotated),
                    {},
                )
            }
        }
    }

    @Test fun aMarkedUpPhotoCarriesTheBadgeInsideThePhotoFrame() {
        show(annotated = true)
        rule.onNode(hasTestTag("fl_photo_marked").and(androidx.compose.ui.test.hasAnyAncestor(hasTestTag("fl_photo_image"))), useUnmergedTree = true)
            .assertExists()
    }

    @Test fun anUnmarkedPhotoHasNoBadge() {
        show(annotated = false)
        rule.onNodeWithTag("fl_photo_marked", useUnmergedTree = true).assertDoesNotExist()
    }
}
