package com.kurtz.homerecord.core.units

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C4: feet and inches by default, metric once picked in Settings, and the choice survives a restart
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsUnitsStoreTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("units_test", Context.MODE_PRIVATE)

    /** Reads and writes run straight away, as if the IO pool had already finished. */
    private fun store() = PrefsUnitsStore({ prefs }, CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)

    @Test fun feetAndInchesByDefault() {
        prefs.edit().clear().commit()
        assertThat(store().units.value).isEqualTo(UnitSystem.IMPERIAL)
    }

    @Test fun metricSticksAcrossRestarts() {
        prefs.edit().clear().commit()
        val store = store()
        store.set(UnitSystem.METRIC)
        assertThat(store.units.value).isEqualTo(UnitSystem.METRIC)
        assertThat(store().units.value).isEqualTo(UnitSystem.METRIC)
    }

    @Test fun anUnknownValueReadsAsFeetAndInches() {
        prefs.edit().putString("units", "CUBITS").commit()
        assertThat(store().units.value).isEqualTo(UnitSystem.IMPERIAL)
    }

    // [r12:units-store-main-thread]: building the store touches no preferences; the read runs on the IO dispatcher
    @Test fun theStoredChoiceIsReadOffTheCallersThreadAndAPickMadeFirstWins() {
        prefs.edit().putString("units", "METRIC").commit()
        val io = TestScope(StandardTestDispatcher())
        var opened = 0
        val store = PrefsUnitsStore({ opened++; prefs }, io, StandardTestDispatcher(io.testScheduler))
        assertThat(opened).isEqualTo(0)
        assertThat(store.units.value).isEqualTo(UnitSystem.IMPERIAL)
        io.advanceUntilIdle()
        assertThat(store.units.value).isEqualTo(UnitSystem.METRIC)

        prefs.edit().putString("units", "METRIC").commit()
        val raced = PrefsUnitsStore({ prefs }, io, StandardTestDispatcher(io.testScheduler))
        raced.set(UnitSystem.IMPERIAL)
        io.advanceUntilIdle()
        assertThat(raced.units.value).isEqualTo(UnitSystem.IMPERIAL)
        assertThat(prefs.getString("units", null)).isEqualTo("IMPERIAL")
    }
}
