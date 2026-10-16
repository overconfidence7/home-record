package com.kurtz.homerecord.core.repo

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.logic.ReportDraftBuilder
import com.kurtz.homerecord.core.model.CompanyProfile
import com.kurtz.homerecord.core.model.DailyReport
import com.kurtz.homerecord.core.model.LogEntry
import com.kurtz.homerecord.core.model.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Daily report drafts (one per job per day) and the single company profile (SPEC §6). */
class ReportRepository(
    private val db: HomeRecordDatabase,
    private val source: Source,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    /** Saved draft for the day, or a prefilled one that is not stored until [save]. */
    suspend fun draftFor(jobId: String, day: String): DailyReport {
        val existing = db.dailyReportDao().getForDay(jobId, day)?.toDomain()
        val entries = if (existing == null) entriesFor(jobId) else emptyList()
        return ReportDraftBuilder.build(jobId, day, entries, existing, ids.next(), clock.now(), source)
    }

    suspend fun photosFor(jobId: String, day: String): List<LogEntry> =
        ReportDraftBuilder.photos(entriesFor(jobId), day)

    suspend fun save(report: DailyReport): DailyReport {
        val saved = report.copy(updatedAt = clock.now())
        db.dailyReportDao().upsert(saved.toEntity())
        return saved
    }

    fun observeProfile(): Flow<CompanyProfile?> = db.companyProfileDao().observeFirst().map { it?.toDomain() }

    suspend fun getProfile(): CompanyProfile? = db.companyProfileDao().getFirst()?.toDomain()

    /** Single-row profile; blank fields become null. */
    suspend fun saveProfile(name: String, phone: String?, email: String?, logoPath: String?): CompanyProfile {
        val now = clock.now()
        val current = getProfile()
        val profile = CompanyProfile(
            id = current?.id ?: ids.next(), name = name.trim(), phone = phone.clean(), email = email.clean(),
            logoPath = logoPath, createdAt = current?.createdAt ?: now, updatedAt = now, source = source,
        )
        db.companyProfileDao().upsert(profile.toEntity())
        return profile
    }

    private suspend fun entriesFor(jobId: String): List<LogEntry> =
        db.logEntryDao().observeForJob(jobId).first().map { it.toDomain() }

    private fun String?.clean(): String? = this?.trim()?.ifEmpty { null }
}
