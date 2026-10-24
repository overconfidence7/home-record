package com.kurtz.homerecord.housemodel.home

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.MaintenanceTaskEntity
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.items.ItemRow
import java.time.LocalDate
import org.junit.Test

class HomeSummaryBuilderTest {
    private val today = LocalDate.parse("2026-10-24")

    private fun row(id: String, name: String, make: String? = "Rheem", model: String? = "XE50", cat: String = "Water", w: WarrantyStatus = WarrantyStatus.Unknown, at: Long = 0) =
        ItemRow(id, name, make, model, cat, 0, "x", "r", "Basement", w, at)

    private fun task(id: String, due: String, item: String? = null, snoozed: String? = null) =
        MaintenanceTaskEntity(id = id, homeId = "h", itemId = item, title = "Task $id", dueOn = due, snoozedUntil = snoozed, createdAt = 0, updatedAt = 0)

    @Test @AcceptanceCriterion("AC-H2")
    fun listsExpiringWarrantiesOverdueRemindersAndMissingMakeModel() {
        val items = listOf(
            row("1", "Dishwasher", w = WarrantyStatus.Expiring("2026-11-20", 27)),
            row("2", "Furnace", make = null),
            row("3", "Fridge", w = WarrantyStatus.Active("2030-01-01")),
            row("4", "Roof", w = WarrantyStatus.Expired("2020-01-01")),
        )
        val tasks = listOf(task("a", "2026-10-01", item = "2"), task("b", "2026-10-24"), task("c", "2026-12-01"))
        val s = HomeSummaryBuilder.build(items, tasks, today)
        assertThat(s.attention.map { it.kind to it.id }).containsExactly(
            AttentionKind.REMINDER_OVERDUE to "a",
            AttentionKind.WARRANTY_EXPIRING to "1",
            AttentionKind.MISSING_MAKE_MODEL to "2",
        ).inOrder()
        assertThat(s.attention.first().itemId).isEqualTo("2")
    }

    @Test @AcceptanceCriterion("AC-H2")
    fun nothingToShowIsEmptyAndSnoozedRemindersWait() {
        val s = HomeSummaryBuilder.build(listOf(row("1", "Fridge")), listOf(task("a", "2026-10-01", snoozed = "2026-11-01")), today)
        assertThat(s.attention).isEmpty()
    }

    @Test @AcceptanceCriterion("AC-H1")
    fun countsShutoffsAndKeepsFiveNewestItems() {
        val items = (1..7).map { row("$it", "Item $it", cat = if (it <= 2) "Shutoffs" else "Water", at = it.toLong()) }
        val s = HomeSummaryBuilder.build(items, emptyList(), today)
        assertThat(s.shutoffCount).isEqualTo(2)
        assertThat(s.recent.map { it.id }).containsExactly("7", "6", "5", "4", "3").inOrder()
        assertThat(s.itemCount).isEqualTo(7)
    }
}
