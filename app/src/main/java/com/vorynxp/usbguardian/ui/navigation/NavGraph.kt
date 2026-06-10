package com.vorynxp.usbguardian.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vorynxp.usbguardian.ui.dashboard.DashboardScreen
import com.vorynxp.usbguardian.ui.devices.DevicesScreen
import com.vorynxp.usbguardian.ui.logs.LogsScreen
import com.vorynxp.usbguardian.ui.onboarding.OnboardingScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Dashboard.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route)
                }
            )
        }
        composable(Screen.Devices.route) {
            DevicesScreen()
        }
        composable(Screen.Logs.route) {
            LogsScreen()
        }
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    navController.popBackStack()
                }
            )
        }
    }
}
