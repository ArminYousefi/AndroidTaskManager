package com.google.mytaskmanager.presentation.ui.boards

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.lucide.Wifi
import com.composables.icons.lucide.WifiOff
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Rows3
import com.composables.icons.lucide.Plus
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.lucide.Brush
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.User
import com.google.mytaskmanager.domain.model.Board
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BoardsScreen(
    onOpenBoard: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: BoardsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Boards", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    // Sync status icon
                    IconButton(onClick = { viewModel.connectOrSync() }) {
                        if (state.isConnected) {
                            Icon(Lucide.Wifi, contentDescription = "Connected", tint = Color(0xFF00C853))
                        } else {
                            Icon(Lucide.WifiOff, contentDescription = "Disconnected", tint = Color(0xFFD50000))
                        }
                    }
                    // Toggle grid/list
                    IconButton(onClick = { viewModel.toggleView() }) {
                        Icon(imageVector = if (state.isGridView) Lucide.LayoutGrid else Lucide.Rows3, contentDescription = "Toggle view")
                    }
                    // Delete all boards
                    IconButton(onClick = {
                        coroutineScope.launch {
                            viewModel.syncBoards() // reuse to refresh after deletion
                        }
                    }) {
                        Icon(Lucide.Trash2, contentDescription = "Delete all", tint = Color.Red)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showCreateDialog() }, shape = RoundedCornerShape(12.dp)) {
                Icon(Lucide.Plus, contentDescription = "Add board")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Lucide.House, contentDescription = null) }, label = { Text("Boards") })
                NavigationBarItem(selected = false, onClick = {}, icon = { Icon(Lucide.User, contentDescription = null) }, label = { Text("Profile") })
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.boards.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No boards yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                if (state.isGridView) {
                    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        items(state.boards) { b ->
                            BoardCard(b, onOpenBoard = onOpenBoard, onEdit = { viewModel.showEditDialog(b.id) }, onDelete = { viewModel.deleteBoard(b.id) }, onEmpty = { viewModel.emptyBoard(b.id) })
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        items(state.boards) { b ->
                            BoardCard(b, onOpenBoard = onOpenBoard, onEdit = { viewModel.showEditDialog(b.id) }, onDelete = { viewModel.deleteBoard(b.id) }, onEmpty = { viewModel.emptyBoard(b.id) })
                        }
                    }
                }
            }
        }
    }

    if (state.showCreateDialog) {
        CreateBoardDialog(onCreate = { title -> viewModel.createBoard(title) }, onDismiss = { viewModel.dismissCreateDialog() })
    }
    if (state.showEditDialog) {
        EditBoardDialog(board = state.editingBoard, onUpdate = { viewModel.updateBoard(it) }, onDismiss = { viewModel.dismissEditDialog() })
    }
}

@Composable
fun BoardCard(board: Board, onOpenBoard: (String)->Unit, onEdit: ()->Unit, onDelete: ()->Unit, onEmpty: ()->Unit) {
    Card(modifier = Modifier
        .padding(8.dp)
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier
            .clickable { onOpenBoard(board.id) }
            .padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(board.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { /* overflow - not implemented */ }) {
                    Icon(Lucide.EllipsisVertical, contentDescription = "Menu")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("${board.listCount} lists    ${board.taskCount} tasks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(board.updatedAtText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onEdit) { Icon(Lucide.Pencil, contentDescription = "Edit"); Spacer(modifier = Modifier.width(4.dp)); Text("Edit") }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onEmpty) { Icon(Lucide.Brush, contentDescription = "Empty"); Spacer(modifier = Modifier.width(4.dp)); Text("Empty") }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Icon(Lucide.Trash2, contentDescription = "Delete", tint = Color.Red); Spacer(modifier = Modifier.width(4.dp)); Text("Delete", color = Color.Red) }
            }
        }
    }
}

@Composable
fun CreateBoardDialog(onCreate: (String)->Unit, onDismiss: ()->Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Board") },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Board Title") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title) }) { Text("Create Board") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditBoardDialog(board: Board?, onUpdate: (Board)->Unit, onDismiss: ()->Unit) {
    var title by remember { mutableStateOf(board?.title ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Board") },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Board Title") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp))
        },
        confirmButton = {
            TextButton(onClick = { board?.let { onUpdate(it.copy(title = title)) } }) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
