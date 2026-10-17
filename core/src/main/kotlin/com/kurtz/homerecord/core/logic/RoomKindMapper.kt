package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.RoomKind

/** AC-104: name -> kind for quick-add rooms. Anything unrecognised (custom rooms) is [RoomKind.OTHER]. */
object RoomKindMapper {
    // Order matters: "Primary Bath" must not fall through to a later keyword.
    private val keywords: List<Pair<String, RoomKind>> = listOf(
        "kitchen" to RoomKind.KITCHEN,
        "bath" to RoomKind.BATHROOM,
        "bedroom" to RoomKind.BEDROOM,
        "garage" to RoomKind.GARAGE,
        "basement" to RoomKind.BASEMENT,
        "laundry" to RoomKind.LAUNDRY,
        "exterior" to RoomKind.EXTERIOR,
        "hallway" to RoomKind.HALLWAY,
        "living" to RoomKind.LIVING,
        "dining" to RoomKind.DINING,
        "office" to RoomKind.OFFICE,
        "attic" to RoomKind.ATTIC,
        "family" to RoomKind.FAMILY,
        "closet" to RoomKind.CLOSET,
        "crawl" to RoomKind.CRAWL_SPACE,
        "utility" to RoomKind.UTILITY,
    )

    fun kindFor(name: String): RoomKind {
        val lower = name.trim().lowercase()
        return keywords.firstOrNull { lower.contains(it.first) }?.second ?: RoomKind.OTHER
    }
}
