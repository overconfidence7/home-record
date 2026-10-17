package com.kurtz.homerecord.core.imports

import com.kurtz.homerecord.core.bundle.RecordBundle
import com.kurtz.homerecord.core.model.Source

enum class ImportEntityType { ROOM, WALL, PANEL, BREAKER, BREAKER_ROOM, ENDPOINT, SHUTOFF, EQUIPMENT, PHOTO }

enum class ConflictChoice { KEEP_MINE, USE_IMPORTED }

/** Same id exists locally with `source = house-manual`; the user picks which version wins (AC-76, AC-78). */
data class ImportConflict(val type: ImportEntityType, val id: String, val label: String)

/**
 * What an import would do. [toInsert] and [conflicting] hold the imported records already remapped to the
 * local Home and marked `source = import`; [conflicting] is only written if the user picks "Use imported".
 */
data class ImportPlan(
    val toInsert: RecordBundle,
    val conflicting: RecordBundle,
    val newCounts: Map<ImportEntityType, Int>,
    val conflicts: List<ImportConflict>,
    /** Records whose id is already present locally and that are not house-manual edits (e.g. a re-import). */
    val alreadyPresent: Int,
) {
    val newTotal: Int get() = newCounts.values.sum()
    val isEmpty: Boolean get() = newTotal == 0 && conflicts.isEmpty()
}

/** Pure: sorts every record in a bundle into new / conflict / already-present (SPEC §6 import rules, AC-76..79). */
object ImportPlanner {
    /**
     * @param localHomeId the Home the import merges into; every imported `homeId` is remapped to it.
     * @param localSources `source` of every local record, keyed by id (ids are UUIDs, unique across types).
     */
    fun plan(bundle: RecordBundle, localHomeId: String, localSources: Map<String, Source>): ImportPlan {
        val counts = linkedMapOf<ImportEntityType, Int>()
        val conflicts = mutableListOf<ImportConflict>()
        var present = 0

        fun <T> split(
            type: ImportEntityType,
            items: List<T>,
            id: (T) -> String,
            label: (T) -> String,
            prepare: (T) -> T,
        ): Pair<List<T>, List<T>> {
            val fresh = mutableListOf<T>()
            val clash = mutableListOf<T>()
            for (item in items) {
                when (localSources[id(item)]) {
                    null -> fresh += prepare(item)
                    Source.HOUSE_MANUAL -> {
                        clash += prepare(item)
                        conflicts += ImportConflict(type, id(item), label(item))
                    }
                    else -> present++
                }
            }
            if (fresh.isNotEmpty()) counts[type] = fresh.size
            return fresh to clash
        }

        val rooms = split(ImportEntityType.ROOM, bundle.rooms, { it.id }, { it.name }) {
            it.copy(homeId = localHomeId, source = Source.IMPORT)
        }
        val walls = split(ImportEntityType.WALL, bundle.walls, { it.id }, { it.label }) { it.copy(source = Source.IMPORT) }
        val panels = split(ImportEntityType.PANEL, bundle.panels, { it.id }, { it.name }) {
            it.copy(homeId = localHomeId, source = Source.IMPORT)
        }
        val breakers = split(ImportEntityType.BREAKER, bundle.breakers, { it.id }, { it.label }) {
            it.copy(source = Source.IMPORT)
        }
        val breakerRooms = split(ImportEntityType.BREAKER_ROOM, bundle.breakerRooms, { it.id }, { it.id }) {
            it.copy(source = Source.IMPORT)
        }
        val endpoints = split(ImportEntityType.ENDPOINT, bundle.endpoints, { it.id }, { it.label }) {
            it.copy(source = Source.IMPORT)
        }
        val shutoffs = split(ImportEntityType.SHUTOFF, bundle.shutoffs, { it.id }, { it.kind.name }) {
            it.copy(homeId = localHomeId, source = Source.IMPORT)
        }
        val equipment = split(
            ImportEntityType.EQUIPMENT, bundle.equipment, { it.id },
            { listOfNotNull(it.make, it.model).joinToString(" ").ifBlank { it.category.name } },
        ) { it.copy(homeId = localHomeId, source = Source.IMPORT) }
        val photos = split(ImportEntityType.PHOTO, bundle.photos, { it.id }, { it.caption ?: it.path }) {
            it.copy(homeId = localHomeId, source = Source.IMPORT)
        }

        val home = bundle.home.copy(id = localHomeId, source = Source.IMPORT)
        fun bundleOf(first: Boolean) = bundle.copy(
            home = home,
            rooms = rooms.pick(first), walls = walls.pick(first), panels = panels.pick(first),
            breakers = breakers.pick(first), breakerRooms = breakerRooms.pick(first),
            endpoints = endpoints.pick(first), shutoffs = shutoffs.pick(first),
            equipment = equipment.pick(first), photos = photos.pick(first),
        )
        return ImportPlan(bundleOf(true), bundleOf(false), counts, conflicts, present)
    }

    private fun <T> Pair<List<T>, List<T>>.pick(first: Boolean) = if (first) this.first else second
}
