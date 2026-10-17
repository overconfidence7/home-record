package com.kurtz.homerecord.core.repo

import com.kurtz.homerecord.core.bundle.BundleCrypto
import com.kurtz.homerecord.core.bundle.BundleWriter
import com.kurtz.homerecord.core.media.MediaFileStore
import java.io.File

/** Writes the whole Home as a passphrase-encrypted `.homerecord` (AC-88). */
class BackupRepository(
    private val handoff: HandoffRepository,
    private val media: MediaFileStore,
) {
    /** Returns false when the Home no longer exists. [target] is replaced. Blocking IO: call off the main thread. */
    suspend fun writeBackup(homeId: String, passphrase: String, target: File): Boolean {
        val bundle = handoff.bundleFor(homeId) ?: return false
        require(BundleCrypto.isValidPassphrase(passphrase)) { "Passphrase too short." }
        target.parentFile?.mkdirs()
        val plain = File.createTempFile("backup", ".zip", target.parentFile)
        try {
            plain.outputStream().use { BundleWriter(media).write(bundle, it) }
            target.delete()
            plain.inputStream().use { input -> target.outputStream().use { BundleCrypto.encrypt(input, it, passphrase) } }
        } finally {
            plain.delete()
        }
        return true
    }

    companion object {
        fun fileName(date: String) = "homerecord-backup-$date.homerecord"
    }
}
