package com.coachie.app.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.Voice as TtsVoice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.viewmodel.VoiceSettingsViewModel
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val viewModel: VoiceSettingsViewModel = viewModel()
    val scope = rememberCoroutineScope()

    // Voice settings state
    var voiceEnabled by remember { mutableStateOf(preferencesManager.voiceEnabled) }
    var selectedVoice by remember { mutableStateOf<String?>(preferencesManager.voiceLanguage) }
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var selectedGender by remember { mutableStateOf<String?>(null) } // "male", "female", "neutral", or null
    var pitch by remember { mutableStateOf(preferencesManager.voicePitch) }
    var rate by remember { mutableStateOf(preferencesManager.voiceRate) }
    var volume by remember { mutableStateOf(preferencesManager.voiceVolume) }

    // Available voices
    var availableVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var availableLanguages by remember { mutableStateOf<List<Locale>>(emptyList()) }

    // Permission states
    val hasMicrophonePermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission dialog state
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicrophonePermission.value = granted
        if (!granted) {
            // Show dialog to go to settings
            showPermissionDialog = true
        }
    }

    // Refresh permission status when screen is visible
    LaunchedEffect(Unit) {
        hasMicrophonePermission.value = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Initialize TTS to get available voices
    LaunchedEffect(Unit) {
        viewModel.initializeTTS(context) { tts ->
            if (tts != null) {
                availableVoices = tts.voices?.toList() ?: emptyList()
                availableLanguages = tts.availableLanguages?.toList() ?: emptyList()
            }
        }
    }

    // Initialize selected language and gender from saved voice
    LaunchedEffect(selectedVoice, availableVoices) {
        if (selectedVoice != null && availableVoices.isNotEmpty()) {
            val voice = availableVoices.find { it.name == selectedVoice }
            voice?.let {
                if (selectedLanguage == null) {
                    selectedLanguage = "${it.locale.language}-${it.locale.country}"
                }
                // Determine gender from voice name
                val name = it.name.lowercase()
                if (selectedGender == null) {
                    selectedGender = when {
                        name.contains("male") || name.contains("man") || name.contains("guy") ||
                        name.contains("alex") || name.contains("daniel") || name.contains("tom") ||
                        name.contains("david") || name.contains("paul") || name.contains("mark") -> "male"
                        name.contains("female") || name.contains("woman") || name.contains("girl") ||
                        name.contains("samantha") || name.contains("susan") || name.contains("zira") ||
                        name.contains("hazel") || name.contains("karen") || name.contains("emma") ||
                        name.contains("sarah") || name.contains("anna") -> "female"
                        else -> "neutral"
                    }
                }
            }
        }
    }

    // Group voices by gender/type
    // Android TTS voices don't always have gender in the name, so we use a more comprehensive approach
    val maleVoices = availableVoices.filter { voice ->
        val name = voice.name.lowercase()
        // Check name patterns
        name.contains("male") || name.contains("man") || name.contains("guy") ||
        name.contains("alex") || name.contains("daniel") || name.contains("tom") ||
        name.contains("david") || name.contains("paul") || name.contains("mark") ||
        name.contains("james") || name.contains("john") || name.contains("michael") ||
        name.contains("william") || name.contains("robert") || name.contains("richard") ||
        name.contains("joseph") || name.contains("thomas") || name.contains("charles") ||
        // Check for common male voice identifiers in TTS engines
        name.contains("en-us-x-sfg") || name.contains("en-us-x-iol") ||
        name.contains("en-us-x-sam") || name.contains("en-us-x-dfd") ||
        // Additional check: if it's en-us and doesn't explicitly say female/woman, might be male
        (name.contains("en-us") && !name.contains("female") && !name.contains("woman") && 
         !name.contains("samantha") && !name.contains("susan") && !name.contains("zira") &&
         !name.contains("hazel") && !name.contains("karen") && !name.contains("emma"))
    }

    val femaleVoices = availableVoices.filter { voice ->
        val name = voice.name.lowercase()
        // Check name patterns
        name.contains("female") || name.contains("woman") || name.contains("girl") ||
        name.contains("samantha") || name.contains("susan") || name.contains("zira") ||
        name.contains("hazel") || name.contains("karen") || name.contains("emma") ||
        name.contains("sarah") || name.contains("anna") || name.contains("linda") ||
        name.contains("mary") || name.contains("patricia") || name.contains("jennifer") ||
        name.contains("elizabeth") || name.contains("barbara") || name.contains("susan") ||
        // Check for common female voice identifiers in TTS engines
        name.contains("en-us-x-sf") && !name.contains("sfg") ||
        name.contains("en-us-x-iof") || name.contains("en-us-x-kpf")
    }

    val neutralVoices = availableVoices.filter { voice ->
        !maleVoices.contains(voice) && !femaleVoices.contains(voice)
    }

    // Filter voices by selected language and gender
    val filteredVoices = remember(selectedLanguage, selectedGender, availableVoices, maleVoices, femaleVoices, neutralVoices) {
        var filtered = availableVoices
        
        // Filter by language
        if (selectedLanguage != null) {
            val langParts = selectedLanguage!!.split("-")
            val langCode = langParts[0]
            val countryCode = if (langParts.size > 1) langParts[1] else null
            
            filtered = filtered.filter { voice ->
                voice.locale.language.equals(langCode, ignoreCase = true) &&
                (countryCode == null || voice.locale.country.equals(countryCode, ignoreCase = true))
            }
            
            android.util.Log.d("VoiceSettings", "After language filter (${selectedLanguage}): ${filtered.size} voices")
        }
        
        // Filter by gender
        if (selectedGender != null && filtered.isNotEmpty()) {
            val beforeGenderFilter = filtered.size
            filtered = when (selectedGender) {
                "male" -> filtered.filter { maleVoices.contains(it) }
                "female" -> filtered.filter { femaleVoices.contains(it) }
                "neutral" -> filtered.filter { neutralVoices.contains(it) }
                else -> filtered
            }
            android.util.Log.d("VoiceSettings", "After gender filter (${selectedGender}): ${filtered.size} voices (was $beforeGenderFilter)")
            
            // If filtering by gender results in empty list, show all voices for that language
            // This handles cases where gender detection isn't perfect
            if (filtered.isEmpty() && selectedLanguage != null) {
                android.util.Log.d("VoiceSettings", "Gender filter resulted in empty list, showing all voices for language")
                // Re-apply only language filter
                val langParts = selectedLanguage!!.split("-")
                val langCode = langParts[0]
                val countryCode = if (langParts.size > 1) langParts[1] else null
                filtered = availableVoices.filter { voice ->
                    voice.locale.language.equals(langCode, ignoreCase = true) &&
                    (countryCode == null || voice.locale.country.equals(countryCode, ignoreCase = true))
                }
            }
        }
        
        android.util.Log.d("VoiceSettings", "Final filtered voices: ${filtered.size} (total available: ${availableVoices.size}, male: ${maleVoices.size}, female: ${femaleVoices.size}, neutral: ${neutralVoices.size})")
        filtered
    }

    // Save settings when they change
    fun saveSettings() {
        preferencesManager.voiceEnabled = voiceEnabled
        preferencesManager.voiceLanguage = selectedVoice
        preferencesManager.voicePitch = pitch
        preferencesManager.voiceRate = rate
        preferencesManager.voiceVolume = volume
    }

    // Test voice function
    fun testVoice() {
        viewModel.testVoice(context, selectedVoice, pitch, rate, volume)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
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
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Permissions Section
                item {
                    PermissionsSection(
                        hasMicrophonePermission = hasMicrophonePermission.value,
                        showPermissionDialog = showPermissionDialog,
                        onRequestPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        onDismissDialog = { showPermissionDialog = false }
                    )
                }

                // Voice Enable/Disable
                item {
                    CoachieCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Voice Output",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Enable voice responses",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Switch(
                                    checked = voiceEnabled,
                                    onCheckedChange = { enabled ->
                                        voiceEnabled = enabled
                                        saveSettings()
                                    }
                                )
                            }

                            Text(
                                text = "When enabled, Coachie will speak responses automatically",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Language Selection
                item {
                    CoachieCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Language",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Language options
                            val languages = listOf("en-US", "en-GB", "es-ES", "fr-FR", "de-DE", "it-IT", "pt-BR", "ja-JP", "ko-KR", "zh-CN")
                            val languageNames = mapOf(
                                "en-US" to "English (US)",
                                "en-GB" to "English (UK)",
                                "es-ES" to "Spanish",
                                "fr-FR" to "French",
                                "de-DE" to "German",
                                "it-IT" to "Italian",
                                "pt-BR" to "Portuguese",
                                "ja-JP" to "Japanese",
                                "ko-KR" to "Korean",
                                "zh-CN" to "Chinese"
                            )

                            languages.forEach { langCode ->
                                val isSelected = selectedLanguage == langCode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Update language preference
                                            selectedLanguage = langCode
                                            selectedVoice = null // Reset voice selection when language changes
                                            selectedGender = null // Reset gender when language changes
                                            saveSettings()
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = languageNames[langCode] ?: langCode,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Gender Selection
                item {
                    CoachieCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Voice Gender",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Female option
                                FilterChip(
                                    selected = selectedGender == "female",
                                    onClick = {
                                        selectedGender = "female"
                                        selectedVoice = null // Reset voice selection when gender changes
                                        saveSettings()
                                    },
                                    label = { Text("Female 👩") },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )

                                // Male option
                                FilterChip(
                                    selected = selectedGender == "male",
                                    onClick = {
                                        selectedGender = "male"
                                        selectedVoice = null // Reset voice selection when gender changes
                                        saveSettings()
                                    },
                                    label = { Text("Male 👨") },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )

                            }
                        }
                    }
                }

                // Voice Selection
                item {
                    CoachieCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Specific Voice",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Choose a specific voice from the available options",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (availableVoices.isNotEmpty()) {
                                // Show filtered voices (by language and gender)
                                if (filteredVoices.isNotEmpty()) {
                                    Text(
                                        text = "Available Voices (${filteredVoices.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                    filteredVoices.forEach { voice ->
                                        VoiceOption(
                                            voice = voice,
                                            isSelected = voice.name == selectedVoice,
                                            onSelect = {
                                                selectedVoice = voice.name
                                                saveSettings()
                                            }
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "No voices available for the selected language and gender combination. Please try a different selection.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = "Loading available voices...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Voice Parameters
                item {
                    CoachieCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "Voice Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            // Pitch
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Pitch: ${String.format("%.1f", pitch)}x",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    IconButton(onClick = { testVoice() }) {
                                        Icon(Icons.Filled.VolumeUp, "Test voice")
                                    }
                                }
                                Slider(
                                    value = pitch,
                                    onValueChange = { pitch = it },
                                    onValueChangeFinished = { saveSettings() },
                                    valueRange = 0.5f..2.0f,
                                    steps = 15
                                )
                                Text(
                                    text = "Lower = deeper voice, Higher = higher pitched voice",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Divider()

                            // Speech Rate
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Speed: ${String.format("%.1f", rate)}x",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Slider(
                                    value = rate,
                                    onValueChange = { rate = it },
                                    onValueChangeFinished = { saveSettings() },
                                    valueRange = 0.5f..2.0f,
                                    steps = 15
                                )
                                Text(
                                    text = "Lower = slower speech, Higher = faster speech",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Divider()

                            // Volume (Note: Android TTS volume is usually controlled system-wide)
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Volume: ${String.format("%.1f", volume)}x",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Slider(
                                    value = volume,
                                    onValueChange = { volume = it },
                                    onValueChangeFinished = { saveSettings() },
                                    valueRange = 0.1f..1.0f,
                                    steps = 9
                                )
                                Text(
                                    text = "Note: Volume is also controlled by system settings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Test Voice Section
                item {
                    CoachieCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Test Your Voice Settings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = "Tap the play button above or use the button below to hear how your settings sound.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Button(
                                onClick = { testVoice() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = voiceEnabled
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Test voice",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Voice")
                            }

                            Text(
                                text = "\"Hello! I'm Coachie, your AI fitness assistant. How can I help you today?\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

// Permissions Section Component
@Composable
private fun PermissionsSection(
    hasMicrophonePermission: Boolean,
    showPermissionDialog: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissDialog: () -> Unit
) {

    CoachieCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Microphone Permission
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Microphone",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (!hasMicrophonePermission) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Permission required",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = if (hasMicrophonePermission) "Granted" else "Required for voice input",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasMicrophonePermission) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }
                
                Button(
                    onClick = {
                        if (hasMicrophonePermission) {
                            // Already granted, open settings if needed
                            onOpenSettings()
                        } else {
                            // Request permission
                            onRequestPermission()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasMicrophonePermission) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        text = if (hasMicrophonePermission) "Settings" else "Enable",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (!hasMicrophonePermission) {
                Text(
                    text = "Microphone permission is required for voice logging and voice commands. Tap 'Enable' to grant permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Permission denied dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = onDismissDialog,
            title = { Text("Microphone Permission Required") },
            text = {
                Text("Coachie needs microphone permission for voice features. Please enable it in app settings.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissDialog()
                    onOpenSettings()
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Get a user-friendly name for a voice based on its technical ID
 */
private fun getVoiceDisplayName(voice: Voice): String {
    val name = voice.name.lowercase()
    val isNetwork = name.contains("network")
    val isLocal = name.contains("local")
    val source = when {
        isNetwork -> " (Online)"
        isLocal -> " (Offline)"
        else -> ""
    }
    
    // Map common voice ID patterns to friendly names
    return when {
        // Google TTS Neural voices - specific mappings
        name.contains("en-us-x-tpf") -> "Premium Voice$source"
        name.contains("en-us-x-sfg") -> "Standard Voice$source"
        name.contains("en-us-x-iob") -> "Balanced Voice$source"
        name.contains("en-us-x-iol") -> "Light Voice$source"
        name.contains("en-us-x-iom") -> "Medium Voice$source"
        name.contains("en-us-x-tpd") -> "Deep Voice$source"
        name.contains("en-us-x-tpc") -> "Clear Voice$source"
        name.contains("en-us-x-iof") -> "Female Voice$source"
        name.contains("en-us-x-kpf") -> "Premium Female Voice$source"
        name.contains("en-us-x-sam") -> "Sam$source"
        name.contains("en-us-x-dfd") -> "David$source"
        
        // Common voice names (check for exact matches first)
        name.contains("samantha") && !name.contains("en-us") -> "Samantha"
        name.contains("alex") && !name.contains("en-us-x") -> "Alex"
        name.contains("daniel") && !name.contains("en-us-x") -> "Daniel"
        name.contains("susan") && !name.contains("en-us-x") -> "Susan"
        name.contains("zira") && !name.contains("en-us-x") -> "Zira"
        name.contains("hazel") && !name.contains("en-us-x") -> "Hazel"
        name.contains("karen") && !name.contains("en-us-x") -> "Karen"
        name.contains("emma") && !name.contains("en-us-x") -> "Emma"
        name.contains("tom") && !name.contains("en-us-x") -> "Tom"
        name.contains("david") && !name.contains("en-us-x") -> "David"
        name.contains("paul") && !name.contains("en-us-x") -> "Paul"
        name.contains("mark") && !name.contains("en-us-x") -> "Mark"
        
        // Generic patterns
        name.contains("male") || name.contains("man") -> "Male Voice$source"
        name.contains("female") || name.contains("woman") -> "Female Voice$source"
        
        // Fallback: use locale with voice type indicator
        else -> {
            val localeName = voice.locale.displayLanguage
            val country = voice.locale.displayCountry
            val type = when {
                name.contains("neural") -> "Neural"
                name.contains("premium") -> "Premium"
                name.contains("standard") -> "Standard"
                else -> "Voice"
            }
            if (country.isNotEmpty() && country != localeName) {
                "$localeName ($country) - $type$source"
            } else {
                "$localeName - $type$source"
            }
        }
    }
}

/**
 * Get a subtitle/description for a voice
 */
private fun getVoiceSubtitle(voice: Voice): String {
    val name = voice.name.lowercase()
    val localeName = voice.locale.displayName
    
    // Add voice characteristics
    val characteristics = mutableListOf<String>()
    
    when {
        name.contains("network") -> characteristics.add("Network")
        name.contains("local") -> characteristics.add("Local")
    }
    
    when {
        name.contains("neural") -> characteristics.add("Neural")
        name.contains("premium") -> characteristics.add("Premium")
    }
    
    val charString = if (characteristics.isNotEmpty()) {
        " • ${characteristics.joinToString(", ")}"
    } else {
        ""
    }
    
    return "$localeName$charString"
}

// Voice option component
@Composable
private fun VoiceOption(
    voice: Voice,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = getVoiceDisplayName(voice),
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = getVoiceSubtitle(voice),
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.7f)
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
