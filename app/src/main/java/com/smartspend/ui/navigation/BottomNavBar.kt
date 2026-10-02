package com.smartspend.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        Screen.bottomNavItems.forEach { screen ->
            val icon = when (screen) {
                Screen.Dashboard -> Icons.Default.Home
                Screen.Budget    -> Icons.Default.PieChart
                Screen.Bills     -> Icons.Default.Receipt
                Screen.Khata     -> Icons.Default.Book
                Screen.Reports   -> Icons.Default.BarChart
                else             -> Icons.Default.Home
            }
            NavigationBarItem(
                icon     = { Icon(icon, screen.label) },
                label    = { Text(screen.label) },
                selected = currentRoute == screen.route,
                onClick  = {
                    if (currentRoute != screen.route) {
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
