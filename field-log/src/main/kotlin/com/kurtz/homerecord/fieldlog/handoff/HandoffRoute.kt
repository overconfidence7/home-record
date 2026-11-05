package com.kurtz.homerecord.fieldlog.handoff

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.io.File

/** Wires FL-Handoff to the job's house and the handoff upload (AC-F51, AC-B40). */
@Composable
fun HandoffRoute(files: HandoffFiles, homeId: String, jobName: String, onBack: () -> Unit) {
    val vm = viewModel<HandoffViewModel>(
        key = "handoff:$homeId",
        factory = viewModelFactory {
            initializer {
                HandoffViewModel(
                    jobName,
                    load = { files.load(homeId) },
                    write = { files.write(it, jobName) },
                    send = { email, file -> files.send(homeId, jobName, email, file) },
                    saved = createSavedStateHandle(),
                    prefillEmail = { files.recipient(homeId)?.email },
                )
            }
        },
    )
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.sharePath) {
        val path = state.sharePath ?: return@LaunchedEffect
        files.share(File(path))
        vm.onEvent(HandoffEvent.ShareHandled)
    }
    HandoffScreen(state) { e -> if (e == HandoffEvent.Back) onBack() else vm.onEvent(e) }
}
