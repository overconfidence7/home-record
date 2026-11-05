package com.kurtz.homerecord.core.model

/** How a panel's slots are numbered (AC-C40). */
enum class PanelLayout {
    /** Two columns: odd slots down the left, even slots down the right (1|2, 3|4 …). */
    NORTH_AMERICAN,

    /** One column, slots in order top to bottom. */
    DIN,
    ;

    companion object {
        fun of(name: String?): PanelLayout = entries.firstOrNull { it.name == name } ?: NORTH_AMERICAN
    }
}

enum class BreakerType {
    STANDARD, GFCI, AFCI, DUAL_FUNCTION;

    companion object {
        fun of(name: String?): BreakerType = entries.firstOrNull { it.name == name } ?: STANDARD
    }
}

/** How sure the homeowner is of a breaker or a breaker link (AC-C41, AC-C42). */
enum class LinkConfidence {
    CONFIRMED, LIKELY, UNKNOWN;

    companion object {
        fun of(name: String?): LinkConfidence = entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

/** Panel facts (AC-C40). */
data class Panel(
    val id: String,
    val itemId: String,
    val slotCount: Int,
    val layout: PanelLayout,
    val mainAmps: Int?,
    val brand: String?,
    val photoPath: String?,
)

/** One breaker: the slots it fills (first slot first), poles, amps, type and label (AC-C40). */
data class Breaker(
    val id: String,
    val panelId: String,
    val slots: List<Int>,
    val poles: Int,
    val amps: Int?,
    val type: BreakerType,
    val label: String,
    val confidence: LinkConfidence,
    val createdAt: Long = 0,
) {
    /** The number a homeowner reads on the panel door: the first slot. */
    val number: Int get() = slots.minOrNull() ?: 0
}

/**
 * One cell of the panel grid. [rowSpan] is 2 for a two-pole breaker on a two-column panel (one tall cell over both
 * slots); [breakerIds] is empty for an open slot, one id for a breaker and two ids (top half first) for a tandem.
 */
data class PanelCell(val slot: Int, val row: Int, val column: Int, val rowSpan: Int, val breakerIds: List<String>) {
    val isEmpty: Boolean get() = breakerIds.isEmpty()
    val isTandem: Boolean get() = breakerIds.size == 2
}

/** The panel grid: [cells] in reading order, [columns] and [rows], and breakers whose slots clash with others. */
data class PanelGrid(val columns: Int, val rows: Int, val cells: List<PanelCell>, val unplaced: List<String>)

/** Why a breaker cannot go where it was asked to. */
sealed interface SlotPlacement {
    data class Ok(val slots: List<Int>) : SlotPlacement
    /** Its slots run past the end of the panel (or start below 1). */
    data object OffPanel : SlotPlacement
    /** These slots already hold a breaker (or a full tandem). */
    data class Taken(val slots: List<Int>) : SlotPlacement
    /** A tandem holds two single-pole breakers only. */
    data object TandemNeedsOnePole : SlotPlacement
}

/**
 * Which slots each breaker fills and how the grid draws them (AC-C40). A slot holds one breaker, or two single-pole
 * breakers as a tandem. A two-pole breaker fills its slot and the one below it: on a two-column panel that is the slot
 * number + 2 (same column, next row), on a DIN rail the next slot.
 */
object SlotOccupancy {
    const val DEFAULT_SLOTS = 40
    const val MAX_SLOTS = 84

    fun columns(layout: PanelLayout): Int = if (layout == PanelLayout.NORTH_AMERICAN) 2 else 1

    fun rows(layout: PanelLayout, slotCount: Int): Int = (slotCount + columns(layout) - 1) / columns(layout)

    /** Grid row and column of [slot] (both 0-based). */
    fun position(layout: PanelLayout, slot: Int): Pair<Int, Int> = when (layout) {
        PanelLayout.NORTH_AMERICAN -> (slot - 1) / 2 to (slot - 1) % 2
        PanelLayout.DIN -> slot - 1 to 0
    }

    /** The slots a breaker of [poles] starting at [start] fills, or null when they run off the panel. */
    fun slotsFor(layout: PanelLayout, slotCount: Int, start: Int, poles: Int): List<Int>? {
        val step = if (layout == PanelLayout.NORTH_AMERICAN) 2 else 1
        val slots = (0 until poles.coerceIn(1, 2)).map { start + it * step }
        return slots.takeIf { s -> s.all { it in 1..slotCount } }
    }

    /**
     * Whether a breaker of [poles] can start at [start] given the [others] already in the panel (the breaker being
     * edited left out). [tandem] asks to share a slot with one single-pole breaker already there.
     */
    fun place(layout: PanelLayout, slotCount: Int, others: List<Breaker>, start: Int, poles: Int, tandem: Boolean = false): SlotPlacement {
        if (tandem && poles != 1) return SlotPlacement.TandemNeedsOnePole
        val slots = slotsFor(layout, slotCount, start, poles) ?: return SlotPlacement.OffPanel
        val taken = slots.filter { slot ->
            val there = others.filter { slot in it.slots }
            when {
                there.isEmpty() -> false
                tandem -> there.size >= 2 || there.any { it.poles != 1 }
                else -> true
            }
        }
        return if (taken.isEmpty()) SlotPlacement.Ok(slots) else SlotPlacement.Taken(taken)
    }

    /**
     * Lays the panel out as cells. Breakers are placed oldest first; one whose slots clash with breakers already
     * placed (two phones edited the panel apart) goes to [PanelGrid.unplaced] so the screen can still show it.
     */
    fun grid(layout: PanelLayout, slotCount: Int, breakers: List<Breaker>): PanelGrid {
        val holders = HashMap<Int, MutableList<Breaker>>()
        val unplaced = mutableListOf<String>()
        breakers.sortedWith(compareBy<Breaker> { it.createdAt }.thenBy { it.id }).forEach { b ->
            val expected = slotsFor(layout, slotCount, b.number, b.poles)
            val fits = expected != null && expected == b.slots.sorted() && b.slots.all { slot ->
                val there = holders[slot].orEmpty()
                there.isEmpty() || (b.poles == 1 && there.size == 1 && there.single().poles == 1)
            }
            if (fits) b.slots.forEach { holders.getOrPut(it) { mutableListOf() } += b } else unplaced += b.id
        }
        val covered = mutableSetOf<Int>()
        val cells = (1..slotCount).mapNotNull { slot ->
            if (slot in covered) return@mapNotNull null
            val there = holders[slot].orEmpty()
            val (row, column) = position(layout, slot)
            val twoPole = there.singleOrNull()?.takeIf { it.poles == 2 }
            if (twoPole != null) covered += twoPole.slots.filter { it != slot }
            // Both layouts put a two-pole breaker's second slot in the next row of the same column.
            PanelCell(slot, row, column, if (twoPole != null) 2 else 1, there.map { it.id })
        }
        return PanelGrid(columns(layout), rows(layout, slotCount), cells, unplaced)
    }

    /** The highest slot any breaker fills; a panel cannot shrink below it. */
    fun highestUsed(breakers: List<Breaker>): Int = breakers.flatMap { it.slots }.maxOrNull() ?: 0
}
