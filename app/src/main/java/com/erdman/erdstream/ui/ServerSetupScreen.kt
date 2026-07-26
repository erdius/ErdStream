package com.erdman.erdstream.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun ServerSetupScreen(
    onTestConnection: suspend (serverUrl: String, username: String, password: String) -> Result<Unit>,
    onConnected: (serverUrl: String, username: String, password: String) -> Unit,
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Connect to Navidrome",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter your Subsonic-compatible server details.",
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("Server URL (e.g. https://music.example.com)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Button(
            onClick = {
                if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                    errorMessage = "Please fill in all fields."
                    return@Button
                }
                isConnecting = true
                errorMessage = null
                scope.launch {
                    val result = onTestConnection(serverUrl, username, password)
                    isConnecting = false
                    result.onSuccess {
                        onConnected(serverUrl, username, password)
                    }.onFailure { error ->
                        errorMessage = error.message ?: "Couldn't connect to server."
                    }
                }
            },
            enabled = !isConnecting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp))
            } else {
                Text("Connect")
            }
        }
    }
}
