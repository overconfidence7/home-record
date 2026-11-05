package com.kurtz.homerecord.core.units

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The units this phone shows lengths in (AC-C4): feet and inches unless the person picks metric in Settings. */
interface UnitsStore {
    val units: StateFlow<UnitSystem>
    fun set(system: UnitSystem)
}

/** Kept in plain preferences: a display choice of this phone, so sign out leaves it alone. */
class PrefsUnitsStore(private val prefs: SharedPreferences) : UnitsStore {
    private val state = MutableStateFlow(read())
    override val units: StateFlow<UnitSystem> = state.asStateFlow()

    override fun set(system: UnitSystem) {
        prefs.edit().putString(KEY, system.name).apply()
        state.value = system
    }

    private fun read(): UnitSystem =
        prefs.getString(KEY, null)?.let { name -> UnitSystem.entries.firstOrNull { it.name == name } } ?: UnitSystem.IMPERIAL

    private companion object {
        const val KEY = "units"
    }
}
