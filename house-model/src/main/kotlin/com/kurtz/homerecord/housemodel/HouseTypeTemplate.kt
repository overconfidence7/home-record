package com.kurtz.homerecord.housemodel

import androidx.annotation.StringRes

/** Which level a seeded floor is; its stored name comes from [nameRes] in the device's language. */
enum class FloorRole(@StringRes val nameRes: Int) {
    BASEMENT(R.string.floor_default_basement),
    LOWER(R.string.floor_default_lower),
    MAIN(R.string.floor_default_main),
    UPPER(R.string.floor_default_upper),
}

/** A floor to create when a home is made. [exteriorWallAtGrade] is true for a walkout basement. */
data class FloorTemplate(
    val role: FloorRole,
    val elevationIn: Int,
    val halfLevel: Boolean = false,
    val ceilingHeightIn: Int = DEFAULT_CEILING_IN,
    val exteriorWallAtGrade: Boolean = false,
)

/** What a new home starts with: floors bottom to top, and the index of the floor that gets a Garage room, if any. */
data class HouseTemplate(val floors: List<FloorTemplate>, val garageFloorIndex: Int?)

const val DEFAULT_CEILING_IN = 96

/** Seeds floors from house type, foundation and garage (AC-C2). Ceilings are 8 ft everywhere; the owner edits them. */
object HouseTypeTemplate {
    private const val HALF_LEVEL_IN = 48
    private const val STORY_IN = 108
    private const val BASEMENT_IN = -96

    fun forHome(type: HouseType, foundation: Foundation, attachedGarage: Boolean): HouseTemplate {
        val basement = FloorTemplate(
            role = FloorRole.BASEMENT,
            elevationIn = BASEMENT_IN,
            exteriorWallAtGrade = foundation == Foundation.WALKOUT_BASEMENT,
        )
        val main = FloorTemplate(FloorRole.MAIN, 0)
        val upper = FloorTemplate(FloorRole.UPPER, STORY_IN)
        val floors: List<FloorTemplate> = when (type) {
            HouseType.RANCHER, HouseType.BUNGALOW, HouseType.OTHER ->
                listOfNotNull(basement.takeIf { foundation.hasBasement }, main)
            HouseType.TWO_STORY, HouseType.COLONIAL ->
                listOfNotNull(basement.takeIf { foundation.hasBasement }, main, upper)
            HouseType.CAPE_COD ->
                listOfNotNull(basement.takeIf { foundation.hasBasement }, main, upper.copy(halfLevel = true))
            HouseType.SPLIT_LEVEL -> listOf(
                FloorTemplate(FloorRole.LOWER, -HALF_LEVEL_IN, halfLevel = true),
                main,
                FloorTemplate(FloorRole.UPPER, HALF_LEVEL_IN, halfLevel = true),
            )
            HouseType.RAISED_RANCH -> listOf(
                FloorTemplate(FloorRole.LOWER, -HALF_LEVEL_IN, halfLevel = true),
                FloorTemplate(FloorRole.UPPER, HALF_LEVEL_IN, halfLevel = true),
            )
            HouseType.TOWNHOUSE ->
                listOfNotNull(basement.takeIf { foundation.hasBasement }?.copy(role = FloorRole.LOWER), main, upper)
        }
        val garageFloor = if (attachedGarage) {
            floors.indexOfFirst { it.role == FloorRole.MAIN }.takeIf { it >= 0 } ?: floors.indexOfFirst { it.role == FloorRole.LOWER }.coerceAtLeast(0)
        } else {
            null
        }
        return HouseTemplate(floors, garageFloor)
    }
}
