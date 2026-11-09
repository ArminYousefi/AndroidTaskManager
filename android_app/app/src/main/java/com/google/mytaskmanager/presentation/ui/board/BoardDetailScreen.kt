package com.google.mytaskmanager.presentation.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mytaskmanager.domain.model.BoardList
import com.google.mytaskmanager.domain.model.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardDetailScreen(
    boardId: String,
    onBack: () -> Unit = {},
    viewModel: BoardDetailViewModel = hiltViewModel()
) {
    // Previously this screen called viewModel.getLists(boardId)/getTasks(boardId)
    // directly in the composable body, which built a brand-new cold Flow (and
    // re-hit the network) on EVERY recomposition. LaunchedEffect(boardId) only
    // (re)runs when boardId actually changes, and load() itself is idempotent.
    LaunchedEffect(boardId) { viewModel.load(boardId) }

    val lists by viewModel.lists.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showCreateListDialog by remember { mutableStateOf(false) }
    var createTaskForListId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Board") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Lucide.ArrowLeft, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateListDialog = true }) {
                Text("+ List")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (lists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No lists yet — tap \"+ List\" to add one", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyRow(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(lists, key = { it.id }) { list ->
                        ListColumn(
                            list = list,
                            tasks = tasks.filter { it.listId == list.id },
                            onAddTask = { createTaskForListId = list.id },
                            onDeleteTask = { taskId -> viewModel.deleteTask(taskId) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateListDialog) {
        CreateListDialog(
            onCreate = { title ->
                viewModel.createList(boardId, title)
                showCreateListDialog = false
            },
            onDismiss = { showCreateListDialog = false }
        )
    }

    createTaskForListId?.let { listId ->
        CreateTaskDialog(
            onCreate = { title ->
                viewModel.createTask(boardId, listId, title)
                createTaskForListId = null
            },
            onDismiss = { createTaskForListId = null }
        )
    }
}

@Composable
private fun ListColumn(
    list: BoardList,
    tasks: List<Task>,
    onAddTask: () -> Unit,
    onDeleteTask: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .width(260.dp)
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp)
    ) {
        Text(list.title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(tasks, key = { it.id }) { task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(task.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { onDeleteTask(task.id) }, modifier = Modifier.size(20.dp)) {
                        Icon(Lucide.X, contentDescription = "Delete task")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onAddTask) { Text("+ Add task") }
    }
}

@Composable
private fun CreateListDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New List") },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("List title") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title) }, enabled = title.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreateTaskDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Task") },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task title") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { onCreate(title) }, enabled = title.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
