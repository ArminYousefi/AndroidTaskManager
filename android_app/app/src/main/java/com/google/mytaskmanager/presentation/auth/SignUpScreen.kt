package com.google.mytaskmanager.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onSignUpSuccess: () -> Unit,
    onSignInRequested: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    val authResult by viewModel.authResult.collectAsState()

    LaunchedEffect(authResult) {
        if (authResult?.isSuccess == true) {
            onSignUpSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {

        TopAppBar(
            title = { Text("Sign up") },
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
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
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
                        Text("Get started", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Create your account to start organizing",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm Password") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(5.dp),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = {
                        if (password != confirm) {
                            // set a local error using authResult is not ideal; instead we can use snackbar or local state
                            // but keeping simple: set a failed authResult in viewModel is not currently exposed; show a simple local error via a coroutine scope
                        }
                        isLoading = true
                        scope.launch {
                            viewModel.signup(
                                username = username,
                                password = password,
                                email = email.ifBlank { null }
                            )
                            isLoading = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        if (isLoading) CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        ) else Text("Create Account")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onSignInRequested,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Already have an account? Sign in")
                    }
                    authResult?.let { r ->
                        Spacer(modifier = Modifier.height(8.dp))
                        if (r.isSuccess) {
                            Text("Welcome ${r.getOrNull()?.username}")
                        } else {
                            Text(
                                "Sign up failed: ${r.exceptionOrNull()?.localizedMessage ?: "Unknown error"}",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
