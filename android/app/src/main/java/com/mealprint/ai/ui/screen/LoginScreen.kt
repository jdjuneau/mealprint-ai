package com.coachie.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprint.ai.viewmodel.AuthState
import com.mealprint.ai.viewmodel.CoachieViewModel
import java.util.regex.Pattern

// Email validation pattern
private val EMAIL_PATTERN = Pattern.compile(
    "[a-zA-Z0-9+._%\\-+]{1,256}" +
    "@" +
    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
    "(" +
    "\\." +
    "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
    ")+"
)

private fun isValidEmail(email: String): Boolean {
    return email.isNotBlank() && EMAIL_PATTERN.matcher(email).matches()
}

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: CoachieViewModel = viewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }

    val authState by viewModel.authState.collectAsState()

    // Validate inputs
    val isEmailValid = isValidEmail(email)
    val isPasswordValid = password.length >= 6
    val canSubmit = isEmailValid && isPasswordValid

    // Handle authentication state
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isSignUp) "Create Account" else "Welcome to Coachie",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            isError = email.isNotBlank() && !isEmailValid,
            supportingText = {
                if (email.isNotBlank() && !isEmailValid) {
                    Text("Please enter a valid email address")
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            isError = password.isNotBlank() && !isPasswordValid,
            supportingText = {
                if (password.isNotBlank() && !isPasswordValid) {
                    Text("Password must be at least 6 characters")
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Show loading or error states
        when (authState) {
            is AuthState.Loading -> {
                CircularProgressIndicator()
            }
            is AuthState.Error -> {
                Text(
                    text = (authState as AuthState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                // Show buttons
                Button(
                    onClick = {
                        if (isSignUp) {
                            viewModel.signUp(email.trim(), password)
                        } else {
                            viewModel.signIn(email.trim(), password)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSubmit
                ) {
                    Text(if (isSignUp) "Sign Up" else "Sign In")
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(
                    onClick = { isSignUp = !isSignUp }
                ) {
                    Text(
                        text = if (isSignUp)
                            "Already have an account? Sign In"
                        else
                            "Don't have an account? Sign Up"
                    )
                }
            }
        }
    }
}
