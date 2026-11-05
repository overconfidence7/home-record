package com.kurtz.homerecord.housemodel.plan

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The plan editor opens fitted and keeps the selected room in view (AC-C6, walkthrough H2). */
class PlanViewportTest {
    private fun box(id: String, x: Int, y: Int, w: Int, h: Int) =
        PlanRoom(id, id, points = listOf(Pt(x, y), Pt(x + w, y), Pt(x + w, y + h), Pt(x, y + h)))

    private fun PlanView.screen(room: PlanRoom) = listOf(
        room.minX * zoom + panX, room.minY * zoom + panY, room.maxX * zoom + panX, room.maxY * zoom + panY,
    )

    @Test fun fitCentresEveryRoomAndFillsTheTighterSide() {
        val rooms = listOf(box("a", 0, 0, 168, 150), box("b", 168, 0, 144, 144))
        val view = checkNotNull(PlanViewport.fit(rooms, 400f, 700f))
        // Width is the tighter side: 312 in across 400 - 2 × 24 dp.
        assertThat(view.zoom).isWithin(0.001f).of(352f / 312f)
        val (l, t, _, _) = view.screen(rooms[0])
        val (_, _, r, b) = view.screen(rooms[1])
        assertThat(l).isWithin(0.5f).of(24f)
        assertThat(400f - r).isWithin(0.5f).of(24f)
        // Centred vertically: the same space above and below.
        assertThat(t).isWithin(0.5f).of(700f - maxOf(b, view.screen(rooms[0])[3]))
    }

    @Test fun fitWithNoRoomsOrNoCanvasLeavesTheViewAlone() {
        assertThat(PlanViewport.fit(emptyList(), 400f, 700f)).isNull()
        assertThat(PlanViewport.fit(listOf(box("a", 0, 0, 120, 120)), 0f, 700f)).isNull()
    }

    @Test fun revealLeavesAVisibleRoomWhereItIs() {
        val room = box("a", 0, 0, 120, 120)
        assertThat(PlanViewport.reveal(PlanView(1f, 50f, 50f), room, 400f, 400f)).isNull()
    }

    @Test fun revealPansARoomBelowTheCanvasUpIntoView() {
        // The panel came up and the canvas shrank to 300 dp: the room's bottom (at 380) is under it.
        val room = box("a", 0, 200, 120, 130)
        val view = checkNotNull(PlanViewport.reveal(PlanView(1f, 50f, 50f), room, 400f, 300f))
        assertThat(view.zoom).isEqualTo(1f)
        assertThat(view.panX).isEqualTo(50f)
        val (_, t, _, b) = view.screen(room)
        assertThat(b).isWithin(0.01f).of(276f)
        assertThat(t).isAtLeast(24f)
    }

    @Test fun revealPansARoomOffTheRightEdgeLeft() {
        val room = box("b", 400, 0, 144, 144)
        val view = checkNotNull(PlanViewport.reveal(PlanView(1f, 24f, 24f), room, 400f, 700f))
        val (l, _, r, _) = view.screen(room)
        assertThat(r).isWithin(0.01f).of(376f)
        assertThat(l).isAtLeast(24f)
    }

    @Test fun revealZoomsOutOnlyWhenTheRoomIsBiggerThanTheCanvas() {
        val room = box("a", 0, 0, 600, 300)
        val view = checkNotNull(PlanViewport.reveal(PlanView(2f, 0f, 0f), room, 400f, 300f))
        assertThat(view.zoom).isLessThan(2f)
        val (l, t, r, b) = view.screen(room)
        assertThat(l).isAtLeast(23.5f)
        assertThat(r).isAtMost(376.5f)
        assertThat(t).isAtLeast(0f)
        assertThat(b).isAtMost(300f)
    }

    @Test fun turnedIsTrueOnlyWhenPortraitBecomesLandscapeOrBack() {
        assertThat(PlanViewport.turned(400f, 700f, 800f, 300f)).isTrue()
        assertThat(PlanViewport.turned(800f, 300f, 400f, 700f)).isTrue()
        assertThat(PlanViewport.turned(400f, 700f, 400f, 400f - 1f)).isTrue()
        assertThat(PlanViewport.turned(400f, 700f, 400f, 500f)).isFalse()
        assertThat(PlanViewport.turned(0f, 0f, 400f, 700f)).isFalse()
    }
}
