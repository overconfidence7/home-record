package com.kurtz.homerecord.core.sync

import com.kurtz.homerecord.core.bundle.HomeBundleExporter
import com.kurtz.homerecord.core.bundle.HomeRowQuery
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.OutboxDao
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.net.apiCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.Path

/** `DELETE /homes/:id` (backend/src/routes/tenants.ts): the owner removes the home's rows and media from the server. */
fun interface HomeCloudCopyApi {
    suspend fun delete(homeId: String): ApiResult<Unit>
}

@Serializable data class DeletedHome(val id: String, val mediaRemoved: Int = 0)

interface HomeCloudCopyService {
    @DELETE("homes/{id}") suspend fun delete(@Path("id") id: String): DeletedHome
}

class RetrofitHomeCloudCopyApi(private val service: HomeCloudCopyService) : HomeCloudCopyApi {
    override suspend fun delete(homeId: String): ApiResult<Unit> = apiCall { service.delete(homeId); Unit }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String, client: Call.Factory): RetrofitHomeCloudCopyApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .callFactory(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitHomeCloudCopyApi(retrofit.create(HomeCloudCopyService::class.java))
        }
    }
}

/**
 * HM-Account "Delete cloud copy" (AC-H15). The server removes the home first; only then does this phone sign out and
 * forget what it had sent, so the home here is untouched and a later sign-in uploads it in full: the tenant's sync
 * cursor, confirmed rows and outbox are dropped, every R2 key the deleted media had is cleared (the files go up again
 * after that first sync), and every row of the home is queued again. A failed delete changes nothing and signs no one out.
 */
class HomeCloudCopy(
    private val api: HomeCloudCopyApi,
    private val engine: SyncEngine,
    private val store: RowStore,
    private val rows: HomeRowQuery,
    private val outbox: OutboxDao,
    private val signOut: () -> Unit,
) {
    suspend fun delete(homeId: String): ApiResult<Unit> {
        val result = api.delete(homeId)
        if (result !is ApiResult.Ok) return result
        // Signed out first, so no sync pass runs while the tenant is reset.
        signOut()
        resetForUpload(homeId)
        return result
    }

    /** Makes this phone's copy of [homeId] look never synced: everything is queued to go up on the next sign-in. */
    suspend fun resetForUpload(homeId: String) {
        val tenant = TenantRef(TenantKind.Home, homeId)
        outbox.deleteForTenant(tenant.kind.wire, tenant.id)
        engine.forgetTenant(tenant)
        val records = HomeBundleExporter(rows).export(homeId, Sources.HOUSE_MANUAL)?.bundle?.records ?: return
        for ((entity, list) in records) {
            if (entity !in HOME_TABLES) continue
            for (record in list) {
                val id = record.id.ifEmpty { continue }
                clearR2Keys(entity, id)
                engine.recordChange(tenant, entity, id)
            }
        }
    }

    private suspend fun clearR2Keys(entity: String, id: String) {
        val row = store.read(entity, id) ?: return
        val keys = R2_COLUMNS.filter { row[it] != null && row[it] != JsonNull }
        if (keys.isEmpty()) return
        store.write(entity, JsonObject(row + keys.associateWith { JsonNull }))
    }

    companion object {
        /** Columns naming an object in R2 ([com.kurtz.homerecord.core.media.UploadedKeys]). */
        private val R2_COLUMNS = listOf("r2Key", "textureR2Key")

        /** The home tenant's tables; a signed change order in the export belongs to a company, not the home. */
        private val HOME_TABLES = setOf(
            "home", "floor", "room", "wall", "wall_photo", "opening", "stair", "overlay", "item", "warranty", "consumable",
            "service_record", "panel", "breaker", "breaker_link", "document", "photo", "contact", "maintenance_task",
        )
    }
}
