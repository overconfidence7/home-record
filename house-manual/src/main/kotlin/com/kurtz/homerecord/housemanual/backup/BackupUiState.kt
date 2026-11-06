package com.kurtz.homerecord.housemanual.backup

/** Why a passphrase cannot lock a backup yet. */
enum class PassphraseProblem { TooShort, Mismatch }

/** The rule for a backup passphrase: at least [MIN_LENGTH] characters, typed the same twice (AC-C48). */
object BackupPassphrase {
    const val MIN_LENGTH = 8

    fun problem(passphrase: String, confirm: String): PassphraseProblem? = when {
        passphrase.length < MIN_LENGTH -> PassphraseProblem.TooShort
        passphrase != confirm -> PassphraseProblem.Mismatch
        else -> null
    }
}

/**
 * HM-Backup: lock the whole home with a passphrase and share the file, or restore one (AC-C48, AC-H14).
 * [problem] shows only after the first Make backup. [uri] is a made backup waiting for the share sheet.
 */
data class BackupUiState(
    val homeName: String = "",
    /** A viewer on a shared home: a backup can be made, but restoring would write into the owner's home (AC-H16). */
    val viewOnly: Boolean = false,
    val passphrase: String = "",
    val confirm: String = "",
    val problem: PassphraseProblem? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val uri: String? = null,
    /** The last backup made on this screen, by file name, so the person knows it worked. */
    val made: String? = null,
)

sealed interface BackupEvent {
    data object Back : BackupEvent
    data class PassphraseChanged(val value: String) : BackupEvent
    data class ConfirmChanged(val value: String) : BackupEvent
    /** Make the backup and open the share sheet. */
    data object Make : BackupEvent
    data object ShareHandled : BackupEvent
    /** Pick a backup file to restore; the route opens the file picker. */
    data object Restore : BackupEvent
}
