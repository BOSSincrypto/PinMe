package com.securecontacts.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class DatabaseKeyRecord(
    val key: ByteArray,
    val migrationPending: Boolean
)

internal class DatabaseKeyManager(context: Context) {
    private val keyFile = AtomicFile(File(context.noBackupFilesDir, KEY_FILE_NAME))

    @Synchronized
    fun loadOrCreate(): DatabaseKeyRecord {
        val exists = keyFile.baseFile.exists()
        val masterKey = getMasterKey(allowCreate = !exists)
        if (exists) {
            return readRecord(masterKey)
        }

        val databaseKey = ByteArray(DATABASE_KEY_SIZE).also { SecureRandom().nextBytes(it) }
        return try {
            writeRecord(masterKey, databaseKey, migrationPending = true)
            DatabaseKeyRecord(databaseKey, migrationPending = true)
        } catch (error: Throwable) {
            databaseKey.fill(0)
            throw error
        }
    }

    @Synchronized
    fun markMigrationComplete(databaseKey: ByteArray) {
        require(databaseKey.size == DATABASE_KEY_SIZE)
        writeRecord(getMasterKey(allowCreate = false), databaseKey, migrationPending = false)
    }

    private fun getMasterKey(allowCreate: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(MASTER_KEY_ALIAS, null)
        if (existing is SecretKey) {
            return existing
        }
        if (!allowCreate) {
            throw GeneralSecurityException("Ключ шифрования базы данных недоступен")
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun readRecord(masterKey: SecretKey): DatabaseKeyRecord {
        val envelope = keyFile.readFully()
        if (envelope.size !in MIN_ENVELOPE_SIZE..MAX_ENVELOPE_SIZE) {
            throw GeneralSecurityException("Повреждено хранилище ключа базы данных")
        }

        val buffer = ByteBuffer.wrap(envelope)
        if (buffer.get() != ENVELOPE_VERSION) {
            throw GeneralSecurityException("Неподдерживаемая версия хранилища ключа")
        }
        val ivSize = buffer.get().toInt() and 0xFF
        if (ivSize != GCM_IV_SIZE || buffer.remaining() <= ivSize) {
            throw GeneralSecurityException("Повреждено хранилище ключа базы данных")
        }

        val iv = ByteArray(ivSize).also { buffer.get(it) }
        val ciphertext = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val plaintext = Cipher.getInstance(AES_MODE).run {
            init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            updateAAD(AAD)
            doFinal(ciphertext)
        }

        try {
            if (plaintext.size != RECORD_SIZE || plaintext[0] != RECORD_VERSION) {
                throw GeneralSecurityException("Повреждено хранилище ключа базы данных")
            }
            val state = plaintext[1]
            if (state != STATE_PENDING && state != STATE_READY) {
                throw GeneralSecurityException("Повреждено состояние ключа базы данных")
            }
            return DatabaseKeyRecord(
                key = plaintext.copyOfRange(RECORD_HEADER_SIZE, plaintext.size),
                migrationPending = state == STATE_PENDING
            )
        } finally {
            plaintext.fill(0)
        }
    }

    private fun writeRecord(masterKey: SecretKey, databaseKey: ByteArray, migrationPending: Boolean) {
        val plaintext = ByteArray(RECORD_SIZE)
        plaintext[0] = RECORD_VERSION
        plaintext[1] = if (migrationPending) STATE_PENDING else STATE_READY
        databaseKey.copyInto(plaintext, RECORD_HEADER_SIZE)

        val envelope = try {
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            cipher.updateAAD(AAD)
            val ciphertext = cipher.doFinal(plaintext)
            ByteBuffer.allocate(2 + cipher.iv.size + ciphertext.size)
                .put(ENVELOPE_VERSION)
                .put(cipher.iv.size.toByte())
                .put(cipher.iv)
                .put(ciphertext)
                .array()
        } finally {
            plaintext.fill(0)
        }

        var output: FileOutputStream? = null
        try {
            output = keyFile.startWrite()
            output.write(envelope)
            keyFile.finishWrite(output)
            output = null
        } catch (error: Throwable) {
            output?.let { keyFile.failWrite(it) }
            throw error
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val MASTER_KEY_ALIAS = "PinMeDatabaseKeyWrapV1"
        const val KEY_FILE_NAME = "database_key_v1"
        const val AES_MODE = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH = 128
        const val GCM_IV_SIZE = 12
        const val DATABASE_KEY_SIZE = 32
        const val RECORD_HEADER_SIZE = 2
        const val RECORD_SIZE = RECORD_HEADER_SIZE + DATABASE_KEY_SIZE
        const val MIN_ENVELOPE_SIZE = 2 + GCM_IV_SIZE + 16 + RECORD_SIZE
        const val MAX_ENVELOPE_SIZE = 256
        const val ENVELOPE_VERSION: Byte = 1
        const val RECORD_VERSION: Byte = 1
        const val STATE_PENDING: Byte = 0
        const val STATE_READY: Byte = 1
        val AAD: ByteArray = "PinMeDatabaseKeyV1".toByteArray(Charsets.UTF_8)
    }
}
