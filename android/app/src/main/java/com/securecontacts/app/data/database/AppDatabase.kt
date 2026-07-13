package com.securecontacts.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 5,
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
                    .addMigrations(MIGRATION_1_5, MIGRATION_2_5, MIGRATION_3_5, MIGRATION_4_5)
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

        private val MIGRATION_1_5 = object : Migration(1, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                migrateLegacySchema(database)
                createPerformanceIndexes(database)
            }
        }

        private val MIGRATION_2_5 = object : Migration(2, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                migrateLegacySchema(database)
                createPerformanceIndexes(database)
            }
        }

        private val MIGRATION_3_5 = object : Migration(3, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                if (database.hasTable("contacts")) {
                    database.addColumnIfMissing("contacts", "passwordIterations INTEGER NOT NULL DEFAULT 100000")
                } else {
                    migrateLegacySchema(database)
                }
                createPerformanceIndexes(database)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                createPerformanceIndexes(database)
            }
        }

        private fun migrateLegacySchema(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL DEFAULT '', `phone` TEXT NOT NULL DEFAULT '', `email` TEXT NOT NULL DEFAULT '', `address` TEXT NOT NULL DEFAULT '', `workplace` TEXT NOT NULL DEFAULT '', `position` TEXT NOT NULL DEFAULT '', `source` TEXT NOT NULL DEFAULT '', `helpInfo` TEXT NOT NULL DEFAULT '', `birthday` INTEGER, `avatarUri` TEXT, `passwordHash` TEXT NOT NULL DEFAULT '', `passwordSalt` TEXT NOT NULL DEFAULT '', `passwordIterations` INTEGER NOT NULL DEFAULT 100000, `encryptedData` TEXT NOT NULL DEFAULT '', `categoryId` INTEGER, `isActive` INTEGER NOT NULL DEFAULT 1, `createdAt` INTEGER NOT NULL DEFAULT 0, `updatedAt` INTEGER NOT NULL DEFAULT 0)"
            )
            database.addColumnIfMissing("contacts", "phone TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("contacts", "email TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("contacts", "address TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("contacts", "workplace TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("contacts", "position TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("contacts", "source TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("contacts", "helpInfo TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("contacts", "birthday INTEGER")
            database.addColumnIfMissing("contacts", "avatarUri TEXT")
            database.addColumnIfMissing("contacts", "passwordHash TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("contacts", "passwordSalt TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("contacts", "passwordIterations INTEGER NOT NULL DEFAULT 100000")
            database.addColumnIfMissing("contacts", "encryptedData TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("contacts", "categoryId INTEGER")
            database.addColumnIfMissing("contacts", "isActive INTEGER NOT NULL DEFAULT 1")
            database.addColumnIfMissing("contacts", "createdAt INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("contacts", "updatedAt INTEGER NOT NULL DEFAULT 0")
            database.execSQL("UPDATE `contacts` SET `createdAt` = CASE WHEN `createdAt` <= 0 THEN strftime('%s','now') * 1000 ELSE `createdAt` END, `updatedAt` = CASE WHEN `updatedAt` <= 0 THEN strftime('%s','now') * 1000 ELSE `updatedAt` END")

            database.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL DEFAULT '', `color` TEXT NOT NULL DEFAULT '#2196F3')")
            database.execSQL("CREATE TABLE IF NOT EXISTS `contact_tags` (`contactId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`contactId`, `tagId`))")
            database.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL DEFAULT '', `color` TEXT NOT NULL DEFAULT '#4CAF50')")
            database.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `contactId` INTEGER NOT NULL, `title` TEXT NOT NULL DEFAULT '', `date` INTEGER NOT NULL DEFAULT 0, `comment` TEXT NOT NULL DEFAULT '', `isRecurring` INTEGER NOT NULL DEFAULT 0)")
            database.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `contactId` INTEGER NOT NULL, `title` TEXT NOT NULL DEFAULT '', `date` INTEGER, `comment` TEXT NOT NULL DEFAULT '', `isCompleted` INTEGER NOT NULL DEFAULT 0, `createdAt` INTEGER NOT NULL DEFAULT 0)")
            database.execSQL("CREATE TABLE IF NOT EXISTS `social_networks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `contactId` INTEGER NOT NULL, `type` TEXT NOT NULL DEFAULT '', `url` TEXT NOT NULL DEFAULT '', `username` TEXT NOT NULL DEFAULT '')")
            database.execSQL("CREATE TABLE IF NOT EXISTS `custom_fields` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `contactId` INTEGER NOT NULL, `fieldName` TEXT NOT NULL DEFAULT '', `fieldValue` TEXT NOT NULL DEFAULT '', `isEncrypted` INTEGER NOT NULL DEFAULT 0)")
            database.execSQL("CREATE TABLE IF NOT EXISTS `conversations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `contactId` INTEGER NOT NULL, `date` INTEGER NOT NULL DEFAULT 0, `topic` TEXT NOT NULL DEFAULT '', `createdAt` INTEGER NOT NULL DEFAULT 0)")
            database.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `query` TEXT NOT NULL DEFAULT '', `timestamp` INTEGER NOT NULL DEFAULT 0)")

            database.addColumnIfMissing("tags", "name TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("tags", "color TEXT NOT NULL DEFAULT '#2196F3'")
            database.addColumnIfMissing("contact_tags", "contactId INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("contact_tags", "tagId INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("categories", "name TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("categories", "color TEXT NOT NULL DEFAULT '#4CAF50'")
            database.addColumnIfMissing("events", "contactId INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("events", "title TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("events", "date INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("events", "comment TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("events", "isRecurring INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("reminders", "contactId INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("reminders", "title TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("reminders", "date INTEGER")
            database.addColumnIfMissing("reminders", "comment TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("reminders", "isCompleted INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("reminders", "createdAt INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("social_networks", "contactId INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("social_networks", "type TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("social_networks", "url TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("social_networks", "username TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("custom_fields", "contactId INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("custom_fields", "fieldName TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("custom_fields", "fieldValue TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("custom_fields", "isEncrypted INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("conversations", "contactId INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("conversations", "date INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("conversations", "topic TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("conversations", "createdAt INTEGER NOT NULL DEFAULT 0")
            database.addColumnIfMissing("search_history", "query TEXT NOT NULL DEFAULT ''")
            database.addColumnIfMissing("search_history", "timestamp INTEGER NOT NULL DEFAULT 0")
            database.execSQL("UPDATE `reminders` SET `createdAt` = CASE WHEN `createdAt` <= 0 THEN strftime('%s','now') * 1000 ELSE `createdAt` END")
            database.execSQL("UPDATE `conversations` SET `createdAt` = CASE WHEN `createdAt` <= 0 THEN strftime('%s','now') * 1000 ELSE `createdAt` END")
        }

        private fun createPerformanceIndexes(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_name` ON `contacts` (`name`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_isActive_name` ON `contacts` (`isActive`, `name`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_birthday` ON `contacts` (`birthday`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_contact_tags_tagId` ON `contact_tags` (`tagId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_events_contactId_date` ON `events` (`contactId`, `date`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_contactId_date` ON `reminders` (`contactId`, `date`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_social_networks_contactId` ON `social_networks` (`contactId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_fields_contactId` ON `custom_fields` (`contactId`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_contactId_date` ON `conversations` (`contactId`, `date`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_date` ON `conversations` (`date`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_search_history_timestamp` ON `search_history` (`timestamp`)")
        }

        private fun SupportSQLiteDatabase.hasTable(table: String): Boolean {
            query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1", arrayOf(table)).use { cursor ->
                return cursor.moveToFirst()
            }
        }

        private fun SupportSQLiteDatabase.addColumnIfMissing(table: String, definition: String) {
            val column = definition.substringBefore(' ').trim('`')
            query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return
                }
            }
            execSQL("ALTER TABLE `$table` ADD COLUMN $definition")
        }
    }
}
