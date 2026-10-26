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
 * (the server dedupes by `(deviceId, outboxId)`). An op the server refuses on its own is set aside
 * ([OutboxEntity.serverSeq] = [REFUSED]) and logged in the result, so it never blocks the changes behind it; a pulled
 * row that cannot be stored is skipped and logged the same way. Any other error ends the pass as [SyncPhase.Failed].
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

    /** Count of changes the server refused and that will not be sent again. */
    val refusedCount: Flow<Int> = outbox.observeRefusedCount(REFUSED)

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

    private suspend fun confirmedRow(key: String): JsonObject? =
        base.get(key)?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

    private suspend fun changedFields(entity: String, entityId: String, row: JsonObject): JsonObject {
        val confirmed = confirmedRow("$entity/$entityId") ?: return row
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
                        val bySeq = batch.associateBy { it.seq }
                        res.value.rejected.forEach { r ->
                            outbox.markSent(r.outboxId, REFUSED)
                            bySeq[r.outboxId]?.let { conflicts += ConflictNote(it.entity, it.entityId, "", "refused:${r.reason}") }
                        }
                        conflicts += res.value.conflicts
                        pushed += res.value.accepted.size
                        if (res.value.accepted.size + res.value.rejected.size < batch.size) return@withLock fail(ApiFailure.Failed)
                    }
                }
            }
            do {
                val since = state.get()?.lastServerSeq ?: 0L
                val page = when (val res = api.pull(tenant, since)) {
                    is ApiResult.Err -> return@withLock fail(res.failure)
                    is ApiResult.Ok -> res.value
                }
                for (row in page.rows) {
                    try {
                        apply(row)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        conflicts += ConflictNote(row.entity, row.entityId, "", "unstored:${e.javaClass.simpleName}")
                    }
                }
                pulled += page.rows.size
                if (page.serverSeq > since) state.set(SyncStateEntity(lastServerSeq = page.serverSeq))
            } while (page.more)
            _phase.value = SyncPhase.Idle
            ApiResult.Ok(SyncResult(pushed, pulled, conflicts))
        } catch (e: kotlinx.coroutines.CancellationException) {
            _phase.value = SyncPhase.Idle
            throw e
        } catch (e: Exception) {
            fail(ApiFailure.Failed)
        }
    }

    private suspend fun apply(pulledRow: PulledRow) {
        val local = store.read(pulledRow.entity, pulledRow.entityId)
        val pending = outbox.pendingCountFor(pulledRow.entity, pulledRow.entityId) > 0
        val key = "${pulledRow.entity}/${pulledRow.entityId}"
        val confirmed = if (pending) confirmedRow(key) else null
        val merged: JsonObject = RowMerge.applyPulled(pulledRow.entity, local, pending, pulledRow.row, pulledRow.times, confirmed)
        if (merged != local) store.write(pulledRow.entity, merged)
        base.set(SyncBaseEntity(key, pulledRow.row.toString()))
    }

    private fun fail(failure: ApiFailure): ApiResult<SyncResult> {
        _phase.value = if (failure == ApiFailure.Offline) SyncPhase.Offline else SyncPhase.Failed
        return ApiResult.Err(failure)
    }

    companion object {
        private const val BATCH = 200

        /** `serverSeq` of an outbox row the server refused: marked sent so it stops blocking, kept for the log. */
        const val REFUSED = -1L
    }
}
