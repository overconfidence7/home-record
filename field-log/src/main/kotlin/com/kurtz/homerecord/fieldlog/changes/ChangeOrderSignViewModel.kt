package com.kurtz.homerecord.fieldlog.changes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.changeorder.JobChangeOrders
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import com.kurtz.homerecord.core.model.Signature
import com.kurtz.homerecord.core.model.SignatureStroke
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChangeOrderSignDeps(
    val jobId: String,
    val changeOrderId: String,
    val orders: JobChangeOrders,
    /** RoleGates.leads(jobId): Owner, Office and the job's Lead get change orders signed (AC-F3). */
    val canManage: Flow<Boolean> = flowOf(false),
    /** Draws the strokes into a PNG in filesDir/media and returns its path, or null when it could not be written. */
    val writeImage: suspend (List<SignatureStroke>) -> String? = { null },
)

/** Sign a Draft change order on the phone (AC-F38). The name and the strokes survive rotation and process death. */
class ChangeOrderSignViewModel(private val d: ChangeOrderSignDeps, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val _state = MutableStateFlow(ChangeOrderSignUiState())
    val state: StateFlow<ChangeOrderSignUiState> = _state.asStateFlow()

    init { load() }

    private fun load() {
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val co = d.orders.get(d.changeOrderId)?.takeIf { it.jobId == d.jobId && it.status == ChangeOrderStatus.DRAFT }
            if (co == null || !d.canManage.first()) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            _state.update {
                it.copy(
                    loading = false, title = co.title, totalCents = co.totalCents, lineCount = co.lines.size,
                    signerName = saved.get<String>(NAME) ?: co.clientName.orEmpty(),
                    strokes = Signature.decode(saved.get<String>(STROKES)),
                )
            }
        }
    }

    fun onEvent(e: ChangeOrderSignEvent) {
        when (e) {
            is ChangeOrderSignEvent.SignerName -> if (ready()) {
                saved[NAME] = e.text
                _state.update { it.copy(signerName = e.text) }
            }
            is ChangeOrderSignEvent.Stroke -> if (ready() && e.stroke.pointCount > 0) strokes { it + e.stroke }
            ChangeOrderSignEvent.Clear -> if (ready()) strokes { emptyList() }
            ChangeOrderSignEvent.Sign -> sign()
            ChangeOrderSignEvent.Retry -> load()
            ChangeOrderSignEvent.Back -> Unit
        }
    }

    private fun ready() = _state.value.let { !it.loading && !it.failed && !it.working && it.signedId == null }

    private fun strokes(change: (List<SignatureStroke>) -> List<SignatureStroke>) {
        val next = change(_state.value.strokes)
        saved[STROKES] = Signature.encode(next)
        _state.update { it.copy(strokes = next, saveFailed = false) }
    }

    private fun sign() {
        val st = _state.value
        if (!st.canSign) return
        _state.update { it.copy(working = true, saveFailed = false) }
        viewModelScope.launch {
            val path = d.writeImage(st.strokes)
            val ok = path != null && d.orders.sign(d.changeOrderId, st.signerName, path, st.strokes)
            if (ok) {
                saved.remove<String>(NAME)
                saved.remove<String>(STROKES)
                _state.update { it.copy(working = false, signedId = d.changeOrderId) }
            } else {
                _state.update { it.copy(working = false, saveFailed = true) }
            }
        }
    }

    private companion object {
        const val NAME = "coSignerName"
        const val STROKES = "coSignatureStrokes"
    }
}
