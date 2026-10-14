package com.kurtz.homerecord.core.repo

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Source
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HomeRepository(
    private val db: HomeRecordDatabase,
    private val source: Source,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    fun observeHome(): Flow<Home?> = db.homeDao().observeFirst().map { it?.toDomain() }

    suspend fun getHome(): Home? = db.homeDao().getAll().firstOrNull()?.toDomain()

    suspend fun createHome(name: String, address: String? = null, yearBuilt: Int? = null, climateZone: String? = null): Home {
        val now = clock.now()
        val home = Home(ids.next(), name.trim(), address, yearBuilt, climateZone, now, now, source)
        db.homeDao().upsert(home.toEntity())
        return home
    }

    /** Creates the home and its rooms in one transaction (onboarding, AC-3). Blank and duplicate room names are skipped. */
    suspend fun createHomeWithRooms(name: String, roomNames: List<String>): Home = db.withTransaction {
        val home = createHome(name)
        roomNames.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            .forEach { addRoom(home.id, it) }
        home
    }

    suspend fun updateHome(home: Home) {
        db.homeDao().upsert(home.copy(updatedAt = clock.now()).toEntity())
    }

    fun observeRooms(homeId: String): Flow<List<Room>> =
        db.roomDao().observeForHome(homeId).map { list -> list.map { it.toDomain() } }

    suspend fun getRoom(id: String): Room? = db.roomDao().get(id)?.toDomain()

    suspend fun addRoom(homeId: String, name: String, floor: String? = null): Room {
        val now = clock.now()
        val room = Room(ids.next(), homeId, name.trim(), floor, now, now, source)
        db.roomDao().upsert(room.toEntity())
        return room
    }

    suspend fun renameRoom(id: String, name: String) {
        val room = db.roomDao().get(id)?.toDomain() ?: return
        db.roomDao().upsert(room.copy(name = name.trim(), updatedAt = clock.now()).toEntity())
    }

    suspend fun deleteRoom(id: String) {
        val room = db.roomDao().get(id) ?: return
        db.roomDao().delete(room)
    }
}
