package com.kurtz.homerecord.fieldlog.share

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.photos.PhotoCell
import com.kurtz.homerecord.fieldlog.photos.PhotoDayUi
import com.kurtz.homerecord.fieldlog.photos.PhotoTile
import com.kurtz.homerecord.fieldlog.photos.PhotosEvent
import com.kurtz.homerecord.fieldlog.photos.PhotosScreen
import com.kurtz.homerecord.fieldlog.photos.PhotosUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

// AC-F28 gallery link and AC-B35 Turn off link on the job's Photos tab
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ShareLinkSectionTest {
    @get:Rule val rule = createComposeRule()

    private val days = listOf(PhotoDayUi(LocalDate.of(2026, 10, 21), listOf(PhotoCell.Single(PhotoTile("p1", "/none/p1.jpg", "Kitchen", "Rough-in")))))

    private fun show(link: ShareLinkUi, events: MutableList<PhotosEvent>) = rule.setContent {
        HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
            PhotosScreen(PhotosUiState(loading = false, days = days, count = 1, link = link), { events += it })
        }
    }

    @Test fun shareLinkThenTurnOffAsksFirst() {
        val events = mutableListOf<PhotosEvent>()
        show(ShareLinkUi(allowed = true, url = "https://api.example/share/gallery/tok1", confirmOff = true), events)
        rule.onNodeWithTag("fl_photos_share_link").performClick()
        rule.onNodeWithTag("fl_photos_link_on").assertExists()
        rule.onNodeWithTag("fl_photos_link_off").performClick()
        rule.onNodeWithTag("fl_photos_link_off_confirm").performClick()
        rule.onNodeWithTag("fl_photos_link_off_cancel").performClick()
        assertThat(events).containsExactly(
            PhotosEvent.Link(ShareLinkEvent.Share), PhotosEvent.Link(ShareLinkEvent.AskOff),
            PhotosEvent.Link(ShareLinkEvent.TurnOff), PhotosEvent.Link(ShareLinkEvent.DismissOff),
        ).inOrder()
    }

    @Test fun crewSeeNoShareLinkAndWorkingDisablesIt() {
        val events = mutableListOf<PhotosEvent>()
        show(ShareLinkUi(allowed = false), events)
        rule.onNodeWithTag("fl_photos_share_link").assertDoesNotExist()
    }

    @Test fun aLinkBeingMadeCannotBeTappedTwiceAndAFailureSaysSo() {
        show(ShareLinkUi(allowed = true, working = true, failed = true), mutableListOf())
        rule.onNodeWithTag("fl_photos_share_link").assertIsNotEnabled()
        rule.onNodeWithTag("fl_photos_link_failed").assertExists()
        rule.onNodeWithTag("fl_photos_link_on").assertDoesNotExist()
    }
}
