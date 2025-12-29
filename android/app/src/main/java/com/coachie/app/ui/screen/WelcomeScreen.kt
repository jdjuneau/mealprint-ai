package com.coachie.app.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import com.coachie.app.ui.components.FunBackground
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.R
import com.coachie.app.utils.DebugLogger
import com.coachie.app.viewmodel.AuthViewModel
import com.coachie.app.viewmodel.AuthState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    navController: androidx.navigation.NavHostController
) {
    val viewModel: AuthViewModel = viewModel()
    val authState by viewModel.authState.collectAsState()
    val userGoals by viewModel.userGoals.collectAsState()
    val areGoalsLoading by viewModel.areGoalsLoading.collectAsState()
    
    // Auto-advance after 2 seconds, but check auth state first
    // CRITICAL: Only navigate if we're actually on the welcome screen (not if user just signed up)
    LaunchedEffect(Unit) {
        delay(2000) // Wait 2 seconds for the welcome screen
        
        val currentAuthState = authState
        val user = (currentAuthState as? AuthState.Authenticated)?.user
        
        // If user is already authenticated (e.g., returning user), skip auth screen and go directly to home/set_goals
        if (user != null && !user.isAnonymous && !user.email.isNullOrBlank()) {
            DebugLogger.logDebug("WelcomeScreen", "User is already authenticated, skipping auth screen")
            
            // Wait for goals to load if still loading
            if (areGoalsLoading) {
                return@LaunchedEffect // Will re-trigger when loading completes
            }
            
            val goalsSetValue = userGoals?.get("goalsSet")
            val hasGoalsSetField = goalsSetValue == true
            val hasGoalFields = userGoals?.containsKey("selectedGoal") == true ||
                               userGoals?.containsKey("weeklyWorkouts") == true ||
                               userGoals?.containsKey("dailySteps") == true
            val hasGoals = hasGoalsSetField || hasGoalFields
            
            if (hasGoals) {
                DebugLogger.logDebug("WelcomeScreen", "Navigating directly to home (user authenticated)")
                navController.navigate("home") {
                    popUpTo("welcome") { inclusive = true }
                }
            } else {
                DebugLogger.logDebug("WelcomeScreen", "Navigating directly to set_goals (user authenticated, no goals)")
                navController.navigate("set_goals") {
                    popUpTo("welcome") { inclusive = true }
                }
            }
        } else {
            // User is not authenticated, go to auth screen
            DebugLogger.logDebug("WelcomeScreen", "User not authenticated, navigating to auth")
            navController.navigate("auth") {
                popUpTo("welcome") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Coachie title image - fills entire screen
        Image(
            painter = painterResource(id = R.drawable.coachietitle),
            contentDescription = "Coachie AI Health",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}