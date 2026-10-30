package com.kurtz.homerecord.fieldlog

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.jobs.JobsScreen
import com.kurtz.homerecord.fieldlog.jobs.JobsUiState
import com.kurtz.homerecord.fieldlog.photos.PhotosScreen
import com.kurtz.homerecord.fieldlog.photos.PhotosUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-U10: the extended FABs carry their label on the clickable node itself, so TalkBack and
// uiautomator name them (QA found fl_jobs_new with NAF="true" and an empty content-desc).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FabLabelsTest {
    @get:Rule val rule = createComposeRule()

    private fun clickableNamed(label: String) =
        SemanticsMatcher("clickable with content description \"$label\"") { node ->
            SemanticsProperties.ContentDescription in node.config &&
                node.config[SemanticsProperties.ContentDescription].contains(label)
        }

    @Test fun newJobFabIsNamed() {
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { JobsScreen(JobsUiState(loading = false), {}) } }
        rule.onNodeWithTag("fl_jobs_new", useUnmergedTree = true).assertHasClickAction().assert(clickableNamed("New job"))
    }

    @Test fun takePhotoFabIsNamed() {
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { PhotosScreen(PhotosUiState(loading = false), {}) } }
        rule.onNodeWithTag("fl_photos_fab", useUnmergedTree = true).assertHasClickAction().assert(clickableNamed("Take a photo"))
    }
}
