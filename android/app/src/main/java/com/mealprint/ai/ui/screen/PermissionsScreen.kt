package com.coachie.app.ui.screen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.PermissionsManager
import com.mealprint.ai.data.health.GoogleFitService
import com.mealprint.ai.data.health.HealthConnectService
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import com.mealprint.ai.data.local.PreferencesManager
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.theme.Primary40
import com.mealprint.ai.ui.theme.rememberCoachieGradient
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import androidx.activity.result.IntentSenderRequest
import android.app.PendingIntent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesManager = remember { PreferencesManager(context) }
    val permissionsManager = remember { PermissionsManager(context, preferencesManager) }
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    // Permission states
    var hasCameraPermission by remember { mutableStateOf(permissionsManager.hasCameraPermission()) }
    var hasNotificationPermission by remember { mutableStateOf(permissionsManager.hasNotificationPermission()) }
    var hasMicrophonePermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
    ) }
    var hasActivityRecognitionPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
    ) }
    // Exact alarms permission removed - not needed since briefs are handled by Firebase Cloud Functions
    var areNotificationsEnabled by remember { mutableStateOf(permissionsManager.areNotificationsEnabled()) }
    
    // Google Fit connection
    val googleFitService = remember { GoogleFitService(context) }
    var isGoogleFitConnected by remember { mutableStateOf(googleFitService.hasPermissions()) }
    
    // Health Connect connection
    val healthConnectService = remember { HealthConnectService(context) }
    var isHealthConnectAvailable by remember { mutableStateOf(healthConnectService.isAvailable()) }
    var isHealthConnectConnected by remember { mutableStateOf(false) }
    
    // Refresh permission states when screen is visible
    DisposableEffect(Unit) {
        // Refresh on mount
        isGoogleFitConnected = googleFitService.hasPermissions()
        hasCameraPermission = permissionsManager.hasCameraPermission()
        hasNotificationPermission = permissionsManager.hasNotificationPermission()
        hasMicrophonePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        // Exact alarms permission removed
        areNotificationsEnabled = permissionsManager.areNotificationsEnabled()
        
        onDispose { }
    }
    
    // Also refresh when lifecycle resumes (user comes back to screen)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // Refresh all permission states when screen resumes
                isGoogleFitConnected = googleFitService.hasPermissions()
                hasCameraPermission = permissionsManager.hasCameraPermission()
                hasNotificationPermission = permissionsManager.hasNotificationPermission()
                hasMicrophonePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                hasActivityRecognitionPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                // Exact alarms permission removed
                areNotificationsEnabled = permissionsManager.areNotificationsEnabled()
                android.util.Log.d("PermissionsScreen", "Screen resumed - refreshed permission states")
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    val fitnessOptions = remember {
        FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_READ)
            .build()
    }
    
    // Google Sign-In launcher for permission requests (must be defined first)
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // Refresh permission state after permission request
        isGoogleFitConnected = googleFitService.hasPermissions()
        android.util.Log.d("PermissionsScreen", "Permission request result: connected=$isGoogleFitConnected")
        if (isGoogleFitConnected) {
            // Trigger sync after connection
            coroutineScope.launch {
                try {
                    com.coachie.app.service.HealthSyncService.sync(context)
                } catch (e: Exception) {
                    android.util.Log.e("PermissionsScreen", "Error syncing after connection", e)
                }
            }
        }
    }
    
    // Google Sign-In launcher for initial sign-in (if no account exists)
    val googleSignInForAccountLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        coroutineScope.launch {
            android.util.Log.d("PermissionsScreen", "=== GOOGLE SIGN-IN RESULT RECEIVED ===")
            android.util.Log.d("PermissionsScreen", "Result code: ${result.resultCode}")
            android.util.Log.d("PermissionsScreen", "Result data: ${result.data}")
            
            // Check if sign-in was successful
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                android.util.Log.d("PermissionsScreen", "✅ Sign-in result is OK, processing account...")
                // Get the account from the result data
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    android.util.Log.d("PermissionsScreen", "✅ Google account signed in successfully: ${account.email}")
                    android.util.Log.d("PermissionsScreen", "  Granted scopes: ${account.grantedScopes}")
                    
                    // Check if fitness scopes were included in sign-in
                    val hasFitnessScopes = account.grantedScopes?.any { scope ->
                        scope.toString().contains("fitness") || scope.toString().contains("activity")
                    } ?: false
                    
                    android.util.Log.d("PermissionsScreen", "  Has fitness scopes from sign-in: $hasFitnessScopes")
                    
                    val activity = context as? ComponentActivity
                    if (activity == null) {
                        android.util.Log.e("PermissionsScreen", "❌ Context is not a ComponentActivity! Cannot request permissions.")
                        return@launch
                    }
                    
                    // Check if permissions are already granted
                    if (GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                        android.util.Log.d("PermissionsScreen", "✅ Google Fit permissions already granted")
                        isGoogleFitConnected = true
                    } else {
                        // Request permissions (even if scopes were requested, we still need explicit permission grant)
                        try {
                            android.util.Log.d("PermissionsScreen", "Requesting Google Fit permissions...")
                            val pendingIntent = GoogleSignIn.requestPermissions(
                                activity,
                                9001,
                                account,
                                fitnessOptions
                            )
                            
                            if (pendingIntent != null) {
                                android.util.Log.d("PermissionsScreen", "✅ Permission request pending intent created, launching...")
                                val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent as PendingIntent).build()
                                googleSignInLauncher.launch(intentSenderRequest)
                            } else {
                                android.util.Log.e("PermissionsScreen", "❌ PendingIntent is null! Permission request failed.")
                                // If pendingIntent is null, permissions might already be granted - check again
                                if (GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                                    android.util.Log.d("PermissionsScreen", "✅ Permissions actually granted (pendingIntent was null but hasPermissions=true)")
                                    isGoogleFitConnected = true
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PermissionsScreen", "❌ Error requesting permissions after sign-in", e)
                            e.printStackTrace()
                            // Check if permissions are actually granted despite the error
                            if (GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                                android.util.Log.d("PermissionsScreen", "✅ Permissions granted despite error")
                                isGoogleFitConnected = true
                            }
                        }
                    }
                } catch (e: ApiException) {
                    android.util.Log.e("PermissionsScreen", "❌ Failed to get account from sign-in result", e)
                    android.util.Log.e("PermissionsScreen", "Error code: ${e.statusCode}, message: ${e.message}")
                    
                    // Show user-friendly error message
                    val errorMessage = when (e.statusCode) {
                        10 -> "Google Play Services update required. Please update Google Play Services."
                        12501 -> "Sign-in was cancelled. Please try again."
                        7 -> "Network error. Please check your internet connection."
                        8 -> "Internal error. Please try again later."
                        16 -> "Sign-in is already in progress. Please wait."
                        else -> "Sign-in failed: ${e.message ?: "Unknown error (code ${e.statusCode})"}"
                    }
                    
                    // Show toast to user
                    android.widget.Toast.makeText(
                        context,
                        "Google Sign-In Error: $errorMessage",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    android.util.Log.e("PermissionsScreen", "❌ Unexpected error processing sign-in result", e)
                    e.printStackTrace()
                    android.widget.Toast.makeText(
                        context,
                        "Unexpected error during sign-in: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                android.util.Log.w("PermissionsScreen", "⚠️ Sign-in was cancelled or failed. Result code: ${result.resultCode}")
                if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
                    android.widget.Toast.makeText(
                        context,
                        "Sign-in was cancelled. Please try again.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "Sign-in failed. Please try again.",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    val healthConnectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        coroutineScope.launch {
            isHealthConnectConnected = healthConnectService.hasPermissions()
            if (isHealthConnectConnected) {
                // Trigger sync after connection
                try {
                    com.coachie.app.service.HealthSyncService.sync(context)
                } catch (e: Exception) {
                    android.util.Log.e("PermissionsScreen", "Error syncing after Health Connect connection", e)
                }
            }
        }
    }
    
    // Permission launchers
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        areNotificationsEnabled = permissionsManager.areNotificationsEnabled()
    }
    
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("PermissionsScreen", "Microphone permission result: $isGranted")
        hasMicrophonePermission = isGranted
        // Also refresh from system to ensure accuracy
        hasMicrophonePermission = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        android.util.Log.d("PermissionsScreen", "Microphone permission after refresh: $hasMicrophonePermission")
    }
    
    val activityRecognitionPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("PermissionsScreen", "Activity Recognition permission result: $isGranted")
        hasActivityRecognitionPermission = isGranted
        // Refresh from system to ensure accuracy
        hasActivityRecognitionPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        android.util.Log.d("PermissionsScreen", "Activity Recognition permission after refresh: $hasActivityRecognitionPermission")
        // Trigger sync after granting permission
        if (isGranted) {
            coroutineScope.launch {
                try {
                    com.coachie.app.service.HealthSyncService.sync(context)
                } catch (e: Exception) {
                    android.util.Log.e("PermissionsScreen", "Error syncing after Activity Recognition permission", e)
                }
            }
        }
    }
    
    // Refresh permissions when screen is visible
    LaunchedEffect(Unit) {
        hasCameraPermission = permissionsManager.hasCameraPermission()
        hasNotificationPermission = permissionsManager.hasNotificationPermission()
        hasMicrophonePermission = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        hasActivityRecognitionPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        // Exact alarms permission removed
        areNotificationsEnabled = permissionsManager.areNotificationsEnabled()
        isGoogleFitConnected = googleFitService.hasPermissions()
        isHealthConnectAvailable = healthConnectService.isAvailable()
        coroutineScope.launch {
            isHealthConnectConnected = healthConnectService.hasPermissions()
            // Update preference based on actual permission status
            if (isHealthConnectConnected) {
                preferencesManager.healthConnectEnabled = true
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CoachieCardDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = Primary40
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Permissions",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Primary40
                        )
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = 16.dp
                )
            ) {
                item {
                    Text(
                        "Manage app permissions to enable all features",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Black.copy(alpha = 0.8f)
                    )
                }
                
                // Camera Permission
                item {
                    PermissionCard(
                        title = "Camera",
                        description = "Required for taking meal photos and body scans",
                        icon = Icons.Filled.CameraAlt,
                        isGranted = hasCameraPermission,
                        onRequestPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onOpenSettings = { permissionsManager.openAppSettings() }
                    )
                }
                
                // Microphone Permission
                item {
                    PermissionCard(
                        title = "Microphone",
                        description = "Required for voice logging and voice commands",
                        icon = Icons.Filled.Mic,
                        isGranted = hasMicrophonePermission,
                        onRequestPermission = {
                            android.util.Log.d("PermissionsScreen", "Requesting microphone permission")
                            try {
                                microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } catch (e: Exception) {
                                android.util.Log.e("PermissionsScreen", "Error launching permission request", e)
                                // Fallback to settings if launcher fails
                                permissionsManager.openAppSettings()
                            }
                        },
                        onOpenSettings = { permissionsManager.openAppSettings() }
                    )
                }
                
                // Activity Recognition Permission (Required for Google Fit)
                item {
                    PermissionCard(
                        title = "Activity Recognition",
                        description = "Required for Google Fit to read steps and workout data",
                        icon = Icons.Filled.FitnessCenter,
                        isGranted = hasActivityRecognitionPermission,
                        onRequestPermission = {
                            android.util.Log.d("PermissionsScreen", "Requesting Activity Recognition permission")
                            try {
                                activityRecognitionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                            } catch (e: Exception) {
                                android.util.Log.e("PermissionsScreen", "Error launching Activity Recognition permission request", e)
                                // Fallback to settings if launcher fails
                                permissionsManager.openAppSettings()
                            }
                        },
                        onOpenSettings = { permissionsManager.openAppSettings() }
                    )
                }
                
                // Notification Permission
                item {
                    PermissionCard(
                        title = "Notifications",
                        description = "Required for daily insights and reminders",
                        icon = Icons.Filled.Notifications,
                        isGranted = hasNotificationPermission && areNotificationsEnabled,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                permissionsManager.openAppSettings()
                            }
                        },
                        onOpenSettings = { permissionsManager.openAppSettings() }
                    )
                }
                
                // Exact Alarms Permission removed - not needed since briefs are handled by Firebase Cloud Functions
                // All local reminders use setRepeating() which doesn't require exact alarms
                
                // Health Connect Connection
                if (isHealthConnectAvailable) {
                    item {
                        HealthConnectCard(
                            isConnected = isHealthConnectConnected,
                            onConnect = {
                                coroutineScope.launch {
                                    try {
                                        if (isHealthConnectConnected) {
                                            // Disconnect - revoke permissions
                                            val client = HealthConnectClient.getOrCreate(context)
                                            val permissions = setOf(
                                                HealthPermission.getReadPermission(StepsRecord::class),
                                                HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
                                                HealthPermission.getReadPermission(SleepSessionRecord::class),
                                                HealthPermission.getReadPermission(ExerciseSessionRecord::class)
                                            )
                                            // Disconnect - user wants to disable Health Connect
                                            // Note: Health Connect doesn't have revokePermissions in this version
                                            // User must revoke in system settings
                                            isHealthConnectConnected = false
                                            preferencesManager.healthConnectEnabled = false
                                            android.util.Log.d("PermissionsScreen", "Health Connect disabled in Coachie")
                                        } else {
                                            // Connect - request permissions
                                            try {
                                                val client = HealthConnectClient.getOrCreate(context)
                                                val permissions = setOf(
                                                    HealthPermission.getReadPermission(StepsRecord::class),
                                                    HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
                                                    HealthPermission.getReadPermission(SleepSessionRecord::class),
                                                    HealthPermission.getReadPermission(ExerciseSessionRecord::class)
                                                )
                                                // Health Connect permissions must be requested through system settings
                                                // Open Health Connect app settings
                                                try {
                                                    val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                        data = android.net.Uri.fromParts("package", "com.google.android.apps.healthdata", null)
                                                    }
                                                    context.startActivity(intent)
                                                    // Mark as enabled - user will grant permissions in Health Connect app
                                                    preferencesManager.healthConnectEnabled = true
                                                    android.util.Log.d("PermissionsScreen", "Health Connect enabled in Coachie - user will grant permissions in Health Connect app")
                                                } catch (e: Exception) {
                                                    android.util.Log.e("PermissionsScreen", "Error opening Health Connect settings", e)
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("PermissionsScreen", "Error requesting Health Connect permissions", e)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("PermissionsScreen", "Error with Health Connect", e)
                                    }
                                }
                            }
                        )
                    }
                }
                
                // Google Fit Connection
                item {
                    GoogleFitCard(
                        isConnected = isGoogleFitConnected,
                        onConnect = {
                            coroutineScope.launch {
                                try {
                                    android.util.Log.d("PermissionsScreen", "=== GOOGLE FIT CONNECT BUTTON CLICKED ===")
                                    
                                    if (isGoogleFitConnected) {
                                        // Disconnect - sign out
                                        android.util.Log.d("PermissionsScreen", "Disconnecting Google Fit...")
                                        GoogleSignIn.getClient(context, com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder().build()).signOut()
                                        isGoogleFitConnected = false
                                        android.util.Log.d("PermissionsScreen", "✅ Google Fit disconnected")
                                    } else {
                                        // Connect - first check if we have a Google account
                                        var account = GoogleSignIn.getLastSignedInAccount(context)
                                        android.util.Log.d("PermissionsScreen", "Checking for Google account: ${account?.email ?: "null"}")
                                        
                                        if (account == null) {
                                            // No Google account - need to sign in first
                                            android.util.Log.d("PermissionsScreen", "❌ No Google account found - requesting sign-in with fitness scopes")
                                            try {
                                                // CRITICAL FIX: Request Google Sign-In WITH fitness scopes included
                                                // This ensures the account has fitness permissions from the start
                                                android.util.Log.d("PermissionsScreen", "Building Google Sign-In options...")
                                                val signInOptions = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                                                )
                                                    .requestEmail()
                                                    // Add fitness scopes to the sign-in request
                                                    .addExtension(fitnessOptions)
                                                    .build()
                                                
                                                android.util.Log.d("PermissionsScreen", "Creating Google Sign-In client...")
                                                val signInClient = GoogleSignIn.getClient(context, signInOptions)
                                                
                                                android.util.Log.d("PermissionsScreen", "Getting sign-in intent...")
                                                val signInIntent = signInClient.signInIntent
                                                
                                                if (signInIntent == null) {
                                                    android.util.Log.e("PermissionsScreen", "❌ Sign-in intent is NULL!")
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "Failed to create sign-in intent. Check Google Play Services.",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                    return@launch
                                                }
                                                
                                                android.util.Log.d("PermissionsScreen", "✅ Sign-in intent created, launching...")
                                                android.util.Log.d("PermissionsScreen", "  Intent action: ${signInIntent.action}")
                                                android.util.Log.d("PermissionsScreen", "  Intent component: ${signInIntent.component}")
                                                
                                                googleSignInForAccountLauncher.launch(signInIntent)
                                                android.util.Log.d("PermissionsScreen", "✅ Launched Google Sign-In with fitness scopes")
                                            } catch (e: Exception) {
                                                android.util.Log.e("PermissionsScreen", "❌ CRITICAL ERROR: Failed to launch Google Sign-In", e)
                                                android.util.Log.e("PermissionsScreen", "  Error type: ${e.javaClass.simpleName}")
                                                android.util.Log.e("PermissionsScreen", "  Error message: ${e.message}")
                                                e.printStackTrace()
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Failed to launch Google Sign-In: ${e.message}\n\nCheck if Google Play Services is installed and updated.",
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            }
                                            return@launch
                                        }
                                        
                                        // We have an account - check permissions
                                        android.util.Log.d("PermissionsScreen", "✅ Google account found: ${account.email}")
                                        val hasPerms = GoogleSignIn.hasPermissions(account, fitnessOptions)
                                        android.util.Log.d("PermissionsScreen", "Current permissions status: $hasPerms")
                                        
                                        if (!hasPerms) {
                                            // Request permissions
                                            android.util.Log.d("PermissionsScreen", "Requesting Google Fit permissions...")
                                            android.util.Log.d("PermissionsScreen", "  Account: ${account.email}")
                                            android.util.Log.d("PermissionsScreen", "  Context type: ${context.javaClass.simpleName}")
                                            
                                            // Check if context is ComponentActivity
                                            val activity = context as? ComponentActivity
                                            if (activity == null) {
                                                android.util.Log.e("PermissionsScreen", "❌ Context is not a ComponentActivity! Cannot request permissions.")
                                                return@launch
                                            }
                                            
                                            try {
                                                android.util.Log.d("PermissionsScreen", "Calling GoogleSignIn.requestPermissions...")
                                                val pendingIntent = GoogleSignIn.requestPermissions(
                                                    activity,
                                                    9001,
                                                    account,
                                                    fitnessOptions
                                                )
                                                android.util.Log.d("PermissionsScreen", "✅ Permission request pending intent created: $pendingIntent")
                                                
                                                if (pendingIntent != null) {
                                                    try {
                                                        val intentSenderRequest = IntentSenderRequest.Builder(pendingIntent as PendingIntent).build()
                                                        android.util.Log.d("PermissionsScreen", "✅ IntentSenderRequest created, launching...")
                                                        googleSignInLauncher.launch(intentSenderRequest)
                                                        android.util.Log.d("PermissionsScreen", "✅ Launched permission request")
                                                    } catch (e: Exception) {
                                                        android.util.Log.e("PermissionsScreen", "❌ Error launching IntentSenderRequest", e)
                                                        e.printStackTrace()
                                                    }
                                                } else {
                                                    android.util.Log.e("PermissionsScreen", "❌ PendingIntent is null! Permission request failed.")
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("PermissionsScreen", "❌ CRITICAL ERROR: Failed to request permissions", e)
                                                android.util.Log.e("PermissionsScreen", "  Error type: ${e.javaClass.simpleName}")
                                                android.util.Log.e("PermissionsScreen", "  Error message: ${e.message}")
                                                e.printStackTrace()
                                            }
                                        } else {
                                            // Already has permissions
                                            android.util.Log.d("PermissionsScreen", "✅ Already has permissions - marking as connected")
                                            isGoogleFitConnected = true
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PermissionsScreen", "❌ CRITICAL ERROR: Exception in Google Fit connect", e)
                                    e.printStackTrace()
                                }
                            }
                        }
                    )
                }
                
                // Open App Settings
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
                        ),
                        onClick = { permissionsManager.openAppSettings() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = null,
                                    tint = Primary40
                                )
                                Column {
                                    Text(
                                        "Open App Settings",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.Black
                                    )
                                    Text(
                                        "Manage all permissions in system settings",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Black.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF4CAF50) else Primary40,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    }
                }
                
                // Status indicator
                Surface(
                    color = if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFFFF9800).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (isGranted) "Granted" else "Not Granted",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isGranted) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            // Action button
            Button(
                onClick = onRequestPermission, // Always launch Google Sign-In, not system settings
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) MaterialTheme.colorScheme.surfaceVariant else Primary40,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    if (isGranted) "Open Settings" else "Enable Permission",
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun GoogleFitCard(
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = if (isConnected) Color(0xFF4CAF50) else Primary40,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Google Fit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Text(
                            "Sync steps, calories, sleep, and workouts",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Surface(
                    color = if (isConnected) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFFFF9800).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (isConnected) "Connected" else "Not Connected",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) MaterialTheme.colorScheme.surfaceVariant else Primary40,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    if (isConnected) "Disconnect" else "Connect",
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
private fun HealthConnectCard(
    isConnected: Boolean,
    onConnect: () -> Unit
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = if (isConnected) Color(0xFF4CAF50) else Primary40,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Health Connect",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Text(
                            "Sync steps, calories, sleep, and workouts",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    }
                }
                
                Surface(
                    color = if (isConnected) Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFFFF9800).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (isConnected) "Connected" else "Not Connected",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isConnected) MaterialTheme.colorScheme.surfaceVariant else Primary40,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    if (isConnected) "Disconnect" else "Connect",
                    color = Color.Black
                )
            }
        }
    }
}
