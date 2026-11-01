package com.kurtz.homerecord.core.sync

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import kotlinx.serialization.json.JsonObject

/** In-memory stand-in for the Worker: serverSeq per tenant, (device, outbox) dedupe and per-field last-writer-wins. */
internal class FakeSyncServer : SyncApi {
    var online = true
    var pushCalls = 0
    /** Encoded size of each push's ops, as the Worker's body limit sees them. */
    val pushBytes = mutableListOf<Int>()
    val pullsSince = mutableListOf<Pair<String, Long>>()
    /** Entities this tenant refuses, as the Worker does for a row outside the tenant's list. */
    var refused = emptySet<String>()
    var throwOnPull: Exception? = null
    val extraPulled = mutableListOf<PulledRow>()
    private val seqs = HashMap<String, Long>()
    private val tenants = HashMap<String, LinkedHashMap<String, PulledRow>>()
    private val applied = HashMap<Pair<String, Long>, Long>()
    /** Ops applied for the first time; a retried (device, outbox) pair is answered from [applied] and not counted. */
    var appliedOps = 0
    /** Applies the next push, then answers as if the connection dropped, so the phone retries the same rows. */
    var loseNextResponse = false

    /** Rows the server holds for [tenant], one per record. */
    fun rows(tenant: TenantRef): List<PulledRow> = tenants[tenant.key]?.values.orEmpty().toList()

    override suspend fun push(tenant: TenantRef, deviceId: String, ops: List<PushOp>): ApiResult<PushResponse> {
        if (!online) return ApiResult.Err(ApiFailure.Offline)
        pushCalls++
        pushBytes += ops.sumOf { PushBatches.encodedSize(it) }
        val rows = tenants.getOrPut(tenant.key) { LinkedHashMap() }
        val rejected = ops.filter { it.entity in refused }.map { Rejected(it.outboxId, "entity") }
        val conflicts = mutableListOf<ConflictNote>()
        val accepted = ops.filter { it.entity !in refused }.map { op ->
            val serverSeq = applied.getOrPut(deviceId to op.outboxId) {
                val incoming = kotlinx.serialization.json.Json.parseToJsonElement(op.payload) as JsonObject
                val key = "${op.entity}/${op.entityId}"
                val old = rows[key]
                val row = LinkedHashMap(old?.row ?: emptyMap())
                val times = HashMap(old?.times ?: emptyMap())
                for ((f, v) in incoming) {
                    if (op.updatedAt >= (times[f] ?: Long.MIN_VALUE)) { row[f] = v; times[f] = op.updatedAt }
                    else if (row[f] != v) conflicts += ConflictNote(op.entity, op.entityId, f, "older")
                }
                appliedOps++
                val seq = (seqs[tenant.key] ?: 0L) + 1
                seqs[tenant.key] = seq
                rows[key] = PulledRow(op.entity, op.entityId, JsonObject(row), times, seq)
                seq
            }
            Accepted(op.outboxId, serverSeq)
        }
        if (loseNextResponse) {
            loseNextResponse = false
            return ApiResult.Err(ApiFailure.Offline)
        }
        return ApiResult.Ok(PushResponse(accepted, conflicts, rejected))
    }

    override suspend fun pull(tenant: TenantRef, since: Long): ApiResult<PullResponse> {
        if (!online) return ApiResult.Err(ApiFailure.Offline)
        throwOnPull?.let { throw it }
        pullsSince += tenant.id to since
        val rows = tenants[tenant.key]?.values.orEmpty()
        val out = (rows + extraPulled).filter { it.serverSeq > since }.sortedBy { it.serverSeq }
        return ApiResult.Ok(PullResponse(out, out.lastOrNull()?.serverSeq ?: since))
    }
}
