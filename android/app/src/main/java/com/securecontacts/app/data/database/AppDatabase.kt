package com.securecontacts.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.securecontacts.app.data.model.Category
import com.securecontacts.app.data.model.Contact
import com.securecontacts.app.data.model.ContactTag
import com.securecontacts.app.data.model.Conversation
import com.securecontacts.app.data.model.CustomField
import com.securecontacts.app.data.model.Event
import com.securecontacts.app.data.model.Reminder
import com.securecontacts.app.data.model.SearchHistory
import com.securecontacts.app.data.model.SocialNetwork
import com.securecontacts.app.data.model.Tag
import com.securecontacts.app.security.DatabaseKeyManager
import com.securecontacts.app.security.DatabaseKeyRecord
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Database(
    entities = [
        Contact::class,
        Tag::class,
        ContactTag::class,
        Category::class,
        Event::class,
        Reminder::class,
        SocialNetwork::class,
        CustomField::class,
        Conversation::class,
        SearchHistory::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun tagDao(): TagDao
    abstract fun categoryDao(): CategoryDao
    abstract fun eventDao(): EventDao
    abstract fun reminderDao(): ReminderDao
    abstract fun socialNetworkDao(): SocialNetworkDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun conversationDao(): ConversationDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        private const val DATABASE_NAME = "secure_contacts.db"
        private const val LEGACY_DATABASE_PASSWORD = "secure_contacts_db_key"

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: createDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        internal fun requireInstance(): AppDatabase {
            return checkNotNull(INSTANCE) { "База данных ещё не инициализирована" }
        }

        private fun createDatabase(context: Context): AppDatabase {
            System.loadLibrary("sqlcipher")
            Logger.setTarget(NoopTarget())

            val keyManager = DatabaseKeyManager(context)
            val keyRecord = keyManager.loadOrCreate()
            try {
                completePendingKeyMigration(context, keyManager, keyRecord)
                val factoryKey = keyRecord.key.copyOf()
                return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                    .openHelperFactory(SupportOpenHelperFactory(factoryKey))
                    .build()
            } finally {
                keyRecord.key.fill(0)
            }
        }

        private fun completePendingKeyMigration(
            context: Context,
            keyManager: DatabaseKeyManager,
            keyRecord: DatabaseKeyRecord
        ) {
            if (!keyRecord.migrationPending) {
                return
            }

            val databaseFile = context.getDatabasePath(DATABASE_NAME)
            val backupFile = File(databaseFile.path + ".legacy-rekey")
            if (!databaseFile.exists() || databaseFile.length() == 0L) {
                deleteLegacyBackup(backupFile)
                keyManager.markMigrationComplete(keyRecord.key)
                return
            }

            if (canOpenDatabase(databaseFile, keyRecord.key)) {
                deleteLegacyBackup(backupFile)
                keyManager.markMigrationComplete(keyRecord.key)
                return
            }

            val legacyKey = LEGACY_DATABASE_PASSWORD.toByteArray(Charsets.UTF_8)
            try {
                if (!canOpenDatabase(databaseFile, legacyKey)) {
                    if (!canOpenDatabase(backupFile, legacyKey)) {
                        throw IllegalStateException("Не удалось открыть существующую зашифрованную базу данных")
                    }
                    restoreLegacyBackup(backupFile, databaseFile)
                }

                prepareLegacyDatabase(databaseFile, legacyKey)
                if (!canOpenDatabase(backupFile, legacyKey)) {
                    copyFileDurably(databaseFile, backupFile)
                }
                rekeyDatabase(databaseFile, legacyKey, keyRecord.key)

                if (!canOpenDatabase(databaseFile, keyRecord.key)) {
                    restoreLegacyBackup(backupFile, databaseFile)
                    throw IllegalStateException("Не удалось проверить новый ключ базы данных")
                }

                deleteLegacyBackup(backupFile)
                keyManager.markMigrationComplete(keyRecord.key)
            } catch (error: Throwable) {
                if (
                    !canOpenDatabase(databaseFile, keyRecord.key) &&
                    !canOpenDatabase(databaseFile, legacyKey) &&
                    canOpenDatabase(backupFile, legacyKey)
                ) {
                    restoreLegacyBackup(backupFile, databaseFile)
                }
                throw error
            } finally {
                legacyKey.fill(0)
            }
        }

        private fun prepareLegacyDatabase(databaseFile: File, legacyKey: ByteArray) {
            openDatabase(databaseFile, legacyKey, SQLiteDatabase.OPEN_READWRITE).use { database ->
                if (!database.isDatabaseIntegrityOk) {
                    throw IllegalStateException("Нарушена целостность существующей базы данных")
                }
                database.rawQuery("PRAGMA wal_checkpoint(FULL)", emptyArray()).use { cursor ->
                    while (cursor.moveToNext()) Unit
                }
                val journalMode = database.rawQuery("PRAGMA journal_mode=DELETE", emptyArray()).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
                if (!journalMode.equals("delete", ignoreCase = true)) {
                    throw IllegalStateException("Не удалось подготовить базу данных к смене ключа")
                }
            }
        }

        private fun rekeyDatabase(databaseFile: File, legacyKey: ByteArray, newKey: ByteArray) {
            val rekeyMaterial = newKey.copyOf()
            try {
                openDatabase(databaseFile, legacyKey, SQLiteDatabase.OPEN_READWRITE).use { database ->
                    if (!database.isDatabaseIntegrityOk) {
                        throw IllegalStateException("Нарушена целостность существующей базы данных")
                    }
                    database.changePassword(rekeyMaterial)
                }
            } finally {
                rekeyMaterial.fill(0)
            }
        }

        private fun canOpenDatabase(databaseFile: File, key: ByteArray): Boolean {
            if (!databaseFile.exists() || databaseFile.length() == 0L) {
                return false
            }
            return try {
                openDatabase(databaseFile, key, SQLiteDatabase.OPEN_READONLY).use { database ->
                    database.isDatabaseIntegrityOk
                }
            } catch (_: Exception) {
                false
            }
        }

        private fun openDatabase(databaseFile: File, key: ByteArray, flags: Int): SQLiteDatabase {
            return SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                key,
                null,
                flags,
                null,
                null
            )
        }

        private fun restoreLegacyBackup(backupFile: File, databaseFile: File) {
            clearDatabaseSidecars(databaseFile)
            copyFileDurably(backupFile, databaseFile)
        }

        private fun copyFileDurably(source: File, target: File) {
            target.parentFile?.mkdirs()
            FileInputStream(source).use { input ->
                FileOutputStream(target, false).use { output ->
                    val size = input.channel.size()
                    var offset = 0L
                    while (offset < size) {
                        val copied = input.channel.transferTo(offset, size - offset, output.channel)
                        if (copied <= 0L) {
                            throw IllegalStateException("Не удалось создать резервную копию базы данных")
                        }
                        offset += copied
                    }
                    output.fd.sync()
                }
            }
        }

        private fun deleteLegacyBackup(backupFile: File) {
            if (backupFile.exists() && !backupFile.delete()) {
                throw IllegalStateException("Не удалось удалить временную копию базы данных")
            }
        }

        private fun clearDatabaseSidecars(databaseFile: File) {
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                File(databaseFile.path + suffix).delete()
            }
        }
    }
}
