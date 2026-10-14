package com.kurtz.homerecord.core.repo

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ShutoffRepository(
    private val db: HomeRecordDatabase,
    private val media: MediaFileStore,
    private val source: Source,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    fun observeShutoffs(homeId: String): Flow<List<Shutoff>> =
        db.shutoffDao().observeForHome(homeId).map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): Shutoff? = db.shutoffDao().get(id)?.toDomain()

    /** Inserts (blank id) or updates; a replaced photo file is removed from storage. */
    suspend fun save(shutoff: Shutoff): Shutoff {
        val now = clock.now()
        val stored: Shutoff
        if (shutoff.id.isBlank()) {
            stored = shutoff.copy(id = ids.next(), createdAt = now, updatedAt = now, source = source)
        } else {
            val old = db.shutoffDao().get(shutoff.id)?.toDomain()
            if (old?.photoPath != null && old.photoPath != shutoff.photoPath) media.delete(old.photoPath)
            stored = shutoff.copy(updatedAt = now)
        }
        db.shutoffDao().upsert(stored.toEntity())
        return stored
    }

    suspend fun delete(id: String) {
        val entity = db.shutoffDao().get(id) ?: return
        media.delete(entity.photoPath)
        db.shutoffDao().delete(entity)
    }
}
