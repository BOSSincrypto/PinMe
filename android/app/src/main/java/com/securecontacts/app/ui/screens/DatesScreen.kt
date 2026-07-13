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
import androidx.compose.ui.unit.dp
import com.securecontacts.app.data.model.DateItem
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatesScreen(
    dateItems: List<DateItem>,
    onContactClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(localized("Даты")) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        if (dateItems.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.EventBusy,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        localized("Нет предстоящих дат"),
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
                items(dateItems) { dateItem ->
                    DateItemCard(
                        dateItem = dateItem,
                        onClick = { onContactClick(dateItem.contactId) }
                    )
                }
            }
        }
    }
}

@Composable
fun DateItemCard(
    dateItem: DateItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = rememberDateFormatter("dd MMMM yyyy")
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (dateItem.daysLeft == 0) {
                MaterialTheme.colorScheme.primaryContainer
            } else if (dateItem.daysLeft <= 7) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                if (dateItem.isBirthday) Icons.Default.Cake else Icons.Default.Event,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (dateItem.daysLeft == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (dateItem.contactName.isBlank()) localized("Неизвестный") else dateItem.contactName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (dateItem.isBirthday) localized("День рождения") else dateItem.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dateItem.comment.isNotEmpty()) {
                    Text(
                        text = dateItem.comment,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val date = Instant.ofEpochMilli(dateItem.date)
                    .atZone(ZoneOffset.UTC)
                    .toLocalDate()
                Text(
                    text = date.format(dateFormatter),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Days left
            Column(horizontalAlignment = Alignment.End) {
                when {
                    dateItem.daysLeft == 0 -> {
                        Text(
                            text = localized("Сегодня!"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    dateItem.daysLeft == 1 -> {
                        Text(
                            text = localized("Завтра"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    dateItem.daysLeft <= 7 -> {
                        Text(
                            text = localized("%d дн.", dateItem.daysLeft),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    dateItem.daysLeft <= 30 -> {
                        val weeks = dateItem.daysLeft / 7
                        Text(
                            text = if (weeks == 1) localized("1 нед.") else localized("%d нед.", weeks),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = localized("(%d дн.)", dateItem.daysLeft),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        val months = dateItem.daysLeft / 30
                        Text(
                            text = if (months == 1) localized("1 мес.") else localized("%d мес.", months),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = localized("(%d дн.)", dateItem.daysLeft),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Age for birthdays
                if (dateItem.isBirthday && dateItem.age != null) {
                    Text(
                        text = localized("%d лет", dateItem.age),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
