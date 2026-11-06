package com.kurtz.homerecord.core.bundle

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import kotlin.random.Random

/** AC-C48: whole-home backups sealed with AES-256-GCM under a PBKDF2 (210k) key, in checked blocks. */
@AcceptanceCriterion("AC-C48")
class BundleCryptoTest {
    @get:Rule val tmp = TemporaryFolder()

    private val pass = "linden-418-basement".toCharArray()
    private val payload = Random(7).nextBytes(10_000)
    private val block = 1_024

    private fun seal(bytes: ByteArray = payload, passphrase: CharArray = pass.copyOf(), size: Int = block): ByteArray =
        ByteArrayOutputStream().also { BundleCrypto.encrypt(ByteArrayInputStream(bytes), it, passphrase, size) }.toByteArray()

    private fun open(sealed: ByteArray, passphrase: CharArray = pass.copyOf()): Pair<DecryptResult, ByteArray> {
        val out = ByteArrayOutputStream()
        return BundleCrypto.decrypt(ByteArrayInputStream(sealed), out, passphrase) to out.toByteArray()
    }

    /** Header is 33 bytes; each block is flag (1) + length (4) + ciphertext and 16-byte tag. */
    private fun blockOffsets(sealed: ByteArray): List<Int> {
        val offsets = mutableListOf<Int>()
        var at = 33
        while (at < sealed.size) {
            offsets += at
            at += 5 + ByteBuffer.wrap(sealed, at + 1, 4).int
        }
        return offsets
    }

    @Test fun `round trips across many blocks with PBKDF2 210k and a random salt`() {
        // AC-C48
        val sealed = seal()
        val (result, plain) = open(sealed)
        assertThat(result).isEqualTo(DecryptResult.Ok(payload.size.toLong()))
        assertThat(plain).isEqualTo(payload)
        assertThat(BundleCrypto.looksLikeBackup(sealed)).isTrue()
        assertThat(ByteBuffer.wrap(sealed, 5, 4).int).isEqualTo(210_000)
        assertThat(blockOffsets(sealed)).hasSize(10)
        // Same plaintext and passphrase seal differently each time (fresh salt and nonce prefix).
        assertThat(seal().copyOfRange(9, 33)).isNotEqualTo(sealed.copyOfRange(9, 33))
    }

    @Test fun `empty input and an exact multiple of the block size round trip`() {
        // AC-C48
        assertThat(open(seal(ByteArray(0))).second).isEmpty()
        val exact = payload.copyOf(4 * block)
        val sealed = seal(exact)
        assertThat(blockOffsets(sealed)).hasSize(4)
        assertThat(open(sealed).second).isEqualTo(exact)
    }

    @Test fun `the plaintext does not appear in the file`() {
        // AC-C48
        val text = "Main water shutoff: basement, north wall, behind the furnace".repeat(20).toByteArray()
        val sealed = seal(text)
        assertThat(String(sealed, Charsets.ISO_8859_1)).doesNotContain("Main water shutoff")
    }

    @Test fun `a wrong passphrase is told apart and writes nothing`() {
        // AC-C48
        val (result, plain) = open(seal(), "linden-418-Basement".toCharArray())
        assertThat(result).isEqualTo(DecryptResult.Failed(BackupError.WrongPassphrase))
        assertThat(plain).isEmpty()
    }

    @Test fun `a file cut at a block boundary is damaged, not short`() {
        // AC-C48
        val sealed = seal()
        val cut = sealed.copyOf(blockOffsets(sealed)[5])
        assertThat(open(cut).first).isEqualTo(DecryptResult.Failed(BackupError.Damaged))
        assertThat(open(sealed.copyOf(sealed.size - 3)).first).isEqualTo(DecryptResult.Failed(BackupError.Damaged))
    }

    @Test fun `swapped blocks and a flipped byte are refused`() {
        // AC-C48
        val sealed = seal()
        val offsets = blockOffsets(sealed)
        val a = sealed.copyOfRange(offsets[1], offsets[2])
        val b = sealed.copyOfRange(offsets[2], offsets[3])
        val swapped = sealed.copyOf().also {
            b.copyInto(it, offsets[1])
            a.copyInto(it, offsets[1] + b.size)
        }
        assertThat(open(swapped).first).isEqualTo(DecryptResult.Failed(BackupError.Damaged))

        val flipped = sealed.copyOf().also { it[offsets[3] + 40] = (it[offsets[3] + 40].toInt() xor 1).toByte() }
        assertThat(open(flipped).first).isEqualTo(DecryptResult.Failed(BackupError.Damaged))

        // Marking an early block as the last one (to drop the rest) fails its tag.
        val early = sealed.copyOf().also { it[offsets[2]] = 1 }
        assertThat(open(early).first).isEqualTo(DecryptResult.Failed(BackupError.Damaged))
    }

    @Test fun `a changed header (salt or iterations) does not open`() {
        // AC-C48
        val sealed = seal()
        val salt = sealed.copyOf().also { it[12] = (it[12].toInt() xor 0x40).toByte() }
        assertThat(open(salt).first).isEqualTo(DecryptResult.Failed(BackupError.WrongPassphrase))
        val iterations = sealed.copyOf().also { ByteBuffer.wrap(it, 5, 4).putInt(200_000) }
        assertThat(open(iterations).first).isEqualTo(DecryptResult.Failed(BackupError.WrongPassphrase))
    }

    @Test fun `other files, newer versions and absurd iterations are refused before any key work`() {
        // AC-C48
        assertThat(open("PK\u0003\u0004 not a backup at all, a plain zip".toByteArray()).first)
            .isEqualTo(DecryptResult.Failed(BackupError.NotABackup))
        assertThat(open(ByteArray(3)).first).isEqualTo(DecryptResult.Failed(BackupError.NotABackup))
        val sealed = seal()
        val newer = sealed.copyOf().also { it[4] = 2 }
        assertThat(open(newer).first).isEqualTo(DecryptResult.Failed(BackupError.NewerVersion(2)))
        val absurd = sealed.copyOf().also { ByteBuffer.wrap(it, 5, 4).putInt(Int.MAX_VALUE) }
        assertThat(open(absurd).first).isEqualTo(DecryptResult.Failed(BackupError.NotABackup))
        assertThat(open(sealed.copyOf(33)).first).isEqualTo(DecryptResult.Failed(BackupError.NotABackup))
    }

    @Test fun `a home backup round trips its records and media`() {
        // AC-C48
        val photo = tmp.newFile("wall-kitchen-north.jpg").apply { writeBytes(Random(3).nextBytes(5_000)) }
        val home = ExportedHome(
            HomeBundle(
                source = "house-manual", exportedAt = 1_762_400_000_000L, homeId = "3e0f6b52-1b7c-4a9e-8f44-6f2d9c1a7b30",
                records = mapOf(
                    "home" to listOf(BundleRecord(buildJsonObject { put("id", "3e0f6b52-1b7c-4a9e-8f44-6f2d9c1a7b30"); put("name", "418 Linden Ave") })),
                    "wall_photo" to listOf(
                        BundleRecord(
                            buildJsonObject {
                                put("id", "8a7b6c5d-4e3f-4a1b-9c8d-7e6f5a4b3c2d")
                                put("stage", "FINISHED")
                            },
                        ),
                    ),
                ),
                media = listOf(photo.name),
            ),
            mapOf(photo.name to photo),
        )
        val scratch = File(tmp.root, "cache/backup.zip")
        val sealed = ByteArrayOutputStream().also { HomeBackup.write(home, it, pass.copyOf(), scratch, blockSize = 1_024) }.toByteArray()
        assertThat(scratch.exists()).isFalse()

        val media = File(tmp.root, "restore")
        val read = HomeBackup.read(ByteArrayInputStream(sealed), pass.copyOf(), File(tmp.root, "cache/restore.zip"), media)
        assertThat(read).isInstanceOf(BackupReadResult.Ok::class.java)
        val bundle = (read as BackupReadResult.Ok).bundle
        assertThat(bundle.homeId).isEqualTo(home.bundle.homeId)
        assertThat(bundle.records.mapValues { it.value.size }).isEqualTo(mapOf("home" to 1, "wall_photo" to 1))
        assertThat(File(media, photo.name).readBytes()).isEqualTo(photo.readBytes())
        assertThat(File(tmp.root, "cache/restore.zip").exists()).isFalse()

        val wrong = HomeBackup.read(ByteArrayInputStream(sealed), "nope".toCharArray(), File(tmp.root, "cache/x.zip"), File(tmp.root, "x"))
        assertThat(wrong).isEqualTo(BackupReadResult.Locked(BackupError.WrongPassphrase))
        assertThat(File(tmp.root, "cache/x.zip").exists()).isFalse()
    }

    @Test fun `a sealed file that is not a bundle inside reads as unreadable`() {
        // AC-C48
        val sealed = seal("just some text, not a zip".toByteArray())
        val read = HomeBackup.read(ByteArrayInputStream(sealed), pass.copyOf(), File(tmp.root, "c.zip"), File(tmp.root, "m"))
        assertThat(read).isEqualTo(BackupReadResult.Unreadable(BundleError.NotABundle))
    }
}
