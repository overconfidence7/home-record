package com.kurtz.homerecord.fieldlog.materials

import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.material.Material

/**
 * What FL-Materials shows a member (AC-F44, T060c2): Owner and Office see every job and receipt; a Lead sees only the
 * jobs they run and the receipts on those jobs. Crew and Sub never reach the screen. Mirrors backend policy.ts.
 */
object MaterialsScope {
    /** The jobs [gates] may log receipts on, by name ignoring case. */
    fun <J> jobs(jobs: List<J>, gates: RoleGates, id: (J) -> String, name: (J) -> String): List<JobOption> =
        jobs.filter { gates.leads(id(it)) }.sortedBy { name(it).lowercase() }.map { JobOption(id(it), name(it)) }

    /** The receipts on jobs [gates] runs. */
    fun materials(materials: List<Material>, gates: RoleGates): List<Material> = materials.filter { gates.leads(it.jobId) }
}
