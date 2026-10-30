package com.kurtz.homerecord.housemodel.home

import com.kurtz.homerecord.core.data.MaintenanceTaskEntity
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.housemodel.items.ItemRow
import java.time.LocalDate

enum class AttentionKind { WARRANTY_EXPIRING, REMINDER_OVERDUE, MISSING_MAKE_MODEL }

/** One "Needs attention" row; [id] is the item (or reminder, for overdue ones) it opens. */
data class AttentionRow(val kind: AttentionKind, val id: String, val itemId: String?, val title: String, val detail: String)

data class HomeSummary(
    val shutoffCount: Int,
    val attention: List<AttentionRow>,
    val recent: List<ItemRow>,
    val itemCount: Int,
)

/** Rules behind HM-Home (AC-H2): what needs attention and what is recent. */
object HomeSummaryBuilder {
    const val RECENT_LIMIT = 5

    fun build(items: List<ItemRow>, tasks: List<MaintenanceTaskEntity>, today: LocalDate): HomeSummary {
        val names = items.associate { it.id to it.name }
        val warranties = items.mapNotNull { row ->
            (row.warranty as? WarrantyStatus.Expiring)?.let { it to row }
        }.sortedBy { (w, _) -> w.endsOn }.map { (w, row) ->
            AttentionRow(AttentionKind.WARRANTY_EXPIRING, row.id, row.id, row.name, w.endsOn)
        }
        val overdue = tasks.filter { isOverdue(it, today) }.sortedBy { it.dueOn }.map {
            AttentionRow(AttentionKind.REMINDER_OVERDUE, it.id, it.itemId?.takeIf { id -> id in names }, it.title, it.dueOn)
        }
        val missing = items.filter { it.make.isNullOrBlank() || it.model.isNullOrBlank() }
            .filter { it.category != ItemCategory.SHUTOFFS }
            .sortedBy { it.name.lowercase() }
            .map { AttentionRow(AttentionKind.MISSING_MAKE_MODEL, it.id, it.id, it.name, "") }
        return HomeSummary(
            shutoffCount = items.count { it.category == ItemCategory.SHUTOFFS },
            attention = overdue + warranties + missing,
            recent = items.sortedByDescending { it.createdAt }.take(RECENT_LIMIT),
            itemCount = items.size,
        )
    }

    private fun isOverdue(t: MaintenanceTaskEntity, today: LocalDate): Boolean {
        val due = runCatching { LocalDate.parse(t.dueOn) }.getOrNull() ?: return false
        val snoozed = t.snoozedUntil?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return due.isBefore(today) && (snoozed == null || !snoozed.isAfter(today))
    }
}
