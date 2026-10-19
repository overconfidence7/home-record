package com.kurtz.homerecord.core.imports

import androidx.room.withTransaction
import com.kurtz.homerecord.core.bundle.RecordBundle
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Source
import java.io.File

data class ImportResult(val inserted: Int, val replaced: Int, val keptMine: Int, val mediaCopied: Int)

/** Plans against the local database and writes an [ImportPlan] in one transaction (AC-77..79). */
class ImportApplier(
    private val db: HomeRecordDatabase,
    private val media: MediaFileStore,
) {
    /**
     * Merges into the first local Home. On a fresh install (no local Home) the bundle's own Home is recreated
     * with its original id so a restored backup matches the original (AC-90).
     */
    suspend fun plan(bundle: RecordBundle): ImportPlan {
        val localHome = db.homeDao().getAll().firstOrNull()
        if (localHome == null) {
            val plan = ImportPlanner.plan(bundle, bundle.home.id, emptyMap())
            return plan.copy(homeToCreate = bundle.home.copy(source = Source.IMPORT))
        }
        val sources = HashMap<String, Source>()
        db.roomDao().getAll().forEach { sources[it.id] = it.source }
        db.wallDao().getAll().forEach { sources[it.id] = it.source }
        db.panelDao().getAll().forEach { sources[it.id] = it.source }
        db.breakerDao().getAll().forEach { sources[it.id] = it.source }
        db.breakerRoomDao().getAll().forEach { sources[it.id] = it.source }
        db.endpointDao().getAll().forEach { sources[it.id] = it.source }
        db.shutoffDao().getAll().forEach { sources[it.id] = it.source }
        db.equipmentDao().getAll().forEach { sources[it.id] = it.source }
        db.photoDao().getAll().forEach { sources[it.id] = it.source }
        db.consumableDao().getAll().forEach { sources[it.id] = it.source }
        db.maintenanceTaskDao().getAll().forEach { sources[it.id] = it.source }
        // One load profile per Home: an imported one takes the id of the local one so it can only conflict, never duplicate.
        val localLoad = db.loadProfileDao().getForHome(localHome.id)
        localLoad?.let { sources[it.id] = it.source }
        val aligned = if (localLoad == null) bundle else bundle.copy(
            loadProfiles = bundle.loadProfiles.take(1).map { it.copy(id = localLoad.id) },
        )
        return ImportPlanner.plan(aligned, localHome.id, sources)
    }

    /**
     * Inserts new records, then applies [choices] to conflicts (missing = keep mine). Media is copied from
     * [stagingDir] for every record written. Re-running the same plan inserts nothing new (upserts by id).
     */
    suspend fun apply(plan: ImportPlan, choices: Map<String, ConflictChoice>, stagingDir: File): ImportResult {
        val imported = plan.conflicting.pickChosen(choices)
        val keptMine = plan.conflicts.size - plan.conflicts.count { choices[it.id] == ConflictChoice.USE_IMPORTED }
        db.withTransaction { plan.homeToCreate?.let { db.homeDao().upsert(it.toEntity()) }; write(plan.toInsert); write(imported) }
        var copied = 0
        val replacedIds = choices.filterValues { it == ConflictChoice.USE_IMPORTED }.keys
        for (path in plan.toInsert.mediaPaths() + imported.mediaPaths()) {
            if (copyMedia(path, stagingDir)) copied++
        }
        return ImportResult(
            inserted = plan.newTotal,
            replaced = plan.conflicts.count { it.id in replacedIds },
            keptMine = keptMine,
            mediaCopied = copied,
        )
    }

    private suspend fun write(b: RecordBundle) {
        b.rooms.forEach { db.roomDao().upsert(it.toEntity()) }
        b.walls.forEach { db.wallDao().upsert(it.toEntity()) }
        b.panels.forEach { db.panelDao().upsert(it.toEntity()) }
        b.breakers.forEach { db.breakerDao().upsert(it.toEntity()) }
        b.breakerRooms.forEach { db.breakerRoomDao().upsert(it.toEntity()) }
        b.endpoints.forEach { db.endpointDao().upsert(it.toEntity()) }
        b.shutoffs.forEach { db.shutoffDao().upsert(it.toEntity()) }
        b.equipment.forEach { db.equipmentDao().upsert(it.toEntity()) }
        b.photos.forEach { db.photoDao().upsert(it.toEntity()) }
        db.consumableDao().upsertAll(b.consumables.map { it.toEntity() })
        db.maintenanceTaskDao().upsertAll(b.maintenanceTasks.map { it.toEntity() })
        b.loadProfiles.forEach { db.loadProfileDao().upsert(it.toEntity()) }
    }

    private fun copyMedia(path: String, stagingDir: File): Boolean {
        val target = media.resolveForWrite(path) ?: return false
        val source = File(stagingDir, path)
        if (!source.isFile || target.exists()) return false
        source.copyTo(target)
        return true
    }

    private fun RecordBundle.pickChosen(choices: Map<String, ConflictChoice>): RecordBundle {
        fun use(id: String) = choices[id] == ConflictChoice.USE_IMPORTED
        return copy(
            rooms = rooms.filter { use(it.id) }, walls = walls.filter { use(it.id) },
            panels = panels.filter { use(it.id) }, breakers = breakers.filter { use(it.id) },
            breakerRooms = breakerRooms.filter { use(it.id) }, endpoints = endpoints.filter { use(it.id) },
            shutoffs = shutoffs.filter { use(it.id) }, equipment = equipment.filter { use(it.id) },
            photos = photos.filter { use(it.id) }, consumables = consumables.filter { use(it.id) },
            maintenanceTasks = maintenanceTasks.filter { use(it.id) }, loadProfiles = loadProfiles.filter { use(it.id) },
        )
    }
}
