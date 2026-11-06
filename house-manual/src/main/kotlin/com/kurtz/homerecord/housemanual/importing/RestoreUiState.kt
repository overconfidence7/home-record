package com.kurtz.homerecord.housemanual.importing

/** HM-Restore: the passphrase that opens a picked backup (AC-C48). [error] is the last try's, shown under the field. */
data class RestoreUiState(
    val passphrase: String = "",
    val opening: Boolean = false,
    val error: ImportError? = null,
)

sealed interface RestoreEvent {
    data object Back : RestoreEvent
    data class PassphraseChanged(val value: String) : RestoreEvent
    data object Open : RestoreEvent
}
