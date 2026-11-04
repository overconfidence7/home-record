package com.kurtz.homerecord.fieldlog.changes

import androidx.compose.runtime.Immutable
import com.kurtz.homerecord.core.model.Signature
import com.kurtz.homerecord.core.model.SignatureStroke

/** Sign on phone (AC-F38): who signs, the pad, Clear and Sign. */
@Immutable
data class ChangeOrderSignUiState(
    val loading: Boolean = true,
    /** Gone, no longer a Draft, or this person may not get it signed (AC-F3). */
    val failed: Boolean = false,
    val title: String = "",
    val totalCents: Long = 0,
    val lineCount: Int = 0,
    val signerName: String = "",
    val strokes: List<SignatureStroke> = emptyList(),
    val working: Boolean = false,
    /** The signature could not be saved; the pad keeps what was drawn. */
    val saveFailed: Boolean = false,
    /** Signed: the route opens the signed order. */
    val signedId: String? = null,
) {
    val nameMissing: Boolean get() = signerName.isBlank()
    val signed: Boolean get() = Signature.isSigned(strokes)
    val canSign: Boolean get() = !loading && !failed && !working && signedId == null && !nameMissing && signed
}

sealed interface ChangeOrderSignEvent {
    data class SignerName(val text: String) : ChangeOrderSignEvent
    data class Stroke(val stroke: SignatureStroke) : ChangeOrderSignEvent
    data object Clear : ChangeOrderSignEvent
    data object Sign : ChangeOrderSignEvent
    data object Retry : ChangeOrderSignEvent
    data object Back : ChangeOrderSignEvent
}
