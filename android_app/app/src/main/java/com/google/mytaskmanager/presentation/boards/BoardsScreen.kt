package com.google.mytaskmanager.presentation.boards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mytaskmanager.domain.model.Board
import com.google.mytaskmanager.domain.rules.BoardKeys
import com.google.mytaskmanager.domain.rules.BoardTemplate
import com.google.mytaskmanager.domain.rules.Permission
import com.google.mytaskmanager.ui.components.*
import com.google.mytaskmanager.ui.icons.AppIcons
import com.google.mytaskmanager.ui.theme.AppTheme

/**
 * Boards tab (Jira "Projects" list): search, grid/list toggle, pull to refresh, and a working overflow menu per board.
 * Menu entries are role-aware: only admins see rename / settings / empty / delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardsScreen(onOpenBoard: (String) -> Unit, onOpenBoardSettings: (String) -> Unit, vm: BoardsViewModel = hiltViewModel()) {
    val boards by vm.boards.collectAsState()
    val state by vm.state.collectAsState()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renameId by rememberSaveable { mutableStateOf<String?>(null) }
    var emptyId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    fun find(id: String?) = boards.firstOrNull { it.id == id }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Boards", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = vm::toggleGrid) { Icon(if (state.grid) AppIcons.BoardsList else AppIcons.BoardsGrid, if (state.grid) "Show as list" else "Show as grid") }
                IconButton(onClick = { vm.refresh() }) { Icon(AppIcons.Refresh, "Refresh") }
            }
            OutlinedTextField(
                value = state.query, onValueChange = vm::setQuery, singleLine = true, shape = RoundedCornerShape(18.dp),
                placeholder = { Text("Search boards") }, leadingIcon = { Icon(AppIcons.Search, null) },
                trailingIcon = { if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(AppIcons.Close, "Clear search") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                when {
                    state.loading && boards.isEmpty() -> LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(6) { ShimmerBox(Modifier.fillMaxWidth().height(150.dp)) }
                    }
                    boards.isEmpty() -> EmptyState(
                        AppIcons.BoardsGrid,
                        if (state.query.isBlank()) "No boards yet" else "No matches",
                        if (state.query.isBlank()) "Create your first board - it starts with To Do, In Progress, In Review and Done." else "Try a different name or key.",
                        Modifier.align(Alignment.Center), if (state.query.isBlank()) "Create board" else null, if (state.query.isBlank()) ({ showCreate = true }) else null
                    )
                    state.grid -> LazyVerticalGrid(
                        GridCells.Fixed(2), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(boards, key = { it.id }) { b ->
                            BoardCard(b, Modifier.animateItem(), { onOpenBoard(b.id) }, { onOpenBoardSettings(b.id) }, { renameId = b.id }, { emptyId = b.id }, { deleteId = b.id })
                        }
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(boards, key = { it.id }) { b ->
                            BoardCard(b, Modifier.animateItem(), { onOpenBoard(b.id) }, { onOpenBoardSettings(b.id) }, { renameId = b.id }, { emptyId = b.id }, { deleteId = b.id }, wide = true)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showCreate = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(20.dp)
        ) { Icon(AppIcons.Add, null); Spacer(Modifier.width(8.dp)); Text("New board") }
    }

    if (showCreate) CreateBoardDialog(onDismiss = { showCreate = false }) { name, key, desc, template ->
        showCreate = false
        vm.create(name, key, desc, template) { onOpenBoard(it.id) }
    }
    find(renameId)?.let { b ->
        TextInputDialog("Rename board", "Board name", initial = b.name, validate = { if (it.isBlank()) "Name can't be empty" else if (it.length > 100) "Max 100 characters" else null },
            onConfirm = { vm.rename(b, it); renameId = null }, onDismiss = { renameId = null })
    }
    find(emptyId)?.let { b ->
        ConfirmDialog("Empty “${b.name}”?", "All ${b.taskCount} tasks on this board will be deleted. Columns and members stay.", "Delete all tasks", danger = true,
            onConfirm = { vm.empty(b); emptyId = null }, onDismiss = { emptyId = null })
    }
    find(deleteId)?.let { b ->
        ConfirmDialog("Delete “${b.name}”?", "The board, its columns, tasks and history are deleted for everyone. This can't be undone.", "Delete board", danger = true,
            onConfirm = { vm.delete(b); deleteId = null }, onDismiss = { deleteId = null })
    }
}

/** A board tile: key badge, name, progress and role, plus the ⋮ menu. */
@Composable
private fun BoardCard(
    board: Board, modifier: Modifier, onOpen: () -> Unit, onSettings: () -> Unit, onRename: () -> Unit, onEmpty: () -> Unit, onDelete: () -> Unit, wide: Boolean = false
) {
    var menu by remember { mutableStateOf(false) }
    val admin = board.can(Permission.EDIT_BOARD)
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surface).clickable(onClick = onOpen).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, AppTheme.extra.glowB))).padding(horizontal = 10.dp, vertical = 6.dp)
            ) { Text(board.key, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(AppIcons.More, "Board menu") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Open board") }, onClick = { menu = false; onOpen() })
                    if (admin) {
                        DropdownMenuItem(text = { Text("Board settings") }, onClick = { menu = false; onSettings() })
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    }
                    if (board.can(Permission.EMPTY_BOARD)) DropdownMenuItem(text = { Text("Empty board") }, onClick = { menu = false; onEmpty() })
                    if (board.can(Permission.DELETE_BOARD)) DropdownMenuItem(text = { Text("Delete board", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Text(board.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (wide && !board.description.isNullOrBlank()) Text(board.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProgressRing(board.progress, size = 40.dp, stroke = 4.dp)
            Column(Modifier.weight(1f)) {
                Text("${board.doneCount}/${board.taskCount} done", style = MaterialTheme.typography.labelMedium)
                Text("${board.listCount} columns", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        RoleChip(board.role.name)
    }
}

/** New-board dialog: name, optional key (auto-suggested), description and a template that decides the starting columns. */
@Composable
private fun CreateBoardDialog(onDismiss: () -> Unit, onCreate: (String, String?, String?, BoardTemplate) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var key by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var template by rememberSaveable { mutableStateOf(BoardTemplate.STANDARD) }
    val suggested = if (name.isBlank()) "KEY" else BoardKeys.generate(name)
    val keyError = if (key.isNotBlank() && !BoardKeys.isValid(key.trim().uppercase())) "2-10 letters/numbers, start with a letter" else null
    AlertDialog(
        onDismissRequest = onDismiss, shape = MaterialTheme.shapes.extraLarge, title = { Text("New board") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(name, { name = it }, "Board name")
                AppTextField(key, { key = it.uppercase().take(10) }, "Key (optional)", error = keyError, help = "Issues will be named $suggested-1, $suggested-2 ...")
                AppTextField(desc, { desc = it }, "Description (optional)", singleLine = false, minLines = 2)
                Text("Template", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoardTemplate.entries.forEach { t -> FilterChip(selected = template == t, onClick = { template = t }, label = { Text(t.displayName) }) }
                }
                Text(template.columns.joinToString("  ›  ") { it.title }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && name.length <= 100 && keyError == null, onClick = { onCreate(name.trim(), key.trim().ifEmpty { null }, desc.trim().ifEmpty { null }, template) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
