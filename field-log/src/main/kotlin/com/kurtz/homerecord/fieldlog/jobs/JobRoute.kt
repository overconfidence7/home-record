package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.data.HomeRecordDatabase

@Composable
fun JobRoute(companyId: String, jobId: String, db: HomeRecordDatabase, onEdit: () -> Unit, onBack: () -> Unit) {
    val vm = viewModel<JobOverviewViewModel>(
        key = jobId,
        factory = viewModelFactory {
            initializer {
                JobOverviewViewModel(
                    companyId, jobId, db.jobDao(), db.clientDao(), db.jobCrewDao(), db.memberDao(),
                    db.assignmentDao(), db.taskDao(), db.punchDao(), db.photoDao(),
                )
            }
        },
    )
    var tab by rememberSaveable { mutableStateOf(JobTab.OVERVIEW) }
    val state by vm.state.collectAsState()
    JobScreen(
        state, tab, { tab = it },
        { event ->
            when (event) {
                is JobOverviewEvent.OpenTab -> tab = event.tab
                JobOverviewEvent.OpenEdit -> onEdit()
            }
        },
        onBack,
    )
}
