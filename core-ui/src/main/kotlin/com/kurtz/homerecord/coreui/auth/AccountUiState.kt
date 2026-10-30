package com.kurtz.homerecord.coreui.auth

enum class AccountPhase { Restoring, SignedOut, SignedIn }

data class AccountUiState(
    val phase: AccountPhase = AccountPhase.Restoring,
    /** The "Sign out?" dialog is open. */
    val confirmSignOut: Boolean = false,
)

sealed interface AccountEvent {
    data object SignOut : AccountEvent
    data object ConfirmSignOut : AccountEvent
    data object DismissSignOut : AccountEvent
}
