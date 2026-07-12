package com.securecontacts.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.MessageDigest
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "SecureContactsKey"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12
    private const val SALT_LENGTH = 32
    private const val PBKDF2_ITERATIONS = 100000
    private const val KEY_LENGTH = 256

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
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(encryptedData: String): String {
        val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
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
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, salt: String): String {
        val hash = derivePasswordHash(password, salt)
        return try {
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } finally {
            hash.fill(0)
        }
    }

    fun verifyPassword(password: String, salt: String, hash: String): Boolean {
        return try {
            val expectedHash = Base64.decode(hash, Base64.NO_WRAP)
            val computedHash = derivePasswordHash(password, salt)
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

    fun deriveKeyFromPassword(password: String, salt: String): SecretKey {
        val keyBytes = derivePasswordHash(password, salt)
        return try {
            SecretKeySpec(keyBytes, "AES")
        } finally {
            keyBytes.fill(0)
        }
    }

    fun encryptWithPassword(data: String, password: String): String {
        val salt = generateSalt()
        val key = deriveKeyFromPassword(password, salt)

        val cipher = Cipher.getInstance(AES_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val combined = ByteArray(saltBytes.size + iv.size + encrypted.size)
        System.arraycopy(saltBytes, 0, combined, 0, saltBytes.size)
        System.arraycopy(iv, 0, combined, saltBytes.size, iv.size)
        System.arraycopy(encrypted, 0, combined, saltBytes.size + iv.size, encrypted.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptWithPassword(encryptedData: String, password: String): String? {
        return try {
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)
            if (combined.size < SALT_LENGTH + GCM_IV_LENGTH + GCM_TAG_LENGTH / 8) {
                return null
            }
            val saltBytes = combined.copyOfRange(0, SALT_LENGTH)
            val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH)
            val encrypted = combined.copyOfRange(SALT_LENGTH + GCM_IV_LENGTH, combined.size)

            val salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP)
            val key = deriveKeyFromPassword(password, salt)

            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun derivePasswordHash(password: String, salt: String): ByteArray {
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val passwordChars = password.toCharArray()
        val spec = PBEKeySpec(passwordChars, saltBytes, PBKDF2_ITERATIONS, KEY_LENGTH)
        passwordChars.fill('\u0000')
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            saltBytes.fill(0)
        }
    }
}
