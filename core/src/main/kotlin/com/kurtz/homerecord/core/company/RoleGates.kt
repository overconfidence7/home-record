package com.kurtz.homerecord.core.company

import com.kurtz.homerecord.core.data.AssignmentDao
import com.kurtz.homerecord.core.data.JobCrewDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * What this member may do in Field Log, from their role and the jobs they are on (AC-F3). Mirrors the server rule in
 * backend/src/sync/policy.ts (AC-B3), so the app hides what the server would refuse instead of failing on sync.
 *
 * Owner and Office do everything. A Lead manages files and photos and clocks in the crew only on the jobs they are on
 * (job crew or a scheduled day). Crew and Sub view files and add captures; they never edit jobs or delete others' work.
 */
data class RoleGates(val role: CompanyRole, val leadJobs: Set<String> = emptySet()) {
    /** New job, edit job and its client, crew and rooms: Owner and Office only. */
    val canEditJobs: Boolean get() = role.isAdmin

    /** True when this member runs [jobId]: Owner and Office on every job, a Lead on the jobs they are on. */
    fun leads(jobId: String?): Boolean = role.isAdmin || (role == CompanyRole.Lead && jobId != null && jobId in leadJobs)

    /** Add, rename, delete files and make folders on the job's Files tab. */
    fun canManageFiles(jobId: String?): Boolean = leads(jobId)

    /** Delete a job photo. */
    fun canDeletePhoto(jobId: String?): Boolean = leads(jobId)

    /** Add, edit, assign, reorder and delete tasks on [jobId]; Crew and Sub only tick their own (AC-F34, backend policy.ts). */
    fun canManageTasks(jobId: String?): Boolean = leads(jobId)

    /** Clock in other people from this phone (AC-F11). */
    fun canClockCrew(jobId: String?): Boolean = leads(jobId)

    companion object {
        /** Live gates for [memberId]; the job list is only read for a Lead. */
        fun observe(role: CompanyRole, memberId: String?, crew: JobCrewDao, assignments: AssignmentDao): Flow<RoleGates> =
            if (role != CompanyRole.Lead || memberId == null) flowOf(RoleGates(role))
            else combine(crew.observeJobIdsForMember(memberId), assignments.observeJobIdsForMember(memberId)) { a, b -> RoleGates(role, (a + b).toSet()) }
    }
}
