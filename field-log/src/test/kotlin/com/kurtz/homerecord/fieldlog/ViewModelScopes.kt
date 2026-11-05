package com.kurtz.homerecord.fieldlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking

/**
 * The ViewModels a test builds, so tearDown can stop their collectors before the in-memory database closes.
 * Without it a Room flow collector outlives `db.close()` and fails a later test in the same Robolectric JVM with
 * "connection pool has been closed" (REVIEW: flaky SignOutDialogTest).
 */
class ViewModelScopes {
    private val created = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(vm: T): T = vm.also { created += it }

    fun cancelAll() = runBlocking {
        created.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() }
        created.clear()
    }
}
