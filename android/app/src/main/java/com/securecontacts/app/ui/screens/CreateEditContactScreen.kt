package com.securecontacts.app.ui.screens

import com.securecontacts.app.localization.appLocale
import com.securecontacts.app.localization.localized

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.securecontacts.app.data.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class SocialNetworkInput(
    val id: Long = 0,
    val type: String = "",
    val url: String = "",
    val username: String = ""
)

data class EventInput(
    val id: Long = 0,
    val title: String = "",
    val date: Long = System.currentTimeMillis(),
    val comment: String = "",
    val isRecurring: Boolean = false
)

data class ReminderInput(
    val id: Long = 0,
    val title: String = "",
    val date: Long? = null,
    val comment: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class CustomFieldInput(
    val id: Long = 0,
    val fieldName: String = "",
    val fieldValue: String = "",
    val isEncrypted: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditContactScreen(
    isEditMode: Boolean,
    existingContact: ContactWithDetails?,
    allTags: List<Tag>,
    allCategories: List<Category>,
    onSaveClick: (
        name: String,
        phone: String,
        email: String,
        address: String,
        workplace: String,
        position: String,
        source: String,
        helpInfo: String,
        birthday: Long?,
        avatarUri: String?,
        password: String,
        categoryId: Long?,
        selectedTagIds: List<Long>,
        socialNetworks: List<SocialNetworkInput>,
        events: List<EventInput>,
        reminders: List<ReminderInput>,
        customFields: List<CustomFieldInput>
    ) -> Unit,
    onBackClick: () -> Unit,
    onCreateTagClick: () -> Unit,
    onCreateCategoryClick: () -> Unit,
    onActivityResultFlowChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var workplace by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var helpInfo by remember { mutableStateOf("") }
    var birthday by remember(existingContact?.contact?.id) {
        mutableStateOf(existingContact?.contact?.birthday)
    }
    var avatarUri by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedCategoryId by remember { mutableStateOf<Long?>(null) }
    var selectedTagIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    var socialNetworks by remember { mutableStateOf<List<SocialNetworkInput>>(emptyList()) }
    var events by remember { mutableStateOf<List<EventInput>>(emptyList()) }
    var reminders by remember { mutableStateOf<List<ReminderInput>>(emptyList()) }
    var customFields by remember { mutableStateOf<List<CustomFieldInput>>(emptyList()) }

    var isInitialized by remember { mutableStateOf(false) }
    val passwordTooShort = password.isNotEmpty() && password.length < 8
    val passwordMismatch = confirmPassword.isNotEmpty() && password != confirmPassword

    LaunchedEffect(existingContact) {
        if (existingContact != null && !isInitialized) {
            name = existingContact.contact.name
            phone = existingContact.contact.phone
            email = existingContact.contact.email
            address = existingContact.contact.address
            workplace = existingContact.contact.workplace
            position = existingContact.contact.position
            source = existingContact.contact.source
            helpInfo = existingContact.contact.helpInfo
            birthday = existingContact.contact.birthday
            avatarUri = existingContact.contact.avatarUri
            selectedCategoryId = existingContact.contact.categoryId
            selectedTagIds = existingContact.tags.map { it.id }
            socialNetworks = existingContact.socialNetworks.map {
                SocialNetworkInput(it.id, it.type, it.url, it.username)
            }
            events = existingContact.events.map {
                EventInput(it.id, it.title, it.date, it.comment, it.isRecurring)
            }
            reminders = existingContact.reminders.map {
                ReminderInput(it.id, it.title, it.date, it.comment, it.isCompleted, it.createdAt)
            }
            customFields = existingContact.customFields.map {
                CustomFieldInput(it.id, it.fieldName, it.fieldValue, it.isEncrypted)
            }
            isInitialized = true
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showAddSocialDialog by remember { mutableStateOf(false) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var showAddFieldDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        onActivityResultFlowChange(false)
        uri?.let { avatarUri = it.toString() }
    }

    val datePickerState = key(existingContact?.contact?.id) {
        rememberDatePickerState(
            initialSelectedDateMillis = birthday ?: System.currentTimeMillis()
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) localized("Редактировать контакт") else localized("Новый контакт")) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Close, contentDescription = localized("Отмена"))
                    }
                },
                actions = {
                    val passwordValid = if (isEditMode) {
                        password.isEmpty() || (!passwordTooShort && password == confirmPassword)
                    } else {
                        !passwordTooShort && password.isNotBlank() && password == confirmPassword
                    }
                    TextButton(
                        onClick = {
                            if (name.isNotBlank() && passwordValid) {
                                onSaveClick(
                                    name, phone, email, address, workplace, position, source, helpInfo,
                                    birthday, avatarUri, password, selectedCategoryId,
                                    selectedTagIds, socialNetworks, events, reminders, customFields
                                )
                            }
                        },
                        enabled = name.isNotBlank() && passwordValid
                    ) {
                        Text(localized("Сохранить"), fontWeight = FontWeight.Bold)
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
            // Avatar
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                onActivityResultFlowChange(true)
                                runCatching { imagePickerLauncher.launch("image/*") }
                                    .onFailure { onActivityResultFlowChange(false) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUri != null) {
                            AsyncImage(
                                model = avatarUri,
                                contentDescription = localized("Аватар"),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = localized("Добавить фото"),
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // Basic Info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = localized("Основная информация"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(localized("Имя *")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text(localized("Телефон")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(localized("Электронная почта")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text(localized("Адрес")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3,
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = workplace,
                            onValueChange = { workplace = it },
                            label = { Text(localized("Место работы")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = position,
                            onValueChange = { position = it },
                            label = { Text(localized("Должность")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Work, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = source,
                            onValueChange = { source = it },
                            label = { Text(localized("Источник (Как познакомились?)")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null) }
                        )

                        OutlinedTextField(
                            value = helpInfo,
                            onValueChange = { helpInfo = it },
                            label = { Text(localized("Чем может помочь")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false,
                            maxLines = 3,
                            leadingIcon = { Icon(Icons.Default.Help, contentDescription = null) }
                        )

                        // Birthday
                        OutlinedTextField(
                            value = birthday?.let {
                                Instant.ofEpochMilli(it)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                    .format(DateTimeFormatter.ofPattern("dd MMM yyyy", appLocale()))
                            } ?: "",
                            onValueChange = {},
                            label = { Text(localized("День рождения")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDatePicker = true },
                            enabled = false,
                            leadingIcon = { Icon(Icons.Default.Cake, contentDescription = null) },
                            trailingIcon = {
                                if (birthday != null) {
                                    IconButton(onClick = { birthday = null }) {
                                        Icon(Icons.Default.Clear, contentDescription = localized("Очистить"))
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Password section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isEditMode) localized("Изменить пароль") else localized("Безопасность"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        if (isEditMode) {
                            Text(
                                text = localized("Оставьте пустым, чтобы сохранить текущий пароль"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(if (isEditMode) localized("Новый пароль") else localized("Пароль *")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            isError = passwordTooShort,
                            supportingText = if (passwordTooShort) {
                                { Text(localized("Пароль должен содержать минимум 8 символов")) }
                            } else null,
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = localized("Показать или скрыть пароль")
                                    )
                                }
                            }
                        )

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text(if (isEditMode) localized("Подтвердите новый пароль") else localized("Подтвердите пароль *")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            isError = passwordMismatch,
                            supportingText = if (passwordMismatch) {
                                { Text(localized("Пароли не совпадают")) }
                            } else null
                        )
                    }
                }
            }

            // Category
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localized("Категория"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onCreateCategoryClick) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(localized("Новая"))
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedCategoryId == null,
                                    onClick = { selectedCategoryId = null },
                                    label = { Text(localized("Нет")) }
                                )
                            }
                            items(allCategories) { category ->
                                val categoryColor = remember(category.color) { parseTagColor(category.color) }
                                FilterChip(
                                    selected = selectedCategoryId == category.id,
                                    onClick = { selectedCategoryId = category.id },
                                    label = { Text(category.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = categoryColor.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Tags
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localized("Теги"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onCreateTagClick) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(localized("Новый"))
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(allTags) { tag ->
                                val tagColor = remember(tag.color) { parseTagColor(tag.color) }
                                FilterChip(
                                    selected = selectedTagIds.contains(tag.id),
                                    onClick = {
                                        selectedTagIds = if (selectedTagIds.contains(tag.id)) {
                                            selectedTagIds - tag.id
                                        } else {
                                            selectedTagIds + tag.id
                                        }
                                    },
                                    label = { Text(tag.name) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tagColor.copy(alpha = 0.3f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Social Networks
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localized("Социальные сети"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showAddSocialDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = localized("Добавить соцсеть"))
                            }
                        }

                        socialNetworks.forEachIndexed { index, sn ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sn.type,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (sn.username.isNotEmpty()) sn.username else sn.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = {
                                    socialNetworks = socialNetworks.toMutableList().also { it.removeAt(index) }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = localized("Удалить"))
                                }
                            }
                        }
                    }
                }
            }

            // Events
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localized("События"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showAddEventDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = localized("Добавить событие"))
                            }
                        }

                        events.forEachIndexed { index, event ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = event.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val eventDate = Instant.ofEpochMilli(event.date)
                                        .atZone(ZoneOffset.UTC)
                                        .toLocalDate()
                                    Text(
                                        text = eventDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", appLocale())),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    events = events.toMutableList().also { it.removeAt(index) }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = localized("Удалить"))
                                }
                            }
                        }
                    }
                }
            }

            // Reminders
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localized("Напоминания"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showAddReminderDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = localized("Добавить напоминание"))
                            }
                        }

                        reminders.forEachIndexed { index, reminder ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        text = reminderDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy", appLocale())),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    reminders = reminders.toMutableList().also { it.removeAt(index) }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = localized("Удалить"))
                                }
                            }
                        }
                    }
                }
            }

            // Custom Fields
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = localized("Пользовательские поля"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showAddFieldDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = localized("Добавить поле"))
                            }
                        }

                        customFields.forEachIndexed { index, field ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = field.fieldName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = field.fieldValue,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    customFields = customFields.toMutableList().also { it.removeAt(index) }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = localized("Удалить"))
                                }
                            }
                        }
                    }
                }
            }

            // Spacer at bottom
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    birthday = datePickerState.selectedDateMillis
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

    // Add Social Network Dialog
    if (showAddSocialDialog) {
        AddSocialNetworkDialog(
            onDismiss = { showAddSocialDialog = false },
            onAdd = { type, url, username ->
                socialNetworks = socialNetworks + SocialNetworkInput(type = type, url = url, username = username)
                showAddSocialDialog = false
            }
        )
    }

    // Add Event Dialog
    if (showAddEventDialog) {
        AddEventDialog(
            onDismiss = { showAddEventDialog = false },
            onAdd = { title, date, comment, isRecurring ->
                events = events + EventInput(title = title, date = date, comment = comment, isRecurring = isRecurring)
                showAddEventDialog = false
            }
        )
    }

    // Add Reminder Dialog
    if (showAddReminderDialog) {
        AddReminderDialog(
            onDismiss = { showAddReminderDialog = false },
            onAdd = { title, date, comment ->
                reminders = reminders + ReminderInput(title = title, date = date, comment = comment)
                showAddReminderDialog = false
            }
        )
    }

    // Add Custom Field Dialog
    if (showAddFieldDialog) {
        AddCustomFieldDialog(
            onDismiss = { showAddFieldDialog = false },
            onAdd = { fieldName, fieldValue ->
                customFields = customFields + CustomFieldInput(fieldName = fieldName, fieldValue = fieldValue)
                showAddFieldDialog = false
            }
        )
    }
}

@Composable
fun AddSocialNetworkDialog(
    onDismiss: () -> Unit,
    onAdd: (type: String, url: String, username: String) -> Unit
) {
    var type by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localized("Добавить соцсеть")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text(localized("Тип (напр., Telegram, WhatsApp)")) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(localized("Имя пользователя")) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(localized("Ссылка (необязательно)")) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(type, url, username) },
                enabled = type.isNotBlank()
            ) {
                Text(localized("Добавить"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localized("Отмена"))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, date: Long, comment: String, isRecurring: Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var isRecurring by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localized("Добавить событие")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(localized("Название")) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = datePickerState.selectedDateMillis?.let {
                        Instant.ofEpochMilli(it)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .format(DateTimeFormatter.ofPattern("dd MMM yyyy", appLocale()))
                    } ?: localized("Выберите дату"),
                    onValueChange = {},
                    label = { Text(localized("Дата")) },
                    enabled = false,
                    modifier = Modifier.clickable { showDatePicker = true }
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(localized("Комментарий")) },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it }
                    )
                    Text(localized("Повторяется ежегодно"))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { date ->
                        onAdd(title, date, comment, isRecurring)
                    }
                },
                enabled = title.isNotBlank() && datePickerState.selectedDateMillis != null
            ) {
                Text(localized("Добавить"))
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
                TextButton(onClick = { showDatePicker = false }) {
                    Text(localized("ОК"))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, date: Long?, comment: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var hasDate by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localized("Добавить напоминание")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(localized("Название")) },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = hasDate,
                        onCheckedChange = { hasDate = it }
                    )
                    Text(localized("Установить дату"))
                }
                if (hasDate) {
                    OutlinedTextField(
                        value = datePickerState.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .format(DateTimeFormatter.ofPattern("dd MMM yyyy", appLocale()))
                        } ?: localized("Выберите дату"),
                        onValueChange = {},
                        label = { Text(localized("Дата")) },
                        enabled = false,
                        modifier = Modifier.clickable { showDatePicker = true }
                    )
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text(localized("Комментарий")) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(title, if (hasDate) datePickerState.selectedDateMillis else null, comment)
                },
                enabled = title.isNotBlank() && (!hasDate || datePickerState.selectedDateMillis != null)
            ) {
                Text(localized("Добавить"))
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
                TextButton(onClick = { showDatePicker = false }) {
                    Text(localized("ОК"))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun AddCustomFieldDialog(
    onDismiss: () -> Unit,
    onAdd: (fieldName: String, fieldValue: String) -> Unit
) {
    var fieldName by remember { mutableStateOf("") }
    var fieldValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localized("Добавить поле")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = fieldName,
                    onValueChange = { fieldName = it },
                    label = { Text(localized("Название поля")) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { fieldValue = it },
                    label = { Text(localized("Значение поля")) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(fieldName, fieldValue) },
                enabled = fieldName.isNotBlank() && fieldValue.isNotBlank()
            ) {
                Text(localized("Добавить"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(localized("Отмена"))
            }
        }
    )
}
