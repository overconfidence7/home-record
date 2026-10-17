package com.kurtz.homerecord.housemanual.settings

import android.content.Context
import com.kurtz.homerecord.core.repo.BackupRepository
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes `cache/shared/homerecord-backup-<date>.homerecord`, replacing any earlier backup file. */
class AndroidBackupExporter(
    private val context: Context,
    private val backups: BackupRepository,
) : BackupExporter {
    override suspend fun export(homeId: String, passphrase: String): String? {
        val relative = "shared/" + BackupRepository.fileName(LocalDate.now().toString())
        val file = File(context.cacheDir, relative)
        val written = withContext(Dispatchers.IO) { backups.writeBackup(homeId, passphrase, file) }
        return relative.takeIf { written }
    }
}
