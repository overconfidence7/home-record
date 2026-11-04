package com.kurtz.homerecord.housemodel.plan

/**
 * The kind a room's name says it is ("Kitchen", "Half bath", "Primary bedroom closet"), so a room named in the plan
 * editor gets its icon and kind without a trip to the Kind picker ([judge:hm-room-kind-not-inferred], walkthrough H3).
 * Words are matched whole, ignoring case; the more specific kind wins when a name has two ("Bedroom closet" is a
 * closet, "Primary bath" a bathroom). Null when no word names a kind.
 */
object RoomKindGuess {
    /** Checked in this order; each kind's words and two-word phrases. */
    private val rules: List<Pair<RoomKind, Set<String>>> = listOf(
        RoomKind.CLOSET to setOf("closet", "closets", "walk-in", "wic"),
        RoomKind.BATHROOM to setOf("bath", "bathroom", "baths", "powder", "ensuite", "en-suite", "washroom", "toilet", "wc", "half bath", "powder room"),
        RoomKind.LAUNDRY to setOf("laundry", "utility room"),
        RoomKind.MUDROOM to setOf("mudroom", "mud room"),
        RoomKind.MECHANICAL to setOf("mechanical", "furnace", "boiler"),
        RoomKind.KITCHEN to setOf("kitchen", "kitchenette"),
        RoomKind.DINING_ROOM to setOf("dining", "dining room"),
        RoomKind.LIVING_ROOM to setOf("living", "living room", "family room", "great room", "den", "lounge"),
        RoomKind.OFFICE to setOf("office", "study"),
        RoomKind.BEDROOM to setOf("bedroom", "bedrooms", "nursery", "guest room", "bunk room"),
        RoomKind.GARAGE to setOf("garage", "carport"),
        RoomKind.BASEMENT to setOf("basement", "cellar"),
        RoomKind.ATTIC to setOf("attic", "loft"),
        RoomKind.HALLWAY to setOf("hall", "hallway", "foyer", "entry", "entryway", "corridor"),
        RoomKind.PORCH to setOf("porch", "sunroom", "veranda"),
        RoomKind.DECK to setOf("deck", "patio"),
        RoomKind.UTILITY to setOf("utility"),
    )

    fun guess(name: String): RoomKind? {
        val words = name.lowercase().split(Regex("[^a-z-]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val terms = words.toSet() + words.zipWithNext { a, b -> "$a $b" }
        return rules.firstOrNull { (_, keys) -> keys.any { it in terms } }?.first
    }

    /**
     * The kind for a room renamed from [oldName] to [newName]: the new name's kind while the room's [current] kind is
     * Other or still the one its old name gave it; a kind the person picked themselves is kept.
     */
    fun afterRename(current: RoomKind, oldName: String, newName: String): RoomKind =
        if (current == RoomKind.OTHER || current == guess(oldName)) guess(newName) ?: RoomKind.OTHER else current
}
