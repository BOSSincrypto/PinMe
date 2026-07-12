package com.securecontacts.app.ui.screens

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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.securecontacts.app.data.model.Contact
import com.securecontacts.app.data.model.Tag
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    contacts: List<Contact>,
    tags: List<Tag>,
    contactTags: Map<Long, List<Tag>>,
    isUnlocked: Boolean,
    onUnlockRequest: suspend (String) -> Boolean,
    onSetPassword: suspend (String) -> Boolean,
    hasPassword: Boolean,
    onContactClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(!isUnlocked) }
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var isVerifyingPassword by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Filter contacts that have helpInfo and match search query
    val filteredContacts = contacts.filter { contact ->
        contact.helpInfo.isNotEmpty() &&
            (searchQuery.isEmpty() || contact.helpInfo.contains(searchQuery, ignoreCase = true))
    }

    // Show password dialog if not unlocked
    if (showPasswordDialog && !isUnlocked) {
        if (!hasPassword) {
            // First time - set password
            SetHelpPasswordDialog(
                onDismiss = { /* Cannot dismiss */ },
                onConfirm = { password ->
                    scope.launch {
                        if (onSetPassword(password)) {
                            showPasswordDialog = false
                        }
                    }
                }
            )
        } else {
            // Enter password
            EnterHelpPasswordDialog(
                isError = passwordError,
                isLoading = isVerifyingPassword,
                onDismiss = { /* Cannot dismiss */ },
                onConfirm = { password ->
                    if (!isVerifyingPassword) {
                        scope.launch {
                            isVerifyingPassword = true
                            val success = onUnlockRequest(password)
                            isVerifyingPassword = false
                            if (success) {
                                showPasswordDialog = false
                                passwordError = false
                            } else {
                                passwordError = true
                            }
                        }
                    }
                }
            )
        }
    }

    if (!isUnlocked) {
        // Show locked screen
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Вкладка защищена паролем",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Введите пароль для доступа",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { showPasswordDialog = true }) {
                    Icon(Icons.Default.LockOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Разблокировать")
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Помощь") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar - searches only by helpInfo field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Поиск по полю 'Чем может помочь'...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Contacts list
            if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Help,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isEmpty())
                                "Нет контактов с информацией о помощи"
                            else
                                "Контакты не найдены",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredContacts, key = { it.id }) { contact ->
                        HelpContactCard(
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
                                    Toast.makeText(context, "Номер скопирован", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HelpContactCard(
    contact: Contact,
    tags: List<Tag>,
    onClick: () -> Unit,
    onCallClick: () -> Unit,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                            contentDescription = "Аватар",
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

                    // Tags
                    if (tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(tags.take(3)) { tag ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(android.graphics.Color.parseColor(tag.color)).copy(alpha = 0.3f)
                                ) {
                                    Text(
                                        text = tag.name,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(android.graphics.Color.parseColor(tag.color))
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
                            contentDescription = "Копировать номер",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Call button
                if (contact.phone.isNotEmpty()) {
                    IconButton(onClick = onCallClick) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "Позвонить",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Help info section
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Help,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Чем может помочь:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = contact.helpInfo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetHelpPasswordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Установить пароль") },
        text = {
            Column {
                Text(
                    text = "Установите пароль длиной не менее 8 символов для защиты вкладки 'Помощь'",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        showError = false
                    },
                    label = { Text("Пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        showError = false
                    },
                    label = { Text("Подтвердите пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Пароли не совпадают") }
                    } else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (password.length >= 8 && password == confirmPassword) {
                        onConfirm(password)
                    } else {
                        showError = true
                    }
                },
                enabled = password.length >= 8 && confirmPassword.isNotEmpty()
            ) {
                Text("Установить")
            }
        },
        dismissButton = null
    )
}

@Composable
fun EnterHelpPasswordDialog(
    isError: Boolean,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Введите пароль") },
        text = {
            Column {
                Text(
                    text = "Введите пароль для доступа к вкладке 'Помощь'",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Неверный пароль") }
                    } else null
                )
                if (isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty() && !isLoading
            ) {
                Text("Войти")
            }
        },
        dismissButton = null
    )
}
