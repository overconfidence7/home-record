package com.kurtz.homerecord.core.repo

import java.util.UUID

fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}

fun interface IdGenerator {
    fun next(): String

    companion object {
        val Random = IdGenerator { UUID.randomUUID().toString() }
    }
}
