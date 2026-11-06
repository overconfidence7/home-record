package com.kurtz.homerecord.housemanual.importing

import android.content.Context
import android.net.Uri
import com.kurtz.homerecord.core.bundle.BackupError
import com.kurtz.homerecord.core.bundle.BackupReadResult
import com.kurtz.homerecord.core.bundle.BundleError
import com.kurtz.homerecord.core.bundle.HomeBackup
import com.kurtz.homerecord.core.bundle.BundleReadResult
import com.kurtz.homerecord.core.bundle.BundleReader
import com.kurtz.homerecord.core.bundle.ConflictChoice
import com.kurtz.homerecord.core.bundle.HomeBundleImporter
import com.kurtz.homerecord.core.bundle.ImportPreview
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.sync.RoomRowStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A read handoff: the preview and the folder its media were unpacked into. */
data class ReadImport(val preview: ImportPreview, val unpacked: File, val homeName: String = "")

sealed interface ImportRead {
    data class Ok(val read: ReadImport) : ImportRead
    data class Failed(val error: ImportError) : ImportRead
}

/**
 * Reads a `.homerecord` from a content Uri (file picker, share, open with) into `cache/import/`, aims it at the home
 * on this phone when there is one ([com.kurtz.homerecord.core.bundle.HomeBundle.retargeted]) and plans it; [apply]
 * writes it with media into `filesDir/media` (AC-H17).
 */
class ImportFiles(
    private val context: Context,
    private val db: HomeRecordDatabase,
    /** Queues each imported record for sync, like every other House Manual write. */
    onWritten: suspend (type: String, id: String) -> Unit = { _, _ -> },
    /** The app's scope: a discard runs after the import screen's ViewModel is gone. */
    private val appScope: kotlinx.coroutines.CoroutineScope? = null,
) {
    private val rows = RoomRowStore(db)
    private val importer = HomeBundleImporter(
        { table, column, values -> rows.readWhere(table, column, values) }, rows, db.importLedgerDao(), File(context.filesDir, "media"), onWritten,
    )

    suspend fun read(uri: Uri): ImportRead = withContext(Dispatchers.IO) {
        val imports = File(context.cacheDir, "import")
        sweepStale(imports, System.currentTimeMillis())
        val dir = File(imports, UUID.randomUUID().toString())
        val result = runCatching {
            context.contentResolver.openInputStream(uri)?.buffered()?.use { BundleReader.read(it, dir) }
        }.getOrNull()
        // A downloaded claim is unpacked into `dir` now (or unreadable), so its copy in the cache is done with.
        dropClaimedCopy(uri)
        if (result == null) {
            dir.deleteRecursively()
            return@withContext ImportRead.Failed(ImportError.Unreadable)
        }
        when (result) {
            is BundleReadResult.Failed -> {
                dir.deleteRecursively()
                ImportRead.Failed(
                    when (result.error) {
                        is BundleError.NewerVersion -> ImportError.NewerVersion
                        BundleError.TooLarge -> ImportError.TooLarge
                        else -> ImportError.NotABundle
                    },
                )
            }
            is BundleReadResult.Ok -> {
                val mine = db.homeDao().observeAll().first().firstOrNull()?.id
                val bundle = mine?.let(result.bundle::retargeted) ?: result.bundle
                ImportRead.Ok(ReadImport(importer.preview(bundle), dir, ImportViewModel.homeName(result.bundle)))
            }
        }
    }

    /**
     * Opens a `.hrbackup` (AC-C48) with [passphrase] into `cache/import/` and plans it like a handoff: on a fresh
     * install the backup's home comes back as it was; with a home on this phone it merges into it (AC-C47).
     */
    suspend fun readBackup(uri: Uri, passphrase: CharArray): ImportRead = withContext(Dispatchers.IO) {
        val imports = File(context.cacheDir, "import")
        sweepStale(imports, System.currentTimeMillis())
        val dir = File(imports, UUID.randomUUID().toString())
        val scratch = File(context.cacheDir, "backup/${UUID.randomUUID()}.zip")
        val result = try {
            context.contentResolver.openInputStream(uri)?.buffered()?.use { HomeBackup.read(it, passphrase, scratch, dir) }
        } catch (_: java.io.IOException) {
            null
        } catch (_: SecurityException) {
            null
        } finally {
            passphrase.fill(' ')
        }
        when (result) {
            null -> {
                dir.deleteRecursively()
                ImportRead.Failed(ImportError.Unreadable)
            }
            is BackupReadResult.Locked -> {
                dir.deleteRecursively()
                ImportRead.Failed(
                    when (result.error) {
                        BackupError.WrongPassphrase -> ImportError.WrongPassphrase
                        BackupError.Damaged -> ImportError.Damaged
                        BackupError.NotABackup -> ImportError.NotABackup
                        is BackupError.NewerVersion -> ImportError.NewerVersion
                    },
                )
            }
            is BackupReadResult.Unreadable -> {
                dir.deleteRecursively()
                ImportRead.Failed(if (result.error is BundleError.NewerVersion) ImportError.NewerVersion else ImportError.Damaged)
            }
            is BackupReadResult.Ok -> {
                val mine = db.homeDao().observeAll().first().firstOrNull()?.id
                val bundle = mine?.let(result.bundle::retargeted) ?: result.bundle
                ImportRead.Ok(ReadImport(importer.preview(bundle), dir, ImportViewModel.homeName(result.bundle)))
            }
        }
    }

    /** Deletes a read that will not be imported (Back, or the screen gone), so its unpacked media leave the cache. */
    fun discard(read: ReadImport) {
        appScope?.launch(Dispatchers.IO) { read.unpacked.deleteRecursively() }
    }

    private fun dropClaimedCopy(uri: Uri) {
        if (uri.scheme != "file") return
        val path = uri.path ?: return
        val file = File(path).canonicalFile
        if (file.parentFile == File(context.cacheDir, "handoff").canonicalFile) file.delete()
    }

    suspend fun apply(read: ReadImport, choices: Map<Pair<String, String>, ConflictChoice>): Boolean = withContext(Dispatchers.IO) {
        val ok = runCatching { importer.apply(read.preview, read.unpacked, choices) }.isSuccess
        read.unpacked.deleteRecursively()
        ok
    }

    companion object {
        /** A read left behind by process death is swept once it is a day old. */
        const val STALE_MILLIS = 24L * 60 * 60 * 1000

        /** Deletes unpacked reads in [imports] older than [STALE_MILLIS]; a read still on screen is younger than that. */
        fun sweepStale(imports: File, now: Long) {
            imports.listFiles().orEmpty().filter { now - it.lastModified() > STALE_MILLIS }.forEach { it.deleteRecursively() }
        }
    }
}
