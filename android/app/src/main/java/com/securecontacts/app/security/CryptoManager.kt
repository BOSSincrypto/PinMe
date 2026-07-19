package com.securecontacts.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.MessageDigest
import java.security.KeyStore
import java.security.SecureRandom
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

object CryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "SecureContactsKey"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val SALT_LENGTH = 32
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 1024
    const val PASSWORD_HASH_ITERATIONS = 100000
    private const val BACKUP_KDF_ITERATIONS = 310000
    private const val KEY_LENGTH = 256
    private const val BACKUP_SALT_LENGTH = SALT_LENGTH
    private const val BACKUP_IV_LENGTH = GCM_IV_LENGTH
    private const val BACKUP_TAG_LENGTH = GCM_TAG_LENGTH / 8
    private const val BACKUP_ENVELOPE_VERSION = 2
    private const val BACKUP_KDF_ID_PBKDF2_SHA256 = 1
    private const val BACKUP_HEADER_LENGTH = 4 + 1 + 1 + 4 + BACKUP_SALT_LENGTH + BACKUP_IV_LENGTH
    private const val MAX_BACKUP_INPUT_BYTES = 70 * 1024 * 1024
    private const val MAX_BACKUP_PLAINTEXT_BYTES = 25 * 1024 * 1024
    private const val MIN_BACKUP_KDF_ITERATIONS = 100000
    private const val MAX_BACKUP_KDF_ITERATIONS = 2000000
    private val BACKUP_MAGIC = byteArrayOf(0x50, 0x4D, 0x42, 0x4B)

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_LENGTH)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            keyGenerator.generateKey()
        }

        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    fun encrypt(data: String): String {
        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encryptedData: String): String {
        val combined = Base64.getDecoder().decode(encryptedData)
        require(combined.size >= GCM_IV_LENGTH + GCM_TAG_LENGTH / 8)
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val encrypted = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hashPassword(password: String, salt: String, iterations: Int = PASSWORD_HASH_ITERATIONS): String {
        val hash = derivePasswordHash(password, salt, iterations)
        return try {
            Base64.getEncoder().encodeToString(hash)
        } finally {
            hash.fill(0)
        }
    }

    fun verifyPassword(
        password: String,
        salt: String,
        hash: String,
        iterations: Int = PASSWORD_HASH_ITERATIONS
    ): Boolean {
        return try {
            val expectedHash = Base64.getDecoder().decode(hash)
            val computedHash = derivePasswordHash(password, salt, iterations)
            try {
                MessageDigest.isEqual(computedHash, expectedHash)
            } finally {
                expectedHash.fill(0)
                computedHash.fill(0)
            }
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    fun deriveKeyFromPassword(
        password: String,
        salt: String,
        iterations: Int = PASSWORD_HASH_ITERATIONS
    ): SecretKey {
        val keyBytes = derivePasswordHash(password, salt, iterations)
        return try {
            SecretKeySpec(keyBytes, "AES")
        } finally {
            keyBytes.fill(0)
        }
    }

    fun encryptWithPassword(data: String, password: String): String {
        require(password.isNotBlank()) { "Пароль резервной копии не может быть пустым" }
        require(password.length <= MAX_PASSWORD_LENGTH) { "Пароль резервной копии слишком длинный" }
        val salt = generateSalt()
        val saltBytes = Base64.getDecoder().decode(salt)
        val key = deriveKeyFromPassword(password, salt, BACKUP_KDF_ITERATIONS)
        val plaintext = data.toByteArray(Charsets.UTF_8)
        try {
            require(plaintext.size <= MAX_BACKUP_PLAINTEXT_BYTES) {
                "Резервная копия превышает допустимый размер"
            }
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encrypted = cipher.doFinal(plaintext)
            val combined = ByteBuffer.allocate(BACKUP_HEADER_LENGTH + encrypted.size)
                .put(BACKUP_MAGIC)
                .put(BACKUP_ENVELOPE_VERSION.toByte())
                .put(BACKUP_KDF_ID_PBKDF2_SHA256.toByte())
                .putInt(BACKUP_KDF_ITERATIONS)
                .put(saltBytes)
                .put(cipher.iv)
                .put(encrypted)
                .array()
            return Base64.getEncoder().encodeToString(combined)
        } finally {
            plaintext.fill(0)
            saltBytes.fill(0)
        }
    }

    fun decryptWithPassword(encryptedData: String, password: String): String? {
        if (password.isBlank() || password.length > MAX_PASSWORD_LENGTH || encryptedData.length > MAX_BACKUP_INPUT_BYTES) {
            return null
        }
        return try {
            val combined = Base64.getDecoder().decode(encryptedData)
            if (combined.size > MAX_BACKUP_INPUT_BYTES) {
                return null
            }
            val versioned = combined.size >= BACKUP_HEADER_LENGTH + BACKUP_TAG_LENGTH &&
                combined.copyOfRange(0, BACKUP_MAGIC.size).contentEquals(BACKUP_MAGIC)
            val saltOffset: Int
            val ivOffset: Int
            val encryptedOffset: Int
            val iterations: Int
            if (versioned) {
                val buffer = ByteBuffer.wrap(combined)
                val magic = ByteArray(BACKUP_MAGIC.size)
                buffer.get(magic)
                val version = buffer.get().toInt() and 0xFF
                val kdfId = buffer.get().toInt() and 0xFF
                iterations = buffer.int
                if (version != BACKUP_ENVELOPE_VERSION || kdfId != BACKUP_KDF_ID_PBKDF2_SHA256 ||
                    iterations !in MIN_BACKUP_KDF_ITERATIONS..MAX_BACKUP_KDF_ITERATIONS
                ) {
                    return null
                }
                saltOffset = buffer.position()
                ivOffset = saltOffset + BACKUP_SALT_LENGTH
                encryptedOffset = ivOffset + BACKUP_IV_LENGTH
            } else {
                if (combined.size < SALT_LENGTH + GCM_IV_LENGTH + BACKUP_TAG_LENGTH) {
                    return null
                }
                saltOffset = 0
                ivOffset = SALT_LENGTH
                encryptedOffset = SALT_LENGTH + GCM_IV_LENGTH
                iterations = PASSWORD_HASH_ITERATIONS
            }
            if (combined.size <= encryptedOffset + BACKUP_TAG_LENGTH) {
                return null
            }
            val saltBytes = combined.copyOfRange(saltOffset, saltOffset + BACKUP_SALT_LENGTH)
            val iv = combined.copyOfRange(ivOffset, ivOffset + BACKUP_IV_LENGTH)
            val encrypted = combined.copyOfRange(encryptedOffset, combined.size)
            val salt = Base64.getEncoder().encodeToString(saltBytes)
            val key = deriveKeyFromPassword(password, salt, iterations)
            try {
                val cipher = Cipher.getInstance(AES_MODE)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
                val plaintext = cipher.doFinal(encrypted)
                if (plaintext.size > MAX_BACKUP_PLAINTEXT_BYTES) {
                    return null
                }
                return String(plaintext, Charsets.UTF_8).also { plaintext.fill(0) }
            } finally {
                saltBytes.fill(0)
                iv.fill(0)
                encrypted.fill(0)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun derivePasswordHash(password: String, salt: String, iterations: Int): ByteArray {
        require(iterations in MIN_BACKUP_KDF_ITERATIONS..MAX_BACKUP_KDF_ITERATIONS)
        val saltBytes = Base64.getDecoder().decode(salt)
        val passwordChars = password.toCharArray()
        val spec = PBEKeySpec(passwordChars, saltBytes, iterations, KEY_LENGTH)
        passwordChars.fill('\u0000')
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            saltBytes.fill(0)
        }
    }
}
