package com.kurtz.homerecord.core.repo

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Consumable
import com.kurtz.homerecord.core.model.ConsumableKind
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.Source
import androidx.room.withTransaction
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

    fun observeConsumables(homeId: String): Flow<List<Consumable>> =
        db.consumableDao().observeForHome(homeId).map { list -> list.map { it.toDomain() } }

    suspend fun getConsumables(equipmentId: String): List<Consumable> =
        db.consumableDao().getForEquipment(equipmentId).map { it.toDomain() }

    /** AC-99: replaces the equipment's consumables with [items] (blank specs are dropped). */
    suspend fun saveConsumables(equipmentId: String, items: List<ConsumableDraft>) {
        val now = clock.now()
        val entities = items.filter { it.spec.isNotBlank() }.mapIndexed { i, d ->
            Consumable(
                id = ids.next(), equipmentId = equipmentId, kind = d.kind, spec = d.spec.trim(),
                createdAt = now + i, updatedAt = now, source = source,
            ).toEntity()
        }
        db.withTransaction {
            db.consumableDao().deleteForEquipment(equipmentId)
            db.consumableDao().upsertAll(entities)
        }
    }

    suspend fun delete(id: String) {
        val entity = db.equipmentDao().get(id) ?: return
        media.delete(entity.labelPhotoPath)
        db.equipmentDao().delete(entity)
    }
}

data class ConsumableDraft(val kind: ConsumableKind, val spec: String)
