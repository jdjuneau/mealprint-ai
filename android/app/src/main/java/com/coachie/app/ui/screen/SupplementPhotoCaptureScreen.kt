package com.coachie.app.ui.screen

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.ai.SupplementAnalysis
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.data.model.formatMicronutrientAmount
import com.coachie.app.data.model.toPersistedMicronutrientMap
import com.coachie.app.viewmodel.SupplementPhotoCaptureUiState
import com.coachie.app.viewmodel.SupplementPhotoCaptureViewModel
import com.coachie.app.ui.theme.rememberCoachieGradient
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplementPhotoCaptureScreen(
    userId: String? = null,
    onBack: () -> Unit = {},
    onSupplementSaved: () -> Unit = {},
    viewModel: SupplementPhotoCaptureViewModel = viewModel(
        factory = SupplementPhotoCaptureViewModel.Factory(
            context = LocalContext.current,
            firebaseRepository = FirebaseRepository.getInstance(),
            preferencesManager = PreferencesManager(LocalContext.current),
            userId = userId ?: com.coachie.app.util.AuthUtils.getAuthenticatedUserId() ?: "anonymous_user"
        )
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val hasCameraPermission by viewModel.hasCameraPermission.collectAsState()

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateCameraPermission(granted)
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            viewModel.onPhotoTaken()
        }
    }

    // Request camera permission when screen opens
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Handle successful save
    LaunchedEffect(uiState) {
        if (uiState is SupplementPhotoCaptureUiState.Success) {
            onSupplementSaved()
        }
    }

    val gradientBackground = rememberCoachieGradient(endY = 2000f)
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Log Supplement", color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = gradientBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Mode selector: Camera or Manual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState is SupplementPhotoCaptureUiState.Idle || uiState is SupplementPhotoCaptureUiState.PhotoTaken,
                    onClick = {
                        if (hasCameraPermission) {
                            viewModel.createPhotoFile(context)?.let { uri ->
                                cameraLauncher.launch(uri)
                            }
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    label = { Text("📷 Camera", color = Color.Black) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = uiState is SupplementPhotoCaptureUiState.ManualEntry,
                    onClick = { viewModel.setManualEntryMode() },
                    label = { Text("✍️ Manual", color = Color.Black) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        selectedLabelColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            when (val state = uiState) {
                is SupplementPhotoCaptureUiState.Idle -> {
                    // Show camera button
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Camera,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "📱 Photograph the BARCODE on your supplement bottle",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                text = "NOT the 'Supplement Facts' panel - find the UPC/EAN barcode on the back or side",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                            Text(
                                text = "Position the camera so the barcode lines are sharp and clearly visible",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Coachie will automatically look up your supplement's nutrient information online",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    if (hasCameraPermission) {
                                        viewModel.createPhotoFile(context)?.let { uri ->
                                            cameraLauncher.launch(uri)
                                        }
                                    } else {
                                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            ) {
                                Text("Scan Barcode")
                            }
                        }
                    }
                }

                is SupplementPhotoCaptureUiState.PhotoTaken -> {
                    PhotoPreviewContent(
                        photoUri = state.photoUri,
                        onRetake = { viewModel.resetState() },
                        onAnalyze = { uri ->
                            viewModel.analyzeSupplementImage(uri)
                        }
                    )
                }

                is SupplementPhotoCaptureUiState.Analyzing -> {
                    AnalyzingContent()
                }

                is SupplementPhotoCaptureUiState.AnalysisResult -> {
                    AnalysisResultContent(
                        analysis = state.analysis,
                        photoUri = state.photoUri,
                        onEdit = { viewModel.editAnalysisResult() },
                        onAddMore = { viewModel.addAnalysisResultToSupplements(it) },
                        onSave = { supplements ->
                            viewModel.saveSupplements(supplements)
                        }
                    )
                }

                is SupplementPhotoCaptureUiState.ManualEntry -> {
                    ManualEntryContent(
                        supplementName = state.supplementName,
                        micronutrients = state.micronutrients,
                        isDaily = state.isDaily,
                        isSearching = state.isSearching,
                        searchError = state.searchError,
                        searchNotes = state.searchNotes,
                        onSupplementNameChange = { viewModel.updateSupplementName(it) },
                        onMicronutrientChange = { type, amount ->
                            viewModel.updateMicronutrient(type, amount)
                        },
                        onIsDailyChange = { viewModel.updateIsDaily(it) },
                        onSearchSupplement = { brand, type ->
                            viewModel.searchSupplement(brand, type)
                        },
                        onSaveSingleSupplement = { viewModel.saveSingleSupplementEntry() },
                        onAddToSupplements = { viewModel.addSupplementToList() },
                        onRemoveFromSupplements = { index -> viewModel.removeSupplementFromList(index) },
                        onSaveCombinedSupplements = { viewModel.saveCombinedSupplements() }
                    )
                }

                is SupplementPhotoCaptureUiState.Saving -> {
                    SavingContent()
                }

                is SupplementPhotoCaptureUiState.Success -> {
                    SuccessContent(
                        onDone = {
                            viewModel.resetState()
                            onBack()
                        }
                    )
                }

                is SupplementPhotoCaptureUiState.Error -> {
                    ErrorContent(
                        error = state.message,
                        onRetry = { viewModel.resetState() },
                        onBack = onBack
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun ManualEntryContent(
    supplementName: String,
    micronutrients: Map<com.coachie.app.data.model.MicronutrientType, Double>,
    isDaily: Boolean = false,
    isSearching: Boolean = false,
    searchError: String? = null,
    searchNotes: String? = null,
    onSupplementNameChange: (String) -> Unit,
    onMicronutrientChange: (com.coachie.app.data.model.MicronutrientType, Double) -> Unit,
    onIsDailyChange: (Boolean) -> Unit,
    onSearchSupplement: (String, String) -> Unit,
    onSaveSingleSupplement: () -> Unit,
    onAddToSupplements: () -> Unit,
    onRemoveFromSupplements: (Int) -> Unit,
    onSaveCombinedSupplements: () -> Unit
) {
    var brand by remember { mutableStateOf("") }
    var supplementType by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Enter Supplement Details",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // AI Search Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🔍 Search by Brand & Type",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Enter the brand and supplement type to automatically find vitamin and mineral amounts. Works for organ supplements too!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand (e.g., Ancestral Supplements, Thorne)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSearching
                )

                OutlinedTextField(
                    value = supplementType,
                    onValueChange = { supplementType = it },
                    label = { Text("Type (e.g., Beef Liver, Multivitamin, D3)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSearching
                )

                if (isSearching) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Searching...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (brand.isNotBlank() && supplementType.isNotBlank()) {
                                onSearchSupplement(brand.trim(), supplementType.trim())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = brand.isNotBlank() && supplementType.isNotBlank()
                    ) {
                        Text("Search & Auto-Fill")
                    }
                }

                searchError?.let { error ->
                    Text(
                        text = "❌ $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                searchNotes?.let { notes ->
                    Text(
                        text = "ℹ️ $notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Divider()

        OutlinedTextField(
            value = supplementName,
            onValueChange = onSupplementNameChange,
            label = { Text("Supplement Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("e.g., Vitamin D3, Multivitamin, Magnesium") }
        )

        // Daily supplement toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mark as Daily Supplement",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "This supplement will be automatically logged daily",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isDaily,
                onCheckedChange = onIsDailyChange
            )
        }

        Divider()

        // Micronutrient inputs
        Text(
            text = "Nutrients & Dosages",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )

        // All 24 micronutrients to input
        val allNutrients = com.coachie.app.data.model.MicronutrientType.ordered

        allNutrients.forEach { nutrient ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = nutrient.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = micronutrients[nutrient]?.toString() ?: "",
                    onValueChange = { value ->
                        val amount = value.toDoubleOrNull() ?: 0.0
                        onMicronutrientChange(nutrient, amount)
                    },
                    modifier = Modifier.weight(0.6f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("0") },
                    suffix = { Text(nutrient.unit.displaySuffix) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Show list of added supplements
        if (micronutrients.isNotEmpty() && supplementName.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Current Supplement",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = supplementName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    if (micronutrients.isNotEmpty()) {
                        Text(
                            text = micronutrients.entries
                                .filter { it.value > 0 }
                                .joinToString(", ") { entry ->
                                    val amount = entry.value.formatMicronutrientAmount()
                                    "${entry.key.displayName}: ${amount}${entry.key.unit.displaySuffix}"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Add to Supplements / Save Supplements buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onAddToSupplements,
                modifier = Modifier.weight(1f),
                enabled = supplementName.isNotBlank() && micronutrients.values.any { it > 0 },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Add to List")
            }

            Button(
                onClick = {
                    if (micronutrients.values.any { it > 0 } || supplementName.isNotBlank()) {
                        onSaveCombinedSupplements()
                    } else {
                        onSaveSingleSupplement()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = supplementName.isNotBlank() || micronutrients.values.any { it > 0 }
            ) {
                Text("Save Supplement")
            }
        }
    }
}

@Composable
private fun PhotoPreviewContent(
    photoUri: Uri,
    onRetake: () -> Unit,
    onAnalyze: (Uri) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Preview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            AsyncImage(
                model = photoUri,
                contentDescription = "Supplement photo preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Retake")
            }

            Button(
                onClick = { onAnalyze(photoUri) },
                modifier = Modifier.weight(1f)
            ) {
                Text("Analyze")
            }
        }
    }
}

@Composable
private fun AnalyzingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = "Analyzing your supplement…",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AnalysisResultContent(
    analysis: SupplementAnalysis,
    photoUri: Uri?,
    onEdit: () -> Unit,
    onAddMore: (SupplementAnalysis) -> Unit,
    onSave: (List<com.coachie.app.data.model.Supplement>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Analysis Result",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        photoUri?.let {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                AsyncImage(
                    model = it,
                    contentDescription = "Analyzed supplement photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = analysis.supplementName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                if (analysis.nutrients.isNotEmpty()) {
                    Text(
                        text = "Detected Nutrients:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    analysis.nutrients.forEach { (nutrient, amount) ->
                        Text(
                            text = "${nutrient.displayName}: ${amount.formatMicronutrientAmount()}${nutrient.unit.displaySuffix}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                analysis.rawLabelText?.takeIf { it.isNotBlank() }?.let { rawText ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Label Text",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = rawText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LinearProgressIndicator(
                    progress = analysis.confidence.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Confidence ${(analysis.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onAddMore(analysis) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add to Supplements & Continue")
            }

            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Edit Details")
            }

            OutlinedButton(
                onClick = {
                    // Convert analysis to supplements and save
                    val supplements = listOf(
                        com.coachie.app.data.model.Supplement(
                            name = analysis.supplementName,
                            micronutrients = analysis.nutrients.toPersistedMicronutrientMap(),
                            labelImagePath = analysis.labelImagePath,
                            labelText = analysis.rawLabelText,
                            isDaily = false // Let user decide
                        )
                    )
                    onSave(supplements)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Save Supplement Now")
            }
        }
    }
}

@Composable
private fun SavingContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = "Saving supplements...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun SuccessContent(
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Supplements Saved!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Button(onClick = onDone) {
            Text("Done")
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Error: $error",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text("Back")
            }

            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f)
            ) {
                Text("Retry")
            }
        }
    }
}
