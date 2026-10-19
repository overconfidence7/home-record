package com.kurtz.homerecord.fieldlog.company

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.company.CompanyApi
import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.TeamMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** FL-Team: members, invite code and role changes. The server enforces every rule again (AC-B3). */
class TeamViewModel(
    private val api: CompanyApi,
    private val membership: CompanyMembership,
    private val inviteLinkFor: (String) -> String,
) : ViewModel() {
    private val _state = MutableStateFlow(TeamUiState(callerRole = membership.role, companyName = membership.companyName))
    val state: StateFlow<TeamUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onEvent(event: TeamEvent) {
        when (event) {
            TeamEvent.Refresh -> refresh()
            is TeamEvent.OpenActions -> update { copy(actionsFor = event.member) }
            TeamEvent.DismissActions -> update { copy(actionsFor = null) }
            is TeamEvent.SetRole -> act { api.setRole(membership.companyId, event.member.userId, event.role) }
            is TeamEvent.AskRemove -> update { copy(actionsFor = null, confirmRemove = event.member) }
            TeamEvent.CancelRemove -> update { copy(confirmRemove = null) }
            TeamEvent.ConfirmRemove -> _state.value.confirmRemove?.let { m -> act { api.remove(membership.companyId, m.userId) } }
            is TeamEvent.AskRate -> update { copy(actionsFor = null, rateFor = event.member) }
            TeamEvent.DismissRate -> update { copy(rateFor = null) }
            is TeamEvent.SaveRate -> _state.value.rateFor?.let { m -> act { api.setRate(membership.companyId, m.userId, event.cents) } }
            TeamEvent.NewCode -> rotate()
            TeamEvent.DismissError -> update { copy(actionError = null) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val members = api.members(membership.companyId)
            val invite = api.invite(membership.companyId)
            when {
                members is ApiResult.Ok -> update {
                    copy(
                        status = TeamStatus.Ready,
                        failure = null,
                        members = members.value.sortedWith(compareBy<TeamMember> { it.role.ordinal }.thenBy { it.displayName.lowercase() }),
                        inviteCode = (invite as? ApiResult.Ok)?.value ?: inviteCode,
                        inviteLink = (invite as? ApiResult.Ok)?.value?.let(inviteLinkFor) ?: inviteLink,
                    )
                }
                // Keep showing the last good list on a failed background refresh.
                _state.value.status == TeamStatus.Ready -> Unit
                else -> update { copy(status = TeamStatus.Failed, failure = (members as ApiResult.Err).failure) }
            }
        }
    }

    private fun rotate() {
        if (_state.value.busy) return
        update { copy(busy = true, actionError = null) }
        viewModelScope.launch {
            when (val r = api.rotateInvite(membership.companyId)) {
                is ApiResult.Ok -> update { copy(busy = false, inviteCode = r.value, inviteLink = inviteLinkFor(r.value)) }
                is ApiResult.Err -> update { copy(busy = false, actionError = r.failure) }
            }
        }
    }

    private fun act(call: suspend () -> ApiResult<Unit>) {
        if (_state.value.busy) return
        update { copy(busy = true, actionError = null) }
        viewModelScope.launch {
            val r = call()
            update {
                copy(
                    busy = false,
                    actionsFor = null,
                    confirmRemove = null,
                    rateFor = null,
                    actionError = (r as? ApiResult.Err)?.failure,
                )
            }
            refresh()
        }
    }

    private fun update(f: TeamUiState.() -> TeamUiState) = _state.update(f)
}
