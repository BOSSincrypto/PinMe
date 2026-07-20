package com.securecontacts.app.ui.screens

import com.securecontacts.app.localization.localized

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
import androidx.compose.ui.unit.dp
import com.securecontacts.app.security.PreferencesManager
import kotlinx.coroutines.launch

@Composable
fun AppLockScreen(
    onPasswordVerified: () -> Unit,
    onBiometricClick: (() -> Unit)?,
    preferencesManager: PreferencesManager
) {
    var password by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var isVerifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        onBiometricClick?.invoke()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = localized("Защищённые Контакты"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = localized("Введите пароль для входа"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    isError = false
                },
                label = { Text(localized("Пароль")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !isVerifying,
                isError = isError,
                supportingText = if (isError) {
                    { Text(localized("Неверный пароль")) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isVerifying = true
                    scope.launch {
                        try {
                            if (preferencesManager.verifyAppLockPassword(password)) {
                                onPasswordVerified()
                            } else {
                                isError = true
                            }
                        } finally {
                            isVerifying = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = password.isNotBlank() && !isVerifying
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(localized("Войти"))
            }

            if (isVerifying) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (onBiometricClick != null) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onBiometricClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(localized("Использовать отпечаток"))
                }
            }
        }
    }
}
