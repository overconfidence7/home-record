package com.kurtz.homerecord.housemodel.pdf

import com.kurtz.homerecord.core.model.WarrantyStatus

/** One item as the home report lists it (AC-H13). [typeName] is the subtype label ("Water heater"). */
data class ReportItem(
    val name: String,
    val typeName: String,
    val make: String?,
    val model: String?,
    val serial: String?,
    val inServiceOn: String?,
    val warranty: WarrantyStatus,
)

/** A room and its items; [floorName] is blank for Exterior and Whole house. */
data class ReportRoom(val name: String, val floorName: String, val items: List<ReportItem>)

/** A shutoff with its instructions and the path of its first photo, if any. */
data class ReportShutoff(val name: String, val roomName: String, val instructions: String, val photoPath: String?)

/** A panel's legend page: its name, the header line ("200 A service · Square D QO · 40 slots") and its slot rows. */
data class ReportPanel(val name: String, val header: String, val columns: Int, val rows: List<LegendRow>)

/** Everything the home report prints, in page order. */
data class HomeReportData(
    val homeName: String,
    val address: String?,
    val houseType: String,
    val floors: List<PdfFloor>,
    val rooms: List<ReportRoom>,
    val shutoffs: List<ReportShutoff>,
    val panels: List<ReportPanel>,
)

/** A room the home has, with the floor it sits on (floors in house order) or null for Exterior and Whole house. */
data class ReportRoomRef(val id: String, val name: String, val floorName: String?, val floorOrder: Int)

/** One item before grouping: which room it is in (null for none) and how it reads in the report. */
data class ReportItemRef(val roomId: String?, val item: ReportItem)

/**
 * Puts the home report's rooms in order (AC-H13): rooms floor by floor in house order, then by name, then the
 * pseudo-rooms (Exterior, Whole house), then items with no room under [unassignedName]. Every room prints, even
 * empty ones (the reader sees the room was not forgotten); a pseudo-room or the unassigned group prints only with
 * items. Items inside a room sort by name.
 */
object HomeReport {
    fun rooms(rooms: List<ReportRoomRef>, items: List<ReportItemRef>, unassignedName: String): List<ReportRoom> {
        val byRoom = items.groupBy { it.roomId }
        val known = rooms.map { it.id }.toSet()
        val (real, pseudo) = rooms.partition { it.floorName != null }
        val ordered = real.sortedWith(compareBy<ReportRoomRef> { it.floorOrder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }) +
            pseudo.sortedBy { it.floorOrder }
        fun itemsOf(key: String?) = byRoom[key].orEmpty().map { it.item }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val out = ordered.mapNotNull { r ->
            val list = itemsOf(r.id)
            if (r.floorName == null && list.isEmpty()) null else ReportRoom(r.name, r.floorName.orEmpty(), list)
        }
        val orphans = items.filter { it.roomId == null || it.roomId !in known }.map { it.item }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return if (orphans.isEmpty()) out else out + ReportRoom(unassignedName, "", orphans)
    }

    /** The detail line under an item's name: "Water heater · Rheem XE50T10H45U0 · Serial 1234 · In service 2021-05-03". */
    fun detailLine(item: ReportItem, words: ReportWords): String = listOfNotNull(
        item.typeName.ifBlank { null },
        listOfNotNull(item.make?.ifBlank { null }, item.model?.ifBlank { null }).joinToString(" ").ifBlank { null },
        item.serial?.ifBlank { null }?.let(words.serial),
        item.inServiceOn?.ifBlank { null }?.let(words.inService),
    ).joinToString(words.separator)

    fun warrantyText(status: WarrantyStatus, words: ReportWords): String = when (status) {
        WarrantyStatus.Unknown -> words.warrantyUnknown
        is WarrantyStatus.Active -> words.warrantyActive(status.endsOn)
        is WarrantyStatus.Expiring -> words.warrantyExpiring(status.endsOn)
        is WarrantyStatus.Expired -> words.warrantyExpired(status.endsOn)
    }
}

/** The words the report prints, from string resources. */
data class ReportWords(
    val title: String,
    val madeOn: (String) -> String,
    val counts: (rooms: Int, items: Int) -> String,
    val roomsAndItems: String,
    val noItems: String,
    val shutoffs: String,
    val noShutoffs: String,
    val separator: String,
    val serial: (String) -> String,
    val inService: (String) -> String,
    val warrantyUnknown: String,
    val warrantyActive: (String) -> String,
    val warrantyExpiring: (String) -> String,
    val warrantyExpired: (String) -> String,
    val scaleFeet: (Int) -> String,
    val page: (Int) -> String,
)
