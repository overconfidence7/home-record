package com.kurtz.homerecord.fieldlog.photos

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** ui-standards rule 27 and SPEC FL-Job: three columns with 2 dp gaps, a pair two cells wide, capture as the FAB (AC-F25). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PhotosGridTest {
    @get:Rule val rule = createComposeRule()

    private fun tile(id: String) = PhotoTile(id, "/none/$id.jpg", "Kitchen", "Rough-in")

    private fun show(state: PhotosUiState, onEvent: (PhotosEvent) -> Unit = {}) = rule.setContent {
        HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { PhotosScreen(state, onEvent, Modifier.width(302.dp)) }
    }

    // AC-F25
    @Test fun threeColumnsWithTwoDpGapsAndAPairSpansTwo() {
        show(
            PhotosUiState(
                loading = false, count = 5,
                days = listOf(
                    PhotoDayUi(
                        LocalDate.of(2026, 10, 21),
                        listOf(PhotoCell.Pair(tile("b1"), tile("a1")), PhotoCell.Single(tile("p1")), PhotoCell.Single(tile("p2"))),
                    ),
                ),
            ),
        )
        val before = rule.onNodeWithTag("fl_photos_tile_b1").getUnclippedBoundsInRoot()
        val after = rule.onNodeWithTag("fl_photos_tile_a1").getUnclippedBoundsInRoot()
        val p1 = rule.onNodeWithTag("fl_photos_tile_p1").getUnclippedBoundsInRoot()
        val p2 = rule.onNodeWithTag("fl_photos_tile_p2").getUnclippedBoundsInRoot()
        // 302 dp less two 2 dp gaps is three 99.33 dp cells.
        listOf(before, after, p1, p2).forEach { assertThat((it.right - it.left).value).isWithin(1f).of(99.33f) }
        assertThat((after.left - before.right).value).isWithin(0.5f).of(2f)
        // The pair and the first single share a row; the second single wraps to the next.
        assertThat((p1.left - after.right).value).isWithin(0.5f).of(2f)
        assertThat(p1.top).isEqualTo(before.top)
        assertThat((p2.top - p1.bottom).value).isWithin(1f).of(2f) // rows round to whole pixels
        assertThat(p2.left).isEqualTo(before.left)
    }

    // AC-F25
    @Test fun fabTakesAPhotoFromTheGridAndFromEmpty() {
        val events = mutableListOf<PhotosEvent>()
        show(PhotosUiState(loading = false), { events += it })
        rule.onNodeWithTag("fl_photos_empty").assertExists()
        rule.onNodeWithTag("fl_photos_fab").performClick()
        assertThat(events).containsExactly(PhotosEvent.Capture)
    }

    @Test fun noFabWhileLoading() {
        show(PhotosUiState())
        rule.onNodeWithTag("fl_photos_fab").assertDoesNotExist()
    }
}
