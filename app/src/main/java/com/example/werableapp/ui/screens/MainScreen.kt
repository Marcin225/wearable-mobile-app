package com.example.werableapp.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.werableapp.ble.BleManager

/**
 * Root screen component containing the bottom navigation bar and the navigation host
 * Manages routing between the Dashboard and Settings screens
 */
@Composable
fun MainScreen(
    bleManager: BleManager,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    // observe the current navigation state to correctly highlight the active tab
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(70.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    selected = currentRoute == "dashboard",
                    onClick = {
                        if (currentRoute != "dashboard") {
                            navController.navigate("dashboard") {
                                // avoid building a large navigation stack and opening the same screen twice
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                // prevent multiple copies of the same screen on the backstack
                                launchSingleTop = true
                                // keep the screen state intact when switching back and forth
                                restoreState = true
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = {
                        if (currentRoute != "settings") {
                            navController.navigate("settings") {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            // prevent content from going under bottom navigation
            modifier = Modifier.padding(innerPadding),

            enterTransition = { EnterTransition.None }, // disable animations
            exitTransition = { ExitTransition.None }
        ) {
            composable("dashboard") {
                DashboardScreen(bleManager = bleManager, isDarkTheme = isDarkTheme)
            }
            composable("settings") {
                SettingsScreen(
                    bleManager = bleManager,
                    isDarkTheme = isDarkTheme,
                    onThemeChange = onThemeChange
                )
            }
        }
    }
}