package com.kurtz.homerecord.housemodel.overlays

/**
 * Where opening an overlay goes (AC-C28: a search result or a link opens the record): an overlay on a wall opens that
 * wall at the stage it was recorded at with it picked; one on the floor plan opens the plan editor on its floor with it
 * picked.
 */
sealed interface OverlayTarget {
    data class Wall(val roomId: String, val wallId: String, val stage: String, val overlayId: String) : OverlayTarget
    data class Plan(val overlayId: String) : OverlayTarget

    companion object {
        fun of(o: Overlay): OverlayTarget {
            val roomId = o.roomId
            val wallId = o.wallId
            return if (wallId != null && roomId != null) Wall(roomId, wallId, o.stage.name, o.id) else Plan(o.id)
        }
    }
}
