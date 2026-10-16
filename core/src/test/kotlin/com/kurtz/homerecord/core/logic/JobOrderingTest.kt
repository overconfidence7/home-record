package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.EntryKind
import com.kurtz.homerecord.core.model.Job
import com.kurtz.homerecord.core.model.LogEntry
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class JobOrderingTest {
    private fun job(id: String, created: Long, archived: Boolean = false) =
        Job(id, "h$id", id, archived = archived, createdAt = created, updatedAt = created, source = Source.FIELD_LOG)

    private fun entry(jobId: String, at: Long) =
        LogEntry("e$at", jobId, "2026-10-13", EntryKind.NOTE, "x", createdAt = at, updatedAt = at, source = Source.FIELD_LOG)

    @Test fun `most recent entry wins, jobs without entries use createdAt`() {
        val jobs = listOf(job("a", 100), job("b", 200), job("c", 300))
        val out = JobOrdering.summarize(jobs, listOf(entry("a", 500), entry("b", 250), entry("a", 50)))
        assertEquals(listOf("a", "c", "b"), out.map { it.job.id })
        assertEquals(500L, out.first().lastActivity)
        assertEquals(2, out.first().entryCount)
    }

    @Test fun `an old job with a new entry beats a new job without`() {
        val out = JobOrdering.summarize(listOf(job("old", 1), job("new", 1000)), listOf(entry("old", 2000)))
        assertEquals("old", out.first().job.id)
    }

    @Test fun `archived jobs are hidden unless requested`() {
        val all = JobOrdering.summarize(listOf(job("a", 1), job("b", 2, archived = true)), emptyList())
        assertEquals(listOf("a"), JobOrdering.visible(all, false).map { it.job.id })
        assertEquals(setOf("a", "b"), JobOrdering.visible(all, true).map { it.job.id }.toSet())
    }

    @Test fun `ties break by name`() {
        val out = JobOrdering.summarize(listOf(job("b", 5), job("a", 5)), emptyList())
        assertEquals(listOf("a", "b"), out.map { it.job.id })
    }
}
