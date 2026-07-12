package com.securecontacts.app.util

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.securecontacts.app.data.model.Contact
import com.securecontacts.app.security.CryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupJsonCodecTest {
    @Test
    fun roundTripPreservesContactPasswordMetadata() {
        val salt = CryptoManager.generateSalt()
        val contact = Contact(
            id = 1,
            name = "Иван",
            passwordHash = CryptoManager.hashPassword("secret", salt, 310000),
            passwordSalt = salt,
            passwordIterations = 310000,
            createdAt = 1700000000000,
            updatedAt = 1700000000000
        )
        val encoded = BackupJsonCodec.encode(
            ExportData(contacts = listOf(ContactExport(contact = contact)))
        )

        val decoded = BackupJsonCodec.decode(encoded)

        assertEquals(310000, decoded.contacts.single().contact.passwordIterations)
        assertEquals(contact.passwordHash, decoded.contacts.single().contact.passwordHash)
    }

    @Test
    fun legacyContactWithoutPasswordFieldsIsAcceptedAsUnprotected() {
        val contact = Contact(
            id = 1,
            name = "Без пароля",
            createdAt = 1700000000000,
            updatedAt = 1700000000000
        )
        val json = BackupJsonCodec.encode(
            ExportData(contacts = listOf(ContactExport(contact = contact)))
        )
        val root = JsonParser.parseString(json).asJsonObject
        val contactObject = root.getAsJsonArray("contacts").single().asJsonObject.getAsJsonObject("contact")
        contactObject.remove("passwordHash")
        contactObject.remove("passwordSalt")
        root.addProperty("version", 1)

        val decoded = BackupJsonCodec.decode(Gson().toJson(root))

        assertEquals("", decoded.contacts.single().contact.passwordHash)
        assertEquals("", decoded.contacts.single().contact.passwordSalt)
    }

    @Test
    fun unsupportedVersionIsRejected() {
        val root = JsonParser.parseString(
            BackupJsonCodec.encode(ExportData())
        ).asJsonObject
        root.addProperty("version", 999)

        assertThrows(BackupFormatException::class.java) {
            BackupJsonCodec.decode(Gson().toJson(root))
        }
    }
}
