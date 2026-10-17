package com.kurtz.homerecord.core.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.bundle.BundleCrypto
import com.kurtz.homerecord.core.bundle.BundleReader
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.imports.ImportApplier
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.model.Photo
import com.kurtz.homerecord.core.model.PhotoStage
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.model.Wall
import java.io.BufferedInputStream
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun `backup restores onto a fresh install with equal ids and media (AC-88, AC-90)`() = runTest {
        val db1 = HomeRecordDatabase.inMemory(context)
        val files1 = tmp.newFolder("files1")
        File(files1, "media").mkdirs()
        File(files1, "media/p1.jpg").writeText("jpeg")
        db1.homeDao().upsert(Home("h1", "Elm", createdAt = 1, updatedAt = 1, source = Source.HOUSE_MANUAL).toEntity())
        db1.roomDao().upsert(Room("r1", "h1", "Kitchen", createdAt = 1, updatedAt = 1, source = Source.HOUSE_MANUAL).toEntity())
        db1.roomDao().upsert(Room("r2", "h1", "Bath", createdAt = 1, updatedAt = 1, source = Source.HOUSE_MANUAL).toEntity())
        db1.wallDao().upsert(Wall("w1", "r1", "N", 1, 1, Source.HOUSE_MANUAL).toEntity())
        db1.photoDao().upsert(
            Photo("p1", "h1", "r1", "w1", PhotoStage.BEHIND_WALL, 5, null, "media/p1.jpg", 1, 1, Source.HOUSE_MANUAL).toEntity(),
        )
        val target = File(tmp.newFolder("out"), "backup.homerecord")
        assertTrue(BackupRepository(HandoffRepository(db1), MediaFileStore(files1)).writeBackup("h1", "passphrase1", target))
        assertEquals(listOf("backup.homerecord"), target.parentFile!!.list()!!.toList())
        db1.close()

        val plainZip = File(tmp.root, "plain.zip")
        BufferedInputStream(target.inputStream()).use { input ->
            assertTrue(BundleCrypto.isEncrypted(input))
            plainZip.outputStream().use { BundleCrypto.decrypt(input, it, "passphrase1") }
        }
        val staging = tmp.newFolder("staging")
        val contents = plainZip.inputStream().use { BundleReader().read(it, staging) }

        val db2 = HomeRecordDatabase.inMemory(context)
        val files2 = tmp.newFolder("files2")
        val applier = ImportApplier(db2, MediaFileStore(files2))
        applier.apply(applier.plan(contents.record), emptyMap(), staging)
        assertEquals(listOf("h1"), db2.homeDao().getAll().map { it.id })
        assertEquals(setOf("r1", "r2"), db2.roomDao().getAll().map { it.id }.toSet())
        assertEquals(listOf("w1"), db2.wallDao().getAll().map { it.id })
        assertEquals(listOf("p1"), db2.photoDao().getAll().map { it.id })
        assertEquals("jpeg", File(files2, "media/p1.jpg").readText())
        db2.close()
    }

    @Test fun `unknown home or short passphrase writes nothing`() = runTest {
        val db = HomeRecordDatabase.inMemory(context)
        val repo = BackupRepository(HandoffRepository(db), MediaFileStore(tmp.newFolder("f")))
        val target = File(tmp.newFolder("o"), "b.homerecord")
        assertFalse(repo.writeBackup("none", "passphrase1", target))
        assertFalse(target.exists())
        db.close()
    }
}
