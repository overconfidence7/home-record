package com.kurtz.homerecord.housemanual.settings

import com.kurtz.homerecord.core.bundle.BundleCrypto

data class SettingsUiState(
    val hasHome: Boolean = false,
    val passphrase: String = "",
    val confirm: String = "",
    val exporting: Boolean = false,
    val exportFailed: Boolean = false,
) {
    val passphraseLongEnough: Boolean get() = BundleCrypto.isValidPassphrase(passphrase)
    val passphraseMismatch: Boolean get() = confirm.isNotEmpty() && confirm != passphrase
    val canExport: Boolean get() = hasHome && passphraseLongEnough && confirm == passphrase && !exporting
}

sealed interface SettingsEvent {
    data class PassphraseChanged(val value: String) : SettingsEvent
    data class ConfirmChanged(val value: String) : SettingsEvent
    data object Export : SettingsEvent
}
