package com.securecontacts.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun rememberDateFormatter(pattern: String): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    return remember(pattern, locale) { DateTimeFormatter.ofPattern(pattern, locale) }
}
