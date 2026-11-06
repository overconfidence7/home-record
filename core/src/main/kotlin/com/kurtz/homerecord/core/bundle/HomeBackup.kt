package com.kurtz.homerecord.core.bundle

import java.io.File
import java.io.InputStream
import java.io.OutputStream

sealed interface BackupReadResult {
    /** Opened and read; the media are in the `mediaDir` given to [HomeBackup.read]. */
    data class Ok(val bundle: HomeBundle) : BackupReadResult

    /** The backup itself could not be opened (wrong passphrase, damaged, not a backup). */
    data class Locked(val error: BackupError) : BackupReadResult

    /** It opened, but what was inside is not a bundle this version reads. */
    data class Unreadable(val error: BundleError) : BackupReadResult
}

/**
 * A whole-home backup (AC-C48): the home's `.homerecord` ([BundleWriter]) sealed with [BundleCrypto]. Both
 * directions go through one plaintext file in [scratch] (the app's cache), deleted before they return, so a backup
 * of several GB never sits in memory.
 */
object HomeBackup {
    const val EXTENSION = "hrbackup"

    fun write(home: ExportedHome, out: OutputStream, passphrase: CharArray, scratch: File, blockSize: Int = BundleCrypto.BLOCK_SIZE) {
        scratch.parentFile?.mkdirs()
        try {
            scratch.outputStream().buffered().use { BundleWriter.write(home.bundle, it, home::mediaFile) }
            scratch.inputStream().buffered().use { BundleCrypto.encrypt(it, out, passphrase, blockSize) }
        } finally {
            scratch.delete()
        }
    }

    fun read(input: InputStream, passphrase: CharArray, scratch: File, mediaDir: File): BackupReadResult {
        scratch.parentFile?.mkdirs()
        try {
            val opened = scratch.outputStream().buffered().use { BundleCrypto.decrypt(input, it, passphrase) }
            if (opened is DecryptResult.Failed) return BackupReadResult.Locked(opened.error)
            return when (val read = scratch.inputStream().buffered().use { BundleReader.read(it, mediaDir) }) {
                is BundleReadResult.Ok -> BackupReadResult.Ok(read.bundle)
                is BundleReadResult.Failed -> BackupReadResult.Unreadable(read.error)
            }
        } finally {
            scratch.delete()
        }
    }
}
