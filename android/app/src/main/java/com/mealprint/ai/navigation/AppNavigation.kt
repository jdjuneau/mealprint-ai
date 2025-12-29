package com.mealprint.ai.navigation

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation routes for the Mealprint AI app
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Welcome : Screen("welcome")
    object Auth : Screen("auth")
    object Home : Screen("home")
    object CoachChat : Screen("coach_chat")
    object WeeklyBlueprint : Screen("weekly_blueprint")
    object MealRecommendation : Screen("meal_recommendation")
    object MyRecipes : Screen("my_recipes")
    object Community : Screen("community")
    object Profile : Screen("profile")
    object ProfileEdit : Screen("profile_edit")
    object Settings : Screen("settings")
    object Subscription : Screen("subscription")
    object Debug : Screen("debug")

    // Social features
    object CircleCreate : Screen("circle_create")
    object CircleDetail : Screen("circle_detail/{circleId}") {
        fun createRoute(circleId: String) = "circle_detail/$circleId"
    }
    object CircleJoin : Screen("circle_join")
    object Messaging : Screen("messaging/{userId}") {
        fun createRoute(userId: String) = "messaging/$userId"
    }
    object ForumDetail : Screen("forum_detail/{postId}") {
        fun createRoute(postId: String) = "forum_detail/$postId"
    }
}

/**
 * Bottom navigation items configuration
 */
data class BottomNavItem(
    val label: String,
    val icon: ImageVector, // Material Icons
    val route: String
)
