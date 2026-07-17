package com.securecontacts.app.localization

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
class LocalizedTextTest {
    @Before
    fun useEnglishByDefault() {
        setActiveLanguage(AppLanguage.ENGLISH)
    }

    @After
    fun restoreEnglishLanguage() {
        setActiveLanguage(AppLanguage.ENGLISH)
    }

    @Test
    fun returnsEnglishTextForEnglishLanguage() {
        assertEquals("Contacts", localized("Контакты"))
        assertEquals("Imported: 2 contacts", localized("Импортировано: %d контактов", 2))
        assertEquals("Version 1.0.0-alpha.1", localized("Версия %s", "1.0.0-alpha.1"))
    }

    @Test
    fun returnsRussianTextForRussianLanguage() {
        setActiveLanguage(AppLanguage.RUSSIAN)

        assertEquals("Контакты", localized("Контакты"))
        assertEquals("Импортировано: 2 контактов", localized("Импортировано: %d контактов", 2))
        assertEquals("Версия 1.0.0-alpha.1", localized("Версия %s", "1.0.0-alpha.1"))
    }

    @Test
    fun returnsRussianTextForRegionalRussianLocale() {
        setActiveLanguage(AppLanguage.RUSSIAN)

        assertEquals("Настройки", localized("Настройки"))
        assertEquals("Ошибка: файл не найден", localized("Ошибка: %s", "файл не найден"))
    }

    @Test
    fun defaultsToEnglishLanguage() {
        setActiveLanguage(AppLanguage.ENGLISH)

        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("de"))
        assertEquals("App language", localized("Язык приложения"))
        assertEquals("Choose interface language", localized("Выберите язык интерфейса"))
    }
}
