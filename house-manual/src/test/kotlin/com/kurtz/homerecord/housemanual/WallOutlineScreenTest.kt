package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.ui.RoomEvent
import com.kurtz.homerecord.housemodel.ui.RoomScreen
import com.kurtz.homerecord.housemodel.ui.RoomUiState
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.ui.WallOutlineError
import com.kurtz.homerecord.housemodel.ui.WallOutlineEvent
import com.kurtz.homerecord.housemodel.ui.WallOutlineScreen
import com.kurtz.homerecord.housemodel.ui.WallOutlineUiState
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallSide
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C18: "Photograph this wall" offers the camera or the gallery; the outline screen shows four handles, a dashed guide
// from the wall's length × height, a loupe while dragging and Use.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WallOutlineScreenTest {
    @get:Rule val rule = createComposeRule()

    private val wall = RoomWall("w1", WallSide.NORTH, null, 168, 96)
    private val base = WallOutlineUiState(loading = false, wall = wall, stage = WallStage.ROUGH_IN, photoPath = "/missing.jpg", photoWidth = 4032, photoHeight = 3024)

    private fun show(state: WallOutlineUiState, events: MutableList<WallOutlineEvent>) = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "hm") {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { WallOutlineScreen(state, { events += it }, ColorPainter(Color.Gray)) }
        }
    }

    @Test
    @AcceptanceCriterion("AC-C18")
    fun showsFourHandlesTheWallsShapeAndUse() {
        val events = mutableListOf<WallOutlineEvent>()
        show(base, events)
        rule.onNodeWithText("Outline the wall").assertExists()
        rule.onNodeWithText("North wall · Rough-in").assertExists()
        rule.onNodeWithText("The dashed box is the wall's shape, 14' × 8'.", substring = true).assertExists()
        (0..3).forEach { rule.onNodeWithTag("hm_wall_outline_handle_$it").assertExists() }
        rule.onNodeWithTag("hm_wall_outline_loupe").assertDoesNotExist()
        rule.onNodeWithTag("hm_wall_outline_use").performClick()
        rule.onNodeWithTag("hm_wall_outline_reset").performClick()
        assertThat(events).containsExactly(WallOutlineEvent.Use, WallOutlineEvent.Reset).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C18")
    fun draggingAHandleSendsItsMovesAndTheLoupeShowsWhileDragging() {
        val events = mutableListOf<WallOutlineEvent>()
        var state by mutableStateOf(base)
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                    WallOutlineScreen(state, { events += it }, ColorPainter(Color.Gray))
                }
            }
        }
        rule.onNodeWithTag("hm_wall_outline_handle_2").performTouchInput { swipe(center, center + Offset(-40f, -30f)) }
        assertThat(events.first()).isEqualTo(WallOutlineEvent.DragStart(2))
        assertThat(events.filterIsInstance<WallOutlineEvent.Drag>()).isNotEmpty()
        assertThat(events.filterIsInstance<WallOutlineEvent.Drag>().all { it.corner == 2 }).isTrue()
        assertThat(events.last()).isEqualTo(WallOutlineEvent.DragEnd)

        state = base.copy(dragging = 2)
        rule.onNodeWithTag("hm_wall_outline_loupe").assertExists()
    }

    @Test
    fun savingWaitsAndAnErrorSaysWhatToDo() {
        show(base.copy(saving = true, error = null), mutableListOf())
        rule.onNodeWithText("Saving…").assertExists()
        rule.onNodeWithTag("hm_wall_outline_use").assertIsNotEnabled()
    }

    @Test
    fun crossedCornersExplainThemselves() {
        show(base.copy(error = WallOutlineError.NotUsable), mutableListOf())
        rule.onNodeWithTag("hm_wall_outline_error").assertExists()
        rule.onNodeWithText("The corners cross", substring = true).assertExists()
    }

    @Test
    fun anUnreadablePhotoOffersBack() {
        val events = mutableListOf<WallOutlineEvent>()
        show(base.copy(error = WallOutlineError.PhotoUnreadable), events)
        rule.onNodeWithText("Can't open this photo").assertExists()
        rule.onNodeWithTag("hm_wall_outline_back").performClick()
        assertThat(events).containsExactly(WallOutlineEvent.Back)
    }

    @Test
    @AcceptanceCriterion("AC-C18")
    fun photographOpensTheCameraOrGallerySheet() {
        val events = mutableListOf<RoomEvent>()
        val state = RoomUiState(
            loading = false, room = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144), walls = listOf(wall),
            canPhotograph = true, photoSheetFor = "w1",
        )
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { RoomScreen(state) { events += it } }
            }
        }
        rule.onNodeWithText("Choose from gallery").assertExists()
        rule.onNodeWithText("Take photo").performClick()
        assertThat(events).containsExactly(RoomEvent.TakeWallPhoto)
    }
}
