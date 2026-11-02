package com.kurtz.homerecord.housemodel.walls

/**
 * The construction stage a wall photo shows (AC-C17). Stored by name in `wall_photo.stage`. Field Log's "behind the wall"
 * capture writes [ROUGH_IN]; House Manual defaults to [FINISHED].
 */
enum class WallStage {
    FRAMING,
    ROUGH_IN,
    INSULATION,
    FINISHED,
    ;

    companion object {
        /** House Manual's stage for a new wall photo. */
        val HouseManualDefault = FINISHED

        /** Field Log's "behind the wall" stage (AC-F49). */
        val BehindTheWall = ROUGH_IN

        /** The stored name read back; an unknown or damaged value counts as [FINISHED], the plain photo of the room. */
        fun fromStored(value: String?): WallStage = entries.firstOrNull { it.name == value } ?: FINISHED
    }
}
