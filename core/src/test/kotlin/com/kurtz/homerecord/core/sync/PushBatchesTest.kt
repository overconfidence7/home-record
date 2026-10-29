package com.kurtz.homerecord.core.sync

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AC-B20, cr:sync-limits: push batches fit the Worker's op count and body limits
class PushBatchesTest {
    private fun op(id: Long, payloadChars: Int) = PushOp(id, "task", "t$id", "upsert", "x".repeat(payloadChars), 10)

    @Test fun smallOpsFillABatchUpToTheOpLimit() {
        val ops = (1L..250L).map { op(it, 40) }
        assertThat(PushBatches.fit(ops)).isEqualTo(PushBatches.MAX_OPS)
    }

    @Test fun largeOpsStopAtTheByteBudget() {
        val ops = (1L..20L).map { op(it, 200_000) }
        val fits = PushBatches.fit(ops)
        assertThat(fits).isEqualTo(5)
        assertThat(ops.take(fits).sumOf { PushBatches.encodedSize(it) }).isAtMost(PushBatches.BUDGET_BYTES)
        assertThat(ops.take(fits + 1).sumOf { PushBatches.encodedSize(it) }).isGreaterThan(PushBatches.BUDGET_BYTES)
    }

    @Test fun aSingleOpOverTheBudgetStillGoesOutAlone() {
        // Control characters encode as \u0001 (6 bytes each), so a payload under the per-op limit can pass the budget;
        // it is sent on its own and still fits the Worker's 2 MiB body.
        val escaped = PushOp(1, "task", "t1", "upsert", "\u0001".repeat(PushBatches.MAX_PAYLOAD), 10)
        assertThat(PushBatches.encodedSize(escaped)).isGreaterThan(PushBatches.BUDGET_BYTES)
        assertThat(PushBatches.encodedSize(escaped)).isLessThan(2 * 1024 * 1024)
        assertThat(PushBatches.fit(listOf(escaped, op(2, 10)))).isEqualTo(1)
        assertThat(PushBatches.fit(listOf(op(2, 10), escaped))).isEqualTo(1)
    }

    @Test fun encodedSizeCountsUtf8Bytes() {
        val ascii = PushOp(1, "task", "t1", "upsert", "a".repeat(100), 10)
        val accented = PushOp(1, "task", "t1", "upsert", "é".repeat(100), 10)
        assertThat(PushBatches.encodedSize(accented) - PushBatches.encodedSize(ascii)).isEqualTo(100)
    }

    @Test fun payloadOverTheWorkerLimitIsTooLarge() {
        assertThat(PushBatches.tooLarge("x".repeat(PushBatches.MAX_PAYLOAD))).isFalse()
        assertThat(PushBatches.tooLarge("x".repeat(PushBatches.MAX_PAYLOAD + 1))).isTrue()
        assertThat(PushBatches.fit(emptyList())).isEqualTo(0)
    }
}
