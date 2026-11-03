package com.kurtz.homerecord.housemodel.ui

/** What a back press on HM-Wall / FL-Wall does next. */
enum class WallBackStep { CloseForm, CancelDrawing, Unpick, Leave }

/**
 * The back rule for the wall screen. The form and the drawing are always stepped out of first. A picked overlay is
 * unpicked by the system back only when it was picked here: the overlay the wall was opened on ([arrivalId], from the
 * room's Overlays list, an item's Links or Home search) is what the person came to see, so back leaves. The top bar's
 * arrow ([fromArrow]) is "up" and leaves past any selection; Done unpicks.
 */
object WallBack {
    fun step(hasForm: Boolean, drawing: Boolean, selectedId: String?, arrivalId: String?, fromArrow: Boolean): WallBackStep = when {
        hasForm -> WallBackStep.CloseForm
        drawing -> WallBackStep.CancelDrawing
        selectedId != null && !fromArrow && selectedId != arrivalId -> WallBackStep.Unpick
        else -> WallBackStep.Leave
    }
}
