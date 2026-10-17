package com.kurtz.homerecord.core.bundle

import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelKind
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.RoomKind
import com.kurtz.homerecord.core.model.TandemPolicy
import com.kurtz.homerecord.core.model.Photo
import com.kurtz.homerecord.core.model.PhotoStage
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.model.Wall
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BundleTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun bundle(photoPath: String = "media/a.jpg") = RecordBundle(
        exportedAt = 99L,
        home = Home("h1", "Maple St", createdAt = 1, updatedAt = 2, source = Source.FIELD_LOG),
        rooms = listOf(Room("r1", "h1", "Kitchen", createdAt = 1, updatedAt = 1, source = Source.FIELD_LOG)),
        walls = listOf(Wall("w1", "r1", "N", 1, 1, Source.FIELD_LOG)),
        panels = listOf(Panel("p1", "h1", "Main", slotCount = 40, layout = PanelLayout.NA_NUMBERED, createdAt = 1, updatedAt = 1, source = Source.HOUSE_MANUAL)),
        breakers = listOf(Breaker("b1", "p1", listOf(1, 3), poles = 2, label = "Range", createdAt = 1, updatedAt = 1, source = Source.HOUSE_MANUAL)),
        photos = listOf(
            Photo("ph1", "h1", "r1", "w1", PhotoStage.BEHIND_WALL, takenAt = 5, path = photoPath, createdAt = 1, updatedAt = 1, source = Source.FIELD_LOG),
        ),
    )

    private fun store(): MediaFileStore = MediaFileStore(tmp.newFolder())

    @Test fun jsonRoundTripPreservesEverythingAndSource() {
        val original = bundle()
        val decoded = RecordJson.decode(RecordJson.encode(original))
        assertEquals(original, decoded)
        assertEquals(Source.HOUSE_MANUAL, decoded.panels.single().source)
        assertTrue(RecordJson.encode(original).contains("\"formatVersion\":1"))
        assertTrue(RecordJson.encode(original).contains("\"source\":\"field-log\""))
    }

    @Test fun newerFormatVersionIsRejectedWithReadableMessage() {
        val text = RecordJson.encode(bundle()).replace("\"formatVersion\":1", "\"formatVersion\":2")
        try {
            RecordJson.decode(text)
            fail("expected rejection")
        } catch (e: BundleFormatException) {
            assertTrue(e.message!!.contains("newer version"))
        }
    }

    @Test fun unknownFieldsAreIgnored() {
        val text = RecordJson.encode(bundle()).replaceFirst("{", "{\"futureThing\":[1,2],")
        assertEquals(bundle(), RecordJson.decode(text))
    }

    @Test fun garbageIsReportedNotThrownRaw() {
        for (text in listOf("not json", "[]", "{}", "{\"formatVersion\":1}")) {
            try {
                RecordJson.decode(text)
                fail("expected rejection for $text")
            } catch (_: BundleFormatException) {
            }
        }
    }

    @Test fun zipRoundTripCarriesRecordAndMedia() {
        val source = store()
        source.resolveForWrite("media/a.jpg")!!.writeBytes(byteArrayOf(1, 2, 3))
        val out = ByteArrayOutputStream()
        val included = BundleWriter(source).write(bundle(), out)
        assertEquals(listOf("media/a.jpg"), included)

        val staging = tmp.newFolder()
        val result = BundleReader().read(ByteArrayInputStream(out.toByteArray()), staging)
        assertEquals(bundle(), result.record)
        assertEquals(listOf("media/a.jpg"), result.extractedMedia)
        assertEquals(listOf<Byte>(1, 2, 3), File(staging, "media/a.jpg").readBytes().toList())
    }

    @Test fun missingMediaFileIsSkippedNotFatal() {
        val out = ByteArrayOutputStream()
        val included = BundleWriter(store()).write(bundle(), out)
        assertTrue(included.isEmpty())
        val result = BundleReader().read(ByteArrayInputStream(out.toByteArray()), tmp.newFolder())
        assertTrue(result.extractedMedia.isEmpty())
    }

    @Test fun writerNeverIncludesFilesOutsideAppStorage() {
        val secret = tmp.newFile("secret.txt").apply { writeText("x") }
        val s = store()
        for (path in listOf("../secret.txt", secret.absolutePath, "media/../../secret.txt", "media/sub/a.jpg")) {
            val out = ByteArrayOutputStream()
            assertTrue(path, BundleWriter(s).write(bundle(path), out).isEmpty())
        }
    }

    @Test fun readerIgnoresZipSlipEntries() {
        val bytes = ByteArrayOutputStream().also { bos ->
            ZipOutputStream(bos).use { z ->
                z.putNextEntry(ZipEntry("record.json")); z.write(RecordJson.encode(bundle()).toByteArray()); z.closeEntry()
                for (name in listOf("../evil.txt", "media/../evil.txt", "/abs.txt", "media/nested/x.jpg", "other/x.jpg")) {
                    z.putNextEntry(ZipEntry(name)); z.write(7); z.closeEntry()
                }
                z.putNextEntry(ZipEntry("media/ok.jpg")); z.write(9); z.closeEntry()
            }
        }.toByteArray()
        val parent = tmp.newFolder()
        val staging = File(parent, "staging")
        val result = BundleReader().read(ByteArrayInputStream(bytes), staging)
        assertEquals(listOf("media/ok.jpg"), result.extractedMedia)
        assertFalse(File(parent, "evil.txt").exists())
        assertFalse(File(staging, "evil.txt").exists())
    }

    @Test fun nonZipAndMissingRecordAreRejected() {
        for (bytes in listOf("hello".toByteArray(), ByteArrayOutputStream().also { b -> ZipOutputStream(b).use { it.putNextEntry(ZipEntry("media/a.jpg")); it.write(1); it.closeEntry() } }.toByteArray())) {
            try {
                BundleReader().read(ByteArrayInputStream(bytes), tmp.newFolder())
                fail("expected rejection")
            } catch (_: BundleFormatException) {
            }
        }
    }

    @Test fun sanitizerAcceptsOnlyDirectMediaChildren() {
        assertTrue(MediaPathSanitizer.isSafe("media/abc.jpg"))
        for (p in listOf("", "media/", "media/..", "media/a/b.jpg", "a.jpg", "/media/a.jpg", "media\\a.jpg", "../media/a.jpg")) {
            assertFalse(p, MediaPathSanitizer.isSafe(p))
        }
    }

    @Test fun newProfileFieldsRoundTripAndOldBundlesStillDecode() {
        val b = bundle().copy(
            home = Home("h1", "Maple St", createdAt = 1, updatedAt = 2, source = Source.FIELD_LOG, squareFeet = 2000),
            rooms = listOf(Room("r1", "h1", "Kitchen", createdAt = 1, updatedAt = 1, source = Source.FIELD_LOG, kind = RoomKind.KITCHEN)),
            panels = listOf(
                Panel("p1", "h1", "Main", slotCount = 40, layout = PanelLayout.NA_NUMBERED, createdAt = 1, updatedAt = 1,
                    source = Source.HOUSE_MANUAL, kind = PanelKind.SUB, ratingAmps = 100, brand = "Eaton BR", model = "BR1",
                    maxCircuits = 20, tandemPolicy = TandemPolicy.NOT_ALLOWED, hasMainBreaker = false, yearInstalled = 1999, feedBreakerId = "b1"),
            ),
            breakers = listOf(Breaker("b1", "p1", listOf(1), label = "Main", createdAt = 1, updatedAt = 1, source = Source.HOUSE_MANUAL, isMain = true)),
        )
        assertEquals(b, RecordJson.decode(RecordJson.encode(b)))

        val old = """{"formatVersion":1,"home":{"id":"h","name":"N","createdAt":1,"updatedAt":1,"source":"house-manual"},
            "rooms":[{"id":"r","homeId":"h","name":"Kitchen","createdAt":1,"updatedAt":1,"source":"house-manual"}],
            "panels":[{"id":"p","homeId":"h","name":"Main","slotCount":40,"layout":"NA_NUMBERED","createdAt":1,"updatedAt":1,"source":"house-manual"}],
            "breakers":[{"id":"b","panelId":"p","slots":[1],"label":"x","createdAt":1,"updatedAt":1,"source":"house-manual"}]}"""
        val decoded = RecordJson.decode(old)
        assertEquals(RoomKind.OTHER, decoded.rooms.single().kind)
        assertEquals(PanelKind.MAIN, decoded.panels.single().kind)
        assertEquals(TandemPolicy.UNKNOWN, decoded.panels.single().tandemPolicy)
        assertTrue(decoded.panels.single().hasMainBreaker)
        assertFalse(decoded.breakers.single().isMain)
    }
}
