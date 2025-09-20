package com.google.mytaskmanager.presentation.ui.board

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.mytaskmanager.domain.model.BoardList
import com.google.mytaskmanager.domain.model.Task

@Composable
fun BoardDetailScreen(
    boardId: String,
    viewModel: BoardDetailViewModel = hiltViewModel()
) {
    val listsFlow = viewModel.getLists(boardId)
    val tasksFlow = viewModel.getTasks(boardId)

    val lists = listsFlow.collectAsState(initial = emptyList()).value
    val tasks = tasksFlow.collectAsState(initial = emptyList()).value

    LazyColumn(modifier = Modifier.padding(8.dp)) {
        lists.forEach { list ->
            // One item for the list title
            item {
                Text(
                    text = "List: ${list.title}",
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Items for tasks in this list
            val tasksForList = tasks.filter { it.listId == list.id }
            items(tasksForList) { t ->
                Text(
                    text = "  - ${t.title}",
                    modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp)
                )
            }
        }
    }
}

