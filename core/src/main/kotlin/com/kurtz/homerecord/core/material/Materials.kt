package com.kurtz.homerecord.core.material

import com.kurtz.homerecord.core.model.TimeExports
import com.kurtz.homerecord.core.units.Money

/** What a receipt was for (AC-F44); stored by name in `material.category`. */
enum class MaterialCategory { MATERIALS, TOOLS, EQUIPMENT_RENTAL, FUEL, PERMITS, OTHER;

    companion object {
        /** A stored category back to the enum; an unknown or blank one reads as [OTHER]. */
        fun of(raw: String?): MaterialCategory = entries.firstOrNull { it.name == raw } ?: OTHER
    }
}

/** One logged receipt or material purchase. */
data class Material(
    val id: String,
    val jobId: String,
    val vendor: String?,
    val amountCents: Long,
    val category: MaterialCategory,
    /** ISO yyyy-MM-dd. */
    val date: String,
    val receiptPath: String?,
    val note: String?,
)

/** One job's receipts, newest first, and their total. */
data class JobMaterialGroup(val jobId: String, val jobName: String, val items: List<Material>, val totalCents: Long)

/** The Materials list (AC-F44): receipts grouped by job with a total per job and for all jobs, and the CSV. */
object MaterialsReport {
    /** Groups [materials] by job; jobs by name ignoring case, each job's receipts newest first; jobs not in [jobNames] are left out. */
    fun group(materials: List<Material>, jobNames: Map<String, String>): List<JobMaterialGroup> =
        materials.filter { it.jobId in jobNames }
            .groupBy { it.jobId }
            .map { (jobId, items) ->
                JobMaterialGroup(jobId, jobNames.getValue(jobId), items.sortedWith(compareByDescending<Material> { it.date }.thenBy { it.vendor.orEmpty().lowercase() }), items.sumOf { it.amountCents })
            }
            .sortedWith(compareBy<JobMaterialGroup> { it.jobName.lowercase() }.thenBy { it.jobId })

    /**
     * The CSV for a bookkeeper: Date, Job, Vendor, Category, Amount, Note, one line per receipt in the order of [groups],
     * then a Total line per job and one for everything. Amounts are dollars with two decimals; [categoryLabel] words the category.
     */
    fun csv(groups: List<JobMaterialGroup>, categoryLabel: (MaterialCategory) -> String): String = buildString {
        append(line("Date", "Job", "Vendor", "Category", "Amount", "Note"))
        for (g in groups) {
            for (m in g.items) append(line(m.date, g.jobName, m.vendor.orEmpty(), categoryLabel(m.category), Money.format(m.amountCents), m.note.orEmpty()))
        }
        for (g in groups) append(line("", g.jobName, "", "Total", Money.format(g.totalCents), ""))
        append(line("", "All jobs", "", "Total", Money.format(groups.sumOf { it.totalCents }), ""))
    }

    /** "materials-2026-11-04.csv" for the share sheet. */
    fun fileName(today: java.time.LocalDate): String = "materials-$today.csv"

    private fun line(vararg cells: String): String = cells.joinToString(",") { TimeExports.csv(it) } + "\r\n"
}
