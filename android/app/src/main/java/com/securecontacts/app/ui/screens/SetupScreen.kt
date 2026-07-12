package com.securecontacts.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SetupBackupPasswordScreen(
    onSetupComplete: (backupPassword: String, helpPassword: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var backupPassword by remember { mutableStateOf("") }
    var confirmBackupPassword by remember { mutableStateOf("") }
    var helpPassword by remember { mutableStateOf("") }
    var confirmHelpPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var currentStep by remember { mutableStateOf(0) }

    val isBackupPasswordValid = backupPassword.length >= 8 && backupPassword == confirmBackupPassword
    val isHelpPasswordValid = helpPassword.length >= 8 && helpPassword == confirmHelpPassword && helpPassword != backupPassword

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Добро пожаловать в Защищённые Контакты",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Давайте настроим ваши пароли безопасности",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Progress indicator
        LinearProgressIndicator(
            progress = if (currentStep == 0) 0.5f else 1f,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (currentStep == 0) {
                    // Backup Password Setup
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Резервный пароль",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Минимум 8 символов. Пароль используется для зашифрованного экспорта/импорта и экстренного доступа к данным.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("Резервный пароль") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Показать или скрыть пароль"
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = confirmBackupPassword,
                        onValueChange = { confirmBackupPassword = it },
                        label = { Text("Подтвердите резервный пароль") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = confirmBackupPassword.isNotEmpty() && backupPassword != confirmBackupPassword,
                        supportingText = if (confirmBackupPassword.isNotEmpty() && backupPassword != confirmBackupPassword) {
                            { Text("Пароли не совпадают") }
                        } else null
                    )

                    Button(
                        onClick = { currentStep = 1 },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = isBackupPasswordValid
                    ) {
                        Text("Далее")
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                } else {
                    // Help Password Setup
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Help,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Пароль помощи",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Минимум 8 символов. Это вторичный пароль для помощи, он должен отличаться от резервного.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = helpPassword,
                        onValueChange = { helpPassword = it },
                        label = { Text("Пароль помощи") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Показать или скрыть пароль"
                                )
                            }
                        }
                    )

                    OutlinedTextField(
                        value = confirmHelpPassword,
                        onValueChange = { confirmHelpPassword = it },
                        label = { Text("Подтвердите пароль помощи") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        isError = confirmHelpPassword.isNotEmpty() && helpPassword != confirmHelpPassword,
                        supportingText = if (confirmHelpPassword.isNotEmpty() && helpPassword != confirmHelpPassword) {
                            { Text("Пароли не совпадают") }
                        } else if (helpPassword.isNotEmpty() && helpPassword == backupPassword) {
                            { Text("Пароль помощи должен отличаться от резервного") }
                        } else null
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { currentStep = 0 },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Назад")
                        }

                        Button(
                            onClick = { onSetupComplete(backupPassword, helpPassword) },
                            modifier = Modifier.weight(1f),
                            enabled = isHelpPasswordValid
                        ) {
                            Text("Готово")
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Запомните эти пароли! Их нельзя восстановить при утере.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun UnlockContactDialog(
    contactName: String,
    onDismiss: () -> Unit,
    onPasswordSubmit: (String) -> Unit,
    onBiometricClick: (() -> Unit)?,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Разблокировать контакт",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Введите пароль для просмотра защищённой информации $contactName",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Неверный пароль") }
                    } else null,
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Показать или скрыть пароль"
                            )
                        }
                    }
                )

                if (onBiometricClick != null) {
                    OutlinedButton(
                        onClick = onBiometricClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Использовать отпечаток")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPasswordSubmit(password) },
                enabled = password.isNotBlank()
            ) {
                Text("Разблокировать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
