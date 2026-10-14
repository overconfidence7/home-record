package com.kurtz.homerecord.core.media

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MediaFileStoreTest {
    private lateinit var dir: File
    private lateinit var store: MediaFileStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("filesdir").toFile()
        store = MediaFileStore(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun newPaths_areRelativeUuidNamesUnderMedia() {
        val photo = store.newPhotoPath()
        val audio = store.newAudioPath()
        assertTrue(Regex("media/[0-9a-f-]{36}\\.jpg").matches(photo))
        assertTrue(audio.startsWith("media/") && audio.endsWith(".m4a"))
        assertTrue(photo != store.newPhotoPath())
    }

    @Test
    fun resolveForWrite_createsDirAndFileSurvivesRoundTrip() {
        val path = store.newPhotoPath()
        assertFalse(store.exists(path))
        val file = store.resolveForWrite(path)
        assertNotNull(file)
        file!!.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(store.exists(path))
        assertEquals(File(dir, path).canonicalFile, store.resolve(path))
    }

    @Test
    fun resolve_rejectsEscapes() {
        assertNull(store.resolve("../secret.jpg"))
        assertNull(store.resolve("media/../../secret.jpg"))
        assertNull(store.resolve("/etc/passwd"))
        assertNull(store.resolve("media/sub/x.jpg"))
        assertNull(store.resolve("media\\x.jpg"))
        assertNull(store.resolve(""))
        assertNull(store.resolve("other/x.jpg"))
    }

    @Test
    fun delete_removesFileOnlyOnce() {
        val path = store.newPhotoPath()
        store.resolveForWrite(path)!!.writeText("x")
        assertTrue(store.delete(path))
        assertFalse(store.exists(path))
        assertFalse(store.delete(path))
        assertFalse(store.delete(null))
        assertFalse(store.delete("../x"))
    }
}
