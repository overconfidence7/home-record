package com.kurtz.homerecord.housemodel.walls

/**
 * Field Log's "Behind the wall" capture (AC-F49): the crew picks a room, then a wall, and the camera opens. The stage the
 * photo is saved at follows from what the wall already has: before drywall nothing is there, so the photo is the Rough-in
 * picture; once the Rough-in photo is in, the next capture on the same wall is the one after drywall, the Finished picture.
 * Retaking a Rough-in photo is done from the room page, where the stage is picked by hand.
 */
object BehindTheWall {
    /** The stage the next "Behind the wall" photo of a wall with photos at [photographed] is saved at. */
    fun captureStage(photographed: Set<WallStage>): WallStage =
        if (WallStage.ROUGH_IN in photographed) WallStage.FINISHED else WallStage.BehindTheWall

    /** How far a room is: walls with a Rough-in photo and walls with a Finished photo, out of [total]. */
    data class Progress(val total: Int, val roughIn: Int, val finished: Int) {
        val complete: Boolean get() = total > 0 && roughIn == total
    }

    fun progress(walls: List<RoomWall>): Progress = Progress(
        total = walls.size,
        roughIn = walls.count { WallStage.ROUGH_IN in it.thumbnails },
        finished = walls.count { WallStage.FINISHED in it.thumbnails },
    )
}
