package com.securecontacts.app.data.repository

import androidx.room.withTransaction
import com.securecontacts.app.data.database.*
import com.securecontacts.app.data.model.*
import com.securecontacts.app.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class ContactRepository(
    private val contactDao: ContactDao,
    private val tagDao: TagDao,
    private val categoryDao: CategoryDao,
    private val eventDao: EventDao,
    private val reminderDao: ReminderDao,
    private val socialNetworkDao: SocialNetworkDao,
    private val customFieldDao: CustomFieldDao,
    private val conversationDao: ConversationDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val database: AppDatabase = AppDatabase.requireInstance()
) {
    suspend fun <T> withTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }

    fun getAllContacts(): Flow<List<Contact>> = contactDao.getAllContacts()

    suspend fun getAllContactsSnapshot(): List<Contact> = contactDao.getAllContactsSync()

    fun getActiveContacts(): Flow<List<Contact>> = contactDao.getActiveContacts()

    suspend fun searchContactsSnapshot(query: String): List<Contact> = contactDao.searchContactsSync(query)

    fun searchByPhoneLastDigits(lastDigits: String): Flow<List<Contact>> =
        contactDao.searchByPhoneLastDigits(lastDigits)

    fun getContactsByCategory(categoryId: Long): Flow<List<Contact>> =
        contactDao.getContactsByCategory(categoryId)

    fun getContactsByTag(tagId: Long): Flow<List<Contact>> = tagDao.getContactsByTag(tagId)

    fun searchContactsByTagName(tagName: String): Flow<List<Contact>> =
        tagDao.searchContactsByTagName(tagName)

    fun searchInCustomFields(query: String): Flow<List<Contact>> =
        customFieldDao.searchInCustomFields(query)

    suspend fun getContactById(id: Long): Contact? = contactDao.getContactById(id)

    fun getContactByIdFlow(id: Long): Flow<Contact?> = contactDao.getContactByIdFlow(id)

    suspend fun getContactWithDetails(contactId: Long): ContactWithDetails? =
        database.withTransaction {
            val contact = contactDao.getContactById(contactId) ?: return@withTransaction null
            ContactWithDetails(
                contact = contact,
                tags = tagDao.getTagsForContactSync(contactId),
                events = eventDao.getEventsForContactSync(contactId),
                reminders = reminderDao.getRemindersForContactSync(contactId),
                socialNetworks = socialNetworkDao.getSocialNetworksForContactSync(contactId),
                customFields = customFieldDao.getCustomFieldsForContactSync(contactId),
                conversations = conversationDao.getConversationsForContactSync(contactId),
                category = contact.categoryId?.let { categoryDao.getCategoryById(it) }
            )
        }

    suspend fun createContact(
        name: String,
        phone: String,
        email: String = "",
        address: String = "",
        workplace: String,
        position: String,
        source: String,
        helpInfo: String = "",
        birthday: Long?,
        avatarUri: String?,
        password: String,
        categoryId: Long?,
        encryptedNotes: String = ""
    ): Long {
        val salt = CryptoManager.generateSalt()
        val passwordHash = CryptoManager.hashPassword(password, salt)

        val contact = Contact(
            name = name,
            phone = phone,
            email = email,
            address = address,
            workplace = workplace,
            position = position,
            source = source,
            helpInfo = helpInfo,
            birthday = birthday,
            avatarUri = avatarUri,
            passwordHash = passwordHash,
            passwordSalt = salt,
            encryptedData = encryptedNotes,
            categoryId = categoryId
        )

        return contactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: Contact) {
        contactDao.updateContact(contact.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteContact(contactId: Long) {
        database.withTransaction {
            tagDao.deleteContactTags(contactId)
            eventDao.deleteEventsForContact(contactId)
            reminderDao.deleteRemindersForContact(contactId)
            socialNetworkDao.deleteSocialNetworksForContact(contactId)
            customFieldDao.deleteCustomFieldsForContact(contactId)
            conversationDao.deleteConversationsForContact(contactId)
            contactDao.deleteContactById(contactId)
        }
    }

    suspend fun toggleContactActive(contactId: Long) {
        database.withTransaction {
            val contact = contactDao.getContactById(contactId) ?: return@withTransaction
            contactDao.updateContact(contact.copy(isActive = !contact.isActive, updatedAt = System.currentTimeMillis()))
        }
    }

    fun verifyContactPassword(contact: Contact, password: String): Boolean {
        if (contact.passwordHash.isBlank() && contact.passwordSalt.isBlank()) {
            return true
        }
        return CryptoManager.verifyPassword(
            password,
            contact.passwordSalt,
            contact.passwordHash,
            contact.passwordIterations
        )
    }

    suspend fun changeContactPassword(contactId: Long, oldPassword: String, newPassword: String): Boolean {
        return database.withTransaction {
            val contact = contactDao.getContactById(contactId) ?: return@withTransaction false
            if (!verifyContactPassword(contact, oldPassword)) return@withTransaction false
            val newSalt = CryptoManager.generateSalt()
            val newHash = CryptoManager.hashPassword(newPassword, newSalt)
            contactDao.updateContact(contact.copy(
                passwordHash = newHash,
                passwordSalt = newSalt,
                passwordIterations = CryptoManager.PASSWORD_HASH_ITERATIONS,
                updatedAt = System.currentTimeMillis()
            ))
            true
        }
    }

    suspend fun updateContactPassword(contactId: Long, newPassword: String): Boolean {
        return database.withTransaction {
            val contact = contactDao.getContactById(contactId) ?: return@withTransaction false
            val newSalt = CryptoManager.generateSalt()
            val newHash = CryptoManager.hashPassword(newPassword, newSalt)
            contactDao.updateContact(contact.copy(
                passwordHash = newHash,
                passwordSalt = newSalt,
                passwordIterations = CryptoManager.PASSWORD_HASH_ITERATIONS,
                updatedAt = System.currentTimeMillis()
            ))
            true
        }
    }

    // Tags
    fun getAllTags(): Flow<List<Tag>> = tagDao.getAllTags()

    suspend fun getAllTagsSnapshot(): List<Tag> = tagDao.getAllTagsSync()

    fun getTagsForContact(contactId: Long): Flow<List<Tag>> = tagDao.getTagsForContact(contactId)

    fun getAllContactTags(): Flow<Map<Long, List<Tag>>> = tagDao.getAllContactTags().map { links ->
        links.groupBy(
            keySelector = ContactTagWithTag::contactId,
            valueTransform = { link ->
                Tag(id = link.tagId, name = link.tagName, color = link.tagColor)
            }
        )
    }

    suspend fun createTag(name: String, color: String): Long =
        tagDao.insertTag(Tag(name = name, color = color))

    suspend fun updateTag(tag: Tag) = tagDao.updateTag(tag)

    suspend fun deleteTag(tag: Tag) {
        database.withTransaction {
            tagDao.deleteContactTagsByTagId(tag.id)
            tagDao.deleteTag(tag)
        }
    }

    suspend fun addTagToContact(contactId: Long, tagId: Long) =
        tagDao.insertContactTag(ContactTag(contactId, tagId))

    suspend fun removeTagFromContact(contactId: Long, tagId: Long) =
        tagDao.deleteContactTag(contactId, tagId)

    suspend fun setContactTags(contactId: Long, tagIds: List<Long>) {
        database.withTransaction {
            tagDao.deleteContactTags(contactId)
            tagIds.forEach { tagId ->
                tagDao.insertContactTag(ContactTag(contactId, tagId))
            }
        }
    }

    // Categories
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun getAllCategoriesSnapshot(): List<Category> = categoryDao.getAllCategoriesSync()

    suspend fun getCategoryById(id: Long): Category? = categoryDao.getCategoryById(id)

    suspend fun createCategory(name: String, color: String): Long =
        categoryDao.insertCategory(Category(name = name, color = color))

    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)

    suspend fun deleteCategory(category: Category) {
        database.withTransaction {
            contactDao.clearCategory(category.id, System.currentTimeMillis())
            categoryDao.deleteCategory(category)
        }
    }

    // Events
    fun getAllEvents(): Flow<List<Event>> = eventDao.getAllEvents()

    fun getEventsForContact(contactId: Long): Flow<List<Event>> = eventDao.getEventsForContact(contactId)

    suspend fun createEvent(contactId: Long, title: String, date: Long, comment: String, isRecurring: Boolean): Long =
        eventDao.insertEvent(Event(contactId = contactId, title = title, date = date, comment = comment, isRecurring = isRecurring))

    suspend fun updateEvent(event: Event) = eventDao.updateEvent(event)

    suspend fun deleteEvent(event: Event) = eventDao.deleteEvent(event)

    // Reminders
    fun getAllActiveReminders(): Flow<List<Reminder>> = reminderDao.getAllActiveReminders()

    fun getAllReminders(): Flow<List<Reminder>> = reminderDao.getAllReminders()

    fun getRemindersForContact(contactId: Long): Flow<List<Reminder>> = reminderDao.getRemindersForContact(contactId)

    suspend fun createReminder(contactId: Long, title: String, date: Long?, comment: String): Long =
        reminderDao.insertReminder(Reminder(contactId = contactId, title = title, date = date, comment = comment))

    suspend fun updateReminder(reminder: Reminder) = reminderDao.updateReminder(reminder)

    suspend fun deleteReminder(reminder: Reminder) = reminderDao.deleteReminder(reminder)

    suspend fun completeReminder(reminderId: Long) {
        database.withTransaction {
            val reminder = reminderDao.getReminderById(reminderId) ?: return@withTransaction
            reminderDao.updateReminder(reminder.copy(isCompleted = true))
        }
    }

    // Social Networks
    fun getSocialNetworksForContact(contactId: Long): Flow<List<SocialNetwork>> =
        socialNetworkDao.getSocialNetworksForContact(contactId)

    suspend fun createSocialNetwork(contactId: Long, type: String, url: String, username: String): Long =
        socialNetworkDao.insertSocialNetwork(SocialNetwork(contactId = contactId, type = type, url = url, username = username))

    suspend fun updateSocialNetwork(socialNetwork: SocialNetwork) = socialNetworkDao.updateSocialNetwork(socialNetwork)

    suspend fun deleteSocialNetwork(socialNetwork: SocialNetwork) = socialNetworkDao.deleteSocialNetwork(socialNetwork)

    suspend fun setSocialNetworks(contactId: Long, socialNetworks: List<SocialNetwork>) {
        database.withTransaction {
            socialNetworkDao.deleteSocialNetworksForContact(contactId)
            socialNetworks.forEach { sn ->
                socialNetworkDao.insertSocialNetwork(sn.copy(id = 0, contactId = contactId))
            }
        }
    }

    // Custom Fields
    fun getCustomFieldsForContact(contactId: Long): Flow<List<CustomField>> =
        customFieldDao.getCustomFieldsForContact(contactId)

    suspend fun createCustomField(contactId: Long, fieldName: String, fieldValue: String, isEncrypted: Boolean): Long =
        customFieldDao.insertCustomField(CustomField(contactId = contactId, fieldName = fieldName, fieldValue = fieldValue, isEncrypted = isEncrypted))

    suspend fun updateCustomField(customField: CustomField) = customFieldDao.updateCustomField(customField)

    suspend fun deleteCustomField(customField: CustomField) = customFieldDao.deleteCustomField(customField)

    suspend fun setCustomFields(contactId: Long, customFields: List<CustomField>) {
        database.withTransaction {
            customFieldDao.deleteCustomFieldsForContact(contactId)
            customFields.forEach { cf ->
                customFieldDao.insertCustomField(cf.copy(id = 0, contactId = contactId))
            }
        }
    }

    // Conversations
    fun getAllConversations(): Flow<List<Conversation>> = conversationDao.getAllConversations()

    fun getConversationsForContact(contactId: Long): Flow<List<Conversation>> =
        conversationDao.getConversationsForContact(contactId)

    suspend fun createConversation(contactId: Long, date: Long, topic: String): Long =
        conversationDao.insertConversation(Conversation(contactId = contactId, date = date, topic = topic))

    suspend fun updateConversation(conversation: Conversation) = conversationDao.updateConversation(conversation)

    suspend fun deleteConversation(conversation: Conversation) = conversationDao.deleteConversation(conversation)

    // Search History
    fun getRecentSearches(): Flow<List<SearchHistory>> = searchHistoryDao.getRecentSearches()

    suspend fun addSearchHistory(query: String) {
        database.withTransaction {
            searchHistoryDao.insertSearch(SearchHistory(query = query))
            searchHistoryDao.trimHistory()
        }
    }

    suspend fun clearSearchHistory() = searchHistoryDao.clearHistory()

    suspend fun restoreContact(
        contact: Contact,
        categoryId: Long?,
        tagIds: List<Long>,
        events: List<Event>,
        reminders: List<Reminder>,
        socialNetworks: List<SocialNetwork>,
        customFields: List<CustomField>,
        conversations: List<Conversation>
    ): Long = database.withTransaction {
        val existing = contactDao.getContactById(contact.id)
        val replaceExisting = existing != null &&
            existing.name.trim().equals(contact.name.trim(), ignoreCase = true) &&
            existing.phone.trim() == contact.phone.trim()
        val contactId = if (replaceExisting) {
            tagDao.deleteContactTags(contact.id)
            eventDao.deleteEventsForContact(contact.id)
            reminderDao.deleteRemindersForContact(contact.id)
            socialNetworkDao.deleteSocialNetworksForContact(contact.id)
            customFieldDao.deleteCustomFieldsForContact(contact.id)
            conversationDao.deleteConversationsForContact(contact.id)
            contactDao.updateContact(contact.copy(id = contact.id, categoryId = categoryId))
            contact.id
        } else {
            contactDao.insertContact(contact.copy(id = 0, categoryId = categoryId))
        }
        tagIds.forEach { tagId ->
            tagDao.insertContactTag(ContactTag(contactId, tagId))
        }
        events.forEach { event ->
            eventDao.insertEvent(event.copy(id = 0, contactId = contactId))
        }
        reminders.forEach { reminder ->
            reminderDao.insertReminder(reminder.copy(id = 0, contactId = contactId))
        }
        socialNetworks.forEach { socialNetwork ->
            socialNetworkDao.insertSocialNetwork(socialNetwork.copy(id = 0, contactId = contactId))
        }
        customFields.forEach { customField ->
            customFieldDao.insertCustomField(customField.copy(id = 0, contactId = contactId))
        }
        conversations.forEach { conversation ->
            conversationDao.insertConversation(conversation.copy(id = 0, contactId = contactId))
        }
        contactId
    }

    // Date Items (Birthdays + Events)
    fun getDateItems(): Flow<List<DateItem>> {
        val today = LocalDate.now()

        return combine(
            contactDao.getContactsWithBirthdays(),
            eventDao.getAllEvents(),
            contactDao.getAllContacts()
        ) { contactsWithBirthdays, events, allContacts ->
            val dateItems = mutableListOf<DateItem>()
            val contactsById = allContacts.associateBy(Contact::id)

            // Add birthdays
            contactsWithBirthdays.forEach { contact ->
                contact.birthday?.let { birthdayMillis ->
                    val birthdayDate = Instant.ofEpochMilli(birthdayMillis)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                    val thisYearBirthday = birthdayDate.withYear(today.year)
                    val nextBirthday = if (thisYearBirthday.isBefore(today)) {
                        thisYearBirthday.plusYears(1)
                    } else {
                        thisYearBirthday
                    }
                    val daysLeft = ChronoUnit.DAYS.between(today, nextBirthday).toInt()
                    val age = nextBirthday.year - birthdayDate.year

                    dateItems.add(DateItem(
                        contactId = contact.id,
                        contactName = contact.name,
                        title = "",
                        date = nextBirthday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                        comment = "",
                        isBirthday = true,
                        daysLeft = daysLeft,
                        age = age
                    ))
                }
            }

            // Add events
            events.forEach { event ->
                val contact = contactsById[event.contactId]
                val eventDate = Instant.ofEpochMilli(event.date)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                val daysLeft = ChronoUnit.DAYS.between(today, eventDate).toInt()

                if (event.isRecurring) {
                    val thisYearEvent = eventDate.withYear(today.year)
                    val nextEvent = if (thisYearEvent.isBefore(today)) {
                        thisYearEvent.plusYears(1)
                    } else {
                        thisYearEvent
                    }
                    val recurringDaysLeft = ChronoUnit.DAYS.between(today, nextEvent).toInt()

                    dateItems.add(DateItem(
                        contactId = event.contactId,
                        contactName = contact?.name.orEmpty(),
                        title = event.title,
                        date = nextEvent.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                        comment = event.comment,
                        isBirthday = false,
                        daysLeft = recurringDaysLeft
                    ))
                } else if (daysLeft >= 0) {
                    dateItems.add(DateItem(
                        contactId = event.contactId,
                        contactName = contact?.name.orEmpty(),
                        title = event.title,
                        date = event.date,
                        comment = event.comment,
                        isBirthday = false,
                        daysLeft = daysLeft
                    ))
                }
            }

            dateItems.sortedBy { it.daysLeft }
        }
    }
}
