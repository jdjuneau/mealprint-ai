package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.viewmodel.SettingsViewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.LifecycleOwner
import com.coachie.app.data.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userId: String,
    onBack: () -> Unit,
    onNavigateToVoiceSettings: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToPersonalInfo: () -> Unit = {},
    onNavigateToPhysicalStats: () -> Unit = {},
    onNavigateToPreferences: () -> Unit = {},
    onNavigateToSubscription: () -> Unit = {},
    onAccountDeleted: () -> Unit = {}, // Callback when account is deleted
    onNavigateToFTUE: () -> Unit = {}, // Callback to navigate to FTUE after clearing data
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            repository = com.coachie.app.data.FirebaseRepository.getInstance(),
            preferencesManager = com.coachie.app.data.local.PreferencesManager(androidx.compose.ui.platform.LocalContext.current),
            context = androidx.compose.ui.platform.LocalContext.current,
            userId = userId
        )
    )
) {
    val context = LocalContext.current
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val uiState = viewModel.uiState.collectAsState().value

    // Clear status message after a delay
    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    actionIconContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
        ) {
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Primary40)
                    Text(
                        text = "Loading settings...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Status/Error Messages
                    if (uiState.statusMessage != null) {
                        item {
                            CoachieCard(
                                colors = CoachieCardDefaults.colors(
                                    containerColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
                                )
                            ) {
                                Text(
                                    text = uiState.statusMessage!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    if (uiState.errorMessage != null) {
                        item {
                            CoachieCard(
                                colors = CoachieCardDefaults.colors(
                                    containerColor = Color(0xFFF44336).copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = uiState.errorMessage!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFF44336),
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.clearError() }) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        }
                    }

                    // Notifications Section
                    item {
                        CoachieCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Notifications",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Daily Nudges",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Receive reminders to log your habits",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                        )
                                    }
                                    Switch(
                                        checked = uiState.nudgesEnabled,
                                        onCheckedChange = { viewModel.setNudgesEnabled(it) },
                                        enabled = !uiState.isSaving
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Morning Brief",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Receive morning brief notifications (9 AM)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                        )
                                    }
                                    Switch(
                                        checked = uiState.morningBriefNotifications,
                                        onCheckedChange = { viewModel.setMorningBriefNotifications(it) },
                                        enabled = !uiState.isSaving
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Afternoon Brief",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Receive afternoon brief notifications (2 PM)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                        )
                                    }
                                    Switch(
                                        checked = uiState.afternoonBriefNotifications,
                                        onCheckedChange = { viewModel.setAfternoonBriefNotifications(it) },
                                        enabled = !uiState.isSaving
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Evening Brief",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Receive evening brief notifications (6 PM)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                        )
                                    }
                                    Switch(
                                        checked = uiState.eveningBriefNotifications,
                                        onCheckedChange = { viewModel.setEveningBriefNotifications(it) },
                                        enabled = !uiState.isSaving
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Weekly Blueprint Sunday Alert",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Get notified when your weekly meal plan is ready (Sundays)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                        )
                                    }
                                    Switch(
                                        checked = uiState.mealPlanNotifications,
                                        onCheckedChange = { viewModel.setMealPlanNotifications(it) },
                                        enabled = !uiState.isSaving
                                    )
                                }
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Daily Meal Reminders",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Get reminders at your scheduled meal times",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                        )
                                    }
                                    Switch(
                                        checked = uiState.mealReminders,
                                        onCheckedChange = { viewModel.setMealReminders(it) },
                                        enabled = !uiState.isSaving
                                    )
                                }
                            }
                        }
                    }
                    
                    // Meal Times Section (only show if meal reminders enabled)
                    if (uiState.mealReminders) {
                        item {
                            CoachieCard(modifier = Modifier.fillMaxWidth()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Meal Times",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Set your preferred meal times for reminders",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    
                                    MealTimeInput(
                                        label = "Breakfast",
                                        value = uiState.breakfastTime,
                                        onValueChange = { viewModel.setMealTime("breakfast", it) },
                                        enabled = !uiState.isSaving
                                    )
                                    
                                    MealTimeInput(
                                        label = "Lunch",
                                        value = uiState.lunchTime,
                                        onValueChange = { viewModel.setMealTime("lunch", it) },
                                        enabled = !uiState.isSaving
                                    )
                                    
                                    MealTimeInput(
                                        label = "Dinner",
                                        value = uiState.dinnerTime,
                                        onValueChange = { viewModel.setMealTime("dinner", it) },
                                        enabled = !uiState.isSaving
                                    )
                                    
                                    if (uiState.snacksPerDay >= 1) {
                                        MealTimeInput(
                                            label = "Snack 1",
                                            value = uiState.snack1Time,
                                            onValueChange = { viewModel.setMealTime("snack1", it) },
                                            enabled = !uiState.isSaving
                                        )
                                    }
                                    
                                    if (uiState.snacksPerDay >= 2) {
                                        MealTimeInput(
                                            label = "Snack 2",
                                            value = uiState.snack2Time,
                                            onValueChange = { viewModel.setMealTime("snack2", it) },
                                            enabled = !uiState.isSaving
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Weekly Blueprint Settings Section - MOVED TO TOP FOR VISIBILITY
                    item {
                        CoachieCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CoachieCardDefaults.colors(
                                containerColor = Primary40.copy(alpha = 0.1f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "📋",
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Weekly Blueprint Preferences",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = Primary40
                                        )
                                        Text(
                                            text = "Customize your weekly shopping list generation",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                        )
                                    }
                                }
                                
                                Text(
                                    text = "Meals per day",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(2, 3, 4).forEach { count ->
                                        FilterChip(
                                            selected = uiState.mealsPerDay == count,
                                            onClick = { 
                                                android.util.Log.d("SettingsScreen", "Meals per day clicked: $count")
                                                viewModel.setMealsPerDay(count) 
                                            },
                                            label = { Text("$count") },
                                            enabled = !uiState.isSaving && !uiState.isLoading,
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "Snacks per day",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(0, 1, 2).forEach { count ->
                                        FilterChip(
                                            selected = uiState.snacksPerDay == count,
                                            onClick = { 
                                                android.util.Log.d("SettingsScreen", "Snacks per day clicked: $count")
                                                viewModel.setSnacksPerDay(count) 
                                            },
                                            label = { Text("$count") },
                                            enabled = !uiState.isSaving && !uiState.isLoading,
                                            modifier = Modifier.weight(1f),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                                containerColor = MaterialTheme.colorScheme.surface,
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Profile Settings Section
                    item {
                        CoachieCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Profile Settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                // Personal Information
                                CoachieCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            onClick = onNavigateToPersonalInfo,
                                            indication = rememberRipple(),
                                            interactionSource = remember { MutableInteractionSource() }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "👤",
                                                style = MaterialTheme.typography.headlineSmall
                                            )
                                            Column {
                                                Text(
                                                    text = "Personal Information",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Name, age, gender",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Physical Stats
                                CoachieCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            onClick = onNavigateToPhysicalStats,
                                            indication = rememberRipple(),
                                            interactionSource = remember { MutableInteractionSource() }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "⚖️",
                                                style = MaterialTheme.typography.headlineSmall
                                            )
                                            Column {
                                                Text(
                                                    text = "Physical Stats",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Weight, height, activity level",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Preferences
                                CoachieCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            onClick = onNavigateToPreferences,
                                            indication = rememberRipple(),
                                            interactionSource = remember { MutableInteractionSource() }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "⚙️",
                                                style = MaterialTheme.typography.headlineSmall
                                            )
                                            Column {
                                                Text(
                                                    text = "Preferences",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Dietary preferences, notifications",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // App Settings Section
                    item {
                        CoachieCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "App Settings",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )


                                // Permissions
                                CoachieCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            onClick = onNavigateToPermissions,
                                            indication = rememberRipple(),
                                            interactionSource = remember { MutableInteractionSource() }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "🔒",
                                                style = MaterialTheme.typography.headlineSmall
                                            )
                                            Column {
                                                Text(
                                                    text = "Permissions",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Camera, location, notifications",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Subscription
                                CoachieCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            onClick = onNavigateToSubscription,
                                            indication = rememberRipple(),
                                            interactionSource = remember { MutableInteractionSource() }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "⭐",
                                                style = MaterialTheme.typography.headlineSmall
                                            )
                                            Column {
                                                Text(
                                                    text = "Subscription",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Manage your Pro subscription",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Voice & Audio
                                CoachieCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            onClick = onNavigateToVoiceSettings,
                                            indication = rememberRipple(),
                                            interactionSource = remember { MutableInteractionSource() }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Text(
                                                text = "🎤",
                                                style = MaterialTheme.typography.headlineSmall
                                            )
                                            Column {
                                                Text(
                                                    text = "Voice Settings",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Customize voice feedback and speech",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Privacy & Legal Section
                    item {
                        CoachieCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Privacy & Legal",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                // Privacy Policy Link
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val url = "https://playspace.games/coachie-privacy-policy"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.util.Log.e("SettingsScreen", "Failed to open privacy policy", e)
                                                viewModel.setErrorMessage("Could not open browser. Please visit: $url")
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Privacy Policy",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Icon(
                                        Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                HorizontalDivider()

                                // Terms of Service Link
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val url = "https://playspace.games/coachie-terms-of-service"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                android.util.Log.e("SettingsScreen", "Failed to open terms of service", e)
                                                viewModel.setErrorMessage("Could not open browser. Please visit: $url")
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Terms of Service",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Icon(
                                        Icons.Filled.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                HorizontalDivider()

                                // Export My Data Button (GDPR/CCPA)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !uiState.isExportingData) {
                                            viewModel.exportUserData()
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Export My Data",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "Download all your data (GDPR/CCPA)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface // Changed from onSurfaceVariant to onSurface (dark) for better readability
                                        )
                                    }
                                    if (uiState.isExportingData) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Show export success message with file location
                                if (uiState.statusMessage != null && uiState.statusMessage!!.contains("exported")) {
                                    Column(modifier = Modifier.padding(top = 4.dp)) {
                                        Text(
                                            text = uiState.statusMessage!!.split("\n").firstOrNull() ?: uiState.statusMessage!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF4CAF50)
                                        )
                                        if (uiState.exportDataUrl != null) {
                                            TextButton(
                                                onClick = {
                                                    // Share the exported file
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "application/json"
                                                        putExtra(Intent.EXTRA_STREAM, Uri.parse(uiState.exportDataUrl))
                                                        putExtra(Intent.EXTRA_SUBJECT, "Coachie Data Export")
                                                        putExtra(Intent.EXTRA_TEXT, "My Coachie data export")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    try {
                                                        context.startActivity(Intent.createChooser(shareIntent, "Share Data Export"))
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("SettingsScreen", "Failed to share file", e)
                                                    }
                                                },
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                Text(
                                                    text = "Share File",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color(0xFF4CAF50)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Account Management Section
                    item {
                        CoachieCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CoachieCardDefaults.colors(
                                containerColor = Color(0xFFFFF3E0).copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Account Management",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF6F00)
                                )

                                Text(
                                    text = "Clear all your logged data, habits, and progress. Your account will remain active.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        viewModel.showClearDataConfirmation()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isSaving,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFF9800)
                                    )
                                ) {
                                    Text("Clear All Data")
                                }
                            }
                        }
                    }

                    // Delete Account Section
                    item {
                        CoachieCard(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CoachieCardDefaults.colors(
                                containerColor = Color(0xFFFFEBEE).copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Delete Account",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD32F2F)
                                )

                                Text(
                                    text = "Permanently delete your account and all associated data. This action cannot be undone.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Button(
                                    onClick = {
                                        viewModel.showDeleteAccountConfirmation()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !uiState.isSaving,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD32F2F)
                                    )
                                ) {
                                    Text("Delete Account")
                                }
                            }
                        }
                    }

                    // App Info Section
                    item {
                        CoachieCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "About",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Coachie - Your Personal Wellness Coach",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Version ${com.coachie.app.BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialogs
    val coroutineScope = rememberCoroutineScope()

    if (uiState.showClearDataConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissClearDataConfirmation() },
            title = { Text("Clear All Data") },
            text = {
                Text("Are you sure you want to clear all your data? This will delete:\n\n" +
                    "• All health logs (meals, workouts, water, sleep)\n" +
                    "• All daily logs and progress\n" +
                    "• All recipes and saved meals\n" +
                    "• All habits and completions\n" +
                    "• All weekly blueprints\n\n" +
                    "Your account and profile settings will remain. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val result = viewModel.clearUserData()
                            if (result.isSuccess) {
                                // Navigate to FTUE screen to set goals again
                                onNavigateToFTUE()
                            } else {
                                viewModel.setErrorMessage(result.exceptionOrNull()?.message ?: "Failed to clear data")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800)
                    )
                ) {
                    Text("Clear Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissClearDataConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showDeleteAccountConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteAccountConfirmation() },
            title = { Text("Delete Account", color = Color(0xFFD32F2F)) },
            text = {
                Text("Are you absolutely sure you want to delete your account?\n\n" +
                    "This will permanently delete:\n" +
                    "• Your account and profile\n" +
                    "• All your data and progress\n" +
                    "• All your habits and logs\n" +
                    "• Your account from Firebase\n\n" +
                    "This action CANNOT be undone. You will need to create a new account to use Coachie again.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            // CRITICAL: Delete Firebase Auth account FIRST (before Firestore data)
                            // This ensures the account is deleted even if Firestore deletion fails
                            val auth = FirebaseAuth.getInstance()
                            val currentUser = auth.currentUser
                            
                            if (currentUser == null) {
                                android.util.Log.e("SettingsScreen", "❌ No authenticated user - cannot delete account")
                                viewModel.setErrorMessage("No authenticated user found. Please sign in and try again.")
                                return@launch
                            }
                            
                            val userEmail = currentUser.email
                            android.util.Log.d("SettingsScreen", "🗑️ Starting account deletion for: $userEmail")
                            
                            // Step 1: Delete Firebase Auth account FIRST (MANDATORY)
                            android.util.Log.d("SettingsScreen", "Step 1: Deleting Firebase Auth account...")
                            val authDeleteResult = kotlin.runCatching {
                                currentUser.delete().await()
                                android.util.Log.d("SettingsScreen", "✅ Firebase Auth account deletion initiated")
                                
                                // CRITICAL: Verify the account is actually deleted
                                // Wait a moment and check if currentUser is null
                                kotlinx.coroutines.delay(500)
                                val verifyUser = auth.currentUser
                                if (verifyUser != null && verifyUser.uid == currentUser.uid) {
                                    android.util.Log.e("SettingsScreen", "❌ Account deletion verification failed - user still exists!")
                                    throw IllegalStateException("Account deletion verification failed - user still authenticated")
                                }
                                android.util.Log.d("SettingsScreen", "✅ Verified: Firebase Auth account is deleted")
                            }
                            
                            if (authDeleteResult.isFailure) {
                                val error = authDeleteResult.exceptionOrNull()
                                android.util.Log.e("SettingsScreen", "❌❌❌ CRITICAL: Firebase Auth account deletion FAILED ❌❌❌", error)
                                android.util.Log.e("SettingsScreen", "Error: ${error?.message}")
                                error?.printStackTrace()
                                
                                // Check if re-authentication is required (Firebase security requirement)
                                if (error is FirebaseAuthRecentLoginRequiredException) {
                                    android.util.Log.d("SettingsScreen", "⚠️ Firebase requires recent authentication for account deletion")
                                    viewModel.dismissDeleteAccountConfirmation() // Close the delete confirmation dialog
                                    viewModel.showReauthDialog(userEmail ?: "") // Show re-auth dialog
                                    return@launch
                                }
                                
                                // For other errors, show error message but don't block - try to continue with Firestore deletion
                                android.util.Log.w("SettingsScreen", "⚠️ Auth deletion failed, but continuing with Firestore deletion")
                                viewModel.setErrorMessage("Warning: Authentication account deletion failed, but continuing with data deletion: ${error?.message}")
                                // Continue anyway - we'll try to delete Firestore data
                            }
                            
                            // Step 2: Delete ALL Firestore data
                            android.util.Log.d("SettingsScreen", "Step 2: Deleting Firestore data...")
                            val firestoreDeleteResult = viewModel.deleteUserAccount()
                            
                            if (firestoreDeleteResult.isFailure) {
                                android.util.Log.w("SettingsScreen", "⚠️ Firestore data deletion failed, but Auth account is deleted")
                                android.util.Log.w("SettingsScreen", "Error: ${firestoreDeleteResult.exceptionOrNull()?.message}")
                                // Continue anyway - Auth account is deleted, which is the critical part
                            } else {
                                android.util.Log.d("SettingsScreen", "✅ Firestore data deleted")
                            }
                            
                            // Step 3: Clear ALL local preferences
                            android.util.Log.d("SettingsScreen", "Step 3: Clearing local preferences...")
                            try {
                                val preferencesManager = com.coachie.app.data.local.PreferencesManager(context)
                                preferencesManager.clearAllData()
                                android.util.Log.d("SettingsScreen", "✅ Local preferences cleared")
                            } catch (e: Exception) {
                                android.util.Log.w("SettingsScreen", "⚠️ Failed to clear preferences: ${e.message}")
                            }
                            
                            // Step 4: Explicitly sign out to ensure auth state is cleared
                            android.util.Log.d("SettingsScreen", "Step 4: Signing out...")
                            try {
                                auth.signOut()
                                android.util.Log.d("SettingsScreen", "✅ Signed out")
                            } catch (e: Exception) {
                                android.util.Log.w("SettingsScreen", "⚠️ Sign out failed: ${e.message}")
                            }
                            
                            // Step 5: Final verification - ensure user is not authenticated
                            val finalCheck = auth.currentUser
                            if (finalCheck != null) {
                                android.util.Log.e("SettingsScreen", "❌❌❌ CRITICAL: User still authenticated after deletion! ❌❌❌")
                                android.util.Log.e("SettingsScreen", "User ID: ${finalCheck.uid}, Email: ${finalCheck.email}")
                                viewModel.setErrorMessage("Account deletion incomplete - user still authenticated. Please try again.")
                                return@launch
                            }
                            
                            android.util.Log.d("SettingsScreen", "✅✅✅ Account deletion COMPLETE and VERIFIED ✅✅✅")
                            android.util.Log.d("SettingsScreen", "🚀 Navigating to auth screen")
                            
                            // Step 6: Navigate to auth screen
                            onAccountDeleted()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
                ) {
                    Text("Delete Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteAccountConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Re-authentication dialog for account deletion
    if (uiState.showReauthDialog && uiState.reauthEmail != null) {
        var password by remember { mutableStateOf("") }
        var isReauthenticating by remember { mutableStateOf(false) }
        var reauthError by remember { mutableStateOf<String?>(null) }
        
        AlertDialog(
            onDismissRequest = { 
                if (!isReauthenticating) {
                    viewModel.dismissReauthDialog()
                }
            },
            title = { Text("Re-authentication Required", color = Color(0xFFD32F2F)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Firebase requires recent authentication for account deletion. " +
                        "Please enter your password to proceed.\n\n" +
                        "Email: ${uiState.reauthEmail}"
                    )
                    
                    if (reauthError != null) {
                        Text(
                            text = reauthError!!,
                            color = Color(0xFFD32F2F),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    OutlinedTextField(
                        value = password,
                        onValueChange = { 
                            password = it
                            reauthError = null
                        },
                        label = { Text("Password") },
                        placeholder = { Text("Enter your password") },
                        enabled = !isReauthenticating,
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        isError = reauthError != null
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (password.isBlank()) {
                            reauthError = "Password is required"
                            return@Button
                        }
                        
                        coroutineScope.launch {
                            isReauthenticating = true
                            reauthError = null
                            
                            try {
                                val auth = FirebaseAuth.getInstance()
                                val currentUser = auth.currentUser
                                
                                if (currentUser == null || currentUser.email != uiState.reauthEmail) {
                                    reauthError = "User session expired. Please sign in again."
                                    isReauthenticating = false
                                    return@launch
                                }
                                
                                // Re-authenticate user
                                android.util.Log.d("SettingsScreen", "Re-authenticating user: ${uiState.reauthEmail}")
                                val credential = EmailAuthProvider.getCredential(uiState.reauthEmail!!, password)
                                currentUser.reauthenticate(credential).await()
                                android.util.Log.d("SettingsScreen", "✅ Re-authentication successful")
                                
                                // Close re-auth dialog
                                viewModel.dismissReauthDialog()
                                
                                // Now proceed with account deletion
                                android.util.Log.d("SettingsScreen", "🗑️ Starting account deletion after re-authentication...")
                                
                                // Step 1: Delete Firebase Auth account
                                val authDeleteResult = kotlin.runCatching {
                                    currentUser.delete().await()
                                    android.util.Log.d("SettingsScreen", "✅ Firebase Auth account deletion initiated")
                                    
                                    kotlinx.coroutines.delay(500)
                                    val verifyUser = auth.currentUser
                                    if (verifyUser != null && verifyUser.uid == currentUser.uid) {
                                        throw IllegalStateException("Account deletion verification failed")
                                    }
                                    android.util.Log.d("SettingsScreen", "✅ Verified: Firebase Auth account is deleted")
                                }
                                
                                if (authDeleteResult.isFailure) {
                                    val error = authDeleteResult.exceptionOrNull()
                                    android.util.Log.e("SettingsScreen", "❌ Account deletion failed after re-auth", error)
                                    viewModel.setErrorMessage("Failed to delete account: ${error?.message ?: "Unknown error"}")
                                    return@launch
                                }
                                
                                // Step 2: Delete Firestore data
                                android.util.Log.d("SettingsScreen", "Step 2: Deleting Firestore data...")
                                val firestoreDeleteResult = viewModel.deleteUserAccount()
                                
                                if (firestoreDeleteResult.isFailure) {
                                    android.util.Log.w("SettingsScreen", "⚠️ Firestore data deletion failed, but Auth account is deleted")
                                } else {
                                    android.util.Log.d("SettingsScreen", "✅ Firestore data deleted")
                                }
                                
                                // Step 3: Clear local preferences
                                try {
                                    val preferencesManager = com.coachie.app.data.local.PreferencesManager(context)
                                    preferencesManager.clearAllData()
                                    android.util.Log.d("SettingsScreen", "✅ Local preferences cleared")
                                } catch (e: Exception) {
                                    android.util.Log.w("SettingsScreen", "⚠️ Failed to clear preferences: ${e.message}")
                                }
                                
                                // Step 4: Sign out
                                try {
                                    auth.signOut()
                                    android.util.Log.d("SettingsScreen", "✅ Signed out")
                                } catch (e: Exception) {
                                    android.util.Log.w("SettingsScreen", "⚠️ Sign out failed: ${e.message}")
                                }
                                
                                // Step 5: Verify deletion
                                val finalCheck = auth.currentUser
                                if (finalCheck != null) {
                                    android.util.Log.e("SettingsScreen", "❌ User still authenticated after deletion!")
                                    viewModel.setErrorMessage("Account deletion incomplete. Please try again.")
                                    return@launch
                                }
                                
                                android.util.Log.d("SettingsScreen", "✅✅✅ Account deletion COMPLETE and VERIFIED ✅✅✅")
                                
                                // Step 6: Navigate to auth screen
                                onAccountDeleted()
                                
                            } catch (e: com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                                reauthError = "Invalid password. Please try again."
                                android.util.Log.e("SettingsScreen", "Re-authentication failed: invalid password", e)
                            } catch (e: Exception) {
                                reauthError = "Re-authentication failed: ${e.message ?: "Unknown error"}"
                                android.util.Log.e("SettingsScreen", "Re-authentication failed", e)
                            } finally {
                                isReauthenticating = false
                            }
                        }
                    },
                    enabled = !isReauthenticating && password.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
                ) {
                    if (isReauthenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Confirm & Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissReauthDialog() },
                    enabled = !isReauthenticating
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
private fun MealTimeInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                // Format as HH:MM
                val formatted = newValue
                    .filter { it.isDigit() || it == ':' }
                    .let { text ->
                        when {
                            text.length <= 2 -> text
                            text.length <= 4 -> "${text.take(2)}:${text.drop(2)}"
                            else -> "${text.take(2)}:${text.drop(2).take(2)}"
                        }
                    }
                onValueChange(formatted)
            },
            placeholder = { Text("07:30") },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            singleLine = true,
            maxLines = 1
        )
    }
}