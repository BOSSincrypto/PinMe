package com.securecontacts.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.securecontacts.app.R

sealed class Screen(val route: String, @StringRes val titleRes: Int, val titleKey: String, val icon: ImageVector) {
    object Contacts : Screen("contacts", R.string.contacts, "Контакты", Icons.Default.Contacts)
    object Dates : Screen("dates", R.string.dates, "Даты", Icons.Default.Event)
    object Reminders : Screen("reminders", R.string.reminders, "Напоминания", Icons.Default.Notifications)
    object Help : Screen("help", R.string.help, "Помощь", Icons.Default.Help)
    object Settings : Screen("settings", R.string.settings, "Настройки", Icons.Default.Settings)
    object ContactDetail : Screen("contact/{contactId}", R.string.contact, "Контакт", Icons.Default.Person) {
        fun createRoute(contactId: Long) = "contact/$contactId"
    }
    object CreateContact : Screen("create_contact", R.string.new_contact, "Новый контакт", Icons.Default.PersonAdd)
    object EditContact : Screen("edit_contact/{contactId}", R.string.edit, "Редактировать", Icons.Default.Edit) {
        fun createRoute(contactId: Long) = "edit_contact/$contactId"
    }
    object Search : Screen("search", R.string.search, "Поиск", Icons.Default.Search)
    object TagManagement : Screen("tags", R.string.tags, "Теги", Icons.Default.Label)
    object CategoryManagement : Screen("categories", R.string.categories, "Категории", Icons.Default.Category)
    object SetupBackupPassword : Screen("setup_backup_password", R.string.password_setup, "Настройка пароля", Icons.Default.Lock)
    object AllConversations : Screen("all_conversations", R.string.conversations, "Разговоры", Icons.Default.Forum)
}

val bottomNavItems = listOf(
    Screen.Contacts,
    Screen.Dates,
    Screen.Reminders,
    Screen.Help,
    Screen.Settings
)
