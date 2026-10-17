package com.kurtz.homerecord.core.bundle

import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BundleCryptoTest {
    private val plain = ByteArray(100_000) { (it * 31).toByte() }

    private fun enc(pass: String = "correct horse"): ByteArray =
        ByteArrayOutputStream().also { BundleCrypto.encrypt(ByteArrayInputStream(plain), it, pass) }.toByteArray()

    private fun dec(data: ByteArray, pass: String): ByteArray =
        ByteArrayOutputStream().also { BundleCrypto.decrypt(ByteArrayInputStream(data), it, pass) }.toByteArray()

    @Test fun `round trip returns the original bytes (AC-88)`() {
        assertArrayEquals(plain, dec(enc(), "correct horse"))
    }

    @Test fun `ciphertext differs between runs and hides the plaintext`() {
        val a = enc()
        val b = enc()
        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOfRange(60, 160).contentEquals(plain.copyOfRange(0, 100)))
    }

    @Test fun `wrong passphrase is reported (AC-89)`() {
        try {
            dec(enc(), "not the one")
            fail("expected WrongPassphraseException")
        } catch (e: WrongPassphraseException) {
            // expected
        }
    }

    @Test fun `tampered ciphertext is rejected`() {
        val data = enc()
        data[data.size / 2] = (data[data.size / 2] + 1).toByte()
        try {
            dec(data, "correct horse")
            fail("expected an exception")
        } catch (e: WrongPassphraseException) {
            // authentication failed
        }
    }

    @Test fun `plain zip is not detected as encrypted and encrypted data is (AC-89)`() {
        assertTrue(BundleCrypto.isEncrypted(BufferedInputStream(ByteArrayInputStream(enc()))))
        assertFalse(BundleCrypto.isEncrypted(BufferedInputStream(ByteArrayInputStream(byteArrayOf(0x50, 0x4b, 3, 4, 0, 0, 0, 0, 0)))))
        assertFalse(BundleCrypto.isEncrypted(BufferedInputStream(ByteArrayInputStream(ByteArray(2)))))
    }

    @Test fun `detection leaves the stream readable from the start`() {
        val stream = BufferedInputStream(ByteArrayInputStream(enc()))
        BundleCrypto.isEncrypted(stream)
        assertArrayEquals(plain, ByteArrayOutputStream().also { BundleCrypto.decrypt(stream, it, "correct horse") }.toByteArray())
    }

    @Test fun `short and non-encrypted input is a format error`() {
        try {
            dec(byteArrayOf(1, 2, 3), "correct horse")
            fail("expected BundleFormatException")
        } catch (e: BundleFormatException) {
            // expected
        }
    }

    @Test fun `passphrase needs at least 8 characters (AC-88)`() {
        assertFalse(BundleCrypto.isValidPassphrase("1234567"))
        assertTrue(BundleCrypto.isValidPassphrase("12345678"))
    }
}
