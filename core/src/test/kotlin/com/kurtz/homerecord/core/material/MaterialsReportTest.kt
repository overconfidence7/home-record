package com.kurtz.homerecord.core.material

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-F44 (list per job with totals; CSV export)
@AcceptanceCriterion("AC-F44")
class MaterialsReportTest {
    private fun m(id: String, job: String, vendor: String?, cents: Long, date: String, note: String? = null, cat: MaterialCategory = MaterialCategory.MATERIALS) =
        Material(id, job, vendor, cents, cat, date, null, note)

    private val jobs = mapOf("j1" to "smith deck", "j2" to "Hale kitchen")
    private val all = listOf(
        m("a", "j1", "Lumber Liquidators", 41_200, "2026-11-02"),
        m("b", "j2", "City Electric", 12_371, "2026-11-03", note = "Wire, \"Wago\" nuts"),
        m("c", "j2", "Sunbelt", 9_500, "2026-11-04", cat = MaterialCategory.EQUIPMENT_RENTAL),
        m("d", "gone", "Nobody", 100, "2026-11-04"),
    )

    @Test fun groupsByJobNameWithTotalsNewestFirst() {
        val groups = MaterialsReport.group(all, jobs)
        assertThat(groups.map { it.jobName }).containsExactly("Hale kitchen", "smith deck").inOrder()
        assertThat(groups[0].items.map { it.id }).containsExactly("c", "b").inOrder()
        assertThat(groups.map { it.totalCents }).containsExactly(21_871L, 41_200L).inOrder()
    }

    @Test fun csvHasOneLinePerReceiptThenTotals() {
        val csv = MaterialsReport.csv(MaterialsReport.group(all, jobs)) { it.name.lowercase() }
        assertThat(csv.split("\r\n").filter { it.isNotEmpty() }).containsExactly(
            "Date,Job,Vendor,Category,Amount,Note",
            "2026-11-04,Hale kitchen,Sunbelt,equipment_rental,95.00,",
            "2026-11-03,Hale kitchen,City Electric,materials,123.71,\"Wire, \"\"Wago\"\" nuts\"",
            "2026-11-02,smith deck,Lumber Liquidators,materials,412.00,",
            ",Hale kitchen,,Total,218.71,",
            ",smith deck,,Total,412.00,",
            ",All jobs,,Total,630.71,",
        ).inOrder()
    }
}
