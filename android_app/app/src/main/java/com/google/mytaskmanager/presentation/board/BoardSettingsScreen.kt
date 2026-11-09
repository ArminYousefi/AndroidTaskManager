package com.google.mytaskmanager.presentation.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mytaskmanager.domain.model.BoardList
import com.google.mytaskmanager.domain.model.BoardTeam
import com.google.mytaskmanager.domain.model.Member
import com.google.mytaskmanager.domain.rules.BoardRole
import com.google.mytaskmanager.domain.rules.ColumnCategory
import com.google.mytaskmanager.domain.rules.Permission
import com.google.mytaskmanager.presentation.common.AddPersonDialog
import com.google.mytaskmanager.presentation.common.BoardRoleChoices
import com.google.mytaskmanager.ui.components.*
import com.google.mytaskmanager.ui.icons.AppIcons
import com.google.mytaskmanager.ui.theme.AppTheme

/**
 * Board settings (Jira: Board settings -> Details / Columns / People): rename, describe, manage columns (order, WIP
 * limits, category, delete) and control who has access with which role. Everything an admin can change is here;
 * non-admins see the same page read-only plus "Leave board".
 */
@Composable
fun BoardSettingsScreen(onBack: () -> Unit, vm: BoardViewModel = hiltViewModel(), access: AccessViewModel = hiltViewModel()) {
    val board by vm.board.collectAsState()
    val columns by vm.columns.collectAsState()
    val me by vm.meId.collectAsState()
    val state by vm.state.collectAsState()
    val acc by access.state.collectAsState()
    val b = board
    LaunchedEffect(state.gone) { if (state.gone) onBack() }
    if (b == null) { Scaffold(topBar = { AppTopBar("Board settings", onBack = onBack) }) { p -> Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }; return }

    val admin = b.can(Permission.MANAGE_COLUMNS)
    var dialog by remember { mutableStateOf<String?>(null) }
    var targetList by remember { mutableStateOf<BoardList?>(null) }
    var deleteCol by remember { mutableStateOf<ColumnUi?>(null) }
    var targetMember by remember { mutableStateOf<Member?>(null) }
    var targetTeam by remember { mutableStateOf<BoardTeam?>(null) }
    var removeMember by remember { mutableStateOf<Member?>(null) }
    var pickedTeam by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { AppTopBar("Board settings", onBack = onBack, subtitle = "${b.name} · ${b.key}") }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ---------------- details
            SectionTitle("Details")
            SoftCard {
                Row2("Name", b.name, admin) { dialog = "name" }
                Row2("Description", b.description ?: "None", admin) { dialog = "desc" }
                Row2("Key", b.key + "  (prefix of issue ids)", false) {}
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text("Your role", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); RoleChip(b.role.name) }
            }

            // ---------------- columns
            SectionTitle("Columns")
            SoftCard {
                columns.forEachIndexed { i, c ->
                    var menu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(AppTheme.extra.category(c.list.category)))
                        Column(Modifier.weight(1f)) {
                            Text(c.list.title, style = MaterialTheme.typography.titleSmall)
                            Text("${c.totalCount} issues · " + (c.list.wipLimit?.let { "limit $it" } ?: "no limit") + " · " + c.list.category.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (admin) {
                            IconButton(onClick = { vm.moveColumn(c.list, -1) }, enabled = i > 0) { Text("↑") }
                            IconButton(onClick = { vm.moveColumn(c.list, 1) }, enabled = i < columns.lastIndex) { Text("↓") }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(AppIcons.More, "Column options") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; targetList = c.list; dialog = "col-rename" })
                                    DropdownMenuItem(text = { Text("WIP limit") }, onClick = { menu = false; targetList = c.list; dialog = "col-wip" })
                                    DropdownMenuItem(text = { Text("Status category") }, onClick = { menu = false; targetList = c.list; dialog = "col-cat" })
                                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleteCol = c })
                                }
                            }
                        }
                    }
                }
                if (admin) TextButton(onClick = { dialog = "col-add" }, modifier = Modifier.padding(start = 8.dp)) { Icon(AppIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add column") }
            }

            // ---------------- people
            SectionTitle("People")
            SoftCard {
                if (acc.loading) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                acc.members.forEach { m ->
                    val isOwner = m.userId == b.ownerId
                    Row(
                        Modifier.fillMaxWidth().then(if (admin && !m.viaTeam && !isOwner) Modifier.clickable { targetMember = m } else Modifier).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Avatar(m.displayName, size = 38.dp)
                        Column(Modifier.weight(1f)) {
                            Text(m.displayName + if (m.userId == me) "  (you)" else "", style = MaterialTheme.typography.titleSmall)
                            Text("@${m.username}" + if (isOwner) " · Owner" else if (m.viaTeam) " · via ${m.source.removePrefix("team:")}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RoleChip(m.role)
                    }
                }
                if (admin) TextButton(onClick = { dialog = "add-person" }, modifier = Modifier.padding(start = 8.dp)) { Icon(AppIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add people") }
            }
            SectionTitle("Teams with access")
            SoftCard {
                if (acc.teams.isEmpty()) Text("No team has been given access.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                acc.teams.forEach { t ->
                    Row(Modifier.fillMaxWidth().then(if (admin) Modifier.clickable { targetTeam = t } else Modifier).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(AppIcons.Team, null, tint = MaterialTheme.colorScheme.primary) }
                        Column(Modifier.weight(1f)) { Text(t.teamName, style = MaterialTheme.typography.titleSmall); Text("${t.memberCount} people", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        RoleChip(t.role.name)
                    }
                }
                if (admin && acc.myTeams.isNotEmpty()) TextButton(onClick = { dialog = "add-team" }, modifier = Modifier.padding(start = 8.dp)) { Icon(AppIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Give a team access") }
            }

            // ---------------- danger zone
            SectionTitle("Danger zone")
            SoftCard {
                if (b.can(Permission.EMPTY_BOARD)) Row2("Empty board", "Delete all ${b.taskCount} issues, keep columns", true) { dialog = "empty" }
                if (b.can(Permission.DELETE_BOARD)) Row2("Delete board", "For everyone, can't be undone", true) { dialog = "delete" }
                if (me != null && me != b.ownerId) Row2("Leave board", "You will lose access", true) { dialog = "leave" }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // ---------------- dialogs
    when (dialog) {
        "name" -> TextInputDialog("Board name", "Name", initial = b.name, validate = { if (it.isBlank()) "Name can't be empty" else if (it.length > 100) "Max 100 characters" else null }, onConfirm = { vm.updateBoard(it, b.description); dialog = null }, onDismiss = { dialog = null })
        "desc" -> TextInputDialog("Description", "Description", initial = b.description.orEmpty(), singleLine = false, onConfirm = { vm.updateBoard(b.name, it.ifBlank { null }); dialog = null }, onDismiss = { dialog = null })
        "col-add" -> AddColumnDialog(onConfirm = { t, c -> vm.addColumn(t, c); dialog = null }, onDismiss = { dialog = null })
        "col-rename" -> targetList?.let { l -> TextInputDialog("Rename column", "Name", initial = l.title, validate = { if (it.isBlank()) "Name can't be empty" else if (it.length > 50) "Max 50 characters" else null }, onConfirm = { vm.renameColumn(l, it); dialog = null }, onDismiss = { dialog = null }) }
        "col-wip" -> targetList?.let { l -> TextInputDialog("WIP limit", "Max cards (empty = none)", initial = l.wipLimit?.toString().orEmpty(), validate = { if (it.isBlank() || it.toIntOrNull() in 1..999) null else "Enter 1-999" }, onConfirm = { vm.setWipLimit(l, it.toIntOrNull()); dialog = null }, onDismiss = { dialog = null }) }
        "col-cat" -> targetList?.let { l -> ChoiceDialog("Status category", ColumnCategory.entries, { it.name.lowercase().replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }, { vm.setColumnCategory(l, it); dialog = null }, { dialog = null }, selected = l.category) }
        "add-person" -> AddPersonDialog("Add people", BoardRoleChoices, acc.suggestions, access::search, { u, r -> access.addMember(u, r); dialog = null }, { dialog = null })
        "add-team" -> ChoiceDialog("Give which team access?", acc.myTeams, { it.name }, { t -> dialog = null; pickedTeam = t.id; dialog = "team-role" }, { dialog = null })
        "team-role" -> ChoiceDialog("Role for the team", BoardRoleChoices, { it.second }, { r -> pickedTeam?.let { access.addTeam(it, r.first) }; dialog = null }, { dialog = null })
        "empty" -> ConfirmDialog("Empty “${b.name}”?", "All ${b.taskCount} issues are deleted. Columns, people and history stay.", "Delete all issues", danger = true, onConfirm = { vm.emptyBoard(); dialog = null }, onDismiss = { dialog = null })
        "delete" -> ConfirmDialog("Delete “${b.name}”?", "Board, issues and history are deleted for everyone.", "Delete board", danger = true, onConfirm = { vm.deleteBoard(); dialog = null }, onDismiss = { dialog = null })
        "leave" -> ConfirmDialog("Leave “${b.name}”?", "You will lose access unless someone adds you again.", "Leave", danger = true, onConfirm = { dialog = null; me?.let { access.leave(it, onBack) } }, onDismiss = { dialog = null })
    }
    deleteCol?.let { c -> DeleteColumnDialog(c, columns.map { it.list }.filter { it.id != c.list.id }, { moveTo, del -> vm.deleteColumn(c.list, moveTo, del); deleteCol = null }, { deleteCol = null }) }
    targetMember?.let { m ->
        ChoiceDialog("${m.displayName}: role", BoardRoleChoices + ("REMOVE" to "Remove from board"), { it.second }, { choice ->
            targetMember = null
            if (choice.first == "REMOVE") removeMember = m else access.setRole(m, choice.first)
        }, { targetMember = null })
    }
    removeMember?.let { m -> ConfirmDialog("Remove ${m.displayName}?", "They lose access to this board.", "Remove", danger = true, onConfirm = { access.remove(m); removeMember = null }, onDismiss = { removeMember = null }) }
    targetTeam?.let { t ->
        ChoiceDialog("Team “${t.teamName}”", BoardRoleChoices + ("REMOVE" to "Remove team access"), { it.second }, { choice ->
            targetTeam = null
            if (choice.first == "REMOVE") access.removeTeam(t) else access.setTeamRole(t, choice.first)
        }, { targetTeam = null })
    }
}

@Composable
private fun Row2(title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyMedium) }
        if (enabled) Icon(AppIcons.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
