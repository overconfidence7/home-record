package com.kurtz.homerecord.core.bundle

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Consumable
import com.kurtz.homerecord.core.model.LoadProfile
import com.kurtz.homerecord.core.model.MaintenanceTask
import com.kurtz.homerecord.core.model.BreakerRoom
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.Photo
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.Wall
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Contents of `record.json`: every entity for one Home plus Consumable, MaintenanceTask and LoadProfile, except Job, LogEntry, DailyReport, CompanyProfile (SPEC §6). */
@Serializable
data class RecordBundle(
    val formatVersion: Int = RecordJson.FORMAT_VERSION,
    val exportedAt: Long = 0L,
    val home: Home,
    val rooms: List<Room> = emptyList(),
    val walls: List<Wall> = emptyList(),
    val panels: List<Panel> = emptyList(),
    val breakers: List<Breaker> = emptyList(),
    val breakerRooms: List<BreakerRoom> = emptyList(),
    val endpoints: List<Endpoint> = emptyList(),
    val shutoffs: List<Shutoff> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val photos: List<Photo> = emptyList(),
    // Optional arrays added after format 1 shipped: older bundles omit them and still import.
    val consumables: List<Consumable> = emptyList(),
    val maintenanceTasks: List<MaintenanceTask> = emptyList(),
    val loadProfiles: List<LoadProfile> = emptyList(),
) {
    /** Relative media paths referenced by any record, in stable order, without duplicates. */
    fun mediaPaths(): List<String> = buildList {
        panels.forEach { it.photoPath?.let(::add) }
        shutoffs.forEach { it.photoPath?.let(::add) }
        equipment.forEach { it.labelPhotoPath?.let(::add) }
        photos.forEach { add(it.path) }
    }.distinct()
}

/** Thrown for a bundle that cannot be read; [message] is safe to show the user. */
class BundleFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object RecordJson {
    const val FORMAT_VERSION = 1

    private val json = Json {
        encodeDefaults = true
        // Newer writers may add fields; older readers keep what they understand.
        ignoreUnknownKeys = true
    }

    fun encode(bundle: RecordBundle): String = json.encodeToString(RecordBundle.serializer(), bundle)

    /** Rejects a `formatVersion` greater than [FORMAT_VERSION] before touching any other field (AC-74). */
    fun decode(text: String): RecordBundle {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: SerializationException) {
            null
        } ?: throw BundleFormatException("This file is not a valid HomeRecord bundle.")
        val version = try {
            root["formatVersion"]?.jsonPrimitive?.int
        } catch (e: IllegalArgumentException) {
            null
        } ?: throw BundleFormatException("This file has no readable format version.")
        if (version > FORMAT_VERSION) {
            throw BundleFormatException(
                "This bundle was made by a newer version of HomeRecord (format $version). Update the app to open it.",
            )
        }
        return try {
            json.decodeFromJsonElement(RecordBundle.serializer(), root)
        } catch (e: SerializationException) {
            throw BundleFormatException("This bundle is damaged or incomplete.", e)
        } catch (e: IllegalArgumentException) {
            throw BundleFormatException("This bundle is damaged or incomplete.", e)
        }
    }
}
