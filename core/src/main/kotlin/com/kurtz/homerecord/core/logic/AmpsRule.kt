package com.kurtz.homerecord.core.logic

/** Validates the optional amperage typed on the breaker form. */
object AmpsRule {
    const val MIN = 1
    const val MAX = 400

    /** Blank input is valid (no amps). */
    fun isValid(text: String): Boolean = text.isBlank() || parse(text) != null

    /** The amperage, or null when blank or invalid. */
    fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in MIN..MAX }
}
