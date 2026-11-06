package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// T076c2/T076c2h: AC-H16 a viewer on HM-Wall and the plan may look at stages and pick overlays; every drawing or change is dropped.
@AcceptanceCriterion("AC-H16")
class WallViewOnlyTest {
    @Test fun lookingAndPickingAreAllowed() {
        listOf(WallOverlaysEvent.SelectStage(WallStage.FINISHED), WallOverlaysEvent.Select("o1"), WallOverlaysEvent.Select(null), WallOverlaysEvent.Back)
            .forEach { assertTrue("$it", WallViewOnly.allows(it)) }
    }

    @Test fun drawingMovingEditingAndDeletingAreDropped() {
        listOf(
            WallOverlaysEvent.StartDrawing, WallOverlaysEvent.AddPoint(OverlayPoint(0.1f, 0.2f)), WallOverlaysEvent.FinishDrawing,
            WallOverlaysEvent.MovePoint("o1", 0, OverlayPoint(0.3f, 0.3f)), WallOverlaysEvent.EditSelected,
            WallOverlaysEvent.AskDelete, WallOverlaysEvent.ConfirmDelete, WallOverlaysEvent.Save,
        ).forEach { assertFalse("$it", WallViewOnly.allows(it)) }
    }

    @Test fun onThePlanAViewerMovesAroundAndPicksButEditsNothing() {
        listOf(
            PlanEditorEvent.SelectFloor("f2"), PlanEditorEvent.Transform(1.5f, 10f, 20f), PlanEditorEvent.Fit(360f, 600f),
            PlanEditorEvent.OverlayEvent(WallOverlaysEvent.Select("p1")),
        ).forEach { assertTrue("$it", PlanViewOnly.allows(it)) }
        listOf(
            PlanEditorEvent.AddRoom(com.kurtz.homerecord.housemodel.plan.Pt(10, 10)), PlanEditorEvent.Delete, PlanEditorEvent.Undo,
            PlanEditorEvent.SetOverlayMode(false), PlanEditorEvent.CommitFields, PlanEditorEvent.DragMove("r1", 12, 0),
            PlanEditorEvent.OverlayEvent(WallOverlaysEvent.StartDrawing), PlanEditorEvent.OverlayEvent(WallOverlaysEvent.EditSelected),
            PlanEditorEvent.OverlayEvent(WallOverlaysEvent.MovePoint("p1", 0, OverlayPoint(1f, 1f))),
        ).forEach { assertFalse("$it", PlanViewOnly.allows(it)) }
    }
}
