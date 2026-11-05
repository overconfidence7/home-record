package com.kurtz.homerecord.core.units

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The units this phone shows lengths in (AC-C4): feet and inches unless the person picks metric in Settings. */
interface UnitsStore {
    val units: StateFlow<UnitSystem>
    fun set(system: UnitSystem)
}

/**
 * Kept in plain preferences: a display choice of this phone, so sign out leaves it alone. The preferences file is
 * opened and read on [io], never on the caller's thread ([r12:units-store-main-thread], S7): [units] starts at feet
 * and inches and moves to the stored choice once read, unless the person picked one first.
 */
class PrefsUnitsStore(
    private val prefs: () -> SharedPreferences,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : UnitsStore {
    private val state = MutableStateFlow(UnitSystem.IMPERIAL)
    @Volatile private var picked = false
    override val units: StateFlow<UnitSystem> = state.asStateFlow()

    init {
        scope.launch(io) {
            val stored = read()
            if (!picked) state.compareAndSet(UnitSystem.IMPERIAL, stored)
        }
    }

    override fun set(system: UnitSystem) {
        picked = true
        state.value = system
        scope.launch(io) { prefs().edit().putString(KEY, system.name).apply() }
    }

    private fun read(): UnitSystem =
        prefs().getString(KEY, null)?.let { name -> UnitSystem.entries.firstOrNull { it.name == name } } ?: UnitSystem.IMPERIAL

    private companion object {
        const val KEY = "units"
    }
}
