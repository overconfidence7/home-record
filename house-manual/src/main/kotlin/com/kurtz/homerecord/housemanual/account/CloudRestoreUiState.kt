package com.kurtz.homerecord.housemanual.account

/** Where "Sign in to get your home" is (AC-H15 second phone). */
enum class CloudRestorePhase { SignedOut, Working, NoHome, Failed }

data class CloudRestoreUiState(
    val phase: CloudRestorePhase = CloudRestorePhase.SignedOut,
    /** The last try could not reach the server at all (no connection), so the error says so. */
    val offline: Boolean = false,
)

sealed interface CloudRestoreEvent {
    data object Back : CloudRestoreEvent
    data object Retry : CloudRestoreEvent
    /** No home on the account, or the user gives up on it: back to onboarding to set one up (still signed in). */
    data object SetUp : CloudRestoreEvent
}
