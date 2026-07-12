package com.securecontacts.app

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.securecontacts.app.data.database.AppDatabase
import com.securecontacts.app.data.model.*
import com.securecontacts.app.data.repository.ContactRepository
import com.securecontacts.app.security.BiometricAuthManager
import com.securecontacts.app.security.PreferencesManager
import com.securecontacts.app.ui.navigation.Screen
import com.securecontacts.app.ui.navigation.bottomNavItems
import com.securecontacts.app.ui.screens.*
import com.securecontacts.app.ui.theme.SecureContactsTheme
import com.securecontacts.app.util.ExportImportManager
import com.securecontacts.app.util.ImportResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Stable
private class UnlockSessionState {
    var isAppUnlocked by mutableStateOf(false)
        private set
    var isHelpUnlocked by mutableStateOf(false)
        private set
    var unlockedContactIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    fun unlockApp() {
        isAppUnlocked = true
    }

    fun unlockHelp() {
        isHelpUnlocked = true
    }

    fun unlockContact(contactId: Long) {
        unlockedContactIds = unlockedContactIds + contactId
    }

    fun lockContact(contactId: Long) {
        unlockedContactIds = unlockedContactIds - contactId
    }

    fun lockAll() {
        isAppUnlocked = false
        isHelpUnlocked = false
        unlockedContactIds = emptySet()
    }
}

private fun Contact.isLegacyUnprotected(): Boolean = passwordHash.isBlank() && passwordSalt.isBlank()

private fun Contact.maskPrivateFields(): Contact = copy(
    phone = "",
    email = "",
    address = "",
    workplace = "",
    position = "",
    birthday = null,
    avatarUri = null
)

class MainActivity : FragmentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var repository: ContactRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var biometricManager: BiometricAuthManager
    private lateinit var exportImportManager: ExportImportManager
    private val unlockSession = UnlockSessionState()
    private var isActivityResultInProgress = false

    fun beginActivityResultFlow() {
        isActivityResultInProgress = true
    }

    fun endActivityResultFlow() {
        isActivityResultInProgress = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        database = (application as SecureContactsApp).database
        repository = ContactRepository(
            database.contactDao(),
            database.tagDao(),
            database.categoryDao(),
            database.eventDao(),
            database.reminderDao(),
            database.socialNetworkDao(),
            database.customFieldDao(),
            database.conversationDao(),
            database.searchHistoryDao()
        )
        preferencesManager = PreferencesManager(this)
        biometricManager = BiometricAuthManager(this)
        exportImportManager = ExportImportManager(this, repository, preferencesManager::verifyBackupPassword)

        setContent {
            val isDarkTheme by preferencesManager.isDarkThemeEnabled.collectAsState(initial = true)
            val hasBackupPassword by preferencesManager.hasBackupPassword.collectAsState(initial = false)

            SecureContactsTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!hasBackupPassword) {
                        SetupBackupPasswordScreen(
                            onSetupComplete = { backupPassword, helpPassword ->
                                lifecycleScope.launch {
                                    preferencesManager.completeInitialSetup(backupPassword, helpPassword)
                                }
                            }
                        )
                    } else {
                        MainApp(
                            repository = repository,
                            preferencesManager = preferencesManager,
                            biometricManager = biometricManager,
                            exportImportManager = exportImportManager,
                            unlockSession = unlockSession,
                            activity = this@MainActivity
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        if (!isActivityResultInProgress) {
            unlockSession.lockAll()
        }
        super.onStop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(
    repository: ContactRepository,
    preferencesManager: PreferencesManager,
    biometricManager: BiometricAuthManager,
    exportImportManager: ExportImportManager,
    unlockSession: UnlockSessionState,
    activity: MainActivity
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // State
    val allContacts by repository.getAllContacts().collectAsState(initial = emptyList())
    val allConversations by repository.getAllConversations().collectAsState(initial = emptyList())
    val appLockEnabledState: Boolean? by preferencesManager.isAppLockEnabled.collectAsState(initial = null)
    val appLockPasswordState: Boolean? by preferencesManager.hasAppLockPassword.collectAsState(initial = null)
    val tags by repository.getAllTags().collectAsState(initial = emptyList())
    val categories by repository.getAllCategories().collectAsState(initial = emptyList())
    val contacts = allContacts
    val dateItems by repository.getDateItems().collectAsState(initial = emptyList())
    val reminders by repository.getAllReminders().collectAsState(initial = emptyList())
    val recentSearches by repository.getRecentSearches().collectAsState(initial = emptyList())
    val isDarkTheme by preferencesManager.isDarkThemeEnabled.collectAsState(initial = true)
    val isBiometricEnabled by preferencesManager.isBiometricEnabled.collectAsState(initial = false)

    if (appLockEnabledState == null || appLockPasswordState == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val isAppLockEnabled = appLockEnabledState == true
    val hasAppLockPassword = appLockPasswordState == true

    var searchQuery by remember { mutableStateOf("") }
    var selectedTagId by remember { mutableStateOf<Long?>(null) }
    var contactTags by remember { mutableStateOf<Map<Long, List<Tag>>>(emptyMap()) }

    // Load contact tags
    LaunchedEffect(contacts, tags) {
        val tagsMap = mutableMapOf<Long, List<Tag>>()
        contacts.forEach { contact ->
            val contactTagsList = repository.getTagsForContact(contact.id).first()
            tagsMap[contact.id] = contactTagsList
        }
        contactTags = tagsMap
    }

    var showActiveOnly by remember { mutableStateOf(true) }

    // Filter contacts
    val filteredContacts = remember(
        allContacts,
        searchQuery,
        selectedTagId,
        contactTags,
        unlockSession.unlockedContactIds
    ) {
        var result = allContacts

        if (searchQuery.isNotBlank()) {
            result = result.filter { contact ->
                contact.name.contains(searchQuery, ignoreCase = true) ||
                (contact.id in unlockSession.unlockedContactIds && (
                    contact.phone.contains(searchQuery, ignoreCase = true) ||
                        contact.workplace.contains(searchQuery, ignoreCase = true) ||
                        contact.position.contains(searchQuery, ignoreCase = true) ||
                        contact.source.contains(searchQuery, ignoreCase = true)
                    ))
            }
        }

        if (selectedTagId != null) {
            result = result.filter { contact ->
                contactTags[contact.id]?.any { it.id == selectedTagId } == true
            }
        }

        result
    }
    val displayContacts = filteredContacts.map { contact ->
        if (contact.id in unlockSession.unlockedContactIds || contact.isLegacyUnprotected()) contact
        else contact.maskPrivateFields()
    }

    // Reminders with contacts
    val remindersWithContacts = remember(reminders, contacts, unlockSession.unlockedContactIds) {
        reminders.filter { it.contactId in unlockSession.unlockedContactIds }.map { reminder ->
            ReminderWithContact(
                reminder = reminder,
                contact = contacts.find { it.id == reminder.contactId }
            )
        }
    }
    val visibleDateItems = dateItems.filter { it.contactId in unlockSession.unlockedContactIds }
    val visibleConversations = allConversations.filter { it.contactId in unlockSession.unlockedContactIds }

    val scope = rememberCoroutineScope()

    // App lock check
    if (isAppLockEnabled && hasAppLockPassword && !unlockSession.isAppUnlocked) {
        AppLockScreen(
            onPasswordVerified = unlockSession::unlockApp,
            onBiometricClick = if (isBiometricEnabled && biometricManager.canAuthenticate()) {
                {
                    biometricManager.authenticate(
                        activity = activity,
                        title = "Вход в приложение",
                        subtitle = "Используйте отпечаток для входа",
                        onSuccess = unlockSession::unlockApp,
                        onError = { },
                        onFailed = { }
                    )
                }
            } else null,
            preferencesManager = preferencesManager
        )
        return
    }

    // Show bottom nav only on main screens
    val showBottomNav = currentRoute in listOf(
        Screen.Contacts.route,
        Screen.Dates.route,
        Screen.Reminders.route,
        Screen.Help.route,
        Screen.Settings.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Contacts.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Contacts Screen
            composable(Screen.Contacts.route) {
                ContactsScreen(
                    contacts = displayContacts,
                    allContacts = allContacts,
                    tags = tags,
                    contactTags = contactTags,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onContactClick = { contactId ->
                        navController.navigate(Screen.ContactDetail.createRoute(contactId))
                    },
                    onAddContactClick = {
                        navController.navigate(Screen.CreateContact.route)
                    },
                    onSearchClick = {
                        navController.navigate(Screen.Search.route)
                    },
                    onTagFilterClick = { tagId ->
                        selectedTagId = tagId
                    },
                    selectedTagId = selectedTagId,
                    showActiveOnly = showActiveOnly,
                    onShowActiveOnlyChange = { showActiveOnly = it }
                )
            }

            // Dates Screen
            composable(Screen.Dates.route) {
                DatesScreen(
                    dateItems = visibleDateItems,
                    onContactClick = { contactId ->
                        navController.navigate(Screen.ContactDetail.createRoute(contactId))
                    }
                )
            }

            // Reminders Screen
            composable(Screen.Reminders.route) {
                RemindersScreen(
                    reminders = remindersWithContacts,
                    onContactClick = { contactId ->
                        navController.navigate(Screen.ContactDetail.createRoute(contactId))
                    },
                    onCompleteReminder = { reminderId ->
                        reminders.firstOrNull {
                            it.id == reminderId && it.contactId in unlockSession.unlockedContactIds
                        }?.let {
                            scope.launch {
                                repository.completeReminder(reminderId)
                            }
                        }
                    },
                    onDeleteReminder = { reminderId ->
                        reminders.firstOrNull {
                            it.id == reminderId && it.contactId in unlockSession.unlockedContactIds
                        }?.let { reminder ->
                            scope.launch {
                                repository.deleteReminder(reminder)
                            }
                        }
                    }
                )
            }

            // Help Screen
            composable(Screen.Help.route) {
                val context = LocalContext.current
                var hasHelpPassword by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    hasHelpPassword = preferencesManager.hasHelpPassword()
                }

                HelpScreen(
                    contacts = contacts,
                    tags = tags,
                    contactTags = contactTags,
                    isUnlocked = unlockSession.isHelpUnlocked,
                    hasPassword = hasHelpPassword,
                    onUnlockRequest = { password ->
                        preferencesManager.verifyHelpPassword(password).also { verified ->
                            if (verified) {
                                unlockSession.unlockHelp()
                            }
                        }
                    },
                    onSetPassword = { password ->
                        if (password.length < 8) {
                            Toast.makeText(context, "Пароль должен содержать минимум 8 символов", Toast.LENGTH_SHORT).show()
                            false
                        } else if (preferencesManager.verifyBackupPassword(password)) {
                            Toast.makeText(context, "Пароль помощи должен отличаться от резервного", Toast.LENGTH_SHORT).show()
                            false
                        } else {
                            preferencesManager.setHelpPassword(password)
                            hasHelpPassword = true
                            unlockSession.unlockHelp()
                            true
                        }
                    },
                    onContactClick = { contactId ->
                        navController.navigate(Screen.ContactDetail.createRoute(contactId))
                    }
                )
            }

            // Settings Screen
            composable(Screen.Settings.route) {
                val context = LocalContext.current
                var showChangeBackupPasswordDialog by remember { mutableStateOf(false) }
                var showChangeHelpPasswordDialog by remember { mutableStateOf(false) }
                var showExportPasswordDialog by remember { mutableStateOf(false) }
                var showPlainExportPasswordDialog by remember { mutableStateOf(false) }
                var showEncryptedImportPasswordDialog by remember { mutableStateOf(false) }
                var showPlainImportPasswordDialog by remember { mutableStateOf(false) }
                var pendingEncryptedExportPassword by remember { mutableStateOf<String?>(null) }
                var pendingPlainExportPassword by remember { mutableStateOf<String?>(null) }
                var pendingEncryptedImportUri by remember { mutableStateOf<Uri?>(null) }
                var pendingPlainImportUri by remember { mutableStateOf<Uri?>(null) }

                DisposableEffect(Unit) {
                    onDispose {
                        pendingEncryptedExportPassword = null
                        pendingPlainExportPassword = null
                        pendingEncryptedImportUri = null
                        pendingPlainImportUri = null
                    }
                }

                val exportEncryptedLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri ->
                    activity.endActivityResultFlow()
                    if (uri == null) {
                        pendingEncryptedExportPassword = null
                    } else {
                        val exportUri = uri
                        pendingEncryptedExportPassword?.let { password ->
                            scope.launch {
                                try {
                                    val success = exportImportManager.exportEncryptedToUri(exportUri, password)
                                    if (success) {
                                        Toast.makeText(context, "Экспорт завершён успешно", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Ошибка экспорта", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                pendingEncryptedExportPassword = null
                            }
                        }
                    }
                }

                val exportPlainLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    activity.endActivityResultFlow()
                    if (uri == null) {
                        pendingPlainExportPassword = null
                    } else {
                        pendingPlainExportPassword?.let { password ->
                            scope.launch {
                                try {
                                    val success = exportImportManager.exportPlainToUri(uri, password)
                                    if (success) {
                                        Toast.makeText(context, "Открытый экспорт завершён", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Ошибка экспорта", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                                pendingPlainExportPassword = null
                            }
                        }
                    }
                }

                val importEncryptedLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    activity.endActivityResultFlow()
                    uri?.let { importUri ->
                        pendingEncryptedImportUri = importUri
                        showEncryptedImportPasswordDialog = true
                    }
                }

                val importPlainLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    activity.endActivityResultFlow()
                    uri?.let { importUri ->
                        pendingPlainImportUri = importUri
                        showPlainImportPasswordDialog = true
                    }
                }

                SettingsScreen(
                    isDarkTheme = isDarkTheme,
                    isBiometricEnabled = isBiometricEnabled,
                    isBiometricAvailable = biometricManager.canAuthenticate(),
                    isAppLockEnabled = isAppLockEnabled,
                    onDarkThemeChange = { enabled ->
                        scope.launch {
                            preferencesManager.setDarkThemeEnabled(enabled)
                        }
                    },
                    onBiometricChange = { enabled ->
                        scope.launch {
                            preferencesManager.setBiometricEnabled(enabled)
                        }
                    },
                    onExportEncrypted = { showExportPasswordDialog = true },
                    onExportPlain = { showPlainExportPasswordDialog = true },
                    onImportEncrypted = {
                        activity.beginActivityResultFlow()
                        runCatching { importEncryptedLauncher.launch(arrayOf("*/*")) }
                            .onFailure { activity.endActivityResultFlow() }
                    },
                    onImportPlain = {
                        activity.beginActivityResultFlow()
                        runCatching { importPlainLauncher.launch(arrayOf("application/json", "text/plain")) }
                            .onFailure { activity.endActivityResultFlow() }
                    },
                    onChangeBackupPassword = { showChangeBackupPasswordDialog = true },
                    onChangeHelpPassword = { showChangeHelpPasswordDialog = true },
                    onManageTags = { navController.navigate(Screen.TagManagement.route) },
                    onManageCategories = { navController.navigate(Screen.CategoryManagement.route) },
                    onAllConversationsClick = { navController.navigate(Screen.AllConversations.route) },
                    onSetupAppLock = { password ->
                        scope.launch {
                            preferencesManager.setAppLockPassword(password)
                        }
                    },
                    onRemoveAppLock = {
                        scope.launch {
                            preferencesManager.removeAppLock()
                        }
                    }
                )

                if (showChangeBackupPasswordDialog) {
                    ChangePasswordDialog(
                        title = "Изменить резервный пароль",
                        onDismiss = { showChangeBackupPasswordDialog = false },
                        onConfirm = { oldPassword, newPassword ->
                            scope.launch {
                                val success = preferencesManager.changeBackupPassword(oldPassword, newPassword)
                                if (success) {
                                    showChangeBackupPasswordDialog = false
                                    Toast.makeText(context, "Пароль изменён", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Неверный текущий пароль", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                if (showChangeHelpPasswordDialog) {
                    ChangePasswordDialog(
                        title = "Изменить пароль помощи",
                        onDismiss = { showChangeHelpPasswordDialog = false },
                        onConfirm = { oldPassword, newPassword ->
                            scope.launch {
                                if (preferencesManager.verifyBackupPassword(newPassword)) {
                                    Toast.makeText(context, "Пароль помощи должен отличаться от резервного", Toast.LENGTH_SHORT).show()
                                } else if (preferencesManager.changeHelpPassword(oldPassword, newPassword)) {
                                    showChangeHelpPasswordDialog = false
                                    Toast.makeText(context, "Пароль изменён", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Неверный текущий пароль", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                if (showExportPasswordDialog) {
                    PasswordInputDialog(
                        title = "Экспорт с шифрованием",
                        message = "Введите резервный пароль для создания зашифрованной копии",
                        onDismiss = { showExportPasswordDialog = false },
                        onConfirm = { password ->
                            scope.launch {
                                if (preferencesManager.verifyBackupPassword(password)) {
                                    showExportPasswordDialog = false
                                    pendingEncryptedExportPassword = password
                                    val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                                    val timestamp = dateFormat.format(Date())
                                    activity.beginActivityResultFlow()
                                    runCatching {
                                        exportEncryptedLauncher.launch("secure_contacts_backup_${timestamp}.enc")
                                    }.onFailure {
                                        activity.endActivityResultFlow()
                                        pendingEncryptedExportPassword = null
                                    }
                                } else {
                                    Toast.makeText(context, "Неверный резервный пароль", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                if (showPlainExportPasswordDialog) {
                    PasswordInputDialog(
                        title = "Открытый экспорт",
                        message = "Введите резервный пароль. Файл будет сохранён без шифрования и будет содержать персональные данные, хэши паролей контактов и дополнительные поля.",
                        onDismiss = { showPlainExportPasswordDialog = false },
                        onConfirm = { password ->
                            scope.launch {
                                if (preferencesManager.verifyBackupPassword(password)) {
                                    showPlainExportPasswordDialog = false
                                    pendingPlainExportPassword = password
                                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                    activity.beginActivityResultFlow()
                                    runCatching {
                                        exportPlainLauncher.launch("secure_contacts_backup_${timestamp}.json")
                                    }.onFailure {
                                        activity.endActivityResultFlow()
                                        pendingPlainExportPassword = null
                                    }
                                } else {
                                    Toast.makeText(context, "Неверный резервный пароль", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                if (showEncryptedImportPasswordDialog && pendingEncryptedImportUri != null) {
                    PasswordInputDialog(
                        title = "Импорт с шифрованием",
                        message = "Введите пароль для расшифровки файла",
                        onDismiss = {
                            showEncryptedImportPasswordDialog = false
                            pendingEncryptedImportUri = null
                        },
                        onConfirm = { password ->
                            pendingEncryptedImportUri?.let { uri ->
                                scope.launch {
                                    try {
                                        val result = exportImportManager.importEncryptedFromUri(uri, password)
                                        when (result) {
                                            is ImportResult.Success -> {
                                                Toast.makeText(context, "Импортировано: ${result.contactsImported} контактов", Toast.LENGTH_SHORT).show()
                                            }
                                            is ImportResult.Error -> {
                                                Toast.makeText(context, "Ошибка: ${result.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    showEncryptedImportPasswordDialog = false
                                    pendingEncryptedImportUri = null
                                }
                            }
                        }
                    )
                }

                if (showPlainImportPasswordDialog && pendingPlainImportUri != null) {
                    PasswordInputDialog(
                        title = "Открытый импорт",
                        message = "Введите текущий резервный пароль. Импорт разрешён только после проверки пароля, а файл не зашифрован.",
                        onDismiss = {
                            showPlainImportPasswordDialog = false
                            pendingPlainImportUri = null
                        },
                        onConfirm = { password ->
                            pendingPlainImportUri?.let { uri ->
                                scope.launch {
                                    try {
                                        val result = exportImportManager.importPlainFromUri(uri, password)
                                        when (result) {
                                            is ImportResult.Success -> Toast.makeText(context, "Импортировано: ${result.contactsImported} контактов", Toast.LENGTH_SHORT).show()
                                            is ImportResult.Error -> Toast.makeText(context, "Ошибка: ${result.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                    showPlainImportPasswordDialog = false
                                    pendingPlainImportUri = null
                                }
                            }
                        }
                    )
                }
            }

            // Contact Detail Screen
            composable(
                route = Screen.ContactDetail.route,
                arguments = listOf(navArgument("contactId") { type = NavType.LongType })
            ) { backStackEntry ->
                val contactId = backStackEntry.arguments?.getLong("contactId") ?: return@composable
                var contactWithDetails by remember { mutableStateOf<ContactWithDetails?>(null) }
                var showUnlockDialog by remember { mutableStateOf(false) }
                var showDeleteDialog by remember { mutableStateOf(false) }
                var passwordError by remember { mutableStateOf(false) }

                LaunchedEffect(contactId) {
                    contactWithDetails = repository.getContactWithDetails(contactId)
                }

                val contactIsUnlocked = contactId in unlockSession.unlockedContactIds ||
                    contactWithDetails?.contact?.isLegacyUnprotected() == true
                val displayContactWithDetails = contactWithDetails?.let { details ->
                    if (contactIsUnlocked) details else details.copy(contact = details.contact.maskPrivateFields())
                }

                ContactDetailScreen(
                    contactWithDetails = displayContactWithDetails,
                    isUnlocked = contactIsUnlocked,
                    onBackClick = { navController.popBackStack() },
                    onEditClick = {
                        navController.navigate(Screen.EditContact.createRoute(contactId))
                    },
                    onDeleteClick = { showDeleteDialog = true },
                    onUnlockClick = { showUnlockDialog = true },
                    onToggleActive = {
                        scope.launch {
                            repository.toggleContactActive(contactId)
                            contactWithDetails = repository.getContactWithDetails(contactId)
                        }
                    },
                    onAddConversation = { date, topic ->
                        scope.launch {
                            repository.createConversation(contactId, date, topic)
                            contactWithDetails = repository.getContactWithDetails(contactId)
                        }
                    },
                    onDeleteConversation = { conversation ->
                        scope.launch {
                            repository.deleteConversation(conversation)
                            contactWithDetails = repository.getContactWithDetails(contactId)
                        }
                    }
                )

                if (showUnlockDialog) {
                    UnlockContactDialog(
                        contactName = contactWithDetails?.contact?.name ?: "",
                        onDismiss = {
                            showUnlockDialog = false
                            passwordError = false
                        },
                        onPasswordSubmit = { password ->
                            contactWithDetails?.contact?.let { contact ->
                                if (repository.verifyContactPassword(contact, password)) {
                                    unlockSession.unlockContact(contactId)
                                    showUnlockDialog = false
                                    passwordError = false
                                } else {
                                    passwordError = true
                                }
                            }
                        },
                        onBiometricClick = if (isBiometricEnabled && biometricManager.canAuthenticate()) {
                            {
                                biometricManager.authenticate(
                                    activity = activity,
                                    title = "Разблокировка контакта",
                                    subtitle = "Используйте отпечаток пальца",
                                    onSuccess = {
                                        unlockSession.unlockContact(contactId)
                                        showUnlockDialog = false
                                    },
                                    onError = { },
                                    onFailed = { }
                                )
                            }
                        } else null,
                        isError = passwordError
                    )
                }

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Удалить контакт") },
                        text = { Text("Удалить этот контакт без возможности восстановления?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        repository.deleteContact(contactId)
                                        unlockSession.lockContact(contactId)
                                        showDeleteDialog = false
                                        navController.popBackStack()
                                    }
                                }
                            ) {
                                Text("Удалить", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("Отмена")
                            }
                        }
                    )
                }
            }

            // Create Contact Screen
            composable(Screen.CreateContact.route) {
                CreateEditContactScreen(
                    isEditMode = false,
                    existingContact = null,
                    allTags = tags,
                    allCategories = categories,
                    onSaveClick = { name, phone, email, address, workplace, position, source, helpInfo, birthday, avatarUri, password, categoryId, selectedTagIds, socialNetworks, events, reminders, customFields ->
                        scope.launch {
                            repository.withTransaction {
                                val contactId = repository.createContact(
                                    name = name,
                                    phone = phone,
                                    email = email,
                                    address = address,
                                    workplace = workplace,
                                    position = position,
                                    source = source,
                                    helpInfo = helpInfo,
                                    birthday = birthday,
                                    avatarUri = avatarUri,
                                    password = password,
                                    categoryId = categoryId
                                )

                                repository.setContactTags(contactId, selectedTagIds)

                                socialNetworks.forEach { sn ->
                                    repository.createSocialNetwork(contactId, sn.type, sn.url, sn.username)
                                }

                                events.forEach { event ->
                                    repository.createEvent(contactId, event.title, event.date, event.comment, event.isRecurring)
                                }

                                reminders.forEach { reminder ->
                                    repository.createReminder(contactId, reminder.title, reminder.date, reminder.comment)
                                }

                                customFields.forEach { cf ->
                                    repository.createCustomField(contactId, cf.fieldName, cf.fieldValue, cf.isEncrypted)
                                }
                            }

                            navController.popBackStack()
                        }
                    },
                    onBackClick = { navController.popBackStack() },
                    onCreateTagClick = { navController.navigate(Screen.TagManagement.route) },
                    onCreateCategoryClick = { navController.navigate(Screen.CategoryManagement.route) },
                    onActivityResultFlowChange = { inProgress ->
                        if (inProgress) activity.beginActivityResultFlow() else activity.endActivityResultFlow()
                    }
                )
            }

            // Edit Contact Screen
            composable(
                route = Screen.EditContact.route,
                arguments = listOf(navArgument("contactId") { type = NavType.LongType })
            ) { backStackEntry ->
                val contactId = backStackEntry.arguments?.getLong("contactId") ?: return@composable
                var contactWithDetails by remember { mutableStateOf<ContactWithDetails?>(null) }

                LaunchedEffect(contactId) {
                    contactWithDetails = repository.getContactWithDetails(contactId)
                }

                val legacyContactIsUnlocked = contactWithDetails?.contact?.let {
                    it.isLegacyUnprotected()
                } == true
                if (contactId !in unlockSession.unlockedContactIds && !legacyContactIsUnlocked) {
                    LaunchedEffect(contactId) {
                        navController.popBackStack()
                    }
                    return@composable
                }

                CreateEditContactScreen(
                    isEditMode = true,
                    existingContact = contactWithDetails,
                    allTags = tags,
                    allCategories = categories,
                    onSaveClick = { name, phone, email, address, workplace, position, source, helpInfo, birthday, avatarUri, newPassword, categoryId, selectedTagIds, socialNetworks, events, reminders, customFields ->
                        scope.launch {
                            repository.withTransaction {
                                contactWithDetails?.contact?.let { existingContact ->
                                    repository.updateContact(
                                        existingContact.copy(
                                            name = name,
                                            phone = phone,
                                            email = email,
                                            address = address,
                                            workplace = workplace,
                                            position = position,
                                            source = source,
                                            helpInfo = helpInfo,
                                            birthday = birthday,
                                            avatarUri = avatarUri,
                                            categoryId = categoryId
                                        )
                                    )

                                    // Update password if a new one was provided
                                    if (newPassword.isNotBlank()) {
                                        repository.updateContactPassword(contactId, newPassword)
                                    }

                                    repository.setContactTags(contactId, selectedTagIds)

                                    // Update social networks
                                    repository.setSocialNetworks(contactId, socialNetworks.map {
                                        SocialNetwork(it.id, contactId, it.type, it.url, it.username)
                                    })

                                    // Update events - delete old and create new
                                    contactWithDetails?.events?.forEach { repository.deleteEvent(it) }
                                    events.forEach { event ->
                                        repository.createEvent(contactId, event.title, event.date, event.comment, event.isRecurring)
                                    }

                                    val existingReminders = contactWithDetails?.reminders.orEmpty().associateBy { it.id }
                                    val retainedReminderIds = reminders.mapNotNull { input ->
                                        input.id.takeIf { it > 0 && it in existingReminders }
                                    }.toSet()
                                    existingReminders.values
                                        .filterNot { it.id in retainedReminderIds }
                                        .forEach { repository.deleteReminder(it) }
                                    reminders.forEach { reminder ->
                                        val existingReminder = existingReminders[reminder.id]
                                        if (existingReminder == null) {
                                            repository.createReminder(contactId, reminder.title, reminder.date, reminder.comment)
                                        } else {
                                            repository.updateReminder(
                                                existingReminder.copy(
                                                    title = reminder.title,
                                                    date = reminder.date,
                                                    comment = reminder.comment,
                                                    isCompleted = reminder.isCompleted,
                                                    createdAt = reminder.createdAt
                                                )
                                            )
                                        }
                                    }

                                    // Update custom fields
                                    repository.setCustomFields(contactId, customFields.map {
                                        CustomField(it.id, contactId, it.fieldName, it.fieldValue, it.isEncrypted)
                                    })
                                }
                            }

                            navController.popBackStack()
                        }
                    },
                    onBackClick = { navController.popBackStack() },
                    onCreateTagClick = { navController.navigate(Screen.TagManagement.route) },
                    onCreateCategoryClick = { navController.navigate(Screen.CategoryManagement.route) },
                    onActivityResultFlowChange = { inProgress ->
                        if (inProgress) activity.beginActivityResultFlow() else activity.endActivityResultFlow()
                    }
                )
            }

            // Search Screen
            composable(Screen.Search.route) {
                var localSearchQuery by remember { mutableStateOf("") }
                var searchResults by remember { mutableStateOf<List<Contact>>(emptyList()) }

                LaunchedEffect(localSearchQuery) {
                    if (localSearchQuery.isNotBlank()) {
                        searchResults = repository.searchContacts(localSearchQuery).first()
                        repository.addSearchHistory(localSearchQuery)
                    } else {
                        searchResults = emptyList()
                    }
                }
                val visibleSearchResults = searchResults.filter { contact ->
                    contact.id in unlockSession.unlockedContactIds ||
                        contact.name.contains(localSearchQuery, ignoreCase = true) ||
                        (contact.id in unlockSession.unlockedContactIds && (
                            contact.phone.contains(localSearchQuery, ignoreCase = true) ||
                                contact.workplace.contains(localSearchQuery, ignoreCase = true) ||
                                contact.position.contains(localSearchQuery, ignoreCase = true)
                            ))
                }.map { contact ->
                    if (contact.id in unlockSession.unlockedContactIds || contact.isLegacyUnprotected()) contact
                    else contact.maskPrivateFields()
                }

                SearchScreen(
                    searchQuery = localSearchQuery,
                    onSearchQueryChange = { localSearchQuery = it },
                    searchResults = visibleSearchResults,
                    recentSearches = recentSearches,
                    contactTags = contactTags,
                    unlockedContactIds = unlockSession.unlockedContactIds,
                    onContactClick = { contactId ->
                        navController.navigate(Screen.ContactDetail.createRoute(contactId))
                    },
                    onBackClick = { navController.popBackStack() },
                    onClearHistory = {
                        scope.launch {
                            repository.clearSearchHistory()
                        }
                    },
                    onRecentSearchClick = { query -> localSearchQuery = query }
                )
            }

            // Tag Management Screen
            composable(Screen.TagManagement.route) {
                TagManagementScreen(
                    tags = tags,
                    onBackClick = { navController.popBackStack() },
                    onCreateTag = { name, color ->
                        scope.launch {
                            repository.createTag(name, color)
                        }
                    },
                    onUpdateTag = { tag ->
                        scope.launch {
                            repository.updateTag(tag)
                        }
                    },
                    onDeleteTag = { tag ->
                        scope.launch {
                            repository.deleteTag(tag)
                        }
                    }
                )
            }

            // All Conversations Screen
            composable(Screen.AllConversations.route) {
                AllConversationsScreen(
                    conversations = visibleConversations,
                    contacts = contacts,
                    onBackClick = { navController.popBackStack() },
                    onContactClick = { contactId ->
                        navController.navigate(Screen.ContactDetail.createRoute(contactId))
                    }
                )
            }

            // Category Management Screen
            composable(Screen.CategoryManagement.route) {
                CategoryManagementScreen(
                    categories = categories,
                    onBackClick = { navController.popBackStack() },
                    onCreateCategory = { name, color ->
                        scope.launch {
                            repository.createCategory(name, color)
                        }
                    },
                    onUpdateCategory = { category ->
                        scope.launch {
                            repository.updateCategory(category)
                        }
                    },
                    onDeleteCategory = { category ->
                        scope.launch {
                            repository.deleteCategory(category)
                        }
                    }
                )
            }
        }
    }
}
