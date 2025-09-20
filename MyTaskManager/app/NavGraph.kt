package com.example.taskmanager.presentation.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.mytaskmanager.presentation.ui.boards.BoardsScreen

@Composable
fun NavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "boards") {
        composable("boards") { BoardsScreen(onOpenBoard = { id -> nav.navigate("board/$id") }) }
        composable("board/{id}") { /* Board details screen */ }
    }
}
