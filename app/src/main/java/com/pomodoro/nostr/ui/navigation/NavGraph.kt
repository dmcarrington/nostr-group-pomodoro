package com.pomodoro.nostr.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pomodoro.nostr.ui.screens.auth.AuthScreen
import com.pomodoro.nostr.ui.screens.settings.SettingsScreen
import com.pomodoro.nostr.ui.screens.timer.TimerScreen
import com.pomodoro.nostr.viewmodel.AuthViewModel

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Timer : Screen("timer")
    object Settings : Screen("settings")
}

@Composable
fun PomodoroNavGraph(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val startDestination = remember {
        if (authViewModel.isAuthenticated.value) Screen.Timer.route else Screen.Auth.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Auth.route) {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate(Screen.Timer.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Timer.route) {
            TimerScreen(
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Screen.Auth.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
