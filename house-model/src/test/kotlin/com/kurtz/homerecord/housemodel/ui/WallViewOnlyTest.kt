package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// T076c2: AC-H16 a viewer on HM-Wall may look at stages and pick overlays; every drawing or change is dropped.
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
}
