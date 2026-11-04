package com.kurtz.homerecord.fieldlog.changes

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.fieldlog.R

/** The platform speech to text screen; the words it heard come back as [ChangeOrderFormEvent.Dictated]. */
internal fun dictationIntent(prompt: String): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)

/** The change order form; closes itself once saved or deleted. [onTakePhoto] opens the job camera (T056b). */
@Composable
fun ChangeOrderFormRoute(
    deps: ChangeOrderFormDeps,
    onClose: () -> Unit,
    onTakePhoto: () -> Unit = {},
    onSign: (String) -> Unit = {},
    onOpenOrder: (String) -> Unit = {},
) {
    val vm = viewModel<ChangeOrderFormViewModel>(
        key = "co-form-${deps.jobId}-${deps.changeOrderId ?: "new"}",
        factory = viewModelFactory { initializer { ChangeOrderFormViewModel(deps, createSavedStateHandle()) } },
    )
    val state by vm.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onClose() }
    LaunchedEffect(state.signId) { state.signId?.let(onSign) }
    LaunchedEffect(state.duplicateId) { state.duplicateId?.let(onOpenOrder) }
    val prompt = stringResource(R.string.co_form_speak_prompt)
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.onEvent(ChangeOrderFormEvent.Dictated(it)) }
        }
    }
    ChangeOrderFormScreen(state, { e ->
        when (e) {
            ChangeOrderFormEvent.Close -> onClose()
            ChangeOrderFormEvent.Speak -> runCatching { dictate.launch(dictationIntent(prompt)) }
            ChangeOrderFormEvent.TakePhoto -> {
                vm.onEvent(e)
                onTakePhoto()
            }
            else -> vm.onEvent(e)
        }
    })
}
