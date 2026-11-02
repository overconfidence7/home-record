package com.kurtz.homerecord.housemodel.overlays

/**
 * Where opening an overlay goes (AC-C28: a search result or a link opens the record): an overlay on a wall opens that
 * wall at the stage it was recorded at with it picked; one on the floor plan opens its room's page; a plan overlay with
 * no room has nowhere to open yet (the plan editor gains overlays with T052c2).
 */
sealed interface OverlayTarget {
    data class Wall(val roomId: String, val wallId: String, val stage: String, val overlayId: String) : OverlayTarget
    data class Room(val roomId: String) : OverlayTarget

    companion object {
        fun of(o: Overlay): OverlayTarget? {
            val roomId = o.roomId ?: return null
            val wallId = o.wallId
            return if (wallId != null) Wall(roomId, wallId, o.stage.name, o.id) else Room(roomId)
        }
    }
}
