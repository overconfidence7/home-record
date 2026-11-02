package com.kurtz.homerecord.housemodel.walls

import com.kurtz.homerecord.core.data.WallEntity
import com.kurtz.homerecord.core.data.WallPhotoEntity

/** The side of the room a wall is on, from the plan's orientation code ("N", "E", "S", "W", "D1"...). */
enum class WallSide {
    NORTH,
    EAST,
    SOUTH,
    WEST,
    ANGLED,
    ;

    companion object {
        fun of(orientation: String): WallSide = when (orientation) {
            "N" -> NORTH
            "E" -> EAST
            "S" -> SOUTH
            "W" -> WEST
            else -> ANGLED
        }
    }
}

/**
 * One wall on the room page (AC-C17): its side, a number when the room has more than one wall on that side (an L-shaped
 * room has two north walls), its length and height, and the picture to show for each stage that has a photo.
 */
data class RoomWall(
    val id: String,
    val side: WallSide,
    val number: Int?,
    val lengthIn: Int,
    val heightIn: Int,
    /** The straightened copy when there is one, else the photo as taken. */
    val thumbnails: Map<WallStage, String> = emptyMap(),
)

object RoomWalls {
    /**
     * The room's walls in compass order (north, east, south, west, then angled walls), keeping plan order within a side.
     * Walls sharing a side are numbered 1, 2... in that order; a wall alone on its side has no number.
     */
    fun build(walls: List<WallEntity>, photos: List<WallPhotoEntity>): List<RoomWall> {
        val photosByWall = photos.groupBy { it.wallId }
        val sorted = walls.sortedWith(compareBy({ WallSide.of(it.orientation).ordinal }, { it.edgeIndex }))
        val countBySide = sorted.groupingBy { WallSide.of(it.orientation) }.eachCount()
        val seen = mutableMapOf<WallSide, Int>()
        return sorted.map { wall ->
            val side = WallSide.of(wall.orientation)
            val index = seen.merge(side, 1, Int::plus) ?: 1
            val byStage = WallPhotoRepository.latestRowByStage(photosByWall[wall.id].orEmpty())
            RoomWall(
                id = wall.id,
                side = side,
                number = index.takeIf { (countBySide[side] ?: 0) > 1 },
                lengthIn = wall.lengthIn,
                heightIn = wall.heightIn,
                thumbnails = byStage.mapValues { (_, photo) -> photo.texturePath ?: photo.originalPath },
            )
        }
    }
}
