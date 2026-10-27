package com.kurtz.homerecord.coreui.pages

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Leaving the viewer while a page renders must not close the PDF under the render (cr:pdf-close-race). */
@OptIn(ExperimentalCoroutinesApi::class)
class RenderGateTest {
    @Test fun closeWaitsForTheRunningRenderThenRefusesNewOnes() = runTest {
        val gate = RenderGate(StandardTestDispatcher(testScheduler))
        val events = mutableListOf<String>()
        val pageOpen = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val render = async {
            gate.use {
                events += "render start"
                pageOpen.complete(Unit)
                finish.await()
                events += "render end"
                "page"
            }
        }
        pageOpen.await()
        gate.close { events += "closed" }
        testScheduler.advanceUntilIdle()
        assertThat(events).containsExactly("render start").inOrder()

        finish.complete(Unit)
        assertThat(render.await()).isEqualTo("page")
        testScheduler.advanceUntilIdle()
        assertThat(events).containsExactly("render start", "render end", "closed").inOrder()

        assertThat(gate.use { events += "late render"; "x" }).isNull()
        assertThat(events).doesNotContain("late render")
    }

    @Test fun closeRunsOnce() = runTest {
        val gate = RenderGate(StandardTestDispatcher(testScheduler))
        var closes = 0
        gate.close { closes++ }
        gate.close { closes++ }
        testScheduler.advanceUntilIdle()
        assertThat(closes).isEqualTo(1)
    }
}
