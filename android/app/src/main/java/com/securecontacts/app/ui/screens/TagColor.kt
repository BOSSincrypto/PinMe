package com.securecontacts.app.ui.screens

import androidx.compose.ui.graphics.Color

fun parseTagColor(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFF2196F3))
