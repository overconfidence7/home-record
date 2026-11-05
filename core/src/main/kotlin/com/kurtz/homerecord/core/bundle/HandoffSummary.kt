package com.kurtz.homerecord.core.bundle

import kotlinx.serialization.json.JsonPrimitive

/**
 * What a handoff carries, counted from the bundle about to be written (AC-F51): rooms, walls with photos (and the
 * photos on them), overlays, items, documents and the change orders the client signed. Soft-deleted records travel
 * in the bundle so an import can remove them, but they are not counted here.
 */
data class HandoffSummary(
    val rooms: Int,
    val wallsWithPhotos: Int,
    val wallPhotos: Int,
    val overlays: Int,
    val items: Int,
    val documents: Int,
    val changeOrders: Int,
) {
    /** Nothing a homeowner would see: no rooms, photos, overlays, items, documents or change orders. */
    val isEmpty: Boolean
        get() = rooms == 0 && wallPhotos == 0 && overlays == 0 && items == 0 && documents == 0 && changeOrders == 0

    companion object {
        fun of(bundle: HomeBundle): HandoffSummary {
            val live = { type: String -> bundle.records[type].orEmpty().filter { !it.deleted } }
            val liveWalls = live("wall").map { it.id }.toSet()
            val photos = live("wall_photo")
            // A photo whose wall a plan edit removed stays on its room (N001); it still counts as a photo, not a wall.
            val walls = photos.mapNotNull { p -> (p.fields["wallId"] as? JsonPrimitive)?.takeIf { it.isString }?.content }
                .filter { it in liveWalls }
                .toSet()
            return HandoffSummary(
                rooms = bundle.count("room"),
                wallsWithPhotos = walls.size,
                wallPhotos = photos.size,
                overlays = bundle.count("overlay"),
                items = bundle.count("item"),
                documents = bundle.count("document"),
                changeOrders = bundle.count("change_order"),
            )
        }

        /** "Hale kitchen 2026-11-05.homerecord", with characters a file name cannot hold dropped. */
        fun fileName(job: String, date: String): String {
            val clean = job.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").replace(Regex("\\s+"), " ").trim().take(60)
            return "${clean.ifEmpty { "Home" }} $date.${HomeBundle.EXTENSION}"
        }
    }
}
