package com.kurtz.homerecord.coreui.account

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Delete account (T081d) on HM-Account and FL-More. */
data class DeleteAccountUiState(
    /** The "Delete your account?" dialog is open. */
    val confirmOpen: Boolean = false,
    /** The server is deleting the account. */
    val deleting: Boolean = false,
    /** The last try failed; [offline] when it never reached the server. */
    val failed: Boolean = false,
    val offline: Boolean = false,
    /** The account is gone and this phone signed out. */
    val deleted: Boolean = false,
)

sealed interface DeleteAccountEvent {
    data object Delete : DeleteAccountEvent
    data object Confirm : DeleteAccountEvent
    data object Dismiss : DeleteAccountEvent
}

/**
 * Asks first (the open dialog survives process death), then [delete] removes the account on the server and runs the
 * app's clean-up ([com.kurtz.homerecord.core.auth.AccountDeletion]). A second Confirm while one runs does nothing.
 */
class DeleteAccountViewModel(
    private val delete: suspend () -> ApiResult<Unit>,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private data class Run(val deleting: Boolean = false, val failure: ApiFailure? = null, val deleted: Boolean = false)

    private val run = MutableStateFlow(Run(deleted = saved[DELETED] ?: false))

    val state: StateFlow<DeleteAccountUiState> = combine(saved.getStateFlow(CONFIRM, false), run) { confirm, r ->
        DeleteAccountUiState(
            confirmOpen = confirm,
            deleting = r.deleting,
            failed = r.failure != null,
            offline = r.failure == ApiFailure.Offline,
            deleted = r.deleted,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DeleteAccountUiState(deleted = run.value.deleted))

    fun onEvent(event: DeleteAccountEvent) {
        when (event) {
            DeleteAccountEvent.Delete -> saved[CONFIRM] = true
            DeleteAccountEvent.Dismiss -> saved[CONFIRM] = false
            DeleteAccountEvent.Confirm -> {
                saved[CONFIRM] = false
                if (run.value.deleting) return
                run.value = Run(deleting = true)
                viewModelScope.launch {
                    run.value = when (val result = delete()) {
                        is ApiResult.Ok -> Run(deleted = true).also { saved[DELETED] = true }
                        is ApiResult.Err -> Run(failure = result.failure)
                    }
                }
            }
        }
    }

    private companion object {
        const val CONFIRM = "confirmDeleteAccount"
        const val DELETED = "accountDeleted"
    }
}
