package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.ShutoffKind

/** AC-26: a shutoff needs a kind and non-blank instructions; room and photo are optional. */
object ShutoffRule {
    fun canSave(kind: ShutoffKind?, instructions: String): Boolean = kind != null && instructions.isNotBlank()
}
