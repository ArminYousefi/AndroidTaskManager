package com.google.mytaskmanager.presentation.ui.conflict

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mytaskmanager.data.conflict.ConflictManager
import kotlinx.coroutines.flow.collect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button

@Composable
fun ConflictResolverScreen(viewModel: ConflictResolverViewModel = viewModel()) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { ConflictManager.init(ctx) }

    val conflicts by viewModel.conflicts.collectAsState(initial = emptyList())
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Conflict Resolver", style = MaterialTheme.typography.titleLarge)
        conflicts.forEach { c ->
            Text("Conflict on ${c.entityType} id=${c.localId}") // simple representation
            Text("Local: ${c.localJson}", maxLines = 2)
            Text("Server: ${c.serverJson}", maxLines = 2)
            Row {
                Button(onClick = { ConflictManager.resolveKeepLocal(c) }) {
                    Text("Keep Local")
                }
                Button(onClick = { ConflictManager.resolveKeepServer(c) }) {
                    Text("Keep Server")
                }
            }
        }
        if (conflicts.isEmpty()) {
            Text("No conflicts") 
        }
    }
}
