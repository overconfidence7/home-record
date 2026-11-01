package com.kurtz.homerecord.fieldlog.log

import android.content.res.Resources
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.CompanyEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.log.DailyLog
import com.kurtz.homerecord.core.log.LogPdfContent
import com.kurtz.homerecord.core.log.LogPdfSection
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.fieldlog.clock.WeekSheetBuilder
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Words a saved daily log for its PDF (AC-F33), in the order the Log tab shows it. Sections with nothing in them are
 * left out so the client reads what happened, not a list of "None". Photos still on another phone have no local file
 * and are skipped.
 */
object LogPdfContentBuilder {
    fun build(
        res: Resources,
        company: CompanyEntity?,
        job: JobEntity,
        client: ClientEntity?,
        log: DailyLog,
        photoPaths: Map<String, String>,
        logoExists: (String) -> Boolean = { java.io.File(it).exists() },
    ): LogPdfContent {
        val s = log.sections
        val money = NumberFormat.getCurrencyInstance(Locale.US)
        fun cents(c: Long) = money.format(c / 100.0)
        val sections = buildList {
            if (s.crew.value.isNotEmpty()) {
                val lines = s.crew.value.map { res.getString(R.string.log_pdf_crew_line, it.name, WeekSheetBuilder.hm(it.minutes * 60_000L)) }
                val total = res.getQuantityString(R.plurals.log_crew_summary, s.crew.value.size, s.crew.value.size, WeekSheetBuilder.hm(s.totalCrewMinutes * 60_000L))
                add(LogPdfSection(res.getString(R.string.log_section_crew), lines + res.getString(R.string.log_pdf_crew_total, total)))
            }
            if (s.workDone.value.isNotEmpty()) add(LogPdfSection(res.getString(R.string.log_section_work), s.workDone.value))
            if (s.tasksDone.value.isNotEmpty()) add(LogPdfSection(res.getString(R.string.log_section_tasks), s.tasksDone.value.map { it.title }))
            if (s.changeOrders.value.isNotEmpty()) add(
                LogPdfSection(res.getString(R.string.log_section_changes), s.changeOrders.value.map { res.getString(R.string.log_pdf_crew_line, it.title, cents(it.totalCents)) }),
            )
            if (s.materials.value.isNotEmpty()) add(
                LogPdfSection(
                    res.getString(R.string.log_section_materials),
                    s.materials.value.map { m ->
                        listOfNotNull(m.vendor, m.category, cents(m.amountCents), m.note).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")
                    },
                ),
            )
            if (s.delays.isNotEmpty()) add(
                LogPdfSection(
                    res.getString(R.string.log_section_delays),
                    s.delays.map { d ->
                        val reason = res.getString(d.reason.label())
                        if (d.note.isBlank()) reason else res.getString(R.string.log_pdf_delay_line, reason, d.note.trim())
                    },
                ),
            )
            s.weather.value?.let { w ->
                val parts = listOfNotNull(
                    res.getString(conditionsLabel(w.conditions)),
                    if (w.highF != null && w.lowF != null) res.getString(R.string.weather_high_low, w.highF ?: 0, w.lowF ?: 0) else null,
                    w.precipIn?.takeIf { it > 0 }?.let { res.getString(R.string.weather_precip, "%.2f".format(Locale.US, it)) },
                )
                add(LogPdfSection(res.getString(R.string.log_section_weather), listOf(parts.joinToString(", "))))
            }
            if (s.visitors.isNotBlank()) add(LogPdfSection(res.getString(R.string.log_section_visitors), listOf(s.visitors)))
            if (s.safety.isNotBlank()) add(LogPdfSection(res.getString(R.string.log_section_safety), listOf(s.safety)))
            if (s.clientNotes.isNotBlank()) add(LogPdfSection(res.getString(R.string.log_section_client), listOf(s.clientNotes)))
        }
        val date = log.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.US))
        return LogPdfContent(
            company = company?.name?.takeIf { it.isNotBlank() } ?: res.getString(R.string.app_name),
            companyLine = listOfNotNull(company?.phone, company?.email).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null },
            logoPath = company?.logoPath?.takeIf { it.isNotBlank() && logoExists(it) },
            title = res.getString(R.string.log_pdf_title, date),
            job = job.name,
            jobLine = listOfNotNull(client?.name, job.address).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null },
            sections = sections,
            photosTitle = res.getString(R.string.log_section_photos),
            photos = s.photoIds.value.mapNotNull { id -> photoPaths[id]?.takeIf { it.isNotBlank() } },
        )
    }
}
