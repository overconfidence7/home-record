package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerRoom
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelKind
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.RoomKind
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.model.TandemPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelInsightsTest {
    private fun panel(
        brand: String? = null, model: String? = null, hasMain: Boolean = true, kind: PanelKind = PanelKind.MAIN,
        tandem: TandemPolicy = TandemPolicy.UNKNOWN, max: Int? = null, year: Int? = null, slots: Int = 20,
    ) = Panel(
        "p", "h", "Main", slotCount = slots, layout = PanelLayout.NA_NUMBERED, createdAt = 0, updatedAt = 0,
        source = Source.HOUSE_MANUAL, kind = kind, brand = brand, model = model, maxCircuits = max,
        tandemPolicy = tandem, hasMainBreaker = hasMain, yearInstalled = year,
    )

    private fun breaker(
        id: String, slot: Int, label: String = "", amps: Int? = 15, poles: Int = 1, type: BreakerType = BreakerType.STANDARD,
        confidence: Confidence = Confidence.UNKNOWN, tandem: Int? = null, main: Boolean = false,
    ) = Breaker(
        id, "p", if (poles == 2) listOf(slot, slot + 2) else listOf(slot), poles, amps, type, label, confidence, tandem,
        0, 0, Source.HOUSE_MANUAL, main,
    )

    private fun room(id: String, name: String, kind: RoomKind) = Room(id, "h", name, null, 0, 0, Source.HOUSE_MANUAL, kind)
    private fun link(b: String, r: String) = BreakerRoom("l$b$r", b, r, 0, 0, Source.HOUSE_MANUAL)
    private fun endpoint(b: String?, r: String, label: String = "Outlet") =
        Endpoint("e$b$r$label", b, r, EndpointKind.OUTLET, label, 0, 0, Source.HOUSE_MANUAL)

    private val rooms = listOf(
        room("k", "Kitchen", RoomKind.KITCHEN), room("b", "Bath", RoomKind.BATHROOM),
        room("br", "Bedroom", RoomKind.BEDROOM), room("g", "Garage", RoomKind.GARAGE), room("l", "Laundry", RoomKind.LAUNDRY),
    )

    private fun run(p: Panel, breakers: List<Breaker>, links: List<BreakerRoom> = emptyList(), endpoints: List<Endpoint> = emptyList()) =
        PanelInsights.compute(p, breakers, rooms, links, endpoints)

    // AC-107
    @Test fun `capacity counts tandems as two, main as zero, and two-pole circuits`() {
        val breakers = listOf(
            breaker("m", 1, "Main", 100, poles = 2, main = true),
            breaker("t0", 5, "A", tandem = 0), breaker("t1", 5, "B", tandem = 1),
            breaker("r", 6, "Range", 50, poles = 2, confidence = Confidence.CONFIRMED),
            breaker("u", 9, ""),
        )
        val c = PanelInsights.capacity(panel(slots = 20), breakers)
        assertEquals(4, c.circuits)
        assertEquals(1, c.twoPoleCircuits)
        assertEquals(6, c.slotsUsed) // 1,3 (main) + 5 + 6,8 + 9
        assertEquals(14, c.slotsFree)
        assertEquals(25, c.percentLabeled) // 1,3,5,6,8 labeled = 5 of 20
        assertEquals(10, c.percentConfirmed) // slots 6 and 8 of 20
    }

    @Test fun `empty panel shows only capacity`() {
        val r = run(panel(brand = "Zinsco", hasMain = false), emptyList())
        assertTrue(r.insights.isEmpty())
        assertEquals(0, r.capacity.circuits)
    }

    // AC-108
    @Test fun `tandem policy checks`() {
        val b = listOf(breaker("t0", 5, "A", tandem = 0))
        assertTrue(run(panel(tandem = TandemPolicy.NOT_ALLOWED), b).insights.contains(Insight.TandemNotAllowed))
        assertTrue(run(panel(tandem = TandemPolicy.UNKNOWN), b).insights.contains(Insight.TandemUnknown))
        val allowed = run(panel(tandem = TandemPolicy.ALLOWED), b).insights
        assertFalse(allowed.contains(Insight.TandemNotAllowed) || allowed.contains(Insight.TandemUnknown))
        assertFalse(run(panel(tandem = TandemPolicy.NOT_ALLOWED), listOf(breaker("x", 1))).insights.contains(Insight.TandemNotAllowed))
    }

    @Test fun `over max circuits and era limit`() {
        val many = (1..43).map { breaker("b$it", it, "c$it") }
        val r = run(panel(max = 40, year = 1990, slots = 50), many).insights
        assertTrue(r.contains(Insight.OverMaxCircuits(43, 40)))
        assertTrue(r.contains(Insight.EraCircuitLimit(43)))
        val modern = run(panel(max = 43, year = 2010, slots = 50), many).insights
        assertTrue(modern.none { it is Insight.OverMaxCircuits || it is Insight.EraCircuitLimit })
    }

    // AC-109
    @Test fun `hazard matcher fixtures`() {
        fun r(brand: String?, model: String? = null) = HazardBrandMatcher.reasons(panel(brand = brand, model = model))
        assertEquals(listOf(HazardReason.ZINSCO), r("GTE Sylvania"))
        assertEquals(listOf(HazardReason.FPE), r("FEDERAL PACIFIC ELECTRIC"))
        assertEquals(listOf(HazardReason.FPE), r("Unknown", "Stab-Lok NA"))
        assertEquals(listOf(HazardReason.FPE), r("FPE"))
        assertEquals(listOf(HazardReason.CHALLENGER_BULLDOG), r("Challenger"))
        assertEquals(listOf(HazardReason.PUSHMATIC), r("ITE Pushmatic"))
        assertTrue(r("Square D", "QO") .isEmpty())
        assertTrue(r("Eaton", "Type BR").isEmpty())
        assertTrue(r(null).isEmpty())
    }

    @Test fun `no main breaker is a hazard on a main panel only and the hazard card is first`() {
        val b = listOf(breaker("a", 1, "Kitchen outlets"))
        assertEquals(listOf(HazardReason.NO_MAIN), HazardBrandMatcher.reasons(panel(hasMain = false)))
        assertTrue(HazardBrandMatcher.reasons(panel(hasMain = false, kind = PanelKind.SUB)).isEmpty())
        val insights = run(panel(hasMain = false, tandem = TandemPolicy.NOT_ALLOWED), b + breaker("t", 5, tandem = 0)).insights
        assertTrue(insights.first() is Insight.Hazard)
    }

    // AC-110
    @Test fun `gfci expected for wet rooms via link or endpoint, one card per breaker`() {
        val b = listOf(
            breaker("a", 1, "Bath"), breaker("b", 2, "Gar", type = BreakerType.AFCI), breaker("c", 3, "Done", type = BreakerType.GFCI),
            breaker("d", 4, "Dual", type = BreakerType.DUAL), breaker("m", 5, "Main", main = true), breaker("e", 6, "Bed"),
        )
        val links = listOf(link("a", "b"), link("c", "b"), link("d", "b"), link("m", "b"), link("e", "br"))
        val eps = listOf(endpoint("b", "g"), endpoint("a", "k"))
        val cards = run(panel(), b, links, eps).insights.filterIsInstance<Insight.Gfci>()
        assertEquals(listOf(1, 2), cards.map { it.slot })
        assertEquals(listOf("Bath", "Kitchen"), cards[0].rooms)
        assertEquals(listOf("Garage"), cards[1].rooms)
    }

    // AC-111
    @Test fun `afci expected for living spaces on one-pole standard or gfci breakers`() {
        val b = listOf(
            breaker("a", 1, "Bed"), breaker("b", 2, "Kit", type = BreakerType.GFCI), breaker("c", 3, "Afci", type = BreakerType.AFCI),
            breaker("d", 4, "Two", poles = 2), breaker("e", 6, "Bath only"),
        )
        val links = listOf(link("a", "br"), link("b", "k"), link("c", "br"), link("d", "br"), link("e", "b"))
        val cards = run(panel(), b, links).insights.filterIsInstance<Insight.Afci>()
        assertEquals(listOf(1, 2), cards.map { it.slot })
    }

    // AC-112
    private fun dedicated(r: PanelInsightsResult, row: DedicatedRow) =
        r.insights.filterIsInstance<Insight.Dedicated>().single { it.row == row }

    @Test fun `dedicated rows found by label keyword, case-insensitive`() {
        val b = listOf(
            breaker("r", 1, "KITCHEN RANGE", 50, poles = 2), breaker("d", 9, "Dishwasher"), breaker("h", 10, "Furnace", 15),
            breaker("w", 11, "Water Heater", 30, poles = 2), breaker("dr", 13, "Dryer", 30, poles = 2),
        )
        val r = run(panel(slots = 30), b)
        listOf(DedicatedRow.RANGE, DedicatedRow.DISHWASHER, DedicatedRow.HVAC, DedicatedRow.WATER_HEATER, DedicatedRow.DRYER)
            .forEach { assertTrue(it.name, dedicated(r, it).found) }
        assertFalse(dedicated(r, DedicatedRow.MICROWAVE).found)
        assertEquals(InsightSeverity.CHECK, dedicated(r, DedicatedRow.MICROWAVE).severity)
        assertEquals(InsightSeverity.INFO, dedicated(r, DedicatedRow.RANGE).severity)
    }

    @Test fun `two-pole rows need a two-pole breaker`() {
        val r = run(panel(), listOf(breaker("r", 1, "Range", 50)))
        assertFalse(dedicated(r, DedicatedRow.RANGE).found)
    }

    @Test fun `endpoint label and linked room kind satisfy rows`() {
        val b = listOf(
            breaker("a", 1, "Circuit 1", 20), breaker("b", 2, "Circuit 2", 20), breaker("c", 3, "Circuit 3", 15),
            breaker("d", 4, "Circuit 4", 20), breaker("m", 5, "Circuit 5", 15),
        )
        val links = listOf(link("a", "k"), link("b", "k"), link("c", "b"), link("d", "g"))
        val eps = listOf(endpoint("m", "k", "Microwave"))
        val r = run(panel(), b, links, eps)
        assertTrue(dedicated(r, DedicatedRow.KITCHEN_SMALL_APPLIANCE).found)
        assertTrue(dedicated(r, DedicatedRow.MICROWAVE).found)
        assertFalse("15 A bath circuit is not the 20 A bath circuit", dedicated(r, DedicatedRow.BATHROOM).found)
        assertTrue(dedicated(r, DedicatedRow.GARAGE).found)
    }

    @Test fun `one kitchen circuit is not enough`() {
        val r = run(panel(), listOf(breaker("a", 1, "Counter", 20)))
        assertFalse(dedicated(r, DedicatedRow.KITCHEN_SMALL_APPLIANCE).found)
    }

    @Test fun `keyword matching is whole word`() {
        assertTrue(KeywordMatcher.contains("Ranges", "range"))
        assertFalse(KeywordMatcher.contains("Orange room", "range"))
        assertTrue(KeywordMatcher.contains("A/C", "a/c"))
    }
}
