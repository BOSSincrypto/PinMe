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
        assertEquals("Version 1.0.0-alpha.1", localized("Версия %s", "1.0.0-alpha.1"))
    }

    @Test
    fun returnsOriginalTextForRussianLocale() {
        Locale.setDefault(Locale("ru"))

        assertEquals("Контакты", localized("Контакты"))
        assertEquals("Импортировано: 2 контактов", localized("Импортировано: %d контактов", 2))
        assertEquals("Версия 1.0.0-alpha.1", localized("Версия %s", "1.0.0-alpha.1"))
    }

    @Test
    fun returnsRussianTextForRegionalRussianLocale() {
        Locale.setDefault(Locale("ru", "RU"))

        assertEquals("Настройки", localized("Настройки"))
        assertEquals("Ошибка: файл не найден", localized("Ошибка: %s", "файл не найден"))
    }
}
