package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.DailyReport
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReportSectionBuilderTest {
    private val labels = ReportLabels("Weather", "Crew", "Hours", "Work", "Delays", "Materials", "Next")
    private fun report(
        weather: String? = null, crew: Int? = null, hours: Double? = null, work: String = "",
        delays: String? = null, materials: String? = null, next: String? = null,
    ) = DailyReport(
        "r", "j", "2026-10-14", weather, crew, hours, work, delays, null, materials, next, null, 1, 1, Source.FIELD_LOG,
    )

    @Test fun `empty fields are dropped and order is fixed`() {
        val s = ReportSectionBuilder.sections(report(weather = "Sunny", crew = 3, hours = 8.0, work = "Framed", next = " Drywall "), labels, null)
        assertEquals(listOf("Weather" to "Sunny", "Crew" to "3", "Hours" to "8", "Work" to "Framed", "Next" to "Drywall"), s.map { it.label to it.text })
    }

    @Test fun `fractional hours keep decimals`() {
        val s = ReportSectionBuilder.sections(report(hours = 7.5), labels, null)
        assertEquals("7.5", s.single().text)
    }

    @Test fun `delay reason and details combine`() {
        assertEquals("Rain: Pumped basement", ReportSectionBuilder.sections(report(delays = "Pumped basement"), labels, "Rain").single().text)
        assertEquals("Rain", ReportSectionBuilder.sections(report(), labels, "Rain").single().text)
        assertEquals("Late delivery", ReportSectionBuilder.sections(report(delays = "Late delivery"), labels, null).single().text)
    }

    @Test fun `blank report has no sections`() {
        assertEquals(emptyList<ReportSection>(), ReportSectionBuilder.sections(report(work = "  "), labels, null))
    }

    @Test fun `photos are capped at six and laid out two per row (AC-69)`() {
        val rows = ReportSectionBuilder.photoRows((1..8).map { "p$it" })
        assertEquals(listOf(listOf("p1", "p2"), listOf("p3", "p4"), listOf("p5", "p6")), rows)
        assertEquals(listOf(listOf("a", "b"), listOf("c")), ReportSectionBuilder.photoRows(listOf("a", "b", "c")))
        assertEquals(emptyList<List<String>>(), ReportSectionBuilder.photoRows(emptyList()))
    }

    @Test fun `same job and day give the same file name (AC-70)`() {
        val a = ReportSectionBuilder.fileName("job-1", "2026-10-14")
        assertEquals(a, ReportSectionBuilder.fileName("job-1", "2026-10-14"))
        assertNotEquals(a, ReportSectionBuilder.fileName("job-1", "2026-10-15"))
        assertEquals("report-job-1-2026-10-14.pdf", a)
        assertEquals("report-ab-2026-10-14.pdf", ReportSectionBuilder.fileName("../ab", "2026-10-14"))
    }
}
