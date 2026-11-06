package com.kurtz.homerecord.housemodel.overlays

import java.util.Locale

/** An item an overlay can be linked to (AC-C26: "the equipment it serves"), with the room it is in. */
/** [subtype] picks the row's category icon in the picker (rule 26). */
data class OverlayItemChoice(val id: String, val name: String, val roomId: String?, val roomName: String?, val subtype: String? = null)

/** An overlay linked to an item, with its room's name, for the item page's Links (AC-C26). */
data class OverlayLink(val overlay: Overlay, val roomName: String?)

/**
 * The linked-item picker's list (AC-C26): items in the overlay's own room first, then the rest, each group by name. A
 * query keeps the items whose name or room holds every word of it, ignoring case, so "water kit" finds the kitchen's
 * water heater.
 */
object OverlayItemPicker {
    fun filter(choices: List<OverlayItemChoice>, query: String, roomId: String?): List<OverlayItemChoice> {
        val words = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotEmpty)
        val matching = choices.filter { c ->
            val text = listOfNotNull(c.name, c.roomName).joinToString(" ").lowercase(Locale.ROOT)
            words.all(text::contains)
        }
        return matching.sortedWith(
            compareBy<OverlayItemChoice> { if (roomId != null && it.roomId == roomId) 0 else 1 }.thenBy { it.name.lowercase(Locale.ROOT) },
        )
    }
}
