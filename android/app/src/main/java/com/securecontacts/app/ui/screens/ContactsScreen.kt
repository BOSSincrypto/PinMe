package com.securecontacts.app.ui.screens

import com.securecontacts.app.localization.localized

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.securecontacts.app.data.model.Contact
import com.securecontacts.app.data.model.Tag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    contacts: List<Contact>,
    allContacts: List<Contact>,
    tags: List<Tag>,
    contactTags: Map<Long, List<Tag>>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onContactClick: (Long) -> Unit,
    onAddContactClick: () -> Unit,
    onSearchClick: () -> Unit,
    onTagFilterClick: (Long?) -> Unit,
    selectedTagId: Long?,
    showActiveOnly: Boolean,
    onShowActiveOnlyChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeCount = allContacts.count(Contact::isActive)
    val inactiveCount = allContacts.size - activeCount
    val displayContacts = if (showActiveOnly) contacts.filter(Contact::isActive) else contacts

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(localized("Контакты")) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, contentDescription = localized("Поиск"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddContactClick,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = localized("Добавить контакт"))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(localized("Поиск контактов...")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = localized("Очистить"))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Tags filter
            if (tags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTagId == null,
                            onClick = { onTagFilterClick(null) },
                            label = { Text(localized("Все")) }
                        )
                    }
                    items(tags) { tag ->
                        val tagColor = remember(tag.color) { parseTagColor(tag.color) }
                        FilterChip(
                            selected = selectedTagId == tag.id,
                            onClick = { onTagFilterClick(tag.id) },
                            label = { Text(tag.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tagColor.copy(alpha = 0.3f)
                            )
                        )
                    }
                }
            }

            // Active/Inactive toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = localized("Только активные"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = showActiveOnly,
                    onCheckedChange = onShowActiveOnlyChange
                )
            }

            // Contacts list
            if (displayContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.PersonOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            localized("Контакты не найдены"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayContacts, key = { it.id }) { contact ->
                        ContactCard(
                            contact = contact,
                            tags = contactTags[contact.id] ?: emptyList(),
                            onClick = { onContactClick(contact.id) },
                            onCallClick = {
                                if (contact.phone.isNotEmpty()) {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${contact.phone}")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            onCopyClick = {
                                if (contact.phone.isNotEmpty()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("phone", contact.phone)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, localized("Номер скопирован"), Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // Contact counts at the bottom
            Divider(modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = localized("Всего: %d | Активных: %d | Неактивных: %d", allContacts.size, activeCount, inactiveCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ContactCard(
    contact: Contact,
    tags: List<Tag>,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val birthdayFormatter = rememberDateFormatter("dd MMM")
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
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
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Contact info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (contact.workplace.isNotEmpty()) {
                    Text(
                        text = contact.workplace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (contact.birthday != null) {
                    val birthdayDate = Instant.ofEpochMilli(contact.birthday)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                    Text(
                        text = localized("ДР: %s", birthdayDate.format(birthdayFormatter)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Tags
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(tags.take(3)) { tag ->
                            val tagColor = remember(tag.color) { parseTagColor(tag.color) }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = tagColor.copy(alpha = 0.3f)
                            ) {
                                Text(
                                    text = tag.name,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tagColor
                                )
                            }
                        }
                        if (tags.size > 3) {
                            item {
                                Text(
                                    text = "+${tags.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Copy button
            if (contact.phone.isNotEmpty()) {
                IconButton(onClick = onCopyClick) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = localized("Копировать номер"),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Call button
            if (contact.phone.isNotEmpty()) {
                IconButton(onClick = onCallClick) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = localized("Позвонить"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Lock icon
            Icon(
                Icons.Default.Lock,
                contentDescription = localized("Защищено"),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
