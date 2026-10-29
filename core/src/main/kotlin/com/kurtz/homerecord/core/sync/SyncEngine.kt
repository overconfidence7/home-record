package com.kurtz.homerecord.core.sync

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.OutboxDao
import com.kurtz.homerecord.core.data.OutboxEntity
import com.kurtz.homerecord.core.data.SyncBaseDao
import com.kurtz.homerecord.core.data.SyncBaseEntity
import com.kurtz.homerecord.core.data.SyncCursorDao
import com.kurtz.homerecord.core.data.SyncCursorEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Local-first sync (AC-F53, AC-B20). Every local write calls [recordChange], which appends the row to the
 * outbox. [sync] pushes pending rows in order, then pulls everything after the tenant's last `serverSeq` (one cursor
 * per tenant, since the server counts per tenant) and applies each row in a transaction.
 * One sync runs at a time; a failed pass leaves the outbox untouched so the next one retries without duplicates
 * (the server dedupes by `(deviceId, outboxId)`). An op the server refuses on its own is set aside
 * ([OutboxEntity.serverSeq] = [REFUSED]) and logged in the result, so it never blocks the changes behind it; a pulled
 * row that cannot be stored is skipped and logged the same way. Any other error ends the pass as [SyncPhase.Failed].
 */
class SyncEngine(
    private val api: SyncApi,
    private val store: RowStore,
    private val outbox: OutboxDao,
    private val cursors: SyncCursorDao,
    private val base: SyncBaseDao,
    val deviceId: String,
    private val now: () -> Long = System::currentTimeMillis,
    /** Runs before each pass; House Manual registers its offline-made home here (AC-H15). An error ends the pass. */
    private val prepare: suspend (TenantRef) -> ApiResult<Unit> = { ApiResult.Ok(Unit) },
) {
    private val lock = Mutex()
    private val _phase = MutableStateFlow(SyncPhase.Idle)
    val phase: StateFlow<SyncPhase> = _phase

    /** Count of changes waiting to be sent, for the Today chip (AC-F52). */
    val pendingCount: Flow<Int> = outbox.observePendingCount()

    /** Count of changes the server refused and that will not be sent again. */
    val refusedCount: Flow<Int> = outbox.observeRefusedCount(REFUSED)

    /** The refused changes, newest first, one per record (a record refused twice shows its latest change). */
    val refused: Flow<List<RefusedChange>> = outbox.observeRefused(REFUSED).map { rows ->
        rows.distinctBy { it.entity to it.entityId }.map { RefusedChange(it.entity, it.entityId, it.updatedAt) }
    }

    /** Clears the refused list once the user has seen it; the records themselves stay as they are on this phone. */
    suspend fun clearRefused() {
        outbox.clearRefused(REFUSED)
    }

    /**
     * Queue the current stored row of [entity]/[entityId] (soft deletes included) for the next push. The payload holds
     * only the fields that differ from the last server-confirmed row, so two devices editing different fields merge.
     */
    suspend fun recordChange(tenant: TenantRef, entity: String, entityId: String) {
        val row = store.read(entity, entityId)?.let { LocalColumns.strip(entity, it) } ?: return
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
            prepare(tenant).let { if (it is ApiResult.Err) return@withLock fail(it.failure) }
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
                val since = cursors.get(tenant.kind.wire, tenant.id) ?: 0L
                val page = when (val res = api.pull(tenant, since)) {
                    is ApiResult.Err -> return@withLock fail(res.failure)
                    is ApiResult.Ok -> res.value
                }
                for (row in page.rows) {
                    try {
                        apply(tenant, row)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        conflicts += ConflictNote(row.entity, row.entityId, "", "unstored:${e.javaClass.simpleName}")
                    }
                }
                pulled += page.rows.size
                if (page.serverSeq > since) cursors.set(SyncCursorEntity(tenant.kind.wire, tenant.id, page.serverSeq))
            } while (page.more)
            // Accepted rows the pull has confirmed are done; keep the outbox bounded (cr:outbox-growth). Outbox ids
            // are AUTOINCREMENT, so a pruned id is never reused and the server's (device, outbox id) dedupe holds.
            cursors.get(tenant.kind.wire, tenant.id)?.let { outbox.pruneConfirmed(tenant.kind.wire, tenant.id, it) }
            _phase.value = SyncPhase.Idle
            ApiResult.Ok(SyncResult(pushed, pulled, conflicts))
        } catch (e: kotlinx.coroutines.CancellationException) {
            _phase.value = SyncPhase.Idle
            throw e
        } catch (e: Exception) {
            fail(ApiFailure.Failed)
        }
    }

    /** Runs [block] with no sync pass in flight; sign-out wipes the database inside it. */
    suspend fun <T> exclusive(block: suspend () -> T): T = lock.withLock { block() }

    /**
     * Drops what this device knows of [tenant]'s server state (its pull cursor and confirmed rows) once the membership
     * ends, so a later return pulls from the start. Unsent outbox rows are kept; signing out decides about those.
     */
    suspend fun forgetTenant(tenant: TenantRef) {
        cursors.delete(tenant.kind.wire, tenant.id)
        base.deleteForTenant(tenant.key)
    }

    /**
     * Read, merge and write in one transaction so a local edit cannot land between the read and the write. Only the
     * fields in this row's unsent outbox payloads count as this device's edits (cr:sync-merge-race). The server row's
     * device-local columns (sent by older builds) are ignored, so this phone keeps its own path and offline choice.
     */
    private suspend fun apply(tenant: TenantRef, pulledRow: PulledRow) = store.inTransaction {
        val serverRow = LocalColumns.strip(pulledRow.entity, pulledRow.row)
        val local = store.read(pulledRow.entity, pulledRow.entityId)
        val payloads = outbox.pendingPayloadsFor(pulledRow.entity, pulledRow.entityId)
        val changedHere = payloads.flatMapTo(mutableSetOf()) { payload ->
            (runCatching { Json.parseToJsonElement(payload) }.getOrNull() as? JsonObject)?.keys ?: local?.keys.orEmpty()
        }
        val merged = RowMerge.applyPulled(pulledRow.entity, local, payloads.isNotEmpty(), serverRow, pulledRow.times, changedHere)
        if (merged != local) store.write(pulledRow.entity, merged)
        base.set(SyncBaseEntity("${pulledRow.entity}/${pulledRow.entityId}", serverRow.toString(), tenant.key))
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
