package com.kurtz.homerecord.core.sync

import kotlinx.serialization.json.Json

/**
 * Sizes push batches to the Worker's limits (cr:sync-limits): at most [MAX_OPS] ops and [BUDGET_BYTES] of encoded ops,
 * half the Worker's 2 MiB body limit (`PUSH_MAX_BYTES` in backend/src/routes/sync.ts). An op whose payload is over
 * [MAX_PAYLOAD] characters is refused by the Worker on its own, so it is set aside here without being sent.
 */
object PushBatches {
    const val MAX_OPS = 200
    const val BUDGET_BYTES = 1024 * 1024
    const val MAX_PAYLOAD = 256_000

    fun tooLarge(payload: String): Boolean = payload.length > MAX_PAYLOAD

    /** Bytes [op] adds to the request body, separator included. */
    fun encodedSize(op: PushOp): Int = Json.encodeToString(PushOp.serializer(), op).toByteArray(Charsets.UTF_8).size + 1

    /** How many of the leading [ops] fit in one push; at least one, so a single large op still goes out on its own. */
    fun fit(ops: List<PushOp>): Int {
        var bytes = 0
        var count = 0
        for (op in ops.take(MAX_OPS)) {
            bytes += encodedSize(op)
            if (count > 0 && bytes > BUDGET_BYTES) break
            count++
        }
        return count
    }
}
