package com.kurtz.homerecord.core.repo

import com.kurtz.homerecord.core.bundle.RecordBundle
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain

/** Gathers every shareable entity for one Home into a [RecordBundle] (SPEC §6, AC-73). */
class HandoffRepository(
    private val db: HomeRecordDatabase,
    private val clock: Clock = Clock.System,
) {
    /** Null when the Home no longer exists. `source` on each record is preserved as stored. */
    suspend fun bundleFor(homeId: String): RecordBundle? {
        val home = db.homeDao().get(homeId)?.toDomain() ?: return null
        val rooms = db.roomDao().getAll().filter { it.homeId == homeId }
        val roomIds = rooms.map { it.id }.toSet()
        val panels = db.panelDao().getAll().filter { it.homeId == homeId }
        val breakers = db.breakerDao().getAll().filter { b -> panels.any { it.id == b.panelId } }
        val breakerIds = breakers.map { it.id }.toSet()
        return RecordBundle(
            exportedAt = clock.now(),
            home = home,
            rooms = rooms.map { it.toDomain() },
            walls = db.wallDao().getAll().filter { it.roomId in roomIds }.map { it.toDomain() },
            panels = panels.map { it.toDomain() },
            breakers = breakers.map { it.toDomain() },
            breakerRooms = db.breakerRoomDao().getAll().filter { it.breakerId in breakerIds }.map { it.toDomain() },
            endpoints = db.endpointDao().getAll()
                .filter { it.roomId in roomIds || (it.breakerId != null && it.breakerId in breakerIds) }
                .map { it.toDomain() },
            shutoffs = db.shutoffDao().getAll().filter { it.homeId == homeId }.map { it.toDomain() },
            equipment = db.equipmentDao().getAll().filter { it.homeId == homeId }.map { it.toDomain() },
            photos = db.photoDao().getAll().filter { it.homeId == homeId }.map { it.toDomain() },
        )
    }
}
