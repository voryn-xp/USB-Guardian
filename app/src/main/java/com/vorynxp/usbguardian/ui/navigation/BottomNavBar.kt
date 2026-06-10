package com.vorynxp.usbguardian.ui.navigation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Lock)
    object Devices : Screen("devices", "Devices", Icons.Default.Settings)
    object Logs : Screen("logs", "Logs", Icons.Default.List)
    object Onboarding : Screen("onboarding", "Setup", Icons.Default.Info)
}

@Composable
fun BottomNavBar(navController: NavController) {
    val items = listOf(
        Screen.Dashboard,
        Screen.Devices,
        Screen.Logs
    )

    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry.value?.destination?.route

    // Ensure we don't show the bottom nav bar on the onboarding screen
    if (currentRoute == Screen.Onboarding.route) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp)),
        color = androidx.compose.material3.MaterialTheme.colorScheme.surface
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            modifier = Modifier.height(72.dp),
            tonalElevation = 0.dp
        ) {
            items.forEach { screen ->
                val selected = currentRoute == screen.route
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (currentRoute != screen.route) {
                            navController.navigate(screen.route) {
                                popUpTo(Screen.Dashboard.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title
                        )
                    },
                    label = {
                        Text(text = screen.title)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        selectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}
