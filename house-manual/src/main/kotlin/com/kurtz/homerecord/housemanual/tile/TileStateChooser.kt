package com.kurtz.homerecord.housemanual.tile

/** AC-34: the tile is active when at least one shutoff exists, inactive otherwise. */
object TileStateChooser {
    fun isActive(shutoffCount: Int): Boolean = shutoffCount > 0
}
