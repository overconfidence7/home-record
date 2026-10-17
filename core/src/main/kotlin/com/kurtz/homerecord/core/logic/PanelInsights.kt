package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerRoom
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelKind
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.RoomKind
import com.kurtz.homerecord.core.model.TandemPolicy

enum class InsightSeverity { WARNING, CHECK, INFO }

enum class HazardReason { FPE, ZINSCO, CHALLENGER_BULLDOG, PUSHMATIC, NO_MAIN }

/** Dedicated-circuit rows of AC-112, in display order. */
enum class DedicatedRow(
    val required: Int,
    val keywords: List<String>,
    /** Linked room kinds that also satisfy the row (only for 20 A branch circuits). */
    val roomKinds: Set<RoomKind> = emptySet(),
    val twoPole: Boolean = false,
) {
    KITCHEN_SMALL_APPLIANCE(2, listOf("small appliance", "counter", "countertop", "kitchen"), setOf(RoomKind.KITCHEN)),
    BATHROOM(1, listOf("bath", "bathroom"), setOf(RoomKind.BATHROOM)),
    LAUNDRY(1, listOf("laundry", "washer"), setOf(RoomKind.LAUNDRY)),
    GARAGE(1, listOf("garage"), setOf(RoomKind.GARAGE)),
    DISHWASHER(1, listOf("dishwasher", "dw")),
    DISPOSAL(1, listOf("disposal", "garbage disposal")),
    MICROWAVE(1, listOf("microwave", "mw")),
    RANGE(1, listOf("range", "stove", "oven", "cooktop"), twoPole = true),
    DRYER(1, listOf("dryer"), twoPole = true),
    WATER_HEATER(1, listOf("water heater", "hot water", "wh"), twoPole = true),
    HVAC(1, listOf("hvac", "furnace", "air handler", "ahu", "heat pump", "a/c", "ac unit", "condenser")),
}

sealed interface Insight {
    val severity: InsightSeverity

    data object TandemNotAllowed : Insight { override val severity = InsightSeverity.WARNING }
    data object TandemUnknown : Insight { override val severity = InsightSeverity.CHECK }
    data class OverMaxCircuits(val circuits: Int, val max: Int) : Insight { override val severity = InsightSeverity.WARNING }
    data class EraCircuitLimit(val circuits: Int) : Insight { override val severity = InsightSeverity.WARNING }
    data class Hazard(val reasons: List<HazardReason>) : Insight { override val severity = InsightSeverity.WARNING }
    data class Gfci(val breakerLabel: String, val slot: Int, val rooms: List<String>) : Insight { override val severity = InsightSeverity.CHECK }
    data class Afci(val breakerLabel: String, val slot: Int, val rooms: List<String>) : Insight { override val severity = InsightSeverity.CHECK }
    /** [found] true is an Info row; false is a Check ("Not labeled yet"). */
    data class Dedicated(val row: DedicatedRow, val found: Boolean) : Insight {
        override val severity get() = if (found) InsightSeverity.INFO else InsightSeverity.CHECK
    }
}

/** AC-107. */
data class PanelCapacity(
    val slotsUsed: Int,
    val slotsFree: Int,
    val circuits: Int,
    val twoPoleCircuits: Int,
    val percentLabeled: Int,
    val percentConfirmed: Int,
)

data class PanelInsightsResult(val capacity: PanelCapacity, val insights: List<Insight>)

/** F21: pure rules over the stored data; never mutates anything (AC-113). */
object PanelInsights {
    private val gfciKinds = setOf(
        RoomKind.BATHROOM, RoomKind.KITCHEN, RoomKind.GARAGE, RoomKind.EXTERIOR,
        RoomKind.BASEMENT, RoomKind.CRAWL_SPACE, RoomKind.LAUNDRY, RoomKind.UTILITY,
    )
    private val afciKinds = setOf(
        RoomKind.BEDROOM, RoomKind.LIVING, RoomKind.FAMILY, RoomKind.DINING, RoomKind.KITCHEN,
        RoomKind.OFFICE, RoomKind.HALLWAY, RoomKind.CLOSET, RoomKind.LAUNDRY,
    )

    fun compute(
        panel: Panel,
        breakers: List<Breaker>,
        rooms: List<Room>,
        links: List<BreakerRoom>,
        endpoints: List<Endpoint>,
    ): PanelInsightsResult {
        val capacity = capacity(panel, breakers)
        if (breakers.isEmpty()) return PanelInsightsResult(capacity, emptyList())

        val branch = breakers.filter { !it.isMain }
        val roomsById = rooms.associateBy { it.id }
        val roomsOf: (Breaker) -> List<Room> = { b ->
            val ids = links.filter { it.breakerId == b.id }.map { it.roomId } +
                endpoints.filter { it.breakerId == b.id }.map { it.roomId }
            ids.distinct().mapNotNull { roomsById[it] }.sortedBy { it.name }
        }

        val out = mutableListOf<Insight>()
        out += HazardBrandMatcher.reasons(panel).let { if (it.isEmpty()) emptyList() else listOf(Insight.Hazard(it)) }
        out += circuitChecks(panel, breakers, capacity.circuits)
        out += protection(branch, roomsOf)
        out += DedicatedCircuitChecklist.evaluate(branch, roomsOf, endpoints)
        val rank = { i: Insight -> i.severity.ordinal }
        return PanelInsightsResult(capacity, out.sortedBy(rank)) // stable: hazard stays first among warnings
    }

    fun capacity(panel: Panel, breakers: List<Breaker>): PanelCapacity {
        val branch = breakers.filter { !it.isMain }
        val used = breakers.flatMap { it.slots }.filter { it in 1..panel.slotCount }.toSet().size
        val labeled = PanelSummary.percentLabeled(panel.slotCount, breakers)
        val confirmedSlots = breakers.filter { it.confidence == Confidence.CONFIRMED }.flatMap { it.slots }.toSet().size
        val confirmed = if (panel.slotCount <= 0) 0 else (confirmedSlots * 100.0 / panel.slotCount).toInt().coerceIn(0, 100)
        return PanelCapacity(
            slotsUsed = used,
            slotsFree = (panel.slotCount - used).coerceAtLeast(0),
            circuits = branch.size,
            twoPoleCircuits = branch.count { it.poles == 2 },
            percentLabeled = labeled,
            percentConfirmed = confirmed,
        )
    }

    private fun circuitChecks(panel: Panel, breakers: List<Breaker>, circuits: Int): List<Insight> {
        val out = mutableListOf<Insight>()
        if (breakers.any { it.tandemIndex != null }) {
            when (panel.tandemPolicy) {
                TandemPolicy.NOT_ALLOWED -> out += Insight.TandemNotAllowed
                TandemPolicy.UNKNOWN -> out += Insight.TandemUnknown
                TandemPolicy.ALLOWED -> Unit
            }
        }
        panel.maxCircuits?.let { if (circuits > it) out += Insight.OverMaxCircuits(circuits, it) }
        if ((panel.yearInstalled ?: Int.MAX_VALUE) < 2008 && circuits > 42) out += Insight.EraCircuitLimit(circuits)
        return out
    }

    private fun protection(branch: List<Breaker>, roomsOf: (Breaker) -> List<Room>): List<Insight> {
        val out = mutableListOf<Insight>()
        for (b in branch.sortedBy { it.slots.minOrNull() ?: Int.MAX_VALUE }) {
            val rooms = roomsOf(b)
            val slot = b.slots.minOrNull() ?: 0
            val gfciRooms = rooms.filter { it.kind in gfciKinds }
            if (gfciRooms.isNotEmpty() && b.type in setOf(BreakerType.STANDARD, BreakerType.AFCI)) {
                out += Insight.Gfci(b.label, slot, gfciRooms.map { it.name })
            }
            val afciRooms = rooms.filter { it.kind in afciKinds }
            if (b.poles == 1 && afciRooms.isNotEmpty() && b.type in setOf(BreakerType.STANDARD, BreakerType.GFCI)) {
                out += Insight.Afci(b.label, slot, afciRooms.map { it.name })
            }
        }
        return out
    }
}

/** AC-109. Matches whole words so "Square D" never trips "fpe"-style fragments. */
object HazardBrandMatcher {
    private val groups: List<Pair<HazardReason, List<String>>> = listOf(
        HazardReason.FPE to listOf("federal pacific", "fpe", "stab-lok", "federal pioneer"),
        HazardReason.ZINSCO to listOf("zinsco", "sylvania"),
        HazardReason.CHALLENGER_BULLDOG to listOf("challenger", "bulldog"),
        HazardReason.PUSHMATIC to listOf("pushmatic"),
    )

    fun reasons(panel: Panel): List<HazardReason> {
        val reasons = brandReasons(listOfNotNull(panel.brand, panel.model).joinToString(" ")).toMutableList()
        if (!panel.hasMainBreaker && panel.kind == PanelKind.MAIN) reasons += HazardReason.NO_MAIN
        return reasons
    }

    fun brandReasons(text: String): List<HazardReason> =
        groups.filter { (_, words) -> words.any { KeywordMatcher.contains(text, it) } }.map { it.first }
}

/** Case-insensitive whole-word keyword search used by the hazard and dedicated-circuit rules. */
internal object KeywordMatcher {
    fun contains(text: String, keyword: String): Boolean {
        val pattern = Regex("(?<![a-z0-9])" + Regex.escape(keyword.lowercase()) + "(?:s|es)?(?![a-z0-9])")
        return pattern.containsMatchIn(text.lowercase())
    }

    fun containsAny(text: String, keywords: List<String>): Boolean = keywords.any { contains(text, it) }
}

/** AC-112. */
object DedicatedCircuitChecklist {
    fun evaluate(branch: List<Breaker>, roomsOf: (Breaker) -> List<Room>, endpoints: List<Endpoint>): List<Insight.Dedicated> =
        DedicatedRow.entries.map { row ->
            val matches = branch.count { matches(row, it, roomsOf(it), endpoints) }
            Insight.Dedicated(row, matches >= row.required)
        }

    private fun matches(row: DedicatedRow, b: Breaker, rooms: List<Room>, endpoints: List<Endpoint>): Boolean {
        if (row.twoPole && b.poles != 2) return false
        val texts = listOf(b.label) + endpoints.filter { it.breakerId == b.id }.map { it.label }
        if (texts.any { KeywordMatcher.containsAny(it, row.keywords) }) return true
        return row.roomKinds.isNotEmpty() && (b.amps ?: 0) >= 20 && rooms.any { it.kind in row.roomKinds }
    }
}
