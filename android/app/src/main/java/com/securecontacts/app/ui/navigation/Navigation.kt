package com.securecontacts.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Contacts : Screen("contacts", "Контакты", Icons.Default.Contacts)
    object Dates : Screen("dates", "Даты", Icons.Default.Event)
    object Reminders : Screen("reminders", "Напомин.", Icons.Default.Notifications)
    object Help : Screen("help", "Помощь", Icons.Default.Help)
    object Settings : Screen("settings", "Настройки", Icons.Default.Settings)
    object ContactDetail : Screen("contact/{contactId}", "Контакт", Icons.Default.Person) {
        fun createRoute(contactId: Long) = "contact/$contactId"
    }
    object CreateContact : Screen("create_contact", "Новый контакт", Icons.Default.PersonAdd)
    object EditContact : Screen("edit_contact/{contactId}", "Редактировать", Icons.Default.Edit) {
        fun createRoute(contactId: Long) = "edit_contact/$contactId"
    }
    object Search : Screen("search", "Поиск", Icons.Default.Search)
    object TagManagement : Screen("tags", "Теги", Icons.Default.Label)
    object CategoryManagement : Screen("categories", "Категории", Icons.Default.Category)
    object SetupBackupPassword : Screen("setup_backup_password", "Настройка пароля", Icons.Default.Lock)
    object AllConversations : Screen("all_conversations", "Разговоры", Icons.Default.Forum)
}

val bottomNavItems = listOf(
    Screen.Contacts,
    Screen.Dates,
    Screen.Reminders,
    Screen.Help,
    Screen.Settings
)
