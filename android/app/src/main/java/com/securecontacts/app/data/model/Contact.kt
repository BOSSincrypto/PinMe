package com.securecontacts.app.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.securecontacts.app.data.database.Converters
import com.securecontacts.app.security.CryptoManager

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["name"]),
        Index(value = ["isActive", "name"]),
        Index(value = ["birthday"])
    ]
)
@TypeConverters(Converters::class)
data class Contact(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val workplace: String = "",
    val position: String = "",
    val source: String = "",
    val helpInfo: String = "",
    val birthday: Long? = null,
    val avatarUri: String? = null,
    val passwordHash: String = "",
    val passwordSalt: String = "",
    @ColumnInfo(defaultValue = "100000")
    val passwordIterations: Int = CryptoManager.PASSWORD_HASH_ITERATIONS,
    val encryptedData: String = "",
    val categoryId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tags", indices = [Index(value = ["name"])])
data class Tag(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: String = "#2196F3"
)

@Entity(
    tableName = "contact_tags",
    primaryKeys = ["contactId", "tagId"],
    indices = [Index(value = ["tagId"])]
)
data class ContactTag(
    val contactId: Long,
    val tagId: Long
)

@Entity(tableName = "categories", indices = [Index(value = ["name"])])
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val color: String = "#4CAF50"
)

@Entity(tableName = "events", indices = [Index(value = ["contactId", "date"])])
data class Event(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long,
    val title: String,
    val date: Long,
    val comment: String = "",
    val isRecurring: Boolean = false
)

@Entity(tableName = "reminders", indices = [Index(value = ["contactId", "date"])])
data class Reminder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long,
    val title: String,
    val date: Long? = null,
    val comment: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "social_networks", indices = [Index(value = ["contactId"])])
data class SocialNetwork(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long,
    val type: String,
    val url: String,
    val username: String = ""
)

@Entity(tableName = "custom_fields", indices = [Index(value = ["contactId"])])
data class CustomField(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long,
    val fieldName: String,
    val fieldValue: String,
    val isEncrypted: Boolean = false
)

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["contactId", "date"]), Index(value = ["date"])]
)
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long,
    val date: Long,
    val topic: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history", indices = [Index(value = ["timestamp"])])
data class SearchHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ContactWithDetails(
    val contact: Contact,
    val tags: List<Tag> = emptyList(),
    val events: List<Event> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val socialNetworks: List<SocialNetwork> = emptyList(),
    val customFields: List<CustomField> = emptyList(),
    val conversations: List<Conversation> = emptyList(),
    val category: Category? = null
)

data class ContactTagWithTag(
    val contactId: Long,
    val tagId: Long,
    val tagName: String,
    val tagColor: String
)

data class ConversationWithContact(
    val conversation: Conversation,
    val contactName: String
)

data class DateItem(
    val contactId: Long,
    val contactName: String,
    val title: String,
    val date: Long,
    val comment: String,
    val isBirthday: Boolean,
    val daysLeft: Int,
    val age: Int? = null
)
