package com.securecontacts.app.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.securecontacts.app.localization.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_contacts_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val BACKUP_PASSWORD_HASH = stringPreferencesKey("backup_password_hash")
        private val BACKUP_PASSWORD_SALT = stringPreferencesKey("backup_password_salt")
        private val HELP_PASSWORD_HASH = stringPreferencesKey("help_password_hash")
        private val HELP_PASSWORD_SALT = stringPreferencesKey("help_password_salt")
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val DARK_THEME_ENABLED = booleanPreferencesKey("dark_theme_enabled")
        private val APP_LANGUAGE = stringPreferencesKey("app_language")
        private val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
        private val APP_LOCK_PASSWORD_HASH = stringPreferencesKey("app_lock_password_hash")
        private val APP_LOCK_PASSWORD_SALT = stringPreferencesKey("app_lock_password_salt")
        private val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    }

    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[FIRST_LAUNCH] ?: true
    }

    val hasBackupPassword: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BACKUP_PASSWORD_HASH] != null
    }

    suspend fun hasHelpPassword(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[HELP_PASSWORD_HASH] != null
    }

    val isBiometricEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[BIOMETRIC_ENABLED] ?: false
    }

    val isDarkThemeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_THEME_ENABLED] ?: true
    }

    val appLanguage: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromCode(prefs[APP_LANGUAGE])
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[APP_LANGUAGE] = language.code
        }
    }

    suspend fun setFirstLaunchComplete() {
        context.dataStore.edit { prefs ->
            prefs[FIRST_LAUNCH] = false
        }
    }

    suspend fun completeInitialSetup(backupPassword: String, helpPassword: String) {
        require(backupPassword.length >= 8)
        require(helpPassword.length >= 8)
        require(backupPassword != helpPassword)
        val backupSalt = CryptoManager.generateSalt()
        val helpSalt = CryptoManager.generateSalt()
        val backupHash = CryptoManager.hashPassword(backupPassword, backupSalt)
        val helpHash = CryptoManager.hashPassword(helpPassword, helpSalt)
        context.dataStore.edit { prefs ->
            prefs[BACKUP_PASSWORD_HASH] = backupHash
            prefs[BACKUP_PASSWORD_SALT] = backupSalt
            prefs[HELP_PASSWORD_HASH] = helpHash
            prefs[HELP_PASSWORD_SALT] = helpSalt
            prefs[FIRST_LAUNCH] = false
        }
    }

    suspend fun setBackupPassword(password: String) {
        require(password.length >= 8)
        val salt = CryptoManager.generateSalt()
        val hash = CryptoManager.hashPassword(password, salt)
        context.dataStore.edit { prefs ->
            prefs[BACKUP_PASSWORD_HASH] = hash
            prefs[BACKUP_PASSWORD_SALT] = salt
        }
    }

    suspend fun verifyBackupPassword(password: String): Boolean {
        if (password.isBlank() || password.length > 1024) return false
        val prefs = context.dataStore.data.first()
        val hash = prefs[BACKUP_PASSWORD_HASH]
        val salt = prefs[BACKUP_PASSWORD_SALT]
        return if (hash != null && salt != null) {
            CryptoManager.verifyPassword(password, salt, hash)
        } else {
            false
        }
    }

    suspend fun changeBackupPassword(oldPassword: String, newPassword: String): Boolean {
        if (!verifyBackupPassword(oldPassword)) return false
        setBackupPassword(newPassword)
        return true
    }

    suspend fun setHelpPassword(password: String) {
        require(password.length >= 8)
        check(!verifyBackupPassword(password)) { "Пароль помощи должен отличаться от резервного" }
        val salt = CryptoManager.generateSalt()
        val hash = CryptoManager.hashPassword(password, salt)
        context.dataStore.edit { prefs ->
            prefs[HELP_PASSWORD_HASH] = hash
            prefs[HELP_PASSWORD_SALT] = salt
        }
    }

    suspend fun verifyHelpPassword(password: String): Boolean {
        val prefs = context.dataStore.data.first()
        val hash = prefs[HELP_PASSWORD_HASH]
        val salt = prefs[HELP_PASSWORD_SALT]
        return if (hash != null && salt != null) {
            CryptoManager.verifyPassword(password, salt, hash)
        } else {
            false
        }
    }

    suspend fun changeHelpPassword(oldPassword: String, newPassword: String): Boolean {
        if (!verifyHelpPassword(oldPassword)) return false
        setHelpPassword(newPassword)
        return true
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setDarkThemeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_THEME_ENABLED] = enabled
        }
    }

    fun getBackupPasswordData(): Flow<Pair<String?, String?>> = context.dataStore.data.map { prefs ->
        Pair(prefs[BACKUP_PASSWORD_HASH], prefs[BACKUP_PASSWORD_SALT])
    }

    val isAppLockEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[APP_LOCK_ENABLED] ?: false
    }

    val hasAppLockPassword: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[APP_LOCK_PASSWORD_HASH] != null
    }

    suspend fun setAppLockPassword(password: String) {
        val salt = CryptoManager.generateSalt()
        val hash = CryptoManager.hashPassword(password, salt)
        context.dataStore.edit { prefs ->
            prefs[APP_LOCK_PASSWORD_HASH] = hash
            prefs[APP_LOCK_PASSWORD_SALT] = salt
            prefs[APP_LOCK_ENABLED] = true
        }
    }

    suspend fun verifyAppLockPassword(password: String): Boolean {
        val prefs = context.dataStore.data.first()
        val hash = prefs[APP_LOCK_PASSWORD_HASH]
        val salt = prefs[APP_LOCK_PASSWORD_SALT]
        return if (hash != null && salt != null) {
            CryptoManager.verifyPassword(password, salt, hash)
        } else {
            false
        }
    }

    suspend fun changeAppLockPassword(oldPassword: String, newPassword: String): Boolean {
        if (!verifyAppLockPassword(oldPassword)) return false
        setAppLockPassword(newPassword)
        return true
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[APP_LOCK_ENABLED] = enabled
        }
    }

    suspend fun removeAppLock() {
        context.dataStore.edit { prefs ->
            prefs.remove(APP_LOCK_PASSWORD_HASH)
            prefs.remove(APP_LOCK_PASSWORD_SALT)
            prefs[APP_LOCK_ENABLED] = false
        }
    }
}
