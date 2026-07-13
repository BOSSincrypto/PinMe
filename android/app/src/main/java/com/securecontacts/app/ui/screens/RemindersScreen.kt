package com.securecontacts.app.ui.screens

import com.securecontacts.app.localization.localized

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.securecontacts.app.data.model.Contact
import com.securecontacts.app.data.model.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class ReminderWithContact(
    val reminder: Reminder,
    val contact: Contact?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    reminders: List<ReminderWithContact>,
    onContactClick: (Long) -> Unit,
    onCompleteReminder: (Long) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCompleted by remember { mutableStateOf(false) }

    val filteredReminders = if (showCompleted) {
        reminders
    } else {
        reminders.filter { !it.reminder.isCompleted }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(localized("Напоминания")) },
                actions = {
                    IconButton(onClick = { showCompleted = !showCompleted }) {
                        Icon(
                            if (showCompleted) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showCompleted) localized("Скрыть завершённые") else localized("Показать завершённые")
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (filteredReminders.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (showCompleted) localized("Нет напоминаний") else localized("Нет активных напоминаний"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Group by urgency
                val today = LocalDate.now()
                val overdue = filteredReminders.filter { rwc ->
                    rwc.reminder.date != null && !rwc.reminder.isCompleted &&
                    Instant.ofEpochMilli(rwc.reminder.date).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
                }
                val todayReminders = filteredReminders.filter { rwc ->
                    rwc.reminder.date != null && !rwc.reminder.isCompleted &&
                    Instant.ofEpochMilli(rwc.reminder.date).atZone(ZoneOffset.UTC).toLocalDate().isEqual(today)
                }
                val upcoming = filteredReminders.filter { rwc ->
                    rwc.reminder.date != null && !rwc.reminder.isCompleted &&
                    Instant.ofEpochMilli(rwc.reminder.date).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
                }
                val noDate = filteredReminders.filter { rwc ->
                    rwc.reminder.date == null && !rwc.reminder.isCompleted
                }
                val completed = filteredReminders.filter { it.reminder.isCompleted }

                if (overdue.isNotEmpty()) {
                    item {
                        Text(
                            text = localized("Просрочено"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(overdue) { rwc ->
                        ReminderCard(
                            reminderWithContact = rwc,
                            onClick = { rwc.contact?.let { onContactClick(it.id) } },
                            onComplete = { onCompleteReminder(rwc.reminder.id) },
                            onDelete = { onDeleteReminder(rwc.reminder.id) },
                            isOverdue = true
                        )
                    }
                }

                if (todayReminders.isNotEmpty()) {
                    item {
                        Text(
                            text = localized("Сегодня"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(todayReminders) { rwc ->
                        ReminderCard(
                            reminderWithContact = rwc,
                            onClick = { rwc.contact?.let { onContactClick(it.id) } },
                            onComplete = { onCompleteReminder(rwc.reminder.id) },
                            onDelete = { onDeleteReminder(rwc.reminder.id) },
                            isToday = true
                        )
                    }
                }

                if (upcoming.isNotEmpty()) {
                    item {
                        Text(
                            text = localized("Предстоящие"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(upcoming) { rwc ->
                        ReminderCard(
                            reminderWithContact = rwc,
                            onClick = { rwc.contact?.let { onContactClick(it.id) } },
                            onComplete = { onCompleteReminder(rwc.reminder.id) },
                            onDelete = { onDeleteReminder(rwc.reminder.id) }
                        )
                    }
                }

                if (noDate.isNotEmpty()) {
                    item {
                        Text(
                            text = localized("Без даты"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(noDate) { rwc ->
                        ReminderCard(
                            reminderWithContact = rwc,
                            onClick = { rwc.contact?.let { onContactClick(it.id) } },
                            onComplete = { onCompleteReminder(rwc.reminder.id) },
                            onDelete = { onDeleteReminder(rwc.reminder.id) }
                        )
                    }
                }

                if (showCompleted && completed.isNotEmpty()) {
                    item {
                        Text(
                            text = localized("Завершено"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    items(completed) { rwc ->
                        ReminderCard(
                            reminderWithContact = rwc,
                            onClick = { rwc.contact?.let { onContactClick(it.id) } },
                            onComplete = { onCompleteReminder(rwc.reminder.id) },
                            onDelete = { onDeleteReminder(rwc.reminder.id) },
                            isCompleted = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderCard(
    reminderWithContact: ReminderWithContact,
    onClick: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    isOverdue: Boolean = false,
    isToday: Boolean = false,
    isCompleted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val reminder = reminderWithContact.reminder
    val contact = reminderWithContact.contact
    val dateFormatter = rememberDateFormatter("dd MMM yyyy")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isOverdue -> MaterialTheme.colorScheme.errorContainer
                isToday -> MaterialTheme.colorScheme.primaryContainer
                isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox
            Checkbox(
                checked = isCompleted,
                onCheckedChange = { if (!isCompleted) onComplete() }
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                    color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )

                if (contact != null) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (reminder.date != null) {
                    val reminderDate = Instant.ofEpochMilli(reminder.date)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                    val today = LocalDate.now()
                    val daysUntil = ChronoUnit.DAYS.between(today, reminderDate).toInt()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = when {
                                isOverdue -> MaterialTheme.colorScheme.error
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = reminderDate.format(dateFormatter),
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                isOverdue -> MaterialTheme.colorScheme.error
                                isToday -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        if (!isCompleted && daysUntil != 0) {
                            Text(
                                text = if (daysUntil < 0) {
                                    localized(" (%d дн. назад)", -daysUntil)
                                } else {
                                    localized(" (через %d дн.)", daysUntil)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (reminder.comment.isNotEmpty()) {
                    Text(
                        text = reminder.comment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = localized("Удалить"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
