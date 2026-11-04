package com.kurtz.homerecord.fieldlog.clients

import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.model.JobStatus
import com.kurtz.homerecord.core.model.effectiveStatus

/** One job under a client on FL-Clients. */
data class ClientJob(val jobId: String, val name: String, val status: JobStatus)

/** One client on FL-Clients: who to call or email and the jobs done for them (AC-F45). */
data class ClientRow(
    val clientId: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val jobs: List<ClientJob>,
)

/**
 * Builds FL-Clients from the company's clients and jobs (AC-F45). Clients come from jobs, so a client no live job names
 * is left out. Rows sort by name ignoring case; each client's jobs list Active, then Upcoming, Done and Archived, by name
 * within a status, the status as of [today] like everywhere else (an Upcoming job past its start date is Active). Blank
 * phone or email reads as none, so the screen never offers to call an empty number.
 */
object ClientListBuilder {
    private val statusOrder = listOf(JobStatus.ACTIVE, JobStatus.UPCOMING, JobStatus.DONE, JobStatus.ARCHIVED)

    fun build(clients: List<ClientEntity>, jobs: List<JobEntity>, today: String): List<ClientRow> {
        val jobsByClient = jobs.filter { it.deletedAt == null && it.clientId != null }.groupBy { it.clientId }
        return clients.filter { it.deletedAt == null }.mapNotNull { client ->
            val own = jobsByClient[client.id].orEmpty()
            if (own.isEmpty()) return@mapNotNull null
            ClientRow(
                clientId = client.id,
                name = client.name.trim(),
                phone = client.phone?.trim()?.takeIf { it.isNotEmpty() },
                email = client.email?.trim()?.takeIf { it.isNotEmpty() },
                jobs = own.map { ClientJob(it.id, it.name, it.effectiveStatus(today)) }
                    .sortedWith(compareBy<ClientJob>({ statusOrder.indexOf(it.status) }, { it.name.lowercase() })),
            )
        }.sortedWith(compareBy({ it.name.lowercase() }, { it.clientId }))
    }

    /** What a phone number dials: digits and a leading plus only. */
    fun dialable(phone: String): String {
        val trimmed = phone.trim()
        return (if (trimmed.startsWith("+")) "+" else "") + trimmed.filter(Char::isDigit)
    }
}
