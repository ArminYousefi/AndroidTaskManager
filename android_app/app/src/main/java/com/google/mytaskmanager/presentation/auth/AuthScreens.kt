package com.google.mytaskmanager.presentation.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mytaskmanager.domain.rules.NicknamePolicy
import com.google.mytaskmanager.domain.rules.PasswordPolicy
import com.google.mytaskmanager.domain.rules.UsernamePolicy
import com.google.mytaskmanager.ui.components.*
import com.google.mytaskmanager.ui.theme.AppTheme

/** Brand mark: gradient squircle with a check, shown above the auth titles. */
@Composable
internal fun BrandMark() {
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, AppTheme.extra.glowB))),
        contentAlignment = Alignment.Center
    ) { Text("✓", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold) }
}

/** Inline error banner (kept next to the form so the user sees what to fix without hunting for a snackbar). */
@Composable
private fun ErrorBanner(message: String?) {
    AnimatedVisibility(visible = message != null) {
        Text(
            message.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)).padding(14.dp)
        )
    }
}

/** Sign-in form. */
@Composable
fun LoginScreen(onGoToSignUp: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val canSubmit = username.isNotBlank() && password.isNotEmpty()

    AuroraBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            BrandMark()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Welcome back", style = MaterialTheme.typography.headlineMedium)
                Text("Sign in to pick up where your team left off.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ErrorBanner(state.error)
            AppTextField(username, { username = it; vm.clearError() }, "Username", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            PasswordField(password, { password = it; vm.clearError() }, "Password", keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password))
            GradientButton("Sign in", { vm.login(username, password) }, Modifier.fillMaxWidth(), enabled = canSubmit, loading = state.loading)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("New here?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onGoToSignUp) { Text("Create an account") }
            }
        }
    }
}

/**
 * Account creation. The password box checks the same rules the server enforces, live, with a tick or cross per rule;
 * the button stays disabled until every field is valid, so the user is never surprised by a server rejection.
 */
@Composable
fun SignUpScreen(onBack: () -> Unit, vm: AuthViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var username by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    val rules = PasswordPolicy.evaluate(password, username.trim())
    val matches = confirm.isNotEmpty() && confirm == password
    val usernameOk = UsernamePolicy.isValid(username.trim())
    val nickError = if (nickname.isBlank()) null else NicknamePolicy.validate(nickname)?.message
    val emailOk = email.isBlank() || (email.contains('@') && email.length <= 255)
    val canSubmit = usernameOk && nickError == null && emailOk && PasswordPolicy.isValid(rules) && matches

    AuroraBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Back") }
            BrandMark()
            Text("Create your account", style = MaterialTheme.typography.headlineMedium)
            ErrorBanner(state.error)
            AppTextField(
                username, { username = it; vm.clearError() }, "Username",
                error = if (username.isNotEmpty() && !usernameOk) "3-30 characters: letters, numbers, . _ -" else null,
                help = "Used to sign in. It can't be changed later."
            )
            AppTextField(nickname, { nickname = it }, "Nickname (optional)", error = nickError, help = "Shown to your teammates. You can change it any time.")
            AppTextField(email, { email = it }, "Email (optional)", error = if (!emailOk) "Enter a valid email" else null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            PasswordField(password, { password = it; vm.clearError() }, "Password")
            PasswordChecklist(password, rules, PasswordPolicy.strength(password, rules), extra = listOf("Passwords match" to matches))
            PasswordField(confirm, { confirm = it }, "Confirm password")
            GradientButton("Create account", { vm.signup(username, password, email.ifBlank { null }, nickname.ifBlank { null }) }, Modifier.fillMaxWidth(), enabled = canSubmit, loading = state.loading)
            Spacer(Modifier.height(24.dp))
        }
    }
}
