package com.kurtz.homerecord.housemanual.backup

import android.content.Context
import androidx.core.content.FileProvider
import com.kurtz.homerecord.core.bundle.HomeBackup
import com.kurtz.homerecord.core.bundle.HomeBundleExporter
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.sync.RoomRowStore
import com.kurtz.homerecord.housemanual.importing.ImportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Writes the whole home as a passphrase-locked `.hrbackup` ([HomeBackup], AC-C48) into cache/exports and returns a
 * FileProvider URI for the share sheet (Drive, Files, email). A failed write leaves no partial file behind.
 */
class BackupFiles(private val context: Context, db: HomeRecordDatabase, private val today: () -> LocalDate = LocalDate::now) {
    private val rows = RoomRowStore(db)
    private val exporter = HomeBundleExporter({ table, column, values -> rows.readWhere(table, column, values) })

    suspend fun export(homeId: String, passphrase: CharArray): MadeBackup? = withContext(Dispatchers.IO) {
        val home = exporter.export(homeId, Sources.HOUSE_MANUAL) ?: return@withContext null
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName(ImportViewModel.homeName(home.bundle), today()))
        val scratch = File(context.cacheDir, "backup/${UUID.randomUUID()}.zip")
        try {
            file.outputStream().buffered().use { HomeBackup.write(home, it, passphrase, scratch) }
            MadeBackup(FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString(), file.name)
        } catch (e: java.io.IOException) {
            file.delete()
            null
        } finally {
            passphrase.fill(' ')
        }
    }

    companion object {
        /** `Maple-Street-house-backup-2026-11-06.hrbackup`; a home with no name is `home`. */
        fun fileName(homeName: String, date: LocalDate): String {
            val safe = homeName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "home" }
            return "$safe-backup-$date.${HomeBackup.EXTENSION}"
        }
    }
}
