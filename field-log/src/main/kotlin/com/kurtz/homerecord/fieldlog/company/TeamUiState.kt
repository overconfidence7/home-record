package com.kurtz.homerecord.fieldlog.company

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.TeamMember

enum class TeamStatus { Loading, Ready, Failed }

data class TeamUiState(
    val status: TeamStatus = TeamStatus.Loading,
    val failure: ApiFailure? = null,
    val callerRole: CompanyRole = CompanyRole.Owner,
    val companyName: String = "",
    val members: List<TeamMember> = emptyList(),
    val inviteCode: String? = null,
    val inviteLink: String? = null,
    /** Member whose action sheet is open. */
    val actionsFor: TeamMember? = null,
    val confirmRemove: TeamMember? = null,
    val rateFor: TeamMember? = null,
    val busy: Boolean = false,
    /** A failed action (role change, remove, new code) while the list itself is fine. */
    val actionError: ApiFailure? = null,
) {
    /** Roles offered in the sheet for [member]: none if the caller cannot manage them. */
    fun rolesFor(member: TeamMember): List<CompanyRole> =
        if (callerRole.canManage(member.role)) callerRole.assignableRoles.filter { it != member.role } else emptyList()

    fun canRemove(member: TeamMember): Boolean = callerRole.canManage(member.role)

    /** Rates are Owner only (SPEC FL-Team) and only for people who can be edited. */
    fun canSetRate(member: TeamMember): Boolean = callerRole == CompanyRole.Owner && member.role != CompanyRole.Owner
}

sealed interface TeamEvent {
    data object Refresh : TeamEvent
    data class OpenActions(val member: TeamMember) : TeamEvent
    data object DismissActions : TeamEvent
    data class SetRole(val member: TeamMember, val role: CompanyRole) : TeamEvent
    data class AskRemove(val member: TeamMember) : TeamEvent
    data object ConfirmRemove : TeamEvent
    data object CancelRemove : TeamEvent
    data class AskRate(val member: TeamMember) : TeamEvent
    data class SaveRate(val cents: Long?) : TeamEvent
    data object DismissRate : TeamEvent
    data object NewCode : TeamEvent
    data object DismissError : TeamEvent
}
