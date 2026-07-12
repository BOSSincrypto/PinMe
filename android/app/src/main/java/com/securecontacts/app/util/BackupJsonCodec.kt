package com.securecontacts.app.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.securecontacts.app.data.model.Category
import com.securecontacts.app.data.model.Contact
import com.securecontacts.app.data.model.Conversation
import com.securecontacts.app.data.model.CustomField
import com.securecontacts.app.data.model.Event
import com.securecontacts.app.data.model.Reminder
import com.securecontacts.app.data.model.SocialNetwork
import com.securecontacts.app.data.model.Tag
import java.nio.charset.StandardCharsets
import java.util.Base64

internal const val BACKUP_FORMAT_VERSION = 1

data class ExportData(
    val version: Int = BACKUP_FORMAT_VERSION,
    val exportDate: Long = System.currentTimeMillis(),
    val contacts: List<ContactExport> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val categories: List<Category> = emptyList()
)

data class ContactExport(
    val contact: Contact,
    val tagIds: List<Long> = emptyList(),
    val events: List<Event> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val socialNetworks: List<SocialNetwork> = emptyList(),
    val customFields: List<CustomField> = emptyList(),
    val conversations: List<Conversation> = emptyList()
)

internal class BackupFormatException(message: String) : Exception(message)

internal object BackupJsonCodec {
    const val MAX_JSON_BYTES = 25 * 1024 * 1024
    private const val MAX_CONTACTS = 50_000
    private const val MAX_METADATA_ITEMS = 10_000
    private const val MAX_RELATIONS_PER_CONTACT = 10_000
    private const val MAX_SHORT_TEXT = 10_000
    private const val MAX_LONG_TEXT = 1_000_000
    private val colorPattern = Regex("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")
    private val integerPattern = Regex("^-?[0-9]+$")
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun encode(data: ExportData): String {
        if (data.version != BACKUP_FORMAT_VERSION) {
            throw BackupFormatException("Неподдерживаемая версия резервной копии")
        }
        val json = gson.toJson(data)
        if (json.toByteArray(StandardCharsets.UTF_8).size > MAX_JSON_BYTES) {
            throw BackupFormatException("Резервная копия превышает допустимый размер")
        }
        return json
    }

    fun decode(json: String): ExportData {
        if (json.toByteArray(StandardCharsets.UTF_8).size > MAX_JSON_BYTES) {
            throw BackupFormatException("Файл превышает допустимый размер")
        }
        return try {
            parse(json)
        } catch (error: BackupFormatException) {
            throw error
        } catch (_: RuntimeException) {
            throw BackupFormatException("Неверный формат резервной копии")
        }
    }

    private fun parse(json: String): ExportData {
        val rootElement = JsonParser.parseString(json)
        if (!rootElement.isJsonObject) {
            throw BackupFormatException("Корневой элемент должен быть объектом")
        }
        val root = rootElement.asJsonObject
        val version = root.requiredInt("version")
        if (version != BACKUP_FORMAT_VERSION) {
            throw BackupFormatException("Неподдерживаемая версия резервной копии: $version")
        }
        val exportDate = root.requiredLong("exportDate")
        if (exportDate <= 0L) {
            throw BackupFormatException("Некорректная дата резервной копии")
        }

        val tags = root.requiredArray("tags").bounded("tags", MAX_METADATA_ITEMS).mapIndexed { index, element ->
            parseTag(element.requiredObject("tags[$index]"), "tags[$index]")
        }
        ensureUniqueIds(tags.map { it.id }, "тегов")

        val categories = root.requiredArray("categories").bounded("categories", MAX_METADATA_ITEMS).mapIndexed { index, element ->
            parseCategory(element.requiredObject("categories[$index]"), "categories[$index]")
        }
        ensureUniqueIds(categories.map { it.id }, "категорий")

        val tagIds = tags.mapTo(mutableSetOf()) { it.id }
        val categoryIds = categories.mapTo(mutableSetOf()) { it.id }
        val contacts = root.requiredArray("contacts").bounded("contacts", MAX_CONTACTS).mapIndexed { index, element ->
            parseContactExport(
                element.requiredObject("contacts[$index]"),
                "contacts[$index]",
                tagIds,
                categoryIds
            )
        }
        ensureUniqueIds(contacts.map { it.contact.id }, "контактов")

        return ExportData(
            version = version,
            exportDate = exportDate,
            contacts = contacts,
            tags = tags,
            categories = categories
        )
    }

    private fun parseTag(value: JsonObject, path: String): Tag {
        val id = value.requiredPositiveId("id", path)
        val name = value.requiredText("name", path, MAX_SHORT_TEXT, allowBlank = false)
        val color = value.requiredText("color", path, 9, allowBlank = false)
        validateColor(color, "$path.color")
        return Tag(id = id, name = name, color = color)
    }

    private fun parseCategory(value: JsonObject, path: String): Category {
        val id = value.requiredPositiveId("id", path)
        val name = value.requiredText("name", path, MAX_SHORT_TEXT, allowBlank = false)
        val color = value.requiredText("color", path, 9, allowBlank = false)
        validateColor(color, "$path.color")
        return Category(id = id, name = name, color = color)
    }

    private fun parseContactExport(
        value: JsonObject,
        path: String,
        knownTagIds: Set<Long>,
        knownCategoryIds: Set<Long>
    ): ContactExport {
        val contact = parseContact(value.requiredObject("contact", path), "$path.contact")
        if (contact.categoryId != null && contact.categoryId !in knownCategoryIds) {
            throw BackupFormatException("$path.contact.categoryId ссылается на неизвестную категорию")
        }

        val tagIds = value.requiredArray("tagIds").bounded("$path.tagIds", MAX_RELATIONS_PER_CONTACT).mapIndexed { index, element ->
            val tagId = element.requiredLong("$path.tagIds[$index]")
            if (tagId <= 0L || tagId !in knownTagIds) {
                throw BackupFormatException("$path.tagIds[$index] ссылается на неизвестный тег")
            }
            tagId
        }
        if (tagIds.size != tagIds.toSet().size) {
            throw BackupFormatException("$path.tagIds содержит дубликаты")
        }

        val events = value.requiredArray("events").bounded("$path.events", MAX_RELATIONS_PER_CONTACT).mapIndexed { index, element ->
            parseEvent(element.requiredObject("$path.events[$index]"), "$path.events[$index]", contact.id)
        }
        val reminders = value.requiredArray("reminders").bounded("$path.reminders", MAX_RELATIONS_PER_CONTACT).mapIndexed { index, element ->
            parseReminder(element.requiredObject("$path.reminders[$index]"), "$path.reminders[$index]", contact.id)
        }
        val socialNetworks = value.requiredArray("socialNetworks").bounded("$path.socialNetworks", MAX_RELATIONS_PER_CONTACT).mapIndexed { index, element ->
            parseSocialNetwork(element.requiredObject("$path.socialNetworks[$index]"), "$path.socialNetworks[$index]", contact.id)
        }
        val customFields = value.requiredArray("customFields").bounded("$path.customFields", MAX_RELATIONS_PER_CONTACT).mapIndexed { index, element ->
            parseCustomField(element.requiredObject("$path.customFields[$index]"), "$path.customFields[$index]", contact.id)
        }
        val conversations = value.requiredArray("conversations").bounded("$path.conversations", MAX_RELATIONS_PER_CONTACT).mapIndexed { index, element ->
            parseConversation(element.requiredObject("$path.conversations[$index]"), "$path.conversations[$index]", contact.id)
        }

        return ContactExport(
            contact = contact,
            tagIds = tagIds,
            events = events,
            reminders = reminders,
            socialNetworks = socialNetworks,
            customFields = customFields,
            conversations = conversations
        )
    }

    private fun parseContact(value: JsonObject, path: String): Contact {
        val passwordHash = value.requiredText("passwordHash", path, 256, allowBlank = false)
        val passwordSalt = value.requiredText("passwordSalt", path, 256, allowBlank = false)
        validateBase64(passwordHash, 32, "$path.passwordHash")
        validateBase64(passwordSalt, 32, "$path.passwordSalt")
        val createdAt = value.requiredLong("createdAt")
        val updatedAt = value.requiredLong("updatedAt")
        if (createdAt <= 0L || updatedAt < createdAt) {
            throw BackupFormatException("$path содержит некорректные временные метки")
        }
        return Contact(
            id = value.requiredPositiveId("id", path),
            name = value.requiredText("name", path, MAX_SHORT_TEXT, allowBlank = false),
            phone = value.requiredText("phone", path, MAX_SHORT_TEXT),
            email = value.requiredText("email", path, MAX_SHORT_TEXT),
            address = value.requiredText("address", path, MAX_LONG_TEXT),
            workplace = value.requiredText("workplace", path, MAX_SHORT_TEXT),
            position = value.requiredText("position", path, MAX_SHORT_TEXT),
            source = value.requiredText("source", path, MAX_LONG_TEXT),
            helpInfo = value.requiredText("helpInfo", path, MAX_LONG_TEXT),
            birthday = value.optionalLong("birthday"),
            avatarUri = value.optionalText("avatarUri", path, MAX_LONG_TEXT),
            passwordHash = passwordHash,
            passwordSalt = passwordSalt,
            encryptedData = value.requiredText("encryptedData", path, MAX_LONG_TEXT),
            categoryId = value.optionalLong("categoryId"),
            isActive = value.requiredBoolean("isActive"),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun parseEvent(value: JsonObject, path: String, contactId: Long): Event {
        requireContactId(value, path, contactId)
        return Event(
            id = value.requiredPositiveId("id", path),
            contactId = contactId,
            title = value.requiredText("title", path, MAX_SHORT_TEXT, allowBlank = false),
            date = value.requiredLong("date"),
            comment = value.requiredText("comment", path, MAX_LONG_TEXT),
            isRecurring = value.requiredBoolean("isRecurring")
        )
    }

    private fun parseReminder(value: JsonObject, path: String, contactId: Long): Reminder {
        requireContactId(value, path, contactId)
        val createdAt = value.requiredLong("createdAt")
        if (createdAt <= 0L) {
            throw BackupFormatException("$path.createdAt имеет недопустимое значение")
        }
        return Reminder(
            id = value.requiredPositiveId("id", path),
            contactId = contactId,
            title = value.requiredText("title", path, MAX_SHORT_TEXT, allowBlank = false),
            date = value.optionalLong("date"),
            comment = value.requiredText("comment", path, MAX_LONG_TEXT),
            isCompleted = value.requiredBoolean("isCompleted"),
            createdAt = createdAt
        )
    }

    private fun parseSocialNetwork(value: JsonObject, path: String, contactId: Long): SocialNetwork {
        requireContactId(value, path, contactId)
        return SocialNetwork(
            id = value.requiredPositiveId("id", path),
            contactId = contactId,
            type = value.requiredText("type", path, MAX_SHORT_TEXT, allowBlank = false),
            url = value.requiredText("url", path, MAX_LONG_TEXT, allowBlank = false),
            username = value.requiredText("username", path, MAX_SHORT_TEXT)
        )
    }

    private fun parseCustomField(value: JsonObject, path: String, contactId: Long): CustomField {
        requireContactId(value, path, contactId)
        return CustomField(
            id = value.requiredPositiveId("id", path),
            contactId = contactId,
            fieldName = value.requiredText("fieldName", path, MAX_SHORT_TEXT, allowBlank = false),
            fieldValue = value.requiredText("fieldValue", path, MAX_LONG_TEXT),
            isEncrypted = value.requiredBoolean("isEncrypted")
        )
    }

    private fun parseConversation(value: JsonObject, path: String, contactId: Long): Conversation {
        requireContactId(value, path, contactId)
        val createdAt = value.requiredLong("createdAt")
        if (createdAt <= 0L) {
            throw BackupFormatException("$path.createdAt имеет недопустимое значение")
        }
        return Conversation(
            id = value.requiredPositiveId("id", path),
            contactId = contactId,
            date = value.requiredLong("date"),
            topic = value.requiredText("topic", path, MAX_LONG_TEXT, allowBlank = false),
            createdAt = createdAt
        )
    }

    private fun requireContactId(value: JsonObject, path: String, expectedContactId: Long) {
        if (value.requiredLong("contactId") != expectedContactId) {
            throw BackupFormatException("$path.contactId не соответствует контакту")
        }
    }

    private fun validateColor(color: String, path: String) {
        if (!colorPattern.matches(color)) {
            throw BackupFormatException("$path содержит недопустимый цвет")
        }
    }

    private fun validateBase64(value: String, expectedSize: Int, path: String) {
        val decoded = try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            throw BackupFormatException("$path содержит некорректные данные")
        }
        try {
            if (decoded.size != expectedSize) {
                throw BackupFormatException("$path имеет некорректную длину")
            }
        } finally {
            decoded.fill(0)
        }
    }

    private fun ensureUniqueIds(ids: List<Long>, label: String) {
        if (ids.size != ids.toSet().size) {
            throw BackupFormatException("Список $label содержит повторяющиеся идентификаторы")
        }
    }

    private fun JsonObject.requiredPositiveId(name: String, path: String): Long {
        val value = requiredLong(name)
        if (value <= 0L) {
            throw BackupFormatException("$path.$name имеет недопустимое значение")
        }
        return value
    }

    private fun JsonObject.requiredText(
        name: String,
        path: String,
        maxLength: Int,
        allowBlank: Boolean = true
    ): String {
        val element = get(name)
        if (element == null || element.isJsonNull || !element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw BackupFormatException("$path.$name должно быть строкой")
        }
        val value = element.asString
        if (value.length > maxLength || (!allowBlank && value.isBlank())) {
            throw BackupFormatException("$path.$name имеет недопустимое значение")
        }
        return value
    }

    private fun JsonObject.optionalText(name: String, path: String, maxLength: Int): String? {
        val element = get(name) ?: return null
        if (element.isJsonNull) return null
        if (!element.isJsonPrimitive || !element.asJsonPrimitive.isString) {
            throw BackupFormatException("$path.$name должно быть строкой")
        }
        return element.asString.also {
            if (it.length > maxLength) {
                throw BackupFormatException("$path.$name превышает допустимую длину")
            }
        }
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val value = requiredLong(name)
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) {
            throw BackupFormatException("$name выходит за допустимый диапазон")
        }
        return value.toInt()
    }

    private fun JsonObject.requiredLong(name: String): Long {
        val element = get(name)
        return element.requiredLong(name)
    }

    private fun JsonElement?.requiredLong(path: String): Long {
        if (this == null || isJsonNull || !isJsonPrimitive || !asJsonPrimitive.isNumber) {
            throw BackupFormatException("$path должно быть целым числом")
        }
        val raw = asJsonPrimitive.asString
        if (!integerPattern.matches(raw)) {
            throw BackupFormatException("$path должно быть целым числом")
        }
        return raw.toLongOrNull() ?: throw BackupFormatException("$path выходит за допустимый диапазон")
    }

    private fun JsonObject.optionalLong(name: String): Long? {
        val element = get(name) ?: return null
        if (element.isJsonNull) return null
        return element.requiredLong(name)
    }

    private fun JsonObject.requiredBoolean(name: String): Boolean {
        val element = get(name)
        if (element == null || element.isJsonNull || !element.isJsonPrimitive || !element.asJsonPrimitive.isBoolean) {
            throw BackupFormatException("$name должно быть логическим значением")
        }
        return element.asBoolean
    }

    private fun JsonObject.requiredArray(name: String): JsonArray {
        val element = get(name)
        if (element == null || !element.isJsonArray) {
            throw BackupFormatException("$name должно быть массивом")
        }
        return element.asJsonArray
    }

    private fun JsonObject.requiredObject(name: String, path: String): JsonObject {
        return get(name).requiredObject("$path.$name")
    }

    private fun JsonElement?.requiredObject(path: String): JsonObject {
        if (this == null || !isJsonObject) {
            throw BackupFormatException("$path должно быть объектом")
        }
        return asJsonObject
    }

    private fun JsonArray.bounded(path: String, maxSize: Int): JsonArray {
        if (size() > maxSize) {
            throw BackupFormatException("$path содержит слишком много элементов")
        }
        return this
    }
}
