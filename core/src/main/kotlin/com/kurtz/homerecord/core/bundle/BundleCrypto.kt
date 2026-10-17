package com.kurtz.homerecord.core.bundle

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The passphrase did not decrypt the backup (AC-89). */
class WrongPassphraseException(cause: Throwable? = null) : Exception("Wrong passphrase.", cause)

/** Passphrase-based AES-256-GCM wrapper around a `.homerecord` zip (AC-88, AC-89). */
object BundleCrypto {
    const val MIN_PASSPHRASE_LENGTH = 8

    private val MAGIC = "HRENC001".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    fun isValidPassphrase(passphrase: String): Boolean = passphrase.length >= MIN_PASSPHRASE_LENGTH

    /** Peeks at the start of [input] without consuming it. */
    fun isEncrypted(input: BufferedInputStream): Boolean {
        input.mark(MAGIC.size)
        return try {
            val head = ByteArray(MAGIC.size)
            var read = 0
            while (read < head.size) {
                val n = input.read(head, read, head.size - read)
                if (n < 0) break
                read += n
            }
            read == head.size && head.contentEquals(MAGIC)
        } finally {
            input.reset()
        }
    }

    /** Writes `MAGIC | iterations | salt | iv | ciphertext+tag` to [out]; [out] is flushed, not closed. */
    fun encrypt(plain: InputStream, out: OutputStream, passphrase: String) {
        require(isValidPassphrase(passphrase)) { "Passphrase too short." }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(MAGIC)
        out.write(MAGIC)
        out.write(intBytes(ITERATIONS))
        out.write(salt)
        out.write(iv)
        val enc = CipherOutputStream(NonClosing(out), cipher)
        plain.copyTo(enc)
        enc.close()
        out.flush()
    }

    /**
     * Decrypts [encrypted] into [out]. Throws [WrongPassphraseException] when the tag does not verify and
     * [BundleFormatException] when the input is not an encrypted bundle.
     */
    fun decrypt(encrypted: InputStream, out: OutputStream, passphrase: String) {
        try {
            val head = ByteArray(MAGIC.size)
            readFully(encrypted, head)
            if (!head.contentEquals(MAGIC)) throw BundleFormatException("This file is not an encrypted HomeRecord backup.")
            val iterations = intFrom(ByteArray(4).also { readFully(encrypted, it) })
            if (iterations !in 1_000..5_000_000) throw BundleFormatException("This backup is damaged.")
            val salt = ByteArray(SALT_BYTES).also { readFully(encrypted, it) }
            val iv = ByteArray(IV_BYTES).also { readFully(encrypted, it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(MAGIC)
            CipherInputStream(encrypted, cipher).use { it.copyTo(out) }
            out.flush()
        } catch (e: IOException) {
            if (generateSequence<Throwable>(e) { it.cause }.any { it is AEADBadTagException }) throw WrongPassphraseException(e)
            throw BundleFormatException("This backup could not be read.", e)
        } catch (e: AEADBadTagException) {
            throw WrongPassphraseException(e)
        } catch (e: GeneralSecurityException) {
            throw BundleFormatException("This backup could not be read.", e)
        }
    }

    private fun key(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun readFully(input: InputStream, into: ByteArray) {
        var read = 0
        while (read < into.size) {
            val n = input.read(into, read, into.size - read)
            if (n < 0) throw BundleFormatException("This backup is damaged.")
            read += n
        }
    }

    private fun intBytes(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
    private fun intFrom(b: ByteArray) = (b[0].toInt() and 0xff shl 24) or (b[1].toInt() and 0xff shl 16) or (b[2].toInt() and 0xff shl 8) or (b[3].toInt() and 0xff)

    private class NonClosing(private val delegate: OutputStream) : OutputStream() {
        override fun write(b: Int) = delegate.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
        override fun flush() = delegate.flush()
        override fun close() = delegate.flush()
    }
}
