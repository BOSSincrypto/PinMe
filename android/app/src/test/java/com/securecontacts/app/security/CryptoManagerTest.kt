package com.securecontacts.app.security

import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CryptoManagerTest {
    @Test
    fun versionedBackupRoundTripsAndRejectsWrongPassword() {
        val payload = "{\"version\":1,\"contacts\":[]}"
        val encrypted = CryptoManager.encryptWithPassword(payload, "correct horse battery")

        assertNotEquals(payload, encrypted)
        assertEquals(payload, CryptoManager.decryptWithPassword(encrypted, "correct horse battery"))
        assertNull(CryptoManager.decryptWithPassword(encrypted, "wrong password"))
    }

    @Test
    fun legacyBackupEnvelopeStillDecrypts() {
        val payload = "legacy backup"
        val password = "legacy password"
        val salt = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, CryptoManager.PASSWORD_HASH_ITERATIONS, 256))
            .encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"))
        val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))
        val legacy = Base64.getEncoder().encodeToString(
            ByteBuffer.allocate(salt.size + cipher.iv.size + encrypted.size)
                .put(salt)
                .put(cipher.iv)
                .put(encrypted)
                .array()
        )

        assertEquals(payload, CryptoManager.decryptWithPassword(legacy, password))
    }

    @Test
    fun contactPasswordIterationsCanBeVersioned() {
        val salt = CryptoManager.generateSalt()
        val hash = CryptoManager.hashPassword("contact password", salt, 310000)

        assertEquals(true, CryptoManager.verifyPassword("contact password", salt, hash, 310000))
        assertEquals(false, CryptoManager.verifyPassword("contact password", salt, hash))
    }
}
