package com.kurtz.homerecord.core.units

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C4: feet and inches by default, metric once picked in Settings, and the choice survives a restart
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsUnitsStoreTest {
    private val prefs = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("units_test", Context.MODE_PRIVATE)

    @Test fun feetAndInchesByDefault() {
        prefs.edit().clear().commit()
        assertThat(PrefsUnitsStore(prefs).units.value).isEqualTo(UnitSystem.IMPERIAL)
    }

    @Test fun metricSticksAcrossRestarts() {
        prefs.edit().clear().commit()
        val store = PrefsUnitsStore(prefs)
        store.set(UnitSystem.METRIC)
        assertThat(store.units.value).isEqualTo(UnitSystem.METRIC)
        assertThat(PrefsUnitsStore(prefs).units.value).isEqualTo(UnitSystem.METRIC)
    }

    @Test fun anUnknownValueReadsAsFeetAndInches() {
        prefs.edit().putString("units", "CUBITS").commit()
        assertThat(PrefsUnitsStore(prefs).units.value).isEqualTo(UnitSystem.IMPERIAL)
    }
}
