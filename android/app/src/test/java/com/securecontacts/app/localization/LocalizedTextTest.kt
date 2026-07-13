package com.securecontacts.app.localization

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import java.util.Locale

class LocalizedTextTest {
    private lateinit var originalLocale: Locale

    @Before
    fun saveLocale() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun returnsEnglishTextForEnglishLocale() {
        Locale.setDefault(Locale.US)

        assertEquals("Contacts", localized("Контакты"))
        assertEquals("Imported: 2 contacts", localized("Импортировано: %d контактов", 2))
    }

    @Test
    fun returnsOriginalTextForRussianLocale() {
        Locale.setDefault(Locale("ru"))

        assertEquals("Контакты", localized("Контакты"))
        assertEquals("Импортировано: 2 контактов", localized("Импортировано: %d контактов", 2))
    }
}
