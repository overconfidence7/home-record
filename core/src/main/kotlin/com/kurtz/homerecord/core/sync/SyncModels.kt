package com.kurtz.homerecord.core.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** The tenant a sync runs against: a company (Field Log) or a home (House Manual). */
data class TenantRef(val kind: TenantKind, val id: String)

enum class TenantKind(val wire: String, val path: String) {
    Company("company", "companies"),
    Home("home", "homes"),
}

@Serializable
data class PushOp(
    val outboxId: Long,
    val entity: String,
    val entityId: String,
    val op: String,
    val payload: String,
    val updatedAt: Long,
)

@Serializable
data class PushRequest(val deviceId: String, val ops: List<PushOp>)

@Serializable
data class Accepted(val outboxId: Long, val serverSeq: Long)

@Serializable
data class ConflictNote(val entity: String, val entityId: String, val field: String, val reason: String)

/** An op the server refused on its own (unknown entity for the tenant, unreadable payload); the rest of the batch applied. */
@Serializable
data class Rejected(val outboxId: Long, val reason: String)

@Serializable
data class PushResponse(
    val accepted: List<Accepted> = emptyList(),
    val conflicts: List<ConflictNote> = emptyList(),
    val rejected: List<Rejected> = emptyList(),
)

@Serializable
data class PulledRow(
    val entity: String,
    val entityId: String,
    val row: JsonObject,
    val times: Map<String, Long> = emptyMap(),
    val serverSeq: Long,
)

@Serializable
data class PullResponse(val rows: List<PulledRow> = emptyList(), val serverSeq: Long = 0, val more: Boolean = false)

/** What one [SyncEngine.sync] pass did; [conflicts] is the log of writes the server refused or dropped. */
data class SyncResult(val pushed: Int, val pulled: Int, val conflicts: List<ConflictNote>)

enum class SyncPhase { Idle, Syncing, Offline, Failed }
