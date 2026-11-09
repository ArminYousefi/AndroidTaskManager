package com.google.mytaskmanager.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.mytaskmanager.core.MessageKind
import com.google.mytaskmanager.core.UiMessage
import com.google.mytaskmanager.presentation.activity.ActivityScreen
import com.google.mytaskmanager.presentation.auth.BrandMark
import com.google.mytaskmanager.presentation.auth.LoginScreen
import com.google.mytaskmanager.presentation.auth.OnboardingScreen
import com.google.mytaskmanager.presentation.auth.SignUpScreen
import com.google.mytaskmanager.presentation.board.BoardScreen
import com.google.mytaskmanager.presentation.board.BoardSettingsScreen
import com.google.mytaskmanager.presentation.boards.BoardsScreen
import com.google.mytaskmanager.presentation.common.AppViewModel
import com.google.mytaskmanager.presentation.settings.ChangePasswordScreen
import com.google.mytaskmanager.presentation.settings.SettingsScreen
import com.google.mytaskmanager.presentation.task.TaskDetailScreen
import com.google.mytaskmanager.presentation.team.TeamDetailScreen
import com.google.mytaskmanager.presentation.team.TeamsScreen
import com.google.mytaskmanager.ui.components.AuroraBackground
import com.google.mytaskmanager.ui.icons.AppIcons
import com.google.mytaskmanager.ui.theme.AppTheme
import com.google.mytaskmanager.ui.theme.rememberReduceMotion

/** Navigation routes. Arguments are part of the path. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val MAIN = "main"
    const val CHANGE_PASSWORD = "change_password"
    const val BOARD = "board/{boardId}"
    const val BOARD_SETTINGS = "board_settings/{boardId}"
    const val ACTIVITY = "activity/{boardId}"
    const val TASK = "task/{taskId}"
    const val TEAM = "team/{teamId}"
    fun board(id: String) = "board/$id"
    fun boardSettings(id: String) = "board_settings/$id"
    fun activity(id: String) = "activity/$id"
    fun task(id: String) = "task/$id"
    fun team(id: String) = "team/$id"
}

private val AuthRoutes = setOf(Routes.ONBOARDING, Routes.LOGIN, Routes.SIGNUP)

/** Snackbar content that carries the message kind so the colour can follow it. */
private class AppSnackbarVisuals(val msg: UiMessage) : SnackbarVisuals {
    override val message: String = msg.text + (msg.detail?.let { "  ($it)" } ?: "")
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = true
    override val duration: SnackbarDuration = if (msg.kind == MessageKind.ERROR) SnackbarDuration.Long else SnackbarDuration.Short
}

/**
 * Root of the UI: the navigation graph plus the two global overlays - the snackbar (every error/success in the app
 * lands here via `MessageCenter`) and the connection pill ("Offline - 3 changes waiting").
 * Login/logout navigate automatically from the session state; screens never do it themselves.
 */
@Composable
fun AppRoot(app: AppViewModel) {
    val loggedIn by app.isLoggedIn.collectAsState()
    val seen by app.onboardingSeen.collectAsState()
    val online by app.online.collectAsState()
    val pending by app.pendingChanges.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { app.messages.collect { snackbar.showSnackbar(AppSnackbarVisuals(it)) } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (loggedIn == null || seen == null) {
            AuroraBackground(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BrandMark() } }
        } else {
            AppNavigation(app, loggedIn == true, seen == true)
        }

        AnimatedVisibility(
            visible = loggedIn == true && (!online || pending > 0), modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp),
            enter = slideInVertically { -it } + fadeIn(), exit = slideOutVertically { -it } + fadeOut()
        ) {
            val warn = !online
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(if (warn) AppTheme.extra.warning else MaterialTheme.colorScheme.primary).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.material3.Icon(AppIcons.Offline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                Text(
                    if (!online) (if (pending > 0) "Offline · $pending change${if (pending == 1) "" else "s"} waiting" else "Offline · showing saved data") else "Syncing $pending change${if (pending == 1) "" else "s"}…",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp)) { data ->
            val kind = (data.visuals as? AppSnackbarVisuals)?.msg?.kind ?: MessageKind.INFO
            Snackbar(
                snackbarData = data, shape = RoundedCornerShape(16.dp),
                containerColor = when (kind) { MessageKind.ERROR -> MaterialTheme.colorScheme.error; MessageKind.SUCCESS -> AppTheme.extra.success; MessageKind.INFO -> MaterialTheme.colorScheme.inverseSurface },
                contentColor = when (kind) { MessageKind.ERROR -> MaterialTheme.colorScheme.onError; MessageKind.SUCCESS -> androidx.compose.ui.graphics.Color.White; MessageKind.INFO -> MaterialTheme.colorScheme.inverseOnSurface }
            )
        }
    }
}

@Composable
private fun AppNavigation(app: AppViewModel, loggedIn: Boolean, onboardingSeen: Boolean) {
    val nav = rememberNavController()
    val start = remember { if (loggedIn) Routes.MAIN else if (onboardingSeen) Routes.LOGIN else Routes.ONBOARDING }
    val reduce = rememberReduceMotion()

    // Session changes drive navigation: login -> main, logout/expired session -> login (back stack cleared).
    LaunchedEffect(loggedIn) {
        val route = nav.currentDestination?.route ?: return@LaunchedEffect
        if (loggedIn && route in AuthRoutes) nav.navigate(Routes.MAIN) { popUpTo(0) { inclusive = true } }
        if (!loggedIn && route !in AuthRoutes) nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
    }
    // Tell the crash reporter which screen the user is on.
    DisposableEffect(nav) {
        val l = NavController.OnDestinationChangedListener { _, d, _ -> app.onScreen(d.route) }
        nav.addOnDestinationChangedListener(l)
        onDispose { nav.removeOnDestinationChangedListener(l) }
    }

    fun pop() { nav.popBackStack() }
    val enter: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> EnterTransition =
        { if (reduce) EnterTransition.None else slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(240)) }
    val exit: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> ExitTransition =
        { if (reduce) ExitTransition.None else fadeOut(tween(160)) }
    val popEnter: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> EnterTransition =
        { if (reduce) EnterTransition.None else fadeIn(tween(240)) }
    val popExit: androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.() -> ExitTransition =
        { if (reduce) ExitTransition.None else slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 6 } + fadeOut(tween(200)) }

    NavHost(nav, startDestination = start, enterTransition = enter, exitTransition = exit, popEnterTransition = popEnter, popExitTransition = popExit) {
        composable(Routes.ONBOARDING) { OnboardingScreen(onFinished = { nav.navigate(Routes.LOGIN) { popUpTo(Routes.ONBOARDING) { inclusive = true } } }) }
        composable(Routes.LOGIN) { LoginScreen(onGoToSignUp = { nav.navigate(Routes.SIGNUP) }) }
        composable(Routes.SIGNUP) { SignUpScreen(onBack = ::pop) }
        composable(Routes.MAIN) {
            MainScreen(
                onOpenBoard = { nav.navigate(Routes.board(it)) }, onOpenBoardSettings = { nav.navigate(Routes.boardSettings(it)) },
                onOpenTeam = { nav.navigate(Routes.team(it)) }, onChangePassword = { nav.navigate(Routes.CHANGE_PASSWORD) }
            )
        }
        composable(Routes.CHANGE_PASSWORD) { ChangePasswordScreen(onBack = ::pop) }
        composable(Routes.BOARD, listOf(navArgument("boardId") { type = NavType.StringType })) { e ->
            val id = e.arguments?.getString("boardId").orEmpty()
            BoardScreen(onBack = ::pop, onOpenTask = { nav.navigate(Routes.task(it)) }, onActivity = { nav.navigate(Routes.activity(id)) }, onSettings = { nav.navigate(Routes.boardSettings(id)) })
        }
        composable(Routes.BOARD_SETTINGS, listOf(navArgument("boardId") { type = NavType.StringType })) { BoardSettingsScreen(onBack = ::pop) }
        composable(Routes.ACTIVITY, listOf(navArgument("boardId") { type = NavType.StringType })) { ActivityScreen(onBack = ::pop, onOpenTask = { nav.navigate(Routes.task(it)) }) }
        composable(Routes.TASK, listOf(navArgument("taskId") { type = NavType.StringType })) { TaskDetailScreen(onBack = ::pop) }
        composable(Routes.TEAM, listOf(navArgument("teamId") { type = NavType.StringType })) { TeamDetailScreen(onBack = ::pop) }
    }
}

/** The three top-level tabs: Boards, Teams, Settings. */
@Composable
private fun MainScreen(onOpenBoard: (String) -> Unit, onOpenBoardSettings: (String) -> Unit, onOpenTeam: (String) -> Unit, onChangePassword: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(Triple("Boards", AppIcons.BoardsGrid, 0), Triple("Teams", AppIcons.Team, 1), Triple("Settings", AppIcons.Settings, 2)).forEach { (label, icon, i) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { androidx.compose.material3.Icon(icon, label) }, label = { Text(label) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).statusBarsPadding()) {
            Crossfade(targetState = tab, label = "tabs") { t ->
                when (t) {
                    0 -> BoardsScreen(onOpenBoard, onOpenBoardSettings)
                    1 -> TeamsScreen(onOpenTeam)
                    else -> SettingsScreen(onChangePassword)
                }
            }
        }
    }
}
