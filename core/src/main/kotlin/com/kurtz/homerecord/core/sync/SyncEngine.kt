package com.kurtz.homerecord.core.sync

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.OutboxDao
import com.kurtz.homerecord.core.data.OutboxEntity
import com.kurtz.homerecord.core.data.SyncBaseDao
import com.kurtz.homerecord.core.data.SyncBaseEntity
import com.kurtz.homerecord.core.data.SyncStateDao
import com.kurtz.homerecord.core.data.SyncStateEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Local-first sync (AC-F53, AC-B20). Every local write calls [recordChange], which appends the row to the
 * outbox. [sync] pushes pending rows in order, then pulls everything after the last `serverSeq` and applies it.
 * One sync runs at a time; a failed pass leaves the outbox untouched so the next one retries without duplicates
 * (the server dedupes by `(deviceId, outboxId)`).
 */
class SyncEngine(
    private val api: SyncApi,
    private val store: RowStore,
    private val outbox: OutboxDao,
    private val state: SyncStateDao,
    private val base: SyncBaseDao,
    val deviceId: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()
    private val _phase = MutableStateFlow(SyncPhase.Idle)
    val phase: StateFlow<SyncPhase> = _phase

    /** Count of changes waiting to be sent, for the Today chip (AC-F52). */
    val pendingCount: Flow<Int> = outbox.observePendingCount()

    /**
     * Queue the current stored row of [entity]/[entityId] (soft deletes included) for the next push. The payload holds
     * only the fields that differ from the last server-confirmed row, so two devices editing different fields merge.
     */
    suspend fun recordChange(tenant: TenantRef, entity: String, entityId: String) {
        val row = store.read(entity, entityId) ?: return
        val updatedAt = row["updatedAt"]?.jsonPrimitive?.longOrNull ?: now()
        val deleted = row["deletedAt"]?.let { it.toString() != "null" } == true
        outbox.add(
            OutboxEntity(
                tenantKind = tenant.kind.wire,
                tenantId = tenant.id,
                entity = entity,
                entityId = entityId,
                op = if (deleted) "delete" else "upsert",
                payload = changedFields(entity, entityId, row).toString(),
                updatedAt = updatedAt,
                deviceId = deviceId,
            ),
        )
    }

    private suspend fun changedFields(entity: String, entityId: String, row: JsonObject): JsonObject {
        val confirmed = base.get("$entity/$entityId")?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?: return row
        return JsonObject(row.filter { (field, value) -> field == "id" || field == "updatedAt" || confirmed[field] != value })
    }

    suspend fun sync(tenant: TenantRef): ApiResult<SyncResult> = lock.withLock {
        _phase.value = SyncPhase.Syncing
        var pushed = 0
        var pulled = 0
        val conflicts = mutableListOf<ConflictNote>()
        try {
            while (true) {
                val batch = outbox.pendingFor(tenant.kind.wire, tenant.id, BATCH)
                if (batch.isEmpty()) break
                val ops = batch.map { PushOp(it.seq, it.entity, it.entityId, it.op, it.payload, it.updatedAt) }
                when (val res = api.push(tenant, deviceId, ops)) {
                    is ApiResult.Err -> return@withLock fail(res.failure)
                    is ApiResult.Ok -> {
                        res.value.accepted.forEach { outbox.markSent(it.outboxId, it.serverSeq) }
                        conflicts += res.value.conflicts
                        pushed += res.value.accepted.size
                        if (res.value.accepted.size < batch.size) return@withLock fail(ApiFailure.Failed)
                    }
                }
            }
            do {
                val since = state.get()?.lastServerSeq ?: 0L
                val page = when (val res = api.pull(tenant, since)) {
                    is ApiResult.Err -> return@withLock fail(res.failure)
                    is ApiResult.Ok -> res.value
                }
                page.rows.forEach { apply(it) }
                pulled += page.rows.size
                if (page.serverSeq > since) state.set(SyncStateEntity(lastServerSeq = page.serverSeq))
            } while (page.more)
            _phase.value = SyncPhase.Idle
            ApiResult.Ok(SyncResult(pushed, pulled, conflicts))
        } catch (e: kotlinx.coroutines.CancellationException) {
            _phase.value = SyncPhase.Idle
            throw e
        }
    }

    private suspend fun apply(pulledRow: PulledRow) {
        val local = store.read(pulledRow.entity, pulledRow.entityId)
        val pending = outbox.pendingCountFor(pulledRow.entity, pulledRow.entityId) > 0
        val merged: JsonObject = RowMerge.applyPulled(local, pending, pulledRow.row, pulledRow.times)
        if (merged != local) store.write(pulledRow.entity, merged)
        base.set(SyncBaseEntity("${pulledRow.entity}/${pulledRow.entityId}", pulledRow.row.toString()))
    }

    private fun fail(failure: ApiFailure): ApiResult<SyncResult> {
        _phase.value = if (failure == ApiFailure.Offline) SyncPhase.Offline else SyncPhase.Failed
        return ApiResult.Err(failure)
    }

    private companion object {
        const val BATCH = 200
    }
}
