package com.coachie.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mealprint.ai.utils.DebugLogger
import androidx.compose.material3.MaterialTheme
import com.mealprint.ai.ui.components.CoachieCard as Card
import com.mealprint.ai.ui.components.CoachieCardDefaults as CardDefaults
import com.mealprint.ai.ui.theme.rememberCoachieGradient

// Validation functions
private fun validateSignIn(email: String, password: String): Boolean {
    return when {
        email.isBlank() -> {
            DebugLogger.logDebug("AuthValidation", "Sign in validation failed: email is blank")
            false
        }
        password.isBlank() -> {
            DebugLogger.logDebug("AuthValidation", "Sign in validation failed: password is blank")
            false
        }
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
            DebugLogger.logDebug("AuthValidation", "Sign in validation failed: invalid email format")
            false
        }
        else -> {
            DebugLogger.logDebug("AuthValidation", "Sign in validation passed")
            true
        }
    }
}

private fun validateSignUp(name: String, email: String, password: String, confirmPassword: String): String? {
    return when {
        name.isBlank() -> "Please enter your name"
        email.isBlank() -> "Please enter your email"
        password.isBlank() -> "Please enter a password"
        confirmPassword.isBlank() -> "Please confirm your password"
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Please enter a valid email address"
        password.length < 6 -> "Password must be at least 6 characters"
        password != confirmPassword -> "Passwords do not match"
        else -> null
    }
}

private @Composable
fun SignInContent(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onShowPasswordChange: (Boolean) -> Unit,
    isLoading: Boolean,
    onSignIn: () -> Unit,
    primaryColor: Color,
    customColors: androidx.compose.material3.ColorScheme = MaterialTheme.colorScheme
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Email field - Enhanced styling
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            leadingIcon = {
                Icon(
                    Icons.Default.Email,
                    contentDescription = "Email",
                    tint = Color.White // White icons instead of green
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = customColors.primary, // Primary color border when focused
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.7f), // More visible unfocused border
                focusedLabelColor = customColors.primary, // Primary color label when focused
                unfocusedLabelColor = Color.Gray,
                focusedTextColor = Color.Black, // Dark text for readability
                unfocusedTextColor = Color.Black
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
            ),
            enabled = !isLoading
        )

        // Password field - Enhanced styling
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            leadingIcon = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Password",
                    tint = primaryColor // Primary color icons
                )
            },
            trailingIcon = {
                IconButton(onClick = { onShowPasswordChange(!showPassword) }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = primaryColor // Primary color icons - visible on white background
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = customColors.primary, // Primary color border when focused
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.7f), // More visible unfocused border
                focusedLabelColor = customColors.primary, // Primary color label when focused
                unfocusedLabelColor = Color.Gray,
                focusedTextColor = Color.Black, // Dark text for readability
                unfocusedTextColor = Color.Black
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
            ),
            enabled = !isLoading
        )

        // Sign In button - Enhanced styling
        Button(
            onClick = onSignIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 2.dp
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    "Sign In",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}

private @Composable
fun SignUpContent(
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onShowPasswordChange: (Boolean) -> Unit,
    isLoading: Boolean,
    onSignUp: () -> Unit,
    primaryColor: Color,
    customColors: androidx.compose.material3.ColorScheme = MaterialTheme.colorScheme
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Name field - Enhanced styling
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Full Name") },
            leadingIcon = {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Name",
                    tint = primaryColor // Primary color icons
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = customColors.primary, // Primary color border when focused
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.7f), // More visible unfocused border
                focusedLabelColor = customColors.primary, // Primary color label when focused
                unfocusedLabelColor = Color.Gray,
                focusedTextColor = Color.Black, // Dark text for readability
                unfocusedTextColor = Color.Black
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words
            ),
            enabled = !isLoading
        )

        // Email field - Enhanced styling
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            leadingIcon = {
                Icon(
                    Icons.Default.Email,
                    contentDescription = "Email",
                    tint = Color.White // White icons instead of green
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = customColors.primary, // Primary color border when focused
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.7f), // More visible unfocused border
                focusedLabelColor = customColors.primary, // Primary color label when focused
                unfocusedLabelColor = Color.Gray,
                focusedTextColor = Color.Black, // Dark text for readability
                unfocusedTextColor = Color.Black
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
            ),
            enabled = !isLoading
        )

        // Password field - Enhanced styling
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            leadingIcon = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Password",
                    tint = primaryColor // Primary color icons
                )
            },
            trailingIcon = {
                IconButton(onClick = { onShowPasswordChange(!showPassword) }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = primaryColor // Primary color icons - visible on white background
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = customColors.primary, // Primary color border when focused
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.7f), // More visible unfocused border
                focusedLabelColor = customColors.primary, // Primary color label when focused
                unfocusedLabelColor = Color.Gray,
                focusedTextColor = Color.Black, // Dark text for readability
                unfocusedTextColor = Color.Black
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
            ),
            enabled = !isLoading
        )

        // Confirm password field - Enhanced styling
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = { Text("Confirm Password") },
            leadingIcon = {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Confirm Password",
                    tint = Color.White // White icons instead of green
                )
            },
            trailingIcon = {
                IconButton(onClick = { onShowPasswordChange(!showPassword) }) {
                    Icon(
                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = Color.White // White icons instead of green
                    )
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = customColors.primary, // Primary color border when focused
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.7f), // More visible unfocused border
                focusedLabelColor = customColors.primary, // Primary color label when focused
                unfocusedLabelColor = Color.Gray,
                focusedTextColor = Color.Black, // Dark text for readability
                unfocusedTextColor = Color.Black
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
            ),
            enabled = !isLoading
        )

        // Create Account button - Enhanced styling
        Button(
            onClick = onSignUp,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isLoading && name.isNotBlank() && email.isNotBlank() &&
                     password.isNotBlank() && confirmPassword.isNotBlank(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp,
                pressedElevation = 2.dp
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    "Create Account",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    authState: com.coachie.app.viewmodel.AuthState,
    errorMessage: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (name: String, email: String, password: String) -> Unit
) {
    val isLoading = authState is com.coachie.app.viewmodel.AuthState.Loading
    var selectedTabIndex by remember { mutableStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var localErrorMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val gradientBackground = rememberCoachieGradient(endY = 1600f)

    // Update local error when auth error message changes
    LaunchedEffect(errorMessage) {
        localErrorMessage = errorMessage
    }

    // Show success snackbar on successful auth
    LaunchedEffect(authState) {
        if (authState is com.coachie.app.viewmodel.AuthState.Authenticated) {
            snackbarHostState.showSnackbar("Welcome!")
        }
    }

    val tabs = listOf("Sign In", "Sign Up")

    // Custom Material 3 theme with specific colors
    val customColors = MaterialTheme.colorScheme.copy(
        primary = Color(0xFF6200EE), // Purple instead of green
        onPrimary = Color.White
    )
    val customTheme = MaterialTheme.colorScheme.copy(
        primary = customColors.primary,
        onPrimary = customColors.onPrimary
    )

    MaterialTheme(colorScheme = customTheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                containerColor = Color.Transparent
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top spacing
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    // Top section with title and icon - More prominent
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        // Large emoji with better styling
                        Text(
                            text = "🏋️‍♀️",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = 80.sp
                            )
                        )

                        Text(
                            text = "Welcome to Coachie",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 32.sp,
                                letterSpacing = (-0.5).sp
                            ),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Your personal fitness companion",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 16.sp
                            ),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))

                    // Modern Tab Row with better styling
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        colors = CardDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.95f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        TabRow(
                            selectedTabIndex = selectedTabIndex,
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color.Transparent,
                            contentColor = customColors.primary,
                            indicator = { }
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = {
                                        selectedTabIndex = index
                                        localErrorMessage = null // Clear error when switching tabs
                                    },
                                    text = { 
                                        Text(
                                            title,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedTabIndex == index) customColors.primary else Color.Gray // Dark text for tabs
                                            )
                                        ) 
                                    },
                                    selectedContentColor = customColors.primary,
                                    unselectedContentColor = Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Content based on selected tab - Enhanced card design
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        colors = CardDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.98f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                        when (selectedTabIndex) {
                            0 -> SignInContent(
                                email = email,
                                onEmailChange = {
                                    email = it
                                    localErrorMessage = null
                                },
                                password = password,
                                onPasswordChange = {
                                    password = it
                                    localErrorMessage = null
                                },
                                showPassword = showPassword,
                                onShowPasswordChange = { showPassword = it },
                                isLoading = isLoading,
                                onSignIn = {
                                    if (validateSignIn(email, password)) {
                                        DebugLogger.logUserInteraction("AuthScreen", "Sign in attempted")
                                        onSignIn(email, password)
                                    }
                                },
                                primaryColor = customColors.primary
                            )
                            1 -> SignUpContent(
                                name = name,
                                onNameChange = {
                                    name = it
                                    localErrorMessage = null
                                },
                                email = email,
                                onEmailChange = {
                                    email = it
                                    localErrorMessage = null
                                },
                                password = password,
                                onPasswordChange = {
                                    password = it
                                    localErrorMessage = null
                                },
                                confirmPassword = confirmPassword,
                                onConfirmPasswordChange = {
                                    confirmPassword = it
                                    localErrorMessage = null
                                },
                                showPassword = showPassword,
                                onShowPasswordChange = { showPassword = it },
                                isLoading = isLoading,
                                onSignUp = {
                                    val validationError = validateSignUp(name, email, password, confirmPassword)
                                    if (validationError == null) {
                                        DebugLogger.logUserInteraction("AuthScreen", "Sign up attempted")
                                        onSignUp(name, email, password)
                                    } else {
                                        localErrorMessage = validationError
                                        DebugLogger.logDebug("AuthScreen", "Sign up validation failed: $validationError")
                                    }
                                },
                                primaryColor = customColors.primary,
                                customColors = customTheme
                            )
                        }

                            // Error message display - Better styling
                            localErrorMessage?.let { error ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.colors(
                                        containerColor = Color(0xFFFFEBEE)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Warning,
                                            contentDescription = "Error",
                                            tint = Color(0xFFD32F2F),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = error,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = Color(0xFFD32F2F)
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}
