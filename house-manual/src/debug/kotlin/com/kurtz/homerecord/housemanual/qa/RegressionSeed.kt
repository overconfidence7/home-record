package com.kurtz.homerecord.housemanual.qa

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanOpening
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.PlanStair
import com.kurtz.homerecord.housemodel.plan.Pt

/** Debug seed for the QA suite: a two-story home with a basement, 12 placed rooms, doors, windows and stairs. */
object RegressionSeed {
    private const val FT = 12

    private fun room(id: String, name: String, kind: String, x: Int, y: Int, w: Int, l: Int) =
        PlanRoom.rectangle(id, name, kind, x * FT, y * FT, w * FT, l * FT)

    private fun door(id: String, edge: Int, offsetFt: Int) = PlanOpening(id, edge, OpeningKind.DOOR, offsetFt * FT, PlanRoom.DEFAULT_DOOR_IN, 80)
    private fun window(id: String, edge: Int, offsetFt: Int) = PlanOpening(id, edge, OpeningKind.WINDOW, offsetFt * FT, PlanRoom.DEFAULT_WINDOW_IN, 48)

    val mainFloor: List<PlanRoom> = listOf(
        room("qa-kitchen", "Kitchen", "KITCHEN", 0, 0, 14, 12).copy(openings = listOf(door("qa-d1", 1, 4), window("qa-w1", 0, 5))),
        room("qa-living", "Living room", "LIVING_ROOM", 14, 0, 18, 14).copy(
            openings = listOf(door("qa-d2", 3, 5), window("qa-w2", 0, 6)),
            stairs = listOf(PlanStair("qa-s1", null, listOf(Pt(27 * FT, 8 * FT), Pt(31 * FT, 8 * FT), Pt(31 * FT, 13 * FT), Pt(27 * FT, 13 * FT)))),
        ),
        room("qa-dining", "Dining room", "DINING_ROOM", 0, 12, 14, 12).copy(openings = listOf(window("qa-w3", 2, 4))),
        room("qa-mudroom", "Mudroom", "MUDROOM", 14, 14, 8, 8),
        room("qa-powder", "Powder room", "BATHROOM", 22, 14, 6, 8).copy(openings = listOf(door("qa-d3", 3, 2))),
    )

    val upperFloor: List<PlanRoom> = listOf(
        room("qa-primary", "Primary bedroom", "BEDROOM", 0, 0, 16, 14).copy(openings = listOf(door("qa-d4", 1, 3), window("qa-w4", 0, 6))),
        room("qa-bath", "Primary bath", "BATHROOM", 16, 0, 10, 10),
        room("qa-bed2", "Bedroom 2", "BEDROOM", 0, 14, 12, 12).copy(openings = listOf(window("qa-w5", 2, 4))),
        room("qa-bed3", "Bedroom 3", "BEDROOM", 12, 14, 12, 12),
    )

    val basement: List<PlanRoom> = listOf(
        room("qa-utility", "Utility room", "UTILITY", 0, 0, 12, 12),
        room("qa-laundry", "Laundry", "LAUNDRY", 12, 0, 10, 12),
        room("qa-rec", "Rec room", "OTHER", 0, 12, 22, 14),
    )

    /** Replaces whatever is on the device with the regression home. Returns the home id. */
    suspend fun seed(db: HomeRecordDatabase, repo: HomeRepository): String {
        reset(db)
        val homeId = repo.createHome(NewHome("QA House", "12 Test Lane", HouseType.TWO_STORY, Foundation.BASEMENT, attachedGarage = false))
        val floors = db.floorDao().listFor(homeId)
        check(floors.size >= 3) { "template gave ${floors.size} floors" }
        repo.savePlan(floors[0].id, basement)
        repo.savePlan(floors[1].id, mainFloor)
        repo.savePlan(floors[2].id, upperFloor)
        return homeId
    }

    suspend fun reset(db: HomeRecordDatabase) = db.clearAllTables()
}
