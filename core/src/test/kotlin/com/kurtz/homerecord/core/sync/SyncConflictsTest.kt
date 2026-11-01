package com.kurtz.homerecord.core.sync

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.SyncConflictEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

// AC-F53: the conflict log's rules (T046a): which notes count, how a line reads and how one pass's notes group.
class SyncConflictsTest {
    private fun row(id: Long, entity: String, entityId: String, field: String, reason: String, at: Long, label: String? = null) =
        SyncConflictEntity(id, "company:c1", entity, entityId, field, reason, label, at)

    @Test fun reasonsMapToOutcomes() {
        assertThat(SyncConflicts.outcomeOf("older")).isEqualTo(ConflictOutcome.NewerKept)
        assertThat(SyncConflicts.outcomeOf("authoritative")).isEqualTo(ConflictOutcome.OfficeKept)
        assertThat(SyncConflicts.outcomeOf("refused:entity")).isEqualTo(ConflictOutcome.Refused)
        assertThat(SyncConflicts.outcomeOf("refused:too_large")).isEqualTo(ConflictOutcome.Refused)
        assertThat(SyncConflicts.outcomeOf("unstored:SQLiteException")).isEqualTo(ConflictOutcome.NotStored)
    }

    @Test fun bookkeepingFieldsAreNotLogged() {
        assertThat(SyncConflicts.worthLogging(ConflictNote("task", "t1", "updatedAt", "older"))).isFalse()
        assertThat(SyncConflicts.worthLogging(ConflictNote("task", "t1", "createdAt", "older"))).isFalse()
        assertThat(SyncConflicts.worthLogging(ConflictNote("task", "t1", "title", "older"))).isTrue()
        assertThat(SyncConflicts.worthLogging(ConflictNote("task", "t1", "", "refused:entity"))).isTrue()
    }

    @Test fun fieldNamesReadAsPlainWords() {
        assertThat(SyncConflicts.fieldWords("dueDate")).isEqualTo("due date")
        assertThat(SyncConflicts.fieldWords("assigneeId")).isEqualTo("assignee")
        assertThat(SyncConflicts.fieldWords("editsJson")).isEqualTo("edits")
        assertThat(SyncConflicts.fieldWords("deletedAt")).isEqualTo("deleted")
        assertThat(SyncConflicts.fieldWords("title")).isEqualTo("title")
        assertThat(SyncConflicts.fieldWords("at")).isEqualTo("time")
        assertThat(SyncConflicts.fieldWords("")).isEmpty()
    }

    @Test fun labelIsTheFirstNameLikeField() {
        assertThat(SyncConflicts.labelOf(JsonObject(mapOf("name" to JsonPrimitive("Hale kitchen"), "title" to JsonPrimitive("x"))))).isEqualTo("Hale kitchen")
        assertThat(SyncConflicts.labelOf(JsonObject(mapOf("title" to JsonPrimitive("  Set outlets ")))) ).isEqualTo("Set outlets")
        assertThat(SyncConflicts.labelOf(JsonObject(mapOf("name" to JsonPrimitive(" "), "day" to JsonPrimitive("2026-10-27"))))).isEqualTo("2026-10-27")
        assertThat(SyncConflicts.labelOf(JsonObject(mapOf("kind" to JsonPrimitive("in"), "at" to JsonPrimitive(5))))).isNull()
        assertThat(SyncConflicts.labelOf(null)).isNull()
    }

    @Test fun onePassesNotesForOneRecordBecomeOneLineNewestFirst() {
        val rows = listOf(
            row(1, "punch", "p1", "", "refused:entity", at = 100),
            row(2, "task", "t1", "title", "older", at = 200, label = "Set the kitchen outlets"),
            row(3, "task", "t1", "dueDate", "older", at = 200, label = "Set the kitchen outlets"),
            row(4, "task", "t1", "title", "older", at = 200, label = "Set the kitchen outlets"),
            row(5, "task", "t1", "title", "older", at = 300),
        )
        val lines = SyncConflicts.entries(rows)
        assertThat(lines.map { it.at }).containsExactly(300L, 200L, 100L).inOrder()
        assertThat(lines[1].fields).containsExactly("due date", "title").inOrder()
        assertThat(lines[1].label).isEqualTo("Set the kitchen outlets")
        assertThat(lines[0].label).isNull()
        assertThat(lines[2].outcome).isEqualTo(ConflictOutcome.Refused)
        assertThat(lines[2].fields).isEmpty()
    }
}
