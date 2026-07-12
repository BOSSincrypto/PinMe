package com.securecontacts.app.util

import android.content.Context
import android.net.Uri
import com.securecontacts.app.data.repository.ContactRepository
import com.securecontacts.app.security.CryptoManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ExportImportManager(
    private val context: Context,
    private val repository: ContactRepository
) {
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        repository.withTransaction {
            val contacts = repository.getAllContactsSnapshot()
            val tags = repository.getAllTagsSnapshot()
            val categories = repository.getAllCategoriesSnapshot()
            val contactExports = contacts.map { contact ->
                val details = repository.getContactWithDetails(contact.id)
                ContactExport(
                    contact = contact,
                    tagIds = details?.tags?.map { it.id }.orEmpty(),
                    events = details?.events.orEmpty(),
                    reminders = details?.reminders.orEmpty(),
                    socialNetworks = details?.socialNetworks.orEmpty(),
                    customFields = details?.customFields.orEmpty(),
                    conversations = details?.conversations.orEmpty()
                )
            }
            BackupJsonCodec.encode(
                ExportData(
                    contacts = contactExports,
                    tags = tags,
                    categories = categories
                )
            )
        }
    }

    suspend fun exportEncrypted(password: String): String = withContext(Dispatchers.IO) {
        require(password.isNotBlank()) { "Пароль резервной копии не может быть пустым" }
        CryptoManager.encryptWithPassword(exportToJson(), password)
    }

    suspend fun exportToUri(uri: Uri, encrypted: Boolean, password: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            if (!encrypted || password.isNullOrBlank()) {
                return@withContext false
            }
            try {
                val data = exportEncrypted(password)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(data.toByteArray(Charsets.UTF_8))
                } ?: return@withContext false
                true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                false
            }
        }

    suspend fun importFromJson(jsonData: String): ImportResult = withContext(Dispatchers.IO) {
        try {
            val exportData = BackupJsonCodec.decode(jsonData)
            repository.withTransaction {
                val existingCategories = repository.getAllCategoriesSnapshot()
                    .associateBy { it.name.trim().lowercase() }
                    .toMutableMap()
                val existingTags = repository.getAllTagsSnapshot()
                    .associateBy { it.name.trim().lowercase() }
                    .toMutableMap()
                val categoryIdMap = mutableMapOf<Long, Long>()
                exportData.categories.forEach { category ->
                    val key = category.name.trim().lowercase()
                    val target = existingCategories[key] ?: category.copy(id = 0).let {
                        val id = repository.createCategory(it.name, it.color)
                        it.copy(id = id).also { created -> existingCategories[key] = created }
                    }
                    categoryIdMap[category.id] = target.id
                }
                val tagIdMap = mutableMapOf<Long, Long>()
                exportData.tags.forEach { tag ->
                    val key = tag.name.trim().lowercase()
                    val target = existingTags[key] ?: tag.copy(id = 0).let {
                        val id = repository.createTag(it.name, it.color)
                        it.copy(id = id).also { created -> existingTags[key] = created }
                    }
                    tagIdMap[tag.id] = target.id
                }
                exportData.contacts.forEach { item ->
                    val categoryId = item.contact.categoryId?.let { categoryIdMap[it] }
                    val tagIds = item.tagIds.mapNotNull { tagIdMap[it] }
                    repository.restoreContact(
                        contact = item.contact,
                        categoryId = categoryId,
                        tagIds = tagIds,
                        events = item.events,
                        reminders = item.reminders,
                        socialNetworks = item.socialNetworks,
                        customFields = item.customFields,
                        conversations = item.conversations
                    )
                }
                ImportResult.Success(
                    contactsImported = exportData.contacts.size,
                    tagsImported = tagIdMap.size,
                    categoriesImported = categoryIdMap.size
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: BackupFormatException) {
            ImportResult.Error(error.message ?: "Неверный формат резервной копии")
        } catch (error: Exception) {
            ImportResult.Error(error.message ?: "Не удалось импортировать резервную копию")
        }
    }

    suspend fun importEncrypted(encryptedData: String, password: String): ImportResult =
        withContext(Dispatchers.IO) {
            val decryptedData = CryptoManager.decryptWithPassword(encryptedData, password)
            if (decryptedData == null) {
                ImportResult.Error("Неверный пароль или повреждённые данные")
            } else {
                importFromJson(decryptedData)
            }
        }

    suspend fun importFromUri(uri: Uri, encrypted: Boolean, password: String? = null): ImportResult =
        withContext(Dispatchers.IO) {
            if (!encrypted || password.isNullOrBlank()) {
                return@withContext ImportResult.Error("Разрешены только зашифрованные резервные копии")
            }
            try {
                val data = readLimited(uri)
                importEncrypted(data, password)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ImportResult.Error(error.message ?: "Не удалось прочитать резервную копию")
            }
        }

    private fun readLimited(uri: Uri): String {
        val limit = BackupJsonCodec.MAX_JSON_BYTES * 2
        val output = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > limit) {
                    throw BackupFormatException("Файл превышает допустимый размер")
                }
                output.write(buffer, 0, count)
            }
        } ?: throw BackupFormatException("Не удалось прочитать файл")
        return output.toString(Charsets.UTF_8.name())
    }
}

sealed class ImportResult {
    data class Success(
        val contactsImported: Int,
        val tagsImported: Int,
        val categoriesImported: Int
    ) : ImportResult()

    data class Error(val message: String) : ImportResult()
}
