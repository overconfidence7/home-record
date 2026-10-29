package com.kurtz.homerecord.fieldlog.qa

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import java.time.LocalDate

/**
 * Debug seed for the AC-U11 scroll measurement (S7: "200-row lists scroll without jank"): [count] active jobs of an
 * electrical contractor on Jobs, one millisecond apart so the list order is stable. Written to this phone only;
 * nothing is queued for sync, so the scroll is measured without 200 uploads running behind it.
 */
object LongListSeed {
    const val JOBS = 200

    private val work = listOf(
        "Panel upgrade", "EV charger install", "Kitchen rewire", "Service call", "Generator hookup",
        "Lighting retrofit", "Basement finish wiring", "Hot tub circuit", "Smoke alarm replacement", "Outlet repair",
    )
    private val clients = listOf(
        "Alvarez", "Brennan", "Chen", "Dubois", "Eriksen", "Fontaine", "Gupta", "Hartley", "Ibarra", "Jensen",
        "Kowalski", "Larsen", "Moreau", "Nakamura", "Okafor", "Petrov", "Quinn", "Rossi", "Sato", "Thompson",
    )
    private val streets = listOf("Maple Ave", "Oak St", "Birch Ln", "Cedar Ct", "Elm Dr", "Pine Rd", "Willow Way", "Spruce Pl")

    fun jobs(companyId: String, today: LocalDate, now: Long, count: Int = JOBS): List<JobEntity> = List(count) { i ->
        val client = clients[i % clients.size]
        JobEntity(
            id = "qa-long-job-${i + 1}",
            companyId = companyId,
            name = "${client} ${work[(i / clients.size) % work.size].lowercase()}".replaceFirstChar { it.uppercase() }
                .let { if (i >= clients.size * work.size) "$it ${i / (clients.size * work.size) + 1}" else it },
            address = "${100 + (i * 37) % 900} ${streets[i % streets.size]}",
            startDate = today.minusDays((i % 60).toLong()).toString(),
            createdAt = now + i,
            updatedAt = now + i,
        )
    }

    suspend fun seed(db: HomeRecordDatabase, companyId: String, today: LocalDate = LocalDate.now(), now: Long = System.currentTimeMillis()) {
        jobs(companyId, today, now).forEach { db.jobDao().upsert(it) }
    }
}
