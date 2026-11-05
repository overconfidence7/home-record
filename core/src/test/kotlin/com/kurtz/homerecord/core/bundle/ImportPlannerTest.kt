package com.kurtz.homerecord.core.bundle

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/** AC-C47: ImportPlanner adds new records, updates only what changed on the imported side, lists conflicts, is idempotent. */
@AcceptanceCriterion("AC-C47")
class ImportPlannerTest {
    private fun room(id: String, name: String, updatedAt: Long, source: String = "field-log", deletedAt: Long? = null) = BundleRecord(
        buildJsonObject {
            put("id", id); put("createdAt", 100L); put("updatedAt", updatedAt); put("source", source); put("name", name)
            if (deletedAt != null) put("deletedAt", deletedAt)
        },
    )

    private fun bundle(vararg rooms: BundleRecord) = HomeBundle(source = "field-log", exportedAt = 9_000L, homeId = "h1", records = mapOf("room" to rooms.toList()))

    @Test fun newRecordsAreAddedAndDeletedOnesAreSkipped() {
        val plan = ImportPlanner.plan(bundle(room("r1", "Kitchen", 500), room("r2", "Pantry", 500, deletedAt = 600)), local = emptyMap())
        assertThat(plan.steps.map { it.id to it.action }).containsExactly("r1" to ImportAction.Add)
        assertThat(plan.added("room")).isEqualTo(1)
    }

    @Test fun theSameRecordIsLeftAloneEvenWithANewerTimestamp() {
        val plan = ImportPlanner.plan(bundle(room("r1", "Kitchen", 900)), mapOf("room" to listOf(room("r1", "Kitchen", 500, source = "house-manual"))))
        assertThat(plan.steps.single().action).isEqualTo(ImportAction.Same)
        assertThat(plan.nothingToDo).isTrue()
    }

    @Test fun onlyTheImportedSideChangedSinceTheLastImportUpdates() {
        val plan = ImportPlanner.plan(
            bundle(room("r1", "Kitchen and pantry", 900)),
            mapOf("room" to listOf(room("r1", "Kitchen", 500))),
            base = mapOf("room" to mapOf("r1" to 500L)),
        )
        assertThat(plan.steps.single().action).isEqualTo(ImportAction.Update)
        assertThat(plan.writes().map { it.id }).containsExactly("r1")
    }

    @Test fun anOlderImportNeverOverwritesALocalEdit() {
        val plan = ImportPlanner.plan(
            bundle(room("r1", "Kitchen", 500)),
            mapOf("room" to listOf(room("r1", "Chef's kitchen", 800, source = "house-manual"))),
            base = mapOf("room" to mapOf("r1" to 500L)),
        )
        assertThat(plan.steps.single().action).isEqualTo(ImportAction.KeepLocal)
        assertThat(plan.writes()).isEmpty()
    }

    @Test fun bothSidesChangedIsAConflictTheUserDecides() {
        val plan = ImportPlanner.plan(
            bundle(room("r1", "Kitchen and pantry", 900)),
            mapOf("room" to listOf(room("r1", "Chef's kitchen", 800, source = "house-manual"))),
            base = mapOf("room" to mapOf("r1" to 500L)),
        )
        assertThat(plan.conflicts.map { it.id }).containsExactly("r1")
        assertThat(plan.conflicts.single().local?.fields?.get("name").toString()).contains("Chef's kitchen")
        // No choice keeps mine; Use imported writes theirs.
        assertThat(plan.writes()).isEmpty()
        assertThat(plan.writes(mapOf(("room" to "r1") to ConflictChoice.KeepMine))).isEmpty()
        assertThat(plan.writes(mapOf(("room" to "r1") to ConflictChoice.UseImported)).single().imported.updatedAt).isEqualTo(900L)
    }

    @Test fun withNoLedgerADifferingRecordIsAConflictNotAnOverwrite() {
        val plan = ImportPlanner.plan(bundle(room("r1", "Kitchen and pantry", 900)), mapOf("room" to listOf(room("r1", "Kitchen", 500, source = "house-manual"))))
        assertThat(plan.steps.single().action).isEqualTo(ImportAction.Conflict)
    }

    @Test fun importingTheSameBundleAgainChangesNothing() {
        val b = bundle(room("r1", "Kitchen and pantry", 900), room("r2", "Mudroom", 900))
        val local = mapOf("room" to listOf(room("r1", "Chef's kitchen", 800, source = "house-manual")))
        val first = ImportPlanner.plan(b, local, base = mapOf("room" to mapOf("r1" to 500L)))
        // Apply: keep mine for the conflict, add the rest; then the ledger records what came in.
        val written = first.writes(mapOf(("room" to "r1") to ConflictChoice.KeepMine)).map { it.imported }
        val after = mapOf("room" to local.getValue("room") + written)
        val second = ImportPlanner.plan(b, after, base = first.ledger())
        assertThat(second.nothingToDo).isTrue()
        assertThat(second.writes()).isEmpty()
    }
}
