package com.google.mytaskmanager.presentation.team

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mytaskmanager.domain.model.Member
import com.google.mytaskmanager.domain.model.Team
import com.google.mytaskmanager.domain.rules.TeamRole
import com.google.mytaskmanager.presentation.common.AddPersonDialog
import com.google.mytaskmanager.presentation.common.TeamRoleChoices
import com.google.mytaskmanager.ui.components.*
import com.google.mytaskmanager.ui.icons.AppIcons

/** Teams tab: groups of people you can give access to boards in one step. */
@Composable
fun TeamsScreen(onOpenTeam: (String) -> Unit, vm: TeamsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text("Teams", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
            when {
                state.loading -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(4) { ShimmerBox(Modifier.fillMaxWidth().height(76.dp), MaterialTheme.shapes.large) } }
                state.teams.isEmpty() -> EmptyState(AppIcons.Team, "No teams yet", "Create a team, add people, then give the whole team access to a board with one tap.", Modifier.padding(top = 40.dp), "Create team") { showCreate = true }
                else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.teams, key = { it.id }) { t -> TeamRow(t, Modifier.animateItem()) { onOpenTeam(t.id) } }
                }
            }
        }
        ExtendedFloatingActionButton(onClick = { showCreate = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp), containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
            Icon(AppIcons.Add, null); Spacer(Modifier.width(8.dp)); Text("New team")
        }
    }
    if (showCreate) TextInputDialog("New team", "Team name", confirmLabel = "Create", validate = { if (it.length < 2) "At least 2 characters" else if (it.length > 100) "Max 100 characters" else null },
        onConfirm = { vm.create(it, null) { t -> onOpenTeam(t.id) }; showCreate = false }, onDismiss = { showCreate = false })
}

@Composable
private fun TeamRow(t: Team, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(AppIcons.Team, null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f)) { Text(t.name, style = MaterialTheme.typography.titleMedium); Text("${t.memberCount} people", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        RoleChip(t.role.name)
        Icon(AppIcons.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One team's page: members with roles, invite, rename, leave/delete. Controls appear only for people allowed to use them. */
@Composable
fun TeamDetailScreen(onBack: () -> Unit, vm: TeamDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val me by vm.me.collectAsState()
    LaunchedEffect(state.gone) { if (state.gone) onBack() }
    val team = state.team
    var dialog by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf<Member?>(null) }
    var removing by remember { mutableStateOf<Member?>(null) }
    val admin = team?.role == TeamRole.ADMIN
    val owner = team != null && team.ownerId == me?.id

    Scaffold(topBar = { AppTopBar(team?.name ?: "Team", onBack = onBack, subtitle = team?.let { "${it.memberCount} people" }) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (state.loading) { Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Scaffold }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("People")
            SoftCard {
                state.members.forEach { m ->
                    Row(Modifier.fillMaxWidth().then(if (admin && m.userId != team?.ownerId) Modifier.clickable { target = m } else Modifier).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(m.displayName, size = 38.dp)
                        Column(Modifier.weight(1f)) {
                            Text(m.displayName + if (m.userId == me?.id) "  (you)" else "", style = MaterialTheme.typography.titleSmall)
                            Text("@${m.username}" + if (m.userId == team?.ownerId) " · Owner" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RoleChip(m.role)
                    }
                }
                if (admin) TextButton(onClick = { dialog = "add" }, modifier = Modifier.padding(start = 8.dp)) { Icon(AppIcons.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add people") }
            }
            Text("Give this team access to a board from the board's settings → People.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            SectionTitle("Manage")
            SoftCard {
                if (admin) ManageRow("Rename team") { dialog = "rename" }
                if (!owner) ManageRow("Leave team", danger = true) { dialog = "leave" }
                if (owner) ManageRow("Delete team", danger = true) { dialog = "delete" }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    when (dialog) {
        "add" -> AddPersonDialog("Add to team", TeamRoleChoices, state.suggestions, vm::search, { u, r -> vm.add(u, r); dialog = null }, { dialog = null })
        "rename" -> TextInputDialog("Rename team", "Team name", initial = team?.name.orEmpty(), validate = { if (it.length < 2) "At least 2 characters" else null }, onConfirm = { vm.rename(it, team?.description); dialog = null }, onDismiss = { dialog = null })
        "leave" -> ConfirmDialog("Leave team?", "Boards shared with this team stop being visible to you.", "Leave", danger = true, onConfirm = { dialog = null; vm.leave() }, onDismiss = { dialog = null })
        "delete" -> ConfirmDialog("Delete team?", "Everyone loses the board access this team gave them.", "Delete team", danger = true, onConfirm = { dialog = null; vm.delete() }, onDismiss = { dialog = null })
    }
    target?.let { m ->
        ChoiceDialog("${m.displayName}", TeamRoleChoices + ("REMOVE" to "Remove from team"), { it.second }, { c -> target = null; if (c.first == "REMOVE") removing = m else vm.setRole(m, c.first) }, { target = null })
    }
    removing?.let { m -> ConfirmDialog("Remove ${m.displayName}?", "They lose any board access that came from this team.", "Remove", danger = true, onConfirm = { vm.remove(m); removing = null }, onDismiss = { removing = null }) }
}

@Composable
private fun ManageRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(label, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 16.dp), style = MaterialTheme.typography.titleSmall, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
}
