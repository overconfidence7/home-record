package com.kurtz.homerecord.core.data

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReloadableTest {
    // AC-U4: a list whose read throws shows its error state, and Retry reads it again
    @AcceptanceCriterion("AC-U4")
    @Test fun aFailedLoadEmitsTheFailedStateAndRetryReadsAgain() = runTest(UnconfinedTestDispatcher()) {
        var calls = 0
        val causes = mutableListOf<Throwable>()
        val load = Reloadable("Test") { causes += it }
        val seen = mutableListOf<String>()
        val job = load.wrap("failed") {
            flow {
                calls++
                if (calls == 1) throw IllegalStateException("database disk image is malformed")
                emit("rows")
            }
        }.onEach { seen += it }.launchIn(this)
        assertThat(seen).containsExactly("failed")
        assertThat(causes.single()).hasMessageThat().contains("malformed")
        load.retry()
        assertThat(seen).containsExactly("failed", "rows").inOrder()
        assertThat(calls).isEqualTo(2)
        job.cancel()
    }
}
