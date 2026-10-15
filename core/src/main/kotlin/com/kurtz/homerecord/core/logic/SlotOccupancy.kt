package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.PanelLayout

/** Result of asking whether a breaker may sit at a slot. */
sealed interface Placement {
    data class Ok(val slots: List<Int>) : Placement
    /** The breaker would extend past the last slot. */
    data object OutOfRange : Placement
    /** [slot] already holds another breaker. */
    data class Occupied(val slot: Int) : Placement
}

/** Result of asking whether the slot count may shrink. */
sealed interface ShrinkCheck {
    data object Ok : ShrinkCheck
    /** [highestOccupied] is the highest slot that would be dropped. */
    data class Blocked(val highestOccupied: Int) : ShrinkCheck
}

/** AC-9 / AC-10 / AC-11: which slots a breaker covers and whether they are free. */
object SlotOccupancy {
    /** Slots covered by a breaker starting at [start]. Two-pole is N and N+2 on NA panels, N and N+1 on DIN. */
    fun slotsFor(start: Int, poles: Int, layout: PanelLayout): List<Int> = when {
        poles < 2 -> listOf(start)
        layout == PanelLayout.NA_NUMBERED -> listOf(start, start + 2)
        else -> listOf(start, start + 1)
    }

    /** Checks a (possibly multi-slot) placement. [ignoreBreakerId] is the breaker being edited. */
    fun place(
        start: Int,
        poles: Int,
        layout: PanelLayout,
        slotCount: Int,
        breakers: List<Breaker>,
        ignoreBreakerId: String? = null,
    ): Placement {
        val slots = slotsFor(start, poles, layout)
        if (start < 1 || slots.any { it > slotCount }) return Placement.OutOfRange
        val others = breakers.filter { it.id != ignoreBreakerId }
        slots.firstOrNull { slot -> others.any { slot in it.slots } }?.let { return Placement.Occupied(it) }
        return Placement.Ok(slots)
    }

    fun highestOccupied(breakers: List<Breaker>): Int = breakers.maxOfOrNull { it.slots.max() } ?: 0

    fun checkShrink(newCount: Int, breakers: List<Breaker>): ShrinkCheck {
        val highest = highestOccupied(breakers)
        return if (highest > newCount) ShrinkCheck.Blocked(highest) else ShrinkCheck.Ok
    }

    /** The single plain one-pole breaker at [slot] that can become a tandem, or null. */
    fun tandemCandidate(slot: Int, breakers: List<Breaker>): Breaker? =
        breakers.filter { slot in it.slots }.singleOrNull()?.takeIf { it.poles == 1 && it.tandemIndex == null }
}
