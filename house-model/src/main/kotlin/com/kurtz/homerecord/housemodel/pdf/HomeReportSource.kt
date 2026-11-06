package com.kurtz.homerecord.housemodel.pdf

import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import com.kurtz.homerecord.housemodel.panel.isPanelSubtype
import kotlinx.coroutines.flow.first

/** The words the source needs that are not the report's own: pseudo-room names and house type labels. */
data class ReportSourceWords(
    val exterior: String,
    val wholeHouse: String,
    val unassigned: String,
    val houseType: (HouseType) -> String,
)

/**
 * Reads one home out of Room for the home report (AC-H13): floors in house order with their placed rooms, every room
 * (placed or not) with its items and today's warranty status, the shutoffs (mains first) and each panel's legend.
 */
class HomeReportSource(
    private val homes: HomeRepository,
    private val items: ItemRepository,
    private val panels: PanelRepository,
    private val words: ReportSourceWords,
    private val legendWords: LegendWords,
) {
    suspend fun load(homeId: String): HomeReportData? {
        val home = homes.observeHome().first()?.takeIf { it.id == homeId } ?: return null
        val pseudo = mapOf(PseudoRooms.EXTERIOR to words.exterior, PseudoRooms.WHOLE_HOUSE to words.wholeHouse)
        val floors = homes.observeFloors(homeId).first()
        val plans = floors.map { it to homes.loadPlan(it.id) }
        val roomRefs = plans.flatMapIndexed { order, (floor, plan) ->
            plan.placed.map { ReportRoomRef(it.id, it.name, floor.name, order) } +
                plan.unplaced.map { ReportRoomRef(it.id, it.name, floor.name, order) }
        } + listOf(
            ReportRoomRef(PseudoRooms.EXTERIOR, words.exterior, null, 0),
            ReportRoomRef(PseudoRooms.WHOLE_HOUSE, words.wholeHouse, null, 1),
        )
        val rows = items.observeRows(homeId, pseudo).first()
        val itemRefs = rows.mapNotNull { row ->
            val e = items.get(row.id) ?: return@mapNotNull null
            ReportItemRef(
                row.roomId,
                ReportItem(row.name, items.subtypeName(row.subtypeId).orEmpty(), e.make, e.model, e.serial, e.inServiceOn, row.warranty),
            )
        }
        val shutoffs = items.observeShutoffs(homeId, pseudo).first().flatMap { it.shutoffs }.map {
            ReportShutoff(it.name, it.roomName, it.instructions, it.photoPath)
        }
        val choices = panels.observeChoices(homeId).first()
        val names = (choices.rooms + choices.items).associate { it.id to it.name }
        val header = PanelLegendPdfWriter(legendWords)
        val panelPages = rows.filter { isPanelSubtype(it.subtypeId) }.sortedBy { it.createdAt }.mapNotNull { row ->
            val p = panels.observe(row.id).first() ?: return@mapNotNull null
            val feeds = p.links.mapValues { (_, links) -> links.mapNotNull { l -> (l.roomId ?: l.itemId)?.let(names::get) }.distinct() }
            ReportPanel(row.name, header.headerLine(p.panel), PanelLegend.columns(p.panel.layout), PanelLegend.rows(p.panel, p.breakers, feeds, legendWords))
        }
        return HomeReportData(
            homeName = home.name,
            address = home.address,
            houseType = words.houseType(HouseType.parse(home.houseType)),
            floors = plans.map { (floor, plan) -> PdfFloor(floor.name, plan.placed) },
            rooms = HomeReport.rooms(roomRefs, itemRefs, words.unassigned),
            shutoffs = shutoffs,
            panels = panelPages,
        )
    }
}
