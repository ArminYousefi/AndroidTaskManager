package com.google.mytaskmanager.presentation.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mytaskmanager.BuildConfig
import com.google.mytaskmanager.domain.model.ThemeMode
import com.google.mytaskmanager.domain.rules.NicknamePolicy
import com.google.mytaskmanager.domain.rules.PasswordPolicy
import com.google.mytaskmanager.ui.components.*
import com.google.mytaskmanager.ui.icons.AppIcons
import com.google.mytaskmanager.ui.theme.AppTheme
import com.google.mytaskmanager.ui.theme.DarkBackground
import com.google.mytaskmanager.ui.theme.DarkSurface
import com.google.mytaskmanager.ui.theme.LightBackground
import com.google.mytaskmanager.ui.theme.Motion

/**
 * Settings tab: profile (nickname editable, username locked), appearance (Light / Dark / Automatic), security
 * (change password), privacy (developer reports) and sign-out.
 */
@Composable
fun SettingsScreen(onChangePassword: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val user by vm.user.collectAsState()
    val theme by vm.theme.collectAsState()
    val diagnostics by vm.diagnostics.collectAsState()
    val state by vm.state.collectAsState()
    var editNickname by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 4.dp, top = 8.dp))

        // ---- profile
        SoftCard {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(user?.displayName ?: "?", size = 64.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(user?.displayName ?: "", style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(AppIcons.Lock, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("@${user?.username ?: ""}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    user?.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            SettingsRow("Nickname", user?.nickname ?: "Not set - your username is shown", AppIcons.Edit) { vm.resetNicknameError(); editNickname = true }
            Text("Username is your login id and can't be changed.", Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---- appearance
        SectionTitle("Appearance")
        SoftCard {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeMode.entries.forEach { mode -> ThemeOption(mode, selected = theme == mode, modifier = Modifier.weight(1f)) { vm.setTheme(mode) } }
            }
            Text("Automatic follows your phone's light/dark setting.", Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // ---- security
        SectionTitle("Security")
        SoftCard { SettingsRow("Change password", "Requires your current password", AppIcons.Lock, onClick = onChangePassword) }

        // ---- privacy
        SectionTitle("Privacy")
        SoftCard {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Send crash reports", style = MaterialTheme.typography.titleSmall)
                    Text("Helps developers fix bugs. Contains no passwords or task content.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = diagnostics, onCheckedChange = vm::setDiagnostics)
            }
        }

        // ---- about + sign out
        SectionTitle("About")
        SoftCard { SettingsRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", AppIcons.Info, onClick = null) }
        OutlinedButton(
            onClick = { confirmLogout = true }, enabled = !state.loggingOut, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
        ) {
            Icon(AppIcons.Logout, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text("Log out", color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (editNickname) {
        TextInputDialog(
            title = "Edit nickname", label = "Nickname", initial = user?.nickname.orEmpty(),
            help = state.nicknameError ?: "2-30 characters. This is what teammates see.",
            validate = { NicknamePolicy.validate(it)?.message },
            onConfirm = { vm.saveNickname(it) { editNickname = false } },
            onDismiss = { editNickname = false }
        )
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "Log out?", message = "Unsynced offline changes on this device will be lost. You'll need to sign in again.",
            confirmLabel = "Log out", danger = true, onConfirm = { confirmLogout = false; vm.logout() }, onDismiss = { confirmLogout = false }
        )
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Icon(AppIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One selectable theme card with a tiny live preview (light, dark, or half-and-half for Automatic). */
@Composable
private fun ThemeOption(mode: ThemeMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), Motion.quick(), label = "b")
    val width by animateDpAsState(if (selected) 2.5.dp else 1.dp, Motion.quick(), label = "w")
    Column(
        modifier.clip(MaterialTheme.shapes.medium).border(width, border, MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(10.dp))) {
            fun pane(bg: Color, card: Color, left: Float, w: Float) {
                drawRect(bg, Offset(left, 0f), Size(w, size.height))
                drawRoundRect(card, Offset(left + w * 0.12f, size.height * 0.2f), Size(w * 0.76f, size.height * 0.2f), androidx.compose.ui.geometry.CornerRadius(6f))
                drawRoundRect(card, Offset(left + w * 0.12f, size.height * 0.55f), Size(w * 0.5f, size.height * 0.2f), androidx.compose.ui.geometry.CornerRadius(6f))
            }
            when (mode) {
                ThemeMode.LIGHT -> pane(LightBackground, Color.White, 0f, size.width)
                ThemeMode.DARK -> pane(DarkBackground, DarkSurface, 0f, size.width)
                ThemeMode.AUTOMATIC -> { pane(LightBackground, Color.White, 0f, size.width / 2); pane(DarkBackground, DarkSurface, size.width / 2, size.width / 2) }
            }
        }
        Text(mode.label, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Change-password screen. The user must type the current password; the new one is checked live against the same rules
 * the server enforces (tick/cross per rule). Submit unlocks only when every rule is met and the confirmation matches.
 */
@Composable
fun ChangePasswordScreen(onBack: () -> Unit, vm: ChangePasswordViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val user by vm.user.collectAsState()
    var current by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }

    val rules = PasswordPolicy.evaluate(new, user?.username, current)
    val matches = confirm.isNotEmpty() && confirm == new
    val canSubmit = current.isNotEmpty() && PasswordPolicy.isValid(rules) && matches

    Scaffold(topBar = { AppTopBar("Change password", onBack = onBack) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)).padding(14.dp))
            }
            PasswordField(current, { current = it; vm.clearErrors() }, "Current password", error = state.currentError)
            PasswordField(new, { new = it; vm.clearErrors() }, "New password")
            PasswordChecklist(new, rules, PasswordPolicy.strength(new, rules), extra = listOf("Passwords match" to matches))
            PasswordField(confirm, { confirm = it }, "Confirm new password")
            GradientButton("Update password", { vm.submit(current, new, onBack) }, Modifier.fillMaxWidth(), enabled = canSubmit, loading = state.loading)
            Text("After changing your password, your other devices are signed out.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}
