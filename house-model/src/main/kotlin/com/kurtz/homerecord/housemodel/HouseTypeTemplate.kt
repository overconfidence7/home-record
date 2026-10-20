package com.kurtz.homerecord.housemodel

/** A floor to create when a home is made. [exteriorWallAtGrade] is true for a walkout basement. */
data class FloorTemplate(
    val name: String,
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
            name = "Basement",
            elevationIn = BASEMENT_IN,
            exteriorWallAtGrade = foundation == Foundation.WALKOUT_BASEMENT,
        )
        val main = FloorTemplate("Main", 0)
        val upper = FloorTemplate("Upper", STORY_IN)
        val floors: List<FloorTemplate> = when (type) {
            HouseType.RANCHER, HouseType.BUNGALOW, HouseType.OTHER ->
                listOfNotNull(basement.takeIf { foundation.hasBasement }, main)
            HouseType.TWO_STORY, HouseType.COLONIAL ->
                listOfNotNull(basement.takeIf { foundation.hasBasement }, main, upper)
            HouseType.CAPE_COD ->
                listOfNotNull(basement.takeIf { foundation.hasBasement }, main, upper.copy(halfLevel = true))
            HouseType.SPLIT_LEVEL -> listOf(
                FloorTemplate("Lower", -HALF_LEVEL_IN, halfLevel = true),
                main,
                FloorTemplate("Upper", HALF_LEVEL_IN, halfLevel = true),
            )
            HouseType.RAISED_RANCH -> listOf(
                FloorTemplate("Lower", -HALF_LEVEL_IN, halfLevel = true),
                FloorTemplate("Upper", HALF_LEVEL_IN, halfLevel = true),
            )
            HouseType.TOWNHOUSE ->
                listOfNotNull(basement.takeIf { foundation.hasBasement }?.copy(name = "Lower"), main, upper)
        }
        val garageFloor = if (attachedGarage) {
            floors.indexOfFirst { it.name == "Main" }.takeIf { it >= 0 } ?: floors.indexOfFirst { it.name == "Lower" }.coerceAtLeast(0)
        } else {
            null
        }
        return HouseTemplate(floors, garageFloor)
    }
}
