package com.google.mytaskmanager.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    onSignUpRequested: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    val authResult by viewModel.authResult.collectAsState()

    // react to auth result
    LaunchedEffect(authResult) {
        if (authResult?.isSuccess == true) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {

        TopAppBar(
            title = { Text("Sign in") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    ),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Welcome back",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Sign in to your account to continue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username or Email") },
                        shape = RoundedCornerShape(5.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(5.dp),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {
                        isLoading = true
                        scope.launch {
                            viewModel.login(username, password)
                            isLoading = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        if (isLoading) CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        ) else Text("Sign In")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { onSignUpRequested() },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Don't have an account? Sign up")
                    }
                    authResult?.let { result ->
                        Spacer(modifier = Modifier.height(8.dp))
                        if (result.isSuccess) {
                            Text("Welcome ${result.getOrNull()?.username}")
                        } else {
                            val errorMessage =
                                result.exceptionOrNull()?.localizedMessage ?: "Unknown"
                            Text(
                                "Login failed: $errorMessage",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
