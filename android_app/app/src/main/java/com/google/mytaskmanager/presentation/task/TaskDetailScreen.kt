package com.google.mytaskmanager.presentation.task

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mytaskmanager.domain.model.Member
import com.google.mytaskmanager.domain.rules.Permission
import com.google.mytaskmanager.domain.rules.BoardRole
import com.google.mytaskmanager.domain.rules.TaskPriority
import com.google.mytaskmanager.domain.rules.TaskType
import com.google.mytaskmanager.presentation.activity.ActivityRow
import com.google.mytaskmanager.presentation.board.ReplanDialog
import com.google.mytaskmanager.presentation.logic.ActivityFormatter
import com.google.mytaskmanager.ui.components.*
import com.google.mytaskmanager.ui.icons.AppIcons
import com.google.mytaskmanager.ui.theme.AppTheme

private fun String.pretty() = lowercase().replaceFirstChar { it.uppercase() }

/**
 * The issue view, laid out like Jira's: title, status (column) switcher, description, a details panel (assignee,
 * reporter, priority, type, dates, labels) and Comments / History tabs. Every field is editable in place by anyone with
 * edit rights; viewers see the same screen read-only.
 */
@Composable
fun TaskDetailScreen(onBack: () -> Unit, vm: TaskDetailViewModel = hiltViewModel()) {
    val task by vm.task.collectAsState()
    val board by vm.board.collectAsState()
    val lists by vm.lists.collectAsState()
    val members by vm.members.collectAsState()
    val me by vm.me.collectAsState()
    val state by vm.state.collectAsState()
    val t = task

    LaunchedEffect(state.gone) { if (state.gone) onBack() }
    if (t == null) {
        Scaffold(topBar = { AppTopBar("Issue", onBack = onBack) }) { p -> Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        return
    }

    val canEdit = board?.can(Permission.EDIT_TASK) == true
    val canMove = board?.can(Permission.MOVE_TASK) == true
    val canDelete = board?.can(Permission.DELETE_TASK) == true
    val canComment = board?.can(Permission.COMMENT) == true
    val isAdmin = board?.role == BoardRole.ADMIN
    val byId = remember(members) { members.associateBy { it.userId } }
    val column = lists.firstOrNull { it.id == t.listId }

    var edit by remember { mutableStateOf<String?>(null) }
    var statusMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var comment by rememberSaveable { mutableStateOf("") }
    val now = remember(state.history, state.comments) { System.currentTimeMillis() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(t.key, onBack = onBack, subtitle = board?.name, actions = {
                if (canDelete) IconButton(onClick = { confirmDelete = true }) { Icon(AppIcons.Delete, "Delete issue", tint = MaterialTheme.colorScheme.error) }
            })
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // ---- title + status
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TypeBadge(t.type, Modifier.padding(top = 6.dp))
                Text(t.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).then(if (canEdit) Modifier.clickable { edit = "title" } else Modifier))
            }
            Box {
                val accent = column?.let { AppTheme.extra.category(it.category) } ?: MaterialTheme.colorScheme.outline
                Row(
                    Modifier.clip(MaterialTheme.shapes.large).background(accent.copy(alpha = 0.16f)).then(if (canMove) Modifier.clickable { statusMenu = true } else Modifier).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
                    Text(column?.title?.uppercase() ?: "—", style = MaterialTheme.typography.labelLarge, color = accent)
                    if (canMove) Icon(AppIcons.ChevronDown, null, Modifier.size(18.dp), tint = accent)
                }
                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                    lists.forEach { l -> DropdownMenuItem(text = { Text(l.title) }, onClick = { statusMenu = false; vm.moveTo(l) }) }
                }
            }

            // ---- description
            SectionTitle("Description")
            SoftCard {
                Text(
                    t.description ?: if (canEdit) "Add a description…" else "No description",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (t.description == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().then(if (canEdit) Modifier.clickable { edit = "description" } else Modifier).padding(16.dp)
                )
            }

            // ---- details
            SectionTitle("Details")
            SoftCard {
                DetailRow("Assignee", canEdit, { edit = "assignee" }) {
                    val a = byId[t.assigneeId]
                    if (a != null) { Avatar(a.displayName, size = 22.dp); Spacer(Modifier.width(8.dp)); Text(a.displayName) } else Text("Unassigned", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DetailRow("Reporter", false, {}) { Text(byId[t.reporterId]?.displayName ?: "—") }
                DetailRow("Priority", canEdit, { edit = "priority" }) { PriorityGlyph(t.priority); Spacer(Modifier.width(8.dp)); Text(t.priority.name.pretty()) }
                DetailRow("Type", canEdit, { edit = "type" }) { Text(t.type.name.pretty()) }
                DetailRow("Dates", canEdit, { edit = "replan" }) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Start: " + (t.startDate?.let { ActivityFormatter.date(it.toString()) } ?: "—"), style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text("Due: " + (t.dueDate?.let { ActivityFormatter.date(it.toString()) } ?: "—"), style = MaterialTheme.typography.bodySmall); com.google.mytaskmanager.presentation.board.DueChip(t) }
                    }
                }
                DetailRow("Labels", canEdit, { edit = "labels" }) { Text(if (t.labels.isEmpty()) "None" else t.labels.joinToString(", "), color = if (t.labels.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface) }
                DetailRow("Created", false, {}) { Text(ActivityFormatter.relativeTime(t.createdAt, now), style = MaterialTheme.typography.bodySmall) }
                DetailRow("Updated", false, {}) { Text(ActivityFormatter.relativeTime(t.updatedAt, now), style = MaterialTheme.typography.bodySmall) }
            }

            // ---- tabs
            TabRow(selectedTabIndex = tab, containerColor = Color0, divider = {}) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Comments (${state.comments.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
            }
            if (tab == 0) {
                if (state.comments.isEmpty()) Text(if (state.loadingExtras) "Loading…" else "No comments yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                state.comments.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(c.authorName, size = 34.dp)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(c.authorName, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.width(8.dp))
                                Text(ActivityFormatter.relativeTime(c.createdAt, now), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.weight(1f))
                                if (c.authorId == me?.id || isAdmin) TextButton(onClick = { vm.deleteComment(c) }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Delete", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
                            }
                            Text(c.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (canComment) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(comment, { comment = it }, Modifier.weight(1f), placeholder = { Text("Add a comment…") }, shape = MaterialTheme.shapes.large, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default), maxLines = 4)
                    Button(onClick = { vm.addComment(comment) { comment = "" } }, enabled = comment.isNotBlank() && !state.sending, shape = MaterialTheme.shapes.large) { Text("Send") }
                } else Text("Viewers can't comment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (state.history.isEmpty()) Text(if (state.loadingExtras) "Loading…" else "No history yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                state.history.forEach { ActivityRow(it, now, Modifier.padding(horizontal = 0.dp)) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    // ---- edit dialogs
    when (edit) {
        "title" -> TextInputDialog("Edit title", "Title", initial = t.title, singleLine = false, validate = { if (it.isBlank()) "Title can't be empty" else if (it.length > 255) "Max 255 characters" else null }, onConfirm = { vm.rename(it); edit = null }, onDismiss = { edit = null })
        "description" -> TextInputDialog("Description", "Description", initial = t.description.orEmpty(), singleLine = false, confirmLabel = "Save", onConfirm = { vm.setDescription(it); edit = null }, onDismiss = { edit = null })
        "labels" -> TextInputDialog("Labels", "Comma-separated labels", initial = t.labels.joinToString(", "), help = "Up to 10 labels, 30 characters each.", onConfirm = { vm.setLabels(it.split(',').map { l -> l.trim() }.filter { l -> l.isNotEmpty() }); edit = null }, onDismiss = { edit = null })
        "priority" -> ChoiceDialog("Priority", TaskPriority.entries, { it.name.pretty() }, { vm.setPriority(it); edit = null }, { edit = null }, selected = t.priority)
        "type" -> ChoiceDialog("Issue type", TaskType.entries, { it.name.pretty() }, { vm.setType(it); edit = null }, { edit = null }, selected = t.type)
        "assignee" -> ChoiceDialog<Member?>("Assignee", listOf<Member?>(null) + members, { it?.displayName ?: "Unassigned" }, { vm.assign(it?.userId); edit = null }, { edit = null }, selected = byId[t.assigneeId])
        "replan" -> ReplanDialog(t, onConfirm = { s, d -> vm.replan(s, d); edit = null }, onDismiss = { edit = null })
    }
    if (confirmDelete) ConfirmDialog("Delete ${t.key}?", "This removes the issue from the board. The deletion is recorded in the activity log.", "Delete", danger = true, onConfirm = { confirmDelete = false; vm.delete() }, onDismiss = { confirmDelete = false })
}

private val Color0 = androidx.compose.ui.graphics.Color.Transparent

@Composable
private fun DetailRow(label: String, editable: Boolean, onClick: () -> Unit, value: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (editable) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = value)
        if (editable) Icon(AppIcons.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
