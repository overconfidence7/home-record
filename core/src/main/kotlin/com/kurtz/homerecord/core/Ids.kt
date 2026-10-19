package com.kurtz.homerecord.core

import java.util.UUID

/** All entity ids are UUID strings. */
object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}
