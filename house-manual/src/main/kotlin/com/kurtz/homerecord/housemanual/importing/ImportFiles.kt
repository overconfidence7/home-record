package com.kurtz.homerecord.housemanual.importing

import android.content.Context
import android.net.Uri
import com.kurtz.homerecord.core.bundle.BundleError
import com.kurtz.homerecord.core.bundle.BundleReadResult
import com.kurtz.homerecord.core.bundle.BundleReader
import com.kurtz.homerecord.core.bundle.ConflictChoice
import com.kurtz.homerecord.core.bundle.HomeBundleImporter
import com.kurtz.homerecord.core.bundle.ImportPreview
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.sync.RoomRowStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
class ImportFiles(private val context: Context, private val db: HomeRecordDatabase) {
    private val rows = RoomRowStore(db)
    private val importer = HomeBundleImporter(
        { table, column, values -> rows.readWhere(table, column, values) }, rows, db.importLedgerDao(), File(context.filesDir, "media"),
    )

    suspend fun read(uri: Uri): ImportRead = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "import/${UUID.randomUUID()}")
        val result = runCatching {
            context.contentResolver.openInputStream(uri)?.buffered()?.use { BundleReader.read(it, dir) }
        }.getOrNull() ?: return@withContext ImportRead.Failed(ImportError.Unreadable)
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

    suspend fun apply(read: ReadImport, choices: Map<Pair<String, String>, ConflictChoice>): Boolean = withContext(Dispatchers.IO) {
        val ok = runCatching { importer.apply(read.preview, read.unpacked, choices) }.isSuccess
        read.unpacked.deleteRecursively()
        ok
    }
}
