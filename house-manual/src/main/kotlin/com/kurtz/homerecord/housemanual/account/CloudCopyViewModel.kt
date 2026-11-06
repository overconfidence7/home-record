package com.kurtz.homerecord.housemanual.account

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

/**
 * Delete cloud copy (AC-H15): asks first (the open dialog survives process death), then [delete] removes the home
 * from the server, signs out and keeps this phone's copy ([com.kurtz.homerecord.core.sync.HomeCloudCopy]).
 */
class CloudCopyViewModel(
    private val homeId: String,
    private val delete: suspend (homeId: String) -> ApiResult<Unit>,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private data class Run(val deleting: Boolean = false, val failure: ApiFailure? = null, val deleted: Boolean = false)

    private val run = MutableStateFlow(Run(deleted = saved[DELETED] ?: false))

    val state: StateFlow<CloudCopyUiState> = combine(saved.getStateFlow(CONFIRM, false), run) { confirm, r ->
        CloudCopyUiState(
            confirmOpen = confirm,
            deleting = r.deleting,
            failed = r.failure != null,
            offline = r.failure == ApiFailure.Offline,
            deleted = r.deleted,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CloudCopyUiState(deleted = run.value.deleted))

    fun onEvent(event: CloudCopyEvent) {
        when (event) {
            CloudCopyEvent.Delete -> saved[CONFIRM] = true
            CloudCopyEvent.Dismiss -> saved[CONFIRM] = false
            CloudCopyEvent.Confirm -> {
                saved[CONFIRM] = false
                if (run.value.deleting) return
                run.value = Run(deleting = true)
                viewModelScope.launch {
                    run.value = when (val result = delete(homeId)) {
                        is ApiResult.Ok -> Run(deleted = true).also { saved[DELETED] = true }
                        is ApiResult.Err -> Run(failure = result.failure)
                    }
                }
            }
        }
    }

    private companion object {
        const val CONFIRM = "confirmDeleteCloud"
        const val DELETED = "cloudDeleted"
    }
}
