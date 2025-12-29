package com.coachie.app.navigation

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation routes for the Coachie app
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Welcome : Screen("welcome")
    object Auth : Screen("auth")
    object SetGoals : Screen("set_goals")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object CoachChat : Screen("coach_chat")
    object Progress : Screen("progress")
    object Profile : Screen("profile")
    object ProfileEdit : Screen("profile_edit")
    object DailyLog : Screen("daily_log")
    object ScanManagement : Screen("scan_management")
    object LogEntry : Screen("log_entry")
    object BodyScan : Screen("body_scan")
    object HealthConnect : Screen("health_connect")
    object Debug : Screen("debug")
}

/**
 * Bottom navigation items configuration
 */
data class BottomNavItem(
    val label: String,
    val icon: ImageVector, // Material Icons
    val route: String
)
