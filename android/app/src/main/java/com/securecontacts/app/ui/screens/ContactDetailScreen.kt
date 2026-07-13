package com.securecontacts.app.ui.screens

import com.securecontacts.app.localization.localized

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.securecontacts.app.data.model.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    contactWithDetails: ContactWithDetails?,
    isUnlocked: Boolean,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onToggleActive: () -> Unit,
    onAddConversation: (Long, String) -> Unit,
    onDeleteConversation: (Conversation) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddConversationDialog by remember { mutableStateOf(false) }

    if (contactWithDetails == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val contact = contactWithDetails.contact

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contact.name) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = localized("Назад"))
                    }
                },
                actions = {
                    if (isUnlocked) {
                        IconButton(onClick = onEditClick) {
                            Icon(Icons.Default.Edit, contentDescription = localized("Редактировать"))
                        }
                        IconButton(onClick = onDeleteClick) {
                            Icon(Icons.Default.Delete, contentDescription = localized("Удалить"))
                        }
                    } else {
                        IconButton(onClick = onUnlockClick) {
                            Icon(Icons.Default.LockOpen, contentDescription = localized("Разблокировать"))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar and basic info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            if (contact.avatarUri != null) {
                                AsyncImage(
                                    model = contact.avatarUri,
                                    contentDescription = localized("Аватар"),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = contact.name.firstOrNull()?.uppercase() ?: "?",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = contact.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (contact.workplace.isNotEmpty()) {
                            Text(
                                text = contact.workplace,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (contact.position.isNotEmpty()) {
                            Text(
                                text = contact.position,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Tags
                        if (contactWithDetails.tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(contactWithDetails.tags) { tag ->
                                    val tagColor = remember(tag.color) { parseTagColor(tag.color) }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = tagColor.copy(alpha = 0.3f)
                                    ) {
                                        Text(
                                            text = tag.name,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = tagColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isUnlocked) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (contact.isActive) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                                    contentDescription = null,
                                    tint = if (contact.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = localized("Статус контакта"),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = if (contact.isActive) localized("Активный (общаюсь)") else localized("Неактивный (не общаюсь)"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (contact.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = contact.isActive,
                                onCheckedChange = { onToggleActive() }
                            )
                        }
                    }
                }
            }

            // Action buttons (Share, WhatsApp, Telegram)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Share button
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                val shareText = buildString {
                                    append("${contact.name}\n")
                                    if (contact.phone.isNotEmpty()) append("Тел: ${contact.phone}\n")
                                    if (contact.email.isNotEmpty()) append("Эл. почта: ${contact.email}\n")
                                    if (contact.workplace.isNotEmpty()) append("Работа: ${contact.workplace}\n")
                                    if (contact.position.isNotEmpty()) append("Должность: ${contact.position}\n")
                                    if (contact.address.isNotEmpty()) append("Адрес: ${contact.address}\n")
                                }
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(intent, localized("Поделиться контактом")))
                            }
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = localized("Поделиться"),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = localized("Поделиться"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // WhatsApp button
                        if (contact.phone.isNotEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    val phoneNumber = contact.phone.replace(Regex("[^0-9+]"), "")
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://wa.me/$phoneNumber")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // WhatsApp not installed
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = "WhatsApp",
                                    tint = Color(0xFF25D366),
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "WhatsApp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Telegram button
                        if (contact.phone.isNotEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    val phoneNumber = contact.phone.replace(Regex("[^0-9+]"), "")
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://t.me/$phoneNumber")
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Telegram not installed
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Send,
                                    contentDescription = "Telegram",
                                    tint = Color(0xFF0088CC),
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Telegram",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Email button
                        if (contact.email.isNotEmpty()) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:${contact.email}")
                                    }
                                    context.startActivity(Intent.createChooser(intent, localized("Отправить email")))
                                }
                            ) {
                                Icon(
                                    Icons.Default.Email,
                                    contentDescription = localized("Электронная почта"),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = localized("Эл. почта"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Phone
            if (contact.phone.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${contact.phone}")
                                }
                                context.startActivity(intent)
                            },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = localized("Телефон"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = contact.phone,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // Email
            if (contact.email.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:${contact.email}")
                                }
                                context.startActivity(Intent.createChooser(intent, localized("Отправить email")))
                            },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = localized("Электронная почта"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = contact.email,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // Address
            if (contact.address.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = localized("Адрес"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = contact.address,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // Help Info (only if unlocked)
            if (isUnlocked && contact.helpInfo.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Help,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = localized("Чем может помочь"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = contact.helpInfo,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // Birthday
            if (contact.birthday != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Cake,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = localized("День рождения"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val birthdayDate = Instant.ofEpochMilli(contact.birthday)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                Text(
                                    text = birthdayDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy")),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // Source (only if unlocked)
            if (isUnlocked && contact.source.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PersonSearch,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = localized("Источник"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = contact.source,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // Category
            if (contactWithDetails.category != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Category,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = localized("Категория"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = contactWithDetails.category.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }

            // Social Networks (only if unlocked)
            if (isUnlocked && contactWithDetails.socialNetworks.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = localized("Социальные сети"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            contactWithDetails.socialNetworks.forEach { sn ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val url = sn.url.trim()
                                            if (url.isNotEmpty()) {
                                                val parsedUri = Uri.parse(url)
                                                val targetUri = if (parsedUri.scheme.isNullOrBlank()) {
                                                    Uri.parse("https://$url")
                                                } else {
                                                    parsedUri
                                                }
                                                val scheme = targetUri.scheme?.lowercase()
                                                if ((scheme == "http" || scheme == "https") && !targetUri.host.isNullOrBlank()) {
                                                    val intent = Intent(Intent.ACTION_VIEW, targetUri)
                                                    if (intent.resolveActivity(context.packageManager) != null) {
                                                        runCatching { context.startActivity(intent) }
                                                    }
                                                }
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sn.type,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(100.dp)
                                    )
                                    Text(
                                        text = if (sn.username.isNotEmpty()) sn.username else sn.url,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Events (only if unlocked)
            if (isUnlocked && contactWithDetails.events.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = localized("События"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            contactWithDetails.events.forEach { event ->
                                val eventDate = Instant.ofEpochMilli(event.date)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = event.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = eventDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (event.comment.isNotEmpty()) {
                                            Text(
                                                text = event.comment,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (event.isRecurring) {
                                        Icon(
                                            Icons.Default.Repeat,
                                            contentDescription = localized("Повторяется"),
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Reminders (only if unlocked)
            if (isUnlocked && contactWithDetails.reminders.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = localized("Напоминания"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            contactWithDetails.reminders.forEach { reminder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = reminder.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (reminder.date != null) {
                                            val reminderDate = Instant.ofEpochMilli(reminder.date)
                                                .atZone(ZoneOffset.UTC)
                                                .toLocalDate()
                                            Text(
                                                text = reminderDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (reminder.comment.isNotEmpty()) {
                                            Text(
                                                text = reminder.comment,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (reminder.isCompleted) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = localized("Завершено"),
                                            modifier = Modifier.size(20.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Custom Fields (only if unlocked)
            if (isUnlocked && contactWithDetails.customFields.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.List,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = localized("Дополнительная информация"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            contactWithDetails.customFields.forEach { field ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = field.fieldName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(120.dp)
                                    )
                                    Text(
                                        text = field.fieldValue,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Conversations section (only if unlocked)
            if (isUnlocked) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Forum,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = localized("О чём разговаривали"),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                IconButton(onClick = { showAddConversationDialog = true }) {
                                    Icon(Icons.Default.Add, contentDescription = localized("Добавить запись"))
                                }
                            }

                            if (contactWithDetails.conversations.isEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = localized("Нет записей о разговорах"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Spacer(modifier = Modifier.height(12.dp))
                                contactWithDetails.conversations.forEach { conversation ->
                                    val convDate = Instant.ofEpochMilli(conversation.date)
                                        .atZone(ZoneOffset.UTC)
                                        .toLocalDate()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = convDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = conversation.topic,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        IconButton(onClick = { onDeleteConversation(conversation) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = localized("Удалить"),
                                                modifier = Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                    Divider()
                                }
                            }
                        }
                    }
                }
            }

            // Locked content message
            if (!isUnlocked) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = localized("Защищённое содержимое"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = localized("Введите пароль или используйте биометрию для просмотра"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = onUnlockClick) {
                                Icon(Icons.Default.LockOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(localized("Разблокировать"))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddConversationDialog) {
        AddConversationDialog(
            onDismiss = { showAddConversationDialog = false },
            onConfirm = { date, topic ->
                onAddConversation(date, topic)
                showAddConversationDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddConversationDialog(
    onDismiss: () -> Unit,
    onConfirm: (date: Long, topic: String) -> Unit
) {
    var topic by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis()
    )
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localized("Новая запись о разговоре")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = Instant.ofEpochMilli(selectedDate)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                        .format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    onValueChange = {},
                    label = { Text(localized("Дата")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false,
                    leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) }
                )

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text(localized("О чём разговаривали")) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 5,
                    leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedDate, topic) },
                enabled = topic.isNotBlank()
            ) {
                Text(localized("Сохранить"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localized("Отмена"))
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        selectedDate = it
                    }
                    showDatePicker = false
                }) {
                    Text(localized("ОК"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(localized("Отмена"))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
