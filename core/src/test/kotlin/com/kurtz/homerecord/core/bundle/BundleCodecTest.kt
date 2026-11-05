package com.kurtz.homerecord.core.bundle

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** AC-C46: the `.homerecord` v2 zip round-trips, refuses newer versions and zip-slip, keeps unknown fields. */
@AcceptanceCriterion("AC-C46")
class BundleCodecTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun record(id: String, vararg extra: Pair<String, String>) = BundleRecord(
        buildJsonObject {
            put("id", id)
            put("createdAt", 1_700_000_000_000L)
            put("updatedAt", 1_700_000_100_000L)
            put("source", "field-log")
            extra.forEach { (k, v) -> put(k, v) }
        },
    )

    private val bundle = HomeBundle(
        source = "field-log", exportedAt = 1_730_000_000_000L, homeId = "9b2f0c5e-0d7a-4b8e-9a51-2a7f3f1c4e10",
        records = mapOf(
            "home" to listOf(record("9b2f0c5e-0d7a-4b8e-9a51-2a7f3f1c4e10", "name" to "418 Linden Ave")),
            "room" to listOf(record("4c1d7e2a-8f3b-4a6c-b9d0-1e2f3a4b5c6d", "name" to "Kitchen")),
            "wall" to listOf(record("0f1e2d3c-4b5a-4968-8776-655443322110", "photoPath" to "media/rough.jpg")),
        ),
        media = listOf("rough.jpg"),
    )

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    private fun json(b: HomeBundle) = BundleWriter.toJson(b).toString().toByteArray()

    @Test fun aWrittenBundleReadsBackWithItsRecordsAndMedia() {
        val photo = tmp.newFile("rough.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val out = ByteArrayOutputStream()
        BundleWriter.write(bundle, out) { if (it == "rough.jpg") photo else null }
        val dir = tmp.newFolder("import")
        val result = BundleReader.read(ByteArrayInputStream(out.toByteArray()), dir)
        assertThat(result).isInstanceOf(BundleReadResult.Ok::class.java)
        val read = (result as BundleReadResult.Ok).bundle
        assertThat(read).isEqualTo(bundle)
        assertThat(read.formatVersion).isEqualTo(2)
        assertThat(read.records.getValue("room").single().createdAt).isEqualTo(1_700_000_000_000L)
        assertThat(java.io.File(dir, "rough.jpg").readBytes()).isEqualTo(byteArrayOf(1, 2, 3, 4))
    }

    @Test fun aNewerFormatVersionFailsCleanlyAndUnpacksNothing() {
        val newer = BundleWriter.toJson(bundle).let { JsonObject(it + ("formatVersion" to JsonPrimitive(3))) }
        val dir = tmp.newFolder("import")
        val result = BundleReader.read(ByteArrayInputStream(zip("record.json" to newer.toString().toByteArray(), "media/rough.jpg" to byteArrayOf(9))), dir)
        assertThat(result).isEqualTo(BundleReadResult.Failed(BundleError.NewerVersion(3)))
        assertThat(dir.listFiles().orEmpty()).isEmpty()
    }

    @Test fun unknownFieldsAndEntityTypesAreKept() {
        val text = """{"formatVersion":2,"source":"field-log","exportedAt":1,"homeId":"h1","futureFlag":true,
            "records":{"room":[{"id":"r1","updatedAt":5,"name":"Kitchen","ceilingFinish":"plaster"}],"solarArray":[{"id":"s1","updatedAt":5}]},
            "media":[]}"""
        val read = (BundleReader.parse(text) as BundleReadResult.Ok).bundle
        assertThat(read.records.getValue("room").single().fields["ceilingFinish"]).isEqualTo(JsonPrimitive("plaster"))
        assertThat(read.records.keys).containsExactly("room", "solarArray")
    }

    @Test fun zipSlipEntriesAreRefusedAndNothingIsLeftBehind() {
        listOf("../evil.sh", "media/../../evil.sh", "/etc/evil", "media/sub/x.jpg", "other/x.jpg").forEach { bad ->
            val dir = tmp.newFolder()
            val result = BundleReader.read(
                ByteArrayInputStream(zip("record.json" to json(bundle), "media/rough.jpg" to byteArrayOf(1), bad to byteArrayOf(0))), dir,
            )
            assertThat(result).isEqualTo(BundleReadResult.Failed(BundleError.UnsafeEntry(bad)))
            assertThat(dir.listFiles().orEmpty()).isEmpty()
            assertThat(java.io.File(dir.parentFile, "evil.sh").exists()).isFalse()
        }
    }

    @Test fun notABundleIsSaidPlainly() {
        val dir = tmp.newFolder()
        assertThat(BundleReader.read(ByteArrayInputStream("not a zip".toByteArray()), dir)).isEqualTo(BundleReadResult.Failed(BundleError.NotABundle))
        assertThat(BundleReader.read(ByteArrayInputStream(zip("media/a.jpg" to byteArrayOf(1))), tmp.newFolder()))
            .isEqualTo(BundleReadResult.Failed(BundleError.NotABundle))
        assertThat(BundleReader.parse("""{"formatVersion":2}""")).isEqualTo(BundleReadResult.Failed(BundleError.NotABundle))
    }

    @Test fun anOversizedBundleStopsAtTheCap() {
        val dir = tmp.newFolder()
        val result = BundleReader.read(ByteArrayInputStream(zip("record.json" to json(bundle), "media/rough.jpg" to ByteArray(4096))), dir, maxBytes = 2048)
        assertThat(result).isEqualTo(BundleReadResult.Failed(BundleError.TooLarge))
        assertThat(dir.listFiles().orEmpty()).isEmpty()
    }

    @Test fun theWriterRefusesAMediaNameThatClimbsOut() {
        val bad = bundle.copy(media = listOf("../x.jpg"))
        val thrown = runCatching { BundleWriter.write(bad, ByteArrayOutputStream()) { null } }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    }
}
