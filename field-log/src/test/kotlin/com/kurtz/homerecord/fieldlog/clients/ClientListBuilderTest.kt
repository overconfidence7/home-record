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

    private fun job(id: String, name: String, clientId: String?, status: String = "ACTIVE", deletedAt: Long? = null, startDate: String? = null) =
        JobEntity(id, "co", clientId = clientId, name = name, status = status, startDate = startDate, createdAt = 1, updatedAt = 1, deletedAt = deletedAt)

    private val today = "2026-11-04"

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
            today,
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
            today,
        )
        assertThat(rows.map { it.clientId }).containsExactly("c3")
    }

    // AC-F45: a blank phone or email is no phone or email, so no Call or Email button.
    @Test fun blankContactDetailsReadAsNone() {
        val row = ClientListBuilder.build(listOf(client("c1", "Hale", "  ", "")), listOf(job("j1", "Kitchen", "c1")), today).single()
        assertThat(row.phone).isNull()
        assertThat(row.email).isNull()
    }

    // AC-F45 (R11): a job's status is read as of today, so an Upcoming job whose start date came is listed as Active, first.
    @Test fun jobStatusIsReadAsOfToday() {
        val row = ClientListBuilder.build(
            listOf(client("c1", "Hale")),
            listOf(job("j1", "Attic fan", "c1", "ACTIVE"), job("j2", "Bath fan", "c1", "UPCOMING", startDate = "2026-11-02")),
            today,
        ).single()
        assertThat(row.jobs.map { it.status }).containsExactly(JobStatus.ACTIVE, JobStatus.ACTIVE)
        assertThat(row.jobs.map { it.name }).containsExactly("Attic fan", "Bath fan").inOrder()
    }

    // AC-F45: tap-to-call dials digits only, keeping a leading plus.
    @Test fun dialableKeepsDigitsAndLeadingPlus() {
        assertThat(ClientListBuilder.dialable("(555) 014-2290")).isEqualTo("5550142290")
        assertThat(ClientListBuilder.dialable(" +1 555-014-2290 ")).isEqualTo("+15550142290")
    }

    /** judge:fl-clients-phone-unformatted: a stored "5550142290" reads (555) 014-2290; other numbers stay as typed. */
    @Test fun phonesReadFormatted() {
        assertThat(ClientListBuilder.displayPhone("5550142290")).isEqualTo("(555) 014-2290")
        assertThat(ClientListBuilder.displayPhone("555-014-2290")).isEqualTo("(555) 014-2290")
        assertThat(ClientListBuilder.displayPhone("+1 555 014 2290")).isEqualTo("(555) 014-2290")
        assertThat(ClientListBuilder.displayPhone("15550142290")).isEqualTo("(555) 014-2290")
        assertThat(ClientListBuilder.displayPhone("+44 20 7946 0958")).isEqualTo("+44 20 7946 0958")
        assertThat(ClientListBuilder.displayPhone("555-0142 ext 4")).isEqualTo("555-0142 ext 4")
        assertThat(ClientListBuilder.displayPhone("0142")).isEqualTo("0142")
    }
}
