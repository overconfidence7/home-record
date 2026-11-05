package com.kurtz.homerecord.housemanual.importing

import com.kurtz.homerecord.core.bundle.HandoffClaimFailure

/** Where opening a claim link (`homerecord://handoff/<token>`) stands (AC-H17, AC-B40). */
enum class ClaimPhase {
    /** The saved session is still being read. */
    Checking,
    /** The link needs a House Manual account; the sign-in form shows. */
    SignIn,
    Claiming,
    Downloading,
    Failed,
    /** The file is on this phone; the import preview takes over. */
    Ready,
}

/** HM-Claim: a handoff link from the contractor's email, claimed for this account and downloaded (AC-H17). */
data class ClaimUiState(
    val phase: ClaimPhase = ClaimPhase.Checking,
    /** Who sent it and whose home, once the server answered the claim. */
    val companyName: String = "",
    val homeName: String = "",
    val failure: HandoffClaimFailure? = null,
) {
    /** Offline and server errors can be retried; a wrong, expired or already claimed link cannot. */
    val canRetry: Boolean get() = failure == HandoffClaimFailure.Offline || failure == HandoffClaimFailure.Failed
}

sealed interface ClaimEvent {
    data object Retry : ClaimEvent
    data object Close : ClaimEvent
}
