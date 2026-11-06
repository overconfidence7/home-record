package com.kurtz.homerecord.housemanual.account

/** HM-Account "Delete cloud copy" (AC-H15). */
data class CloudCopyUiState(
    /** The "Delete cloud copy?" dialog is open. */
    val confirmOpen: Boolean = false,
    /** The server is removing the home. */
    val deleting: Boolean = false,
    /** The last try failed; [offline] when it never reached the server. */
    val failed: Boolean = false,
    val offline: Boolean = false,
    /** The cloud copy is gone and this phone signed out; HM-Account says so above the sign-in form. */
    val deleted: Boolean = false,
)

sealed interface CloudCopyEvent {
    data object Delete : CloudCopyEvent
    data object Confirm : CloudCopyEvent
    data object Dismiss : CloudCopyEvent
}
