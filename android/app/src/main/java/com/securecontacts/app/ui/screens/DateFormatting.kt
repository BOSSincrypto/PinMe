package com.securecontacts.app.ui.screens

import com.securecontacts.app.localization.appLocale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.time.format.DateTimeFormatter

@Composable
fun rememberDateFormatter(pattern: String): DateTimeFormatter {
    val locale = appLocale()
    return remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}
