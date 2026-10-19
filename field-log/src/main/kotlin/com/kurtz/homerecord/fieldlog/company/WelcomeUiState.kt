package com.kurtz.homerecord.fieldlog.company

import com.kurtz.homerecord.core.company.ApiFailure

enum class WelcomeMode { Create, Join }

data class WelcomeUiState(
    val mode: WelcomeMode = WelcomeMode.Create,
    val name: String = "",
    val trade: String = "",
    val phone: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: ApiFailure? = null,
) {
    val canSubmit: Boolean
        get() = !busy && when (mode) {
            WelcomeMode.Create -> name.isNotBlank()
            WelcomeMode.Join -> normalizeInviteCode(code).length == INVITE_CODE_LENGTH
        }
}

sealed interface WelcomeEvent {
    data class ModeChanged(val mode: WelcomeMode) : WelcomeEvent
    data class NameChanged(val value: String) : WelcomeEvent
    data class TradeChanged(val value: String) : WelcomeEvent
    data class PhoneChanged(val value: String) : WelcomeEvent
    data class CodeChanged(val value: String) : WelcomeEvent
    data object Submit : WelcomeEvent
    data object SignOut : WelcomeEvent
}

const val INVITE_CODE_LENGTH = 8

/** Upper-cases and drops spaces and dashes so "k7m2-qx9r" and "K7M2QX9R" are the same code. */
fun normalizeInviteCode(raw: String): String = raw.filter { it.isLetterOrDigit() }.uppercase().take(INVITE_CODE_LENGTH)
