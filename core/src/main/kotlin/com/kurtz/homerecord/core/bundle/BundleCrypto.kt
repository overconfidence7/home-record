package com.kurtz.homerecord.core.bundle

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Why a backup could not be opened. */
sealed interface BackupError {
    /** Not a HomeRecord backup at all (wrong file, or a plain `.homerecord`). */
    data object NotABackup : BackupError

    /** Written by a newer app version. */
    data class NewerVersion(val version: Int) : BackupError

    /** The passphrase does not open it (or the first block was changed). */
    data object WrongPassphrase : BackupError

    /** It opened, but a later block was changed, reordered or the file was cut short. */
    data object Damaged : BackupError
}

sealed interface DecryptResult {
    data class Ok(val bytes: Long) : DecryptResult
    data class Failed(val error: BackupError) : DecryptResult
}

/**
 * Passphrase encryption for a whole-home backup (AC-C48): the `.homerecord` zip, sealed with AES-256-GCM under a key
 * from PBKDF2-HMAC-SHA256 (210 000 iterations, 16-byte random salt).
 *
 * A home with every wall photographed runs to a few GB, and Android's GCM decrypt holds the whole ciphertext until the
 * tag checks, so the stream is sealed in 1 MiB blocks, each its own GCM message:
 *
 * ```
 * header  = "HRBK" | version u8 (1) | iterations i32 | salt[16] | noncePrefix[8]
 * block*  = last u8 (0|1) | length i32 | ciphertext+tag[length]
 * nonce_i = noncePrefix | i (u32 big-endian)          aad_i = header | last
 * ```
 *
 * The counter in the nonce stops blocks being reordered or dropped, the header in the AAD stops the salt or
 * iterations being swapped, and the `last` flag in the AAD stops the file being cut at a block boundary: a stream
 * that ends before a block marked last is [BackupError.Damaged]. Plaintext is only written after its block checks.
 */
object BundleCrypto {
    val MAGIC = byteArrayOf('H'.code.toByte(), 'R'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    const val VERSION = 1
    const val ITERATIONS = 210_000
    const val BLOCK_SIZE = 1 shl 20
    private const val SALT_BYTES = 16
    private const val PREFIX_BYTES = 8
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val KEY_BITS = 256
    /** A header asking for more than this is not one we wrote; deriving it would hang the phone. */
    private const val MAX_ITERATIONS = 10_000_000
    private const val HEADER_BYTES = 4 + 1 + 4 + SALT_BYTES + PREFIX_BYTES

    private val random = SecureRandom()

    /** True when [head] (the first bytes of a file) starts like a backup; used to tell a backup from a `.homerecord`. */
    fun looksLikeBackup(head: ByteArray): Boolean = head.size >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    /** Seals all of [input] into [output] under [passphrase]. Neither stream is closed. */
    fun encrypt(input: InputStream, output: OutputStream, passphrase: CharArray, blockSize: Int = BLOCK_SIZE) {
        require(blockSize > 0)
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val prefix = ByteArray(PREFIX_BYTES).also(random::nextBytes)
        val header = header(ITERATIONS, salt, prefix)
        val key = deriveKey(passphrase, salt, ITERATIONS)
        val out = DataOutputStream(output)
        out.write(header)
        var counter = 0L
        var current = readBlock(input, blockSize)
        while (true) {
            val next = if (current.size < blockSize) ByteArray(0) else readBlock(input, blockSize)
            // A full block followed by an empty read is still the last one: no empty trailing block needed.
            val last = current.size < blockSize || next.isEmpty()
            val sealed = cipher(Cipher.ENCRYPT_MODE, key, prefix, counter, header, last).doFinal(current)
            out.writeByte(if (last) 1 else 0)
            out.writeInt(sealed.size)
            out.write(sealed)
            if (last) break
            counter++
            current = next
        }
        out.flush()
    }

    /**
     * Opens a backup from [input] into [output]. On a failure part of the plaintext may already be in [output]
     * (every byte of it checked); the caller throws that away.
     */
    fun decrypt(input: InputStream, output: OutputStream, passphrase: CharArray): DecryptResult {
        val data = DataInputStream(input)
        val header = ByteArray(HEADER_BYTES)
        try {
            data.readFully(header)
        } catch (_: EOFException) {
            return DecryptResult.Failed(BackupError.NotABackup)
        }
        if (!looksLikeBackup(header)) return DecryptResult.Failed(BackupError.NotABackup)
        val buf = ByteBuffer.wrap(header, MAGIC.size, HEADER_BYTES - MAGIC.size)
        val version = buf.get().toInt() and 0xFF
        if (version > VERSION) return DecryptResult.Failed(BackupError.NewerVersion(version))
        if (version < 1) return DecryptResult.Failed(BackupError.NotABackup)
        val iterations = buf.int
        if (iterations !in 1..MAX_ITERATIONS) return DecryptResult.Failed(BackupError.NotABackup)
        val salt = ByteArray(SALT_BYTES).also { buf.get(it) }
        val prefix = ByteArray(PREFIX_BYTES).also { buf.get(it) }
        val key = deriveKey(passphrase, salt, iterations)
        var counter = 0L
        var written = 0L
        try {
            while (true) {
                val flag = data.read()
                if (flag < 0) return DecryptResult.Failed(if (counter == 0L) BackupError.NotABackup else BackupError.Damaged)
                if (flag > 1) return DecryptResult.Failed(BackupError.Damaged)
                val length = data.readInt()
                if (length < TAG_BYTES || length > MAX_SEALED) return DecryptResult.Failed(BackupError.Damaged)
                val sealed = ByteArray(length).also(data::readFully)
                val plain = try {
                    cipher(Cipher.DECRYPT_MODE, key, prefix, counter, header, flag == 1).doFinal(sealed)
                } catch (_: AEADBadTagException) {
                    return DecryptResult.Failed(if (counter == 0L) BackupError.WrongPassphrase else BackupError.Damaged)
                }
                output.write(plain)
                written += plain.size
                if (flag == 1) break
                counter++
            }
        } catch (_: EOFException) {
            return DecryptResult.Failed(BackupError.Damaged)
        } catch (_: GeneralSecurityException) {
            return DecryptResult.Failed(BackupError.Damaged)
        }
        output.flush()
        return DecryptResult.Ok(written)
    }

    /** Largest block a reader accepts: [BLOCK_SIZE] plaintext plus the tag, with room for a bigger writer block size. */
    private const val MAX_SEALED = 16 * BLOCK_SIZE + TAG_BYTES

    private fun header(iterations: Int, salt: ByteArray, prefix: ByteArray): ByteArray =
        ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).put(VERSION.toByte()).putInt(iterations).put(salt).put(prefix).array()

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, prefix: ByteArray, counter: Long, header: ByteArray, last: Boolean): Cipher {
        require(counter <= 0xFFFF_FFFFL) { "backup too large" }
        val nonce = ByteBuffer.allocate(PREFIX_BYTES + 4).put(prefix).putInt(counter.toInt()).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
            updateAAD(byteArrayOf(if (last) 1 else 0))
        }
    }

    /** Up to [size] bytes; fewer only at the end of [input]. */
    private fun readBlock(input: InputStream, size: Int): ByteArray {
        val block = ByteArray(size)
        var filled = 0
        while (filled < size) {
            val n = input.read(block, filled, size - filled)
            if (n < 0) break
            filled += n
        }
        return if (filled == size) block else block.copyOf(filled)
    }
}
