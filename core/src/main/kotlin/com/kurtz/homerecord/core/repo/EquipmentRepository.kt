package com.kurtz.homerecord.core.repo

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EquipmentRepository(
    private val db: HomeRecordDatabase,
    private val media: MediaFileStore,
    private val source: Source,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    fun observeEquipment(homeId: String): Flow<List<Equipment>> =
        db.equipmentDao().observeForHome(homeId).map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): Equipment? = db.equipmentDao().get(id)?.toDomain()

    /** Inserts (blank id) or updates; a replaced label photo is removed from storage. */
    suspend fun save(equipment: Equipment): Equipment {
        val now = clock.now()
        val stored: Equipment
        if (equipment.id.isBlank()) {
            stored = equipment.copy(id = ids.next(), createdAt = now, updatedAt = now, source = source)
        } else {
            val old = db.equipmentDao().get(equipment.id)?.toDomain()
            if (old?.labelPhotoPath != null && old.labelPhotoPath != equipment.labelPhotoPath) {
                media.delete(old.labelPhotoPath)
            }
            stored = equipment.copy(updatedAt = now)
        }
        db.equipmentDao().upsert(stored.toEntity())
        return stored
    }

    suspend fun delete(id: String) {
        val entity = db.equipmentDao().get(id) ?: return
        media.delete(entity.labelPhotoPath)
        db.equipmentDao().delete(entity)
    }
}
