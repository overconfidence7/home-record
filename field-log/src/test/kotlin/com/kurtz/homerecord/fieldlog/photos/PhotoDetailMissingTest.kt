package com.kurtz.homerecord.fieldlog.photos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F25, [judge:fl-photo-missing-bare]: a deleted photo opens a rule 10 error state with a way back, not a bare line.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PhotoDetailMissingTest {
    @get:Rule val rule = createComposeRule()

    @Test fun deletedPhotoSaysSoAndGoesBackToPhotos() {
        val events = mutableListOf<PhotoDetailEvent>()
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "fl") {
                HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                    PhotoDetailScreen(PhotoDetailUiState(loading = false, missing = true), { events += it })
                }
            }
        }
        rule.onNodeWithText("Photo not found").assertIsDisplayed()
        rule.onNodeWithText("This photo was deleted, maybe on another phone. Go back to the job's photos.").assertIsDisplayed()
        rule.onNodeWithTag("fl_photo_delete").assertDoesNotExist()
        rule.onNodeWithTag("fl_photo_missing_back").assertIsDisplayed().performClick()
        assertThat(events).containsExactly(PhotoDetailEvent.Back)
    }
}
