package com.kurtz.homerecord.fieldlog.clients

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.model.JobStatus
import org.junit.Test

/** AC-F45: FL-Clients lists the clients named on jobs, with their phone, email and jobs. */
class ClientListBuilderTest {
    private fun client(id: String, name: String, phone: String? = null, email: String? = null, deletedAt: Long? = null) =
        ClientEntity(id, "co", name, phone, email, createdAt = 1, updatedAt = 1, deletedAt = deletedAt)

    private fun job(id: String, name: String, clientId: String?, status: String = "ACTIVE", deletedAt: Long? = null) =
        JobEntity(id, "co", clientId = clientId, name = name, status = status, createdAt = 1, updatedAt = 1, deletedAt = deletedAt)

    // AC-F45
    @Test fun clientsSortByNameWithTheirJobsActiveFirst() {
        val rows = ClientListBuilder.build(
            listOf(client("c1", "smith family", "(555) 014-2290", "pat@smith.test"), client("c2", "Dana Hale")),
            listOf(
                job("j1", "Smith deck", "c1", "DONE"),
                job("j2", "Smith basement", "c1", "ACTIVE"),
                job("j3", "Hale kitchen", "c2", "UPCOMING"),
                job("j4", "Garage subpanel", null),
            ),
        )
        assertThat(rows.map { it.name }).containsExactly("Dana Hale", "smith family").inOrder()
        assertThat(rows[1].jobs.map { it.jobId }).containsExactly("j2", "j1").inOrder()
        assertThat(rows[1].jobs.first().status).isEqualTo(JobStatus.ACTIVE)
        assertThat(rows[1].phone).isEqualTo("(555) 014-2290")
        assertThat(rows[1].email).isEqualTo("pat@smith.test")
    }

    // AC-F45: clients come from jobs; a client with no live job, and deleted rows, are left out.
    @Test fun clientsWithoutLiveJobsAreLeftOut() {
        val rows = ClientListBuilder.build(
            listOf(client("c1", "Old client"), client("c2", "Gone", deletedAt = 5), client("c3", "Kept")),
            listOf(job("j1", "Removed job", "c1", deletedAt = 9), job("j2", "Job", "c2"), job("j3", "Porch", "c3")),
        )
        assertThat(rows.map { it.clientId }).containsExactly("c3")
    }

    // AC-F45: a blank phone or email is no phone or email, so no Call or Email button.
    @Test fun blankContactDetailsReadAsNone() {
        val row = ClientListBuilder.build(listOf(client("c1", "Hale", "  ", "")), listOf(job("j1", "Kitchen", "c1"))).single()
        assertThat(row.phone).isNull()
        assertThat(row.email).isNull()
    }

    // AC-F45: tap-to-call dials digits only, keeping a leading plus.
    @Test fun dialableKeepsDigitsAndLeadingPlus() {
        assertThat(ClientListBuilder.dialable("(555) 014-2290")).isEqualTo("5550142290")
        assertThat(ClientListBuilder.dialable(" +1 555-014-2290 ")).isEqualTo("+15550142290")
    }
}
