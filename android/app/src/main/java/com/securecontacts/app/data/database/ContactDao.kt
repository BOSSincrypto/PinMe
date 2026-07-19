package com.securecontacts.app.data.database

import androidx.room.*
import com.securecontacts.app.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    suspend fun getAllContactsSync(): List<Contact>

    @Query("SELECT * FROM contacts WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContactById(id: Long): Contact?

    @Query("SELECT * FROM contacts WHERE id = :id")
    fun getContactByIdFlow(id: Long): Flow<Contact?>

    @Query("""
        SELECT * FROM contacts
        WHERE name LIKE '%' || :query || '%'
        OR phone LIKE '%' || :query || '%'
        OR workplace LIKE '%' || :query || '%'
        OR position LIKE '%' || :query || '%'
        OR source LIKE '%' || :query || '%'
        ORDER BY
            CASE WHEN name LIKE :query || '%' THEN 0 ELSE 1 END,
            name ASC
    """)
    suspend fun searchContactsSync(query: String): List<Contact>

    @Query("""
        SELECT * FROM contacts
        WHERE phone LIKE '%' || :lastDigits
        ORDER BY name ASC
    """)
    fun searchByPhoneLastDigits(lastDigits: String): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE categoryId = :categoryId ORDER BY name ASC")
    fun getContactsByCategory(categoryId: Long): Flow<List<Contact>>

    @Query("SELECT * FROM contacts WHERE birthday IS NOT NULL ORDER BY birthday ASC")
    fun getContactsWithBirthdays(): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long

    @Update
    suspend fun updateContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContactById(id: Long)

    @Query("UPDATE contacts SET categoryId = NULL, updatedAt = :updatedAt WHERE categoryId = :categoryId")
    suspend fun clearCategory(categoryId: Long, updatedAt: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAllTagsSync(): List<Tag>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getTagById(id: Long): Tag?

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN contact_tags ct ON t.id = ct.tagId
        WHERE ct.contactId = :contactId
    """)
    fun getTagsForContact(contactId: Long): Flow<List<Tag>>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN contact_tags ct ON t.id = ct.tagId
        WHERE ct.contactId = :contactId
    """)
    suspend fun getTagsForContactSync(contactId: Long): List<Tag>

    @Query("""
        SELECT ct.contactId AS contactId, t.id AS tagId, t.name AS tagName, t.color AS tagColor
        FROM contact_tags ct
        INNER JOIN tags t ON t.id = ct.tagId
        ORDER BY ct.contactId ASC, t.name ASC
    """)
    fun getAllContactTags(): Flow<List<ContactTagWithTag>>

    @Query("""
        SELECT ct.contactId AS contactId, t.id AS tagId, t.name AS tagName, t.color AS tagColor
        FROM contact_tags ct
        INNER JOIN tags t ON t.id = ct.tagId
        ORDER BY ct.contactId ASC, t.name ASC
    """)
    suspend fun getAllContactTagsSync(): List<ContactTagWithTag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: Tag): Long

    @Update
    suspend fun updateTag(tag: Tag)

    @Delete
    suspend fun deleteTag(tag: Tag)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertContactTag(contactTag: ContactTag)

    @Query("DELETE FROM contact_tags WHERE contactId = :contactId")
    suspend fun deleteContactTags(contactId: Long)

    @Query("DELETE FROM contact_tags WHERE contactId = :contactId AND tagId = :tagId")
    suspend fun deleteContactTag(contactId: Long, tagId: Long)

    @Query("DELETE FROM contact_tags WHERE tagId = :tagId")
    suspend fun deleteContactTagsByTagId(tagId: Long)

    @Query("""
        SELECT DISTINCT c.* FROM contacts c
        INNER JOIN contact_tags ct ON c.id = ct.contactId
        WHERE ct.tagId = :tagId
        ORDER BY c.name ASC
    """)
    fun getContactsByTag(tagId: Long): Flow<List<Contact>>

    @Query("""
        SELECT DISTINCT c.* FROM contacts c
        INNER JOIN contact_tags ct ON c.id = ct.contactId
        INNER JOIN tags t ON ct.tagId = t.id
        WHERE t.name LIKE '%' || :tagName || '%'
        ORDER BY c.name ASC
    """)
    fun searchContactsByTagName(tagName: String): Flow<List<Contact>>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY name ASC")
    suspend fun getAllCategoriesSync(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY date ASC")
    fun getAllEvents(): Flow<List<Event>>

    @Query("SELECT * FROM events ORDER BY contactId ASC, date ASC")
    suspend fun getAllEventsSync(): List<Event>

    @Query("SELECT * FROM events WHERE contactId = :contactId ORDER BY date ASC")
    fun getEventsForContact(contactId: Long): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE contactId = :contactId ORDER BY date ASC")
    suspend fun getEventsForContactSync(contactId: Long): List<Event>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: Long): Event?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: Event): Long

    @Update
    suspend fun updateEvent(event: Event)

    @Delete
    suspend fun deleteEvent(event: Event)

    @Query("DELETE FROM events WHERE contactId = :contactId")
    suspend fun deleteEventsForContact(contactId: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY CASE WHEN date IS NULL THEN 1 ELSE 0 END, date ASC")
    fun getAllActiveReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders ORDER BY CASE WHEN date IS NULL THEN 1 ELSE 0 END, date ASC")
    fun getAllReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders ORDER BY contactId ASC, CASE WHEN date IS NULL THEN 1 ELSE 0 END, date ASC")
    suspend fun getAllRemindersSync(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE contactId = :contactId ORDER BY CASE WHEN date IS NULL THEN 1 ELSE 0 END, date ASC")
    fun getRemindersForContact(contactId: Long): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE contactId = :contactId ORDER BY CASE WHEN date IS NULL THEN 1 ELSE 0 END, date ASC")
    suspend fun getRemindersForContactSync(contactId: Long): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminderById(id: Long): Reminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Update
    suspend fun updateReminder(reminder: Reminder)

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    @Query("DELETE FROM reminders WHERE contactId = :contactId")
    suspend fun deleteRemindersForContact(contactId: Long)
}

@Dao
interface SocialNetworkDao {
    @Query("SELECT * FROM social_networks WHERE contactId = :contactId")
    fun getSocialNetworksForContact(contactId: Long): Flow<List<SocialNetwork>>

    @Query("SELECT * FROM social_networks ORDER BY contactId ASC, id ASC")
    suspend fun getAllSocialNetworksSync(): List<SocialNetwork>

    @Query("SELECT * FROM social_networks WHERE contactId = :contactId")
    suspend fun getSocialNetworksForContactSync(contactId: Long): List<SocialNetwork>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSocialNetwork(socialNetwork: SocialNetwork): Long

    @Update
    suspend fun updateSocialNetwork(socialNetwork: SocialNetwork)

    @Delete
    suspend fun deleteSocialNetwork(socialNetwork: SocialNetwork)

    @Query("DELETE FROM social_networks WHERE contactId = :contactId")
    suspend fun deleteSocialNetworksForContact(contactId: Long)
}

@Dao
interface CustomFieldDao {
    @Query("SELECT * FROM custom_fields WHERE contactId = :contactId")
    fun getCustomFieldsForContact(contactId: Long): Flow<List<CustomField>>

    @Query("SELECT * FROM custom_fields ORDER BY contactId ASC, id ASC")
    suspend fun getAllCustomFieldsSync(): List<CustomField>

    @Query("SELECT * FROM custom_fields WHERE contactId = :contactId")
    suspend fun getCustomFieldsForContactSync(contactId: Long): List<CustomField>

    @Query("""
        SELECT DISTINCT c.* FROM contacts c
        INNER JOIN custom_fields cf ON c.id = cf.contactId
        WHERE cf.fieldValue LIKE '%' || :query || '%'
        ORDER BY c.name ASC
    """)
    fun searchInCustomFields(query: String): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomField(customField: CustomField): Long

    @Update
    suspend fun updateCustomField(customField: CustomField)

    @Delete
    suspend fun deleteCustomField(customField: CustomField)

    @Query("DELETE FROM custom_fields WHERE contactId = :contactId")
    suspend fun deleteCustomFieldsForContact(contactId: Long)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE contactId = :contactId ORDER BY date DESC")
    fun getConversationsForContact(contactId: Long): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE contactId = :contactId ORDER BY date DESC")
    suspend fun getConversationsForContactSync(contactId: Long): List<Conversation>

    @Query("SELECT * FROM conversations ORDER BY date DESC")
    fun getAllConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations ORDER BY contactId ASC, date DESC")
    suspend fun getAllConversationsSync(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): Conversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation): Long

    @Update
    suspend fun updateConversation(conversation: Conversation)

    @Delete
    suspend fun deleteConversation(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE contactId = :contactId")
    suspend fun deleteConversationsForContact(contactId: Long)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getRecentSearches(): Flow<List<SearchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(searchHistory: SearchHistory)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()

    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestamp DESC LIMIT 20)")
    suspend fun trimHistory()
}
