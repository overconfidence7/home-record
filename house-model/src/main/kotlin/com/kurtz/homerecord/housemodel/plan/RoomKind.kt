package com.kurtz.homerecord.housemodel.plan

/** Room kinds offered by the room picker (AC-C6). The enum name is what the room row stores. */
enum class RoomKind {
    KITCHEN, LIVING_ROOM, DINING_ROOM, BEDROOM, BATHROOM, OFFICE, LAUNDRY, GARAGE, BASEMENT, UTILITY,
    HALLWAY, CLOSET, MUDROOM, PORCH, DECK, ATTIC, MECHANICAL, OTHER;

    companion object {
        fun parse(value: String?): RoomKind = entries.firstOrNull { it.name == value } ?: OTHER
    }
}
