package com.google.mytaskmanager.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.mytaskmanager.domain.model.User
import com.google.mytaskmanager.ui.components.AppTextField
import com.google.mytaskmanager.ui.components.Avatar

/**
 * Invite dialog shared by boards and teams: type a username (suggestions appear as you type), pick a role, confirm.
 *
 * @param roles pairs of (role value sent to the server, label shown to the user).
 * @param suggestions live search results for what has been typed.
 */
@Composable
fun AddPersonDialog(
    title: String, roles: List<Pair<String, String>>, suggestions: List<User>,
    onQuery: (String) -> Unit, onConfirm: (username: String, role: String) -> Unit, onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(roles.first().first) }
    AlertDialog(
        onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(username, { username = it; onQuery(it) }, "Username or name", help = "Type at least 2 characters to search")
                suggestions.take(5).forEach { u ->
                    Row(Modifier.fillMaxWidth().clickable { username = u.username }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(u.displayName, size = 30.dp)
                        Column { Text(u.displayName, style = MaterialTheme.typography.titleSmall); Text("@${u.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                Text("Role", style = MaterialTheme.typography.labelLarge)
                Column {
                    roles.forEach { (value, label) ->
                        Row(Modifier.fillMaxWidth().clickable { role = value }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = role == value, onClick = { role = value }); Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = username.isNotBlank(), onClick = { onConfirm(username.trim(), role) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Board role choices with a plain-language description of what each can do. */
val BoardRoleChoices = listOf(
    "VIEWER" to "Viewer - can look, can't change anything",
    "MEMBER" to "Member - can create, edit, move and comment",
    "ADMIN" to "Admin - everything, incl. columns and access"
)

/** Team role choices. */
val TeamRoleChoices = listOf("MEMBER" to "Member", "ADMIN" to "Admin - can manage the team's people")
