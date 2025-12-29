package com.mealprint.ai.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mealprint.ai.ui.auth.AuthScreen
import com.mealprint.ai.ui.screen.*

/**
 * Main navigation host for Mealprint AI
 * Focused on meal planning and social features
 */
@Composable
fun MealprintNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Splash.route
) {
    NavHost(navController = navController, startDestination = startDestination) {

        // Authentication flow
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashComplete = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Welcome.route) {
            WelcomeScreen(navController = navController)
        }

        composable(Screen.Auth.route) {
            AuthScreen(navController = navController)
        }

        // Main app screens
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(Screen.WeeklyBlueprint.route) {
            WeeklyBlueprintScreen(navController = navController)
        }

        composable(Screen.MealRecommendation.route) {
            MealRecommendationScreen(navController = navController)
        }

        composable(Screen.MyRecipes.route) {
            MyRecipesScreen(navController = navController)
        }

        composable(Screen.CoachChat.route) {
            CoachChatScreen(navController = navController)
        }

        // Social features
        composable(Screen.Community.route) {
            CommunityScreen(navController = navController)
        }

        composable(Screen.CircleCreate.route) {
            CircleCreateScreen(navController = navController)
        }

        composable(Screen.CircleJoin.route) {
            CircleJoinScreen(navController = navController)
        }

        composable(
            route = Screen.CircleDetail.route,
            arguments = listOf(navArgument("circleId") {
                type = androidx.navigation.NavType.StringType
            })
        ) { backStackEntry ->
            val circleId = backStackEntry.arguments?.getString("circleId") ?: ""
            CircleDetailScreen(navController = navController, circleId = circleId)
        }

        composable(
            route = Screen.Messaging.route,
            arguments = listOf(navArgument("userId") {
                type = androidx.navigation.NavType.StringType
            })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            MessagingScreen(navController = navController, otherUserId = userId)
        }

        composable(
            route = Screen.ForumDetail.route,
            arguments = listOf(navArgument("postId") {
                type = androidx.navigation.NavType.StringType
            })
        ) { backStackEntry ->
            val postId = backStackEntry.arguments?.getString("postId") ?: ""
            ForumDetailScreen(navController = navController, postId = postId)
        }

        // User profile and settings
        composable(Screen.Profile.route) {
            ProfileScreen(navController = navController)
        }

        composable(Screen.ProfileEdit.route) {
            ProfileEditScreen(navController = navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }

        composable(Screen.Subscription.route) {
            SubscriptionScreen(navController = navController)
        }

        // Recipe features
        composable("meal_capture") {
            MealCaptureScreen(navController = navController)
        }

        composable("recipe_capture") {
            RecipeCaptureScreen(navController = navController)
        }

        composable("meal_detail/{mealId}") { backStackEntry ->
            val mealId = backStackEntry.arguments?.getString("mealId") ?: ""
            MealDetailScreen(navController = navController, mealId = mealId)
        }

        composable("recipe_detail/{recipeId}") { backStackEntry ->
            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: ""
            RecipeDetailScreen(navController = navController, recipeId = recipeId)
        }

        // Utility screens
        composable(Screen.Help.route) {
            HelpScreen(navController = navController)
        }

        composable(Screen.Debug.route) {
            DebugScreen(navController = navController)
        }

        composable("todays_log_detail") {
            TodaysLogDetailScreen(navController = navController)
        }
    }
}
