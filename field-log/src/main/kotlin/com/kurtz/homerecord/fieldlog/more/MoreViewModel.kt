package com.kurtz.homerecord.fieldlog.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.photo.FileStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What FL-More needs from the app: who is signed in, the unsent change count and the job file storage (AC-F4, AC-F20). */
class MoreDeps(
    val companyName: String,
    val role: CompanyRole,
    val unsynced: Flow<Int>,
    val storage: suspend () -> FileStorage,
    val freeUp: suspend () -> Unit,
    val io: CoroutineDispatcher = Dispatchers.IO,
)

/** FL-More: counts what job files hold on this phone and frees the cached copies; navigation stays in the route. */
class MoreViewModel(private val deps: MoreDeps) : ViewModel() {
    private val storage = MutableStateFlow<FileStorage?>(null)
    private var freeing = false

    val state: StateFlow<MoreUiState> = combine(deps.unsynced, storage) { unsynced, s ->
        MoreUiState(deps.companyName, deps.role, s, unsynced)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MoreUiState(deps.companyName, deps.role))

    fun freeUp() {
        if (freeing) return
        freeing = true
        viewModelScope.launch {
            try {
                withContext(deps.io) { deps.freeUp() }
                storage.value = withContext(deps.io) { deps.storage() }
            } finally {
                freeing = false
            }
        }
    }

    /** Counts again; the route calls it each time More is shown, since downloads elsewhere change the total. */
    fun refresh() {
        viewModelScope.launch { storage.value = withContext(deps.io) { deps.storage() } }
    }
}
