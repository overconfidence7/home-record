package com.kurtz.homerecord.coreui.auth

import com.kurtz.homerecord.core.auth.AuthResult

enum class SignInStep { Email, Code }

data class SignInUiState(
    val step: SignInStep = SignInStep.Email,
    val email: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: AuthResult? = null,
    val signedIn: Boolean = false,
) {
    val emailValid: Boolean get() = email.trim().let { it.contains('@') && it.substringAfter('@').contains('.') }
    val codeValid: Boolean get() = code.trim().length == 6 && code.trim().all(Char::isDigit)
}

sealed interface SignInEvent {
    data class EmailChanged(val value: String) : SignInEvent
    data class CodeChanged(val value: String) : SignInEvent
    data object SendCode : SignInEvent
    data object VerifyCode : SignInEvent
    data object ChangeEmail : SignInEvent
    data object GoogleSignIn : SignInEvent
}
