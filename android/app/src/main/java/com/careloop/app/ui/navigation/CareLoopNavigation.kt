package com.careloop.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.careloop.app.ui.screens.history.HistoryScreen
import com.careloop.app.ui.screens.home.HomeScreen
import com.careloop.app.ui.screens.medications.MedicationsScreen
import com.careloop.app.ui.screens.settings.SettingsScreen
import com.careloop.app.ui.screens.sharing.WhatISharedScreen
import com.careloop.app.ui.screens.vitals.VitalsScreen
import com.careloop.app.ui.theme.CareColors

/**
 * Top-level navigation.
 *
 * Five destinations, all reachable in one tap from anywhere. Deliberately flat: nested
 * navigation means remembering where you are, which is exactly the cognitive load this
 * audience does not need.
 *
 * **Bottom-bar labels are always visible.** Material's default hides labels for unselected
 * items, which leaves icon-only targets — the failure mode research is clearest about for
 * older users, who do not reliably decode digital iconography.
 */
sealed class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Home : Destination("home", "Home", Icons.Rounded.Home)
    data object Medications : Destination("medications", "Medicines", Icons.Rounded.Medication)
    data object Vitals : Destination("vitals", "Health", Icons.Rounded.Favorite)
    data object History : Destination("history", "Calls", Icons.Rounded.Phone)
    data object Settings : Destination("settings", "Settings", Icons.Rounded.Settings)

    companion object {
        val bottomBar = listOf(Home, Medications, Vitals, History, Settings)
        const val WHAT_I_SHARED = "what_i_shared"
    }
}

@Composable
fun CareLoopApp(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            // Hidden on sub-screens so they read as focused, dedicated views.
            if (currentRoute in Destination.bottomBar.map { it.route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    Destination.bottomBar.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateToTab(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            // Never icon-only — see the class comment.
                            label = { Text(destination.label) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CareColors.Navy,
                                selectedTextColor = CareColors.Navy,
                                indicatorColor = CareColors.Yellow.copy(alpha = 0.35f),
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Home.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Home.route) { HomeScreen() }

            composable(Destination.Medications.route) { MedicationsScreen() }

            composable(Destination.Vitals.route) { VitalsScreen() }

            composable(Destination.History.route) { HistoryScreen() }

            composable(Destination.Settings.route) {
                SettingsScreen(
                    onOpenWhatIShared = { navController.navigate(Destination.WHAT_I_SHARED) },
                )
            }

            composable(Destination.WHAT_I_SHARED) { WhatISharedScreen() }
        }
    }
}

/**
 * Tab switching that does not grow the back stack. Without this, tapping between tabs a few
 * times means the back button has to be pressed repeatedly to leave the app — confusing for
 * anyone, worse for someone unsure whether they have broken something.
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
