package com.kurtz.homerecord.core.bundle

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/** AC-F51: the handoff summary counts what the homeowner will get, from the bundle about to be written. */
@AcceptanceCriterion("AC-F51")
class HandoffSummaryTest {
    private fun rec(id: String, deletedAt: Long? = null, vararg extra: Pair<String, String?>) = BundleRecord(
        buildJsonObject {
            put("id", id)
            put("createdAt", 1L)
            put("updatedAt", 2L)
            if (deletedAt == null) put("deletedAt", JsonNull) else put("deletedAt", deletedAt)
            extra.forEach { (k, v) -> if (v == null) put(k, JsonNull) else put(k, v) }
        },
    )

    private fun bundle(records: Map<String, List<BundleRecord>>) =
        HomeBundle(source = "field-log", exportedAt = 3L, homeId = "h1", records = records)

    @Test fun countsLiveRecordsOfEachKindTheSummaryLists() { // AC-F51
        val s = HandoffSummary.of(
            bundle(
                mapOf(
                    "room" to listOf(rec("kitchen"), rec("bath"), rec("old", deletedAt = 9L)),
                    "overlay" to listOf(rec("wire"), rec("drain")),
                    "item" to listOf(rec("water-heater")),
                    "document" to listOf(rec("permit"), rec("gone", deletedAt = 5L)),
                    "change_order" to listOf(rec("co1")),
                ),
            ),
        )
        assertThat(s).isEqualTo(HandoffSummary(rooms = 2, wallsWithPhotos = 0, wallPhotos = 0, overlays = 2, items = 1, documents = 1, changeOrders = 1))
        assertThat(s.isEmpty).isFalse()
    }

    @Test fun wallsWithPhotosCountsEachLiveWallOnceAndPhotosOnRemovedWallsOnlyAsPhotos() { // AC-F51
        val s = HandoffSummary.of(
            bundle(
                mapOf(
                    "wall" to listOf(rec("north"), rec("east"), rec("removed", deletedAt = 4L)),
                    "wall_photo" to listOf(
                        rec("p1", null, "wallId" to "north"),
                        rec("p2", null, "wallId" to "north"),
                        rec("p3", null, "wallId" to "east"),
                        rec("p4", null, "wallId" to null),
                        rec("p5", null, "wallId" to "removed"),
                        rec("p6", 8L, "wallId" to "east"),
                    ),
                ),
            ),
        )
        assertThat(s.wallsWithPhotos).isEqualTo(2)
        assertThat(s.wallPhotos).isEqualTo(5)
    }

    @Test fun aHomeWithNothingRecordedIsEmpty() { // AC-F51
        val s = HandoffSummary.of(bundle(mapOf("home" to listOf(rec("h1")), "floor" to listOf(rec("f1")))))
        assertThat(s.isEmpty).isTrue()
    }

    @Test fun fileNameIsTheJobAndDateWithUnsafeCharactersDropped() { // AC-F51
        assertThat(HandoffSummary.fileName("Hale kitchen", "2026-11-05")).isEqualTo("Hale kitchen 2026-11-05.homerecord")
        assertThat(HandoffSummary.fileName("Unit 2/3: \"rear\"", "2026-11-05")).isEqualTo("Unit 2 3 rear 2026-11-05.homerecord")
        assertThat(HandoffSummary.fileName("  ", "2026-11-05")).isEqualTo("Home 2026-11-05.homerecord")
    }
}
