package com.kurtz.homerecord.fieldlog.materials

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.material.Material
import com.kurtz.homerecord.core.material.MaterialCategory
import org.junit.Test

// AC-F44 (T060c2): a Lead logs receipts on the jobs they run; Owner and Office see every job.
class MaterialsScopeTest {
    private data class Job(val id: String, val name: String)

    private val jobs = listOf(Job("j2", "smith panel swap"), Job("j1", "Garage subpanel"), Job("j3", "Ortiz kitchen"))

    private fun receipt(id: String, jobId: String) =
        Material(id, jobId, "Home Depot", 4_250, MaterialCategory.MATERIALS, "2026-11-02", null, null)

    private val receipts = listOf(receipt("m1", "j1"), receipt("m2", "j2"), receipt("m3", "j3"))

    @Test fun ownerSeesEveryJobByName() {
        val options = MaterialsScope.jobs(jobs, RoleGates(CompanyRole.Owner), { it.id }, { it.name })
        assertThat(options.map { it.name }).containsExactly("Garage subpanel", "Ortiz kitchen", "smith panel swap").inOrder()
        assertThat(MaterialsScope.materials(receipts, RoleGates(CompanyRole.Office)).map { it.id }).containsExactly("m1", "m2", "m3")
    }

    @Test fun leadSeesOnlyTheJobsTheyRun() {
        val lead = RoleGates(CompanyRole.Lead, leadJobs = setOf("j3", "j1"))
        assertThat(MaterialsScope.jobs(jobs, lead, { it.id }, { it.name }).map { it.id }).containsExactly("j1", "j3").inOrder()
        assertThat(MaterialsScope.materials(receipts, lead).map { it.id }).containsExactly("m1", "m3")
    }

    @Test fun leadOnNoJobsSeesNothing() {
        val lead = RoleGates(CompanyRole.Lead)
        assertThat(MaterialsScope.jobs(jobs, lead, { it.id }, { it.name })).isEmpty()
        assertThat(MaterialsScope.materials(receipts, lead)).isEmpty()
    }
}
