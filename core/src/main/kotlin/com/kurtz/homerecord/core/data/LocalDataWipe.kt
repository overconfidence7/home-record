package com.kurtz.homerecord.core.data

import java.io.File

/**
 * Removes everything a signed-out phone must not keep (AC-F4): every Room table (jobs, clients, punches with GPS, pay
 * rates, photos, files, the outbox), the media, offline and download directories, and the app's own preferences. The
 * device id and the session token are not here; the auth store clears the token and the device id outlives accounts.
 * [exclusive] runs the wipe with no sync pass in flight, so a pass cannot write rows back half way through.
 */
class LocalDataWipe(
    private val clearTables: suspend () -> Unit,
    private val dirs: List<File>,
    private val clearPrefs: List<() -> Unit>,
    private val exclusive: suspend (suspend () -> Unit) -> Unit = { it() },
) {
    suspend fun run() = exclusive {
        clearTables()
        dirs.forEach { it.deleteRecursively() }
        clearPrefs.forEach { it() }
    }
}
