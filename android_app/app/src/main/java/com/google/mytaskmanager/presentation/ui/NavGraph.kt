package com.google.mytaskmanager.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.mytaskmanager.presentation.auth.LoginScreen
import com.google.mytaskmanager.presentation.auth.OnboardingScreen
import com.google.mytaskmanager.presentation.auth.SignUpScreen
import com.google.mytaskmanager.presentation.auth.AuthViewModel
import com.google.mytaskmanager.presentation.ui.boards.BoardsScreen

@Composable
fun NavGraph() {
    val nav = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    NavHost(navController = nav, startDestination = "splash") {
        composable("splash") {
            // decide where to navigate based on persisted login state
            val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
            androidx.compose.runtime.LaunchedEffect(isLoggedIn) {
                if (isLoggedIn) {
                    nav.navigate("boards") { popUpTo("splash") { inclusive = true } }
                } else {
                    nav.navigate("onboarding") { popUpTo("splash") { inclusive = true } }
                }
            }
        }
        composable("onboarding") {
            OnboardingScreen(onGetStarted = {
                nav.navigate("login") {
                    popUpTo(
                        "onboarding"
                    )
                }
            })
        }
        composable("login") {
            LoginScreen(
                onLoginSuccess = { nav.navigate("boards") { popUpTo("login") } },
                onSignUpRequested = { nav.navigate("signup") },
                onBack = { nav.popBackStack() })
        }
        composable("signup") {
            SignUpScreen(
                onSignUpSuccess = { nav.navigate("boards") { popUpTo("signup") } },
                onSignInRequested = { nav.navigate("login") },
                onBack = { nav.popBackStack() })
        }
        composable("boards") {
            BoardsScreen(onLogout = {
                // perform logout via viewModel and navigate to login
                authViewModel.logout()
                nav.navigate("login") { popUpTo("boards") { inclusive = true } }
            })
        }
        composable("board/{id}") { /* Board details screen */ }
    }
}
