package com.kurtz.homerecord.core.media

/** A document copied into app-private storage. */
data class StoredDocument(val path: String, val name: String, val mime: String, val size: Long)

/** Copies a picked PDF or image into app storage (AC-C37). */
interface DocumentFiles {
    /** Null when the file can't be read or is not a PDF or image. */
    suspend fun import(uri: String): StoredDocument?

    fun discard(path: String)
}
