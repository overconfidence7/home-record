package com.kurtz.homerecord.core.logic

/** Validates the slot count typed on the panel setup form. */
object SlotCountRule {
    const val MIN = 2
    const val MAX = 84
    const val DEFAULT = 40

    /** Parses [text] to a slot count within [MIN]..[MAX], or null when it is not valid. */
    fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in MIN..MAX }
}
