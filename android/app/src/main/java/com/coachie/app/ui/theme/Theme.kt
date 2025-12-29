package com.coachie.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================================
// FEMININE COLOR SCHEMES (Default - current design)
// ============================================================================

private val DarkColorScheme = darkColorScheme(
    primary = Primary80,
    onPrimary = Primary20,
    primaryContainer = Primary30,
    onPrimaryContainer = Primary90,
    secondary = Secondary80,
    onSecondary = Secondary20,
    secondaryContainer = Secondary30,
    onSecondaryContainer = Secondary90,
    tertiary = Tertiary80,
    onTertiary = Tertiary20,
    tertiaryContainer = Tertiary30,
    onTertiaryContainer = Tertiary90,
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,
    background = Grey10,
    onBackground = Grey90,
    surface = Grey10,
    onSurface = Grey90,
    surfaceVariant = Grey30,
    onSurfaceVariant = Grey80,
    outline = Grey60
)

private val LightColorScheme = lightColorScheme(
    primary = Primary40,
    onPrimary = Primary100,
    primaryContainer = Primary90,
    onPrimaryContainer = Primary10,
    secondary = Secondary40,
    onSecondary = Secondary100,
    secondaryContainer = Secondary90,
    onSecondaryContainer = Secondary10,
    tertiary = Tertiary40,
    onTertiary = Tertiary100,
    tertiaryContainer = Tertiary90,
    onTertiaryContainer = Tertiary10,
    error = Error40,
    onError = Error100,
    errorContainer = Error90,
    onErrorContainer = Error10,
    background = Grey99,
    onBackground = Color.White, // Changed to white for better readability on colored gradient backgrounds
    surface = Color.White,
    onSurface = Grey10, // Keep dark for white surfaces/cards
    surfaceVariant = Grey90,
    onSurfaceVariant = Grey10, // Changed from Grey80 (light blue) to dark grey for better readability - removed alpha to make it fully opaque
    outline = Grey50
)

// ============================================================================
// MASCULINE COLOR SCHEMES
// ============================================================================

private val MaleDarkColorScheme = darkColorScheme(
    primary = MalePrimary80,
    onPrimary = MalePrimary20,
    primaryContainer = MalePrimary30,
    onPrimaryContainer = MalePrimary90,
    secondary = MaleSecondary80,
    onSecondary = MaleSecondary20,
    secondaryContainer = MaleSecondary30,
    onSecondaryContainer = MaleSecondary90,
    tertiary = MaleTertiary80,
    onTertiary = MaleTertiary20,
    tertiaryContainer = MaleTertiary30,
    onTertiaryContainer = MaleTertiary90,
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,
    background = MaleGrey10,
    onBackground = MaleGrey90,
    surface = MaleGrey10,
    onSurface = MaleGrey90,
    surfaceVariant = MaleGrey30,
    onSurfaceVariant = MaleGrey80,
    outline = MaleGrey60
)

private val MaleLightColorScheme = lightColorScheme(
    primary = MaleTertiary40, // Changed from blue to orange for non-blue theme
    onPrimary = Color.White, // WHITE text on colored primary buttons
    primaryContainer = MaleTertiary90,
    onPrimaryContainer = Color.White, // WHITE text on primary containers
    secondary = MaleSecondary40,
    onSecondary = Color.White, // WHITE text on colored secondary buttons
    secondaryContainer = MaleSecondary90,
    onSecondaryContainer = Color.White, // WHITE text on secondary containers
    tertiary = MaleTertiary40,
    onTertiary = Color.White, // WHITE text on colored tertiary buttons
    tertiaryContainer = MaleTertiary90,
    onTertiaryContainer = Color.White, // WHITE text on tertiary containers
    error = Error40,
    onError = Color.White, // WHITE text on error buttons
    errorContainer = Error90,
    onErrorContainer = Color.White, // WHITE text on error containers
    // Background text: WHITE for gradient background
    background = MaleGrey99, // Very light background (top of gradient)
    onBackground = Color.White, // WHITE text on gradient background
    // Surface text: DARK for white cards/surfaces
    surface = Color.White, // White surface (cards, sheets, etc.)
    onSurface = MaleGrey10, // DARK text on white surfaces for readability
    surfaceVariant = MaleGrey90, // Light variant surface
    onSurfaceVariant = MaleGrey10, // DARK text on surface variants for readability
    outline = MaleGrey50
)

// ============================================================================
// GRADIENT COLORS
// ============================================================================

private val CoachieGradientColors = listOf(
    Color(0xFFF9FBFF),
    Accent90.copy(alpha = 0.6f),
    Primary40.copy(alpha = 0.45f)
)

private val MaleCoachieGradientColors = listOf(
    MaleGrey99, // Light grey at top
    MaleTertiary90.copy(alpha = 0.4f), // Lighter orange/amber tones - reduced from 0.6f
    MaleSecondary90.copy(alpha = 0.3f) // Much lighter teal/green tones - changed from MaleSecondary40 (0.5f) to MaleSecondary90 (0.3f)
)

// Composition local for gender information
val LocalGender = compositionLocalOf<String> { "" }

@Composable
fun rememberCoachieGradient(
    startY: Float = 0f,
    endY: Float = 1600f
): Brush {
    val gender = LocalGender.current
    return remember(startY, endY, gender) {
        val isMale = gender.lowercase() == "male"
        android.util.Log.d("rememberCoachieGradient", "Creating gradient - Gender: '$gender', isMale: $isMale")
        val gradientColors = if (isMale) {
            android.util.Log.d("rememberCoachieGradient", "Using MALE gradient colors")
            MaleCoachieGradientColors
        } else {
            android.util.Log.d("rememberCoachieGradient", "Using FEMALE gradient colors")
            CoachieGradientColors
        }
        Brush.verticalGradient(
            colors = gradientColors,
            startY = startY,
            endY = endY
        )
    }
}

@Composable
fun CoachieTheme(
    darkTheme: Boolean = false, // Default to light theme for a more fun, pleasant experience
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    gender: String = "", // "male", "female", "other" - defaults to feminine scheme
    content: @Composable () -> Unit
) {
    // CRITICAL: Check gender FIRST and log it for debugging
    val genderLower = gender.lowercase().trim()
    val isMale = genderLower == "male" || genderLower == "m"
    android.util.Log.d("CoachieTheme", "🎨🎨🎨 THEME COMPOSITION 🎨🎨🎨")
    android.util.Log.d("CoachieTheme", "  Gender parameter: '$gender'")
    android.util.Log.d("CoachieTheme", "  Gender (lowercase): '$genderLower'")
    android.util.Log.d("CoachieTheme", "  Is Male: $isMale")
    android.util.Log.d("CoachieTheme", "  Using ${if (isMale) "MALE" else "FEMALE"} theme")
    
    val colorScheme = when {
        // CRITICAL: Disable dynamic color to ensure consistent custom colors
        // Dynamic color overrides our custom theme and uses system colors instead
        // This causes UI inconsistencies between male and female themes
        // dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isMale -> {
        //     val context = LocalContext.current
        //     if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        // }

        darkTheme -> if (isMale) MaleDarkColorScheme else DarkColorScheme
        else -> if (isMale) MaleLightColorScheme else LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }

    CompositionLocalProvider(LocalGender provides gender) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(rememberCoachieGradient())
                ) {
                    content()
                }
            }
        )
    }
}
