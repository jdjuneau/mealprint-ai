package com.coachie.app.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.ui.theme.rememberCoachieGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import com.coachie.app.data.model.HealthLog
import com.coachie.app.data.model.MicronutrientTarget
import com.coachie.app.data.model.MicronutrientUnit
import com.coachie.app.data.model.MicronutrientType
import com.coachie.app.data.model.formatMicronutrientAmount
import com.coachie.app.data.model.Supplement
import com.coachie.app.data.model.isSatisfiedBy
import com.coachie.app.util.SunshineVitaminDCalculator
import com.coachie.app.viewmodel.MicronutrientTrackerViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SunshineLogCard(
    currentVitaminDIu: Double,
    isSaving: Boolean,
    onLogSunshine: (Int, Double, SunshineVitaminDCalculator.ExposureLevel, SunshineVitaminDCalculator.SkinType) -> Unit
) {
    var minutesText by rememberSaveable { mutableStateOf("") }
    var uvIndexText by rememberSaveable { mutableStateOf("") }
    var exposureId by rememberSaveable { mutableStateOf(SunshineVitaminDCalculator.ExposureLevel.FACE_ARMS.id) }
    var skinTypeId by rememberSaveable { mutableStateOf(SunshineVitaminDCalculator.SkinType.MEDIUM.id) }

    val exposure = SunshineVitaminDCalculator.ExposureLevel.fromId(exposureId)
    val skinType = SunshineVitaminDCalculator.SkinType.fromId(skinTypeId)
    val minutes = minutesText.toIntOrNull() ?: 0
    val uvIndex = uvIndexText.toDoubleOrNull() ?: 0.0
    val estimatedVitaminD = SunshineVitaminDCalculator.estimateVitaminD(minutes, uvIndex, exposure, skinType)

    var exposureExpanded by remember { mutableStateOf(false) }
    var skinExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Sunshine → Vitamin D",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Minutes outside") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = uvIndexText,
                    onValueChange = { uvIndexText = it },
                    label = { Text("UV Index") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(
                    expanded = exposureExpanded,
                    onExpandedChange = { exposureExpanded = !exposureExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = exposure.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Exposure") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = exposureExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = exposureExpanded,
                        onDismissRequest = { exposureExpanded = false }
                    ) {
                        SunshineVitaminDCalculator.ExposureLevel.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    exposureId = option.id
                                    exposureExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = skinExpanded,
                    onExpandedChange = { skinExpanded = !skinExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = skinType.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Skin Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = skinExpanded) },
                        modifier = Modifier.menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = skinExpanded,
                        onDismissRequest = { skinExpanded = false }
                    ) {
                        SunshineVitaminDCalculator.SkinType.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    skinTypeId = option.id
                                    skinExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Text(
                text = "Estimated vitamin D: ${estimatedVitaminD.roundToInt()} IU",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Logged today from sunshine: ${currentVitaminDIu.roundToInt()} IU",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = { onLogSunshine(minutes, uvIndex, exposure, skinType) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Log Sunshine")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MicronutrientTrackerScreen(
    userId: String,
    onBack: () -> Unit,
    onNavigateToSupplementPhotoLog: () -> Unit = {},
    onNavigateToSavedSupplements: () -> Unit = {},
    viewModel: MicronutrientTrackerViewModel = viewModel(
        factory = MicronutrientTrackerViewModel.Factory(
            repository = FirebaseRepository.getInstance(),
            userId = userId
        )
    )
) {
    val gender by viewModel.gender.collectAsState()
    val mealTotals by viewModel.mealTotals.collectAsState()
    val supplementTotals by viewModel.supplementTotals.collectAsState()
    val combinedTotals by viewModel.combinedTotals.collectAsState()
    val sunshineTotals by viewModel.sunshineTotals.collectAsState()
    val manualOverrides by viewModel.manualOverrides.collectAsState()
    val supplements by viewModel.supplements.collectAsState()
    val savedSupplements by viewModel.savedSupplements.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val gradientBackground = rememberCoachieGradient(endY = 1600f)

    // Refresh supplements when screen is displayed and when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Also refresh when the screen lifecycle changes (when returning to screen)
    DisposableEffect(Unit) {
        viewModel.refresh()
        onDispose { }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
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
            TopAppBar(
                title = { Text("Vitamins & Minerals") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToSupplementPhotoLog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Filled.Camera,
                    contentDescription = "Add Supplement"
                )
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val completedCount = MicronutrientType.ordered.count { type ->
                val total = combinedTotals[type] ?: 0.0
                val target = type.targetForGender(gender)
                val progress = calculateNutrientProgress(total, target)
                progress >= 1.0f // Goal reached based on progress bar
            }
            val totalCount = MicronutrientType.ordered.size
            
            // Debug: Log to verify all 24 items are present
            android.util.Log.d("MicronutrientTracker", "Total micronutrient types: $totalCount")
            android.util.Log.d("MicronutrientTracker", "Saved supplements count: ${savedSupplements.size}")

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.colors()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Micronutrient Summary",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("$completedCount of $totalCount nutrients completed")
                            Text("${supplements.size} supplements logged")
                        }
                    }
                }


                items(MicronutrientType.ordered) { type ->
                    val total = combinedTotals[type] ?: 0.0
                    val mealAmount = mealTotals[type] ?: 0.0
                    val supplementAmount = supplementTotals[type] ?: 0.0
                    val sunshineAmount = sunshineTotals[type] ?: 0.0
                    val target = type.targetForGender(gender)
                    val progress = calculateNutrientProgress(total, target)
                    // Only show red for significant overages (>150% of max), yellow for moderate overages
                    val isOverLimit = target.max != null && total > target.max
                    val isSignificantlyOverLimit = target.max != null && total > (target.max * 1.5)

                    MicronutrientRow(
                        type = type,
                        total = total,
                        mealAmount = mealAmount,
                        supplementAmount = supplementAmount,
                        sunshineAmount = sunshineAmount,
                        gender = gender,
                        progress = progress,
                        isOverLimit = isSignificantlyOverLimit // Only show red for significant overages
                    )
                }

                item {
                    Button(
                        onClick = { viewModel.saveMicronutrients() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save Vitamins & Minerals")
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.colors()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Supplements",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (savedSupplements.isNotEmpty()) {
                                    TextButton(
                                        onClick = onNavigateToSavedSupplements,
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Text("View All (${savedSupplements.size})")
                                    }
                                }
                            }
                            Text("Logged today: ${supplements.size}")
                            Text("Saved supplements: ${savedSupplements.size}")
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun MicronutrientSummaryCard(completed: Int, total: Int, supplementCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Daily Checklist",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$completed of $total goals reached ($total total vitamins & minerals)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            LinearProgressIndicator(
                progress = if (total == 0) 0f else (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            Text(
                text = "Supplements logged: $supplementCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}



@Composable
fun SupplementsSection(
    savedSupplements: List<Supplement>,
    loggedSupplements: List<HealthLog.SupplementLog>,
    onToggleDaily: (String, Boolean) -> Unit,
    onLogSavedSupplement: (String) -> Unit,
    onAddSupplement: (String, Map<MicronutrientType, Double>, Boolean, Boolean) -> Unit,
    onNavigateToSavedSupplements: () -> Unit = {}
) {
    var supplementName by remember { mutableStateOf("") }
    var micronutrientInputs by remember { mutableStateOf<Map<MicronutrientType, String>>(emptyMap()) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var saveForFuture by remember { mutableStateOf(true) }
    var markDaily by remember { mutableStateOf(false) }

    val availableTypes = MicronutrientType.ordered.filter { it !in micronutrientInputs.keys }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Supplements",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (savedSupplements.isEmpty() && loggedSupplements.isEmpty()) {
            Text(
                text = "No supplements logged yet. Add supplements you take to include their nutrients in your totals.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (savedSupplements.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Saved Supplements (${savedSupplements.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = onNavigateToSavedSupplements,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("View All")
                    }
                }
            }

            savedSupplements.forEach { supplement ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = supplement.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (supplement.micronutrientsTyped.isNotEmpty()) {
                            Text(
                                text = supplement.micronutrientsTyped.entries
                                    .sortedBy { it.key.displayName }
                                    .joinToString(", ") { entry ->
                                        val amount = entry.value.formatMicronutrientAmount()
                                        "${entry.key.displayName}: ${amount}${entry.key.unit.displaySuffix}"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "No nutrients recorded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Checkbox(
                                    checked = supplement.isDaily,
                                    onCheckedChange = { isChecked ->
                                        if (supplement.id.isNotBlank()) {
                                            onToggleDaily(supplement.id, isChecked)
                                        }
                                    }
                                )
                                Text("Daily")
                            }

                            Button(
                                onClick = { onLogSavedSupplement(supplement.id) },
                                enabled = supplement.id.isNotBlank()
                            ) {
                                Text("Log Today")
                            }
                        }
                    }
                }
            }

            if (loggedSupplements.isNotEmpty()) {
                Text(
                    text = "Today's Logged Supplements",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            loggedSupplements.forEach { supplement ->
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = supplement.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (supplement.micronutrientsTyped.isNotEmpty()) {
                            Text(
                                text = supplement.micronutrientsTyped.entries
                                    .sortedBy { it.key.displayName }
                                    .joinToString(", ") { entry ->
                                        val amount = entry.value.formatMicronutrientAmount()
                                        "${entry.key.displayName}: ${amount}${entry.key.unit.displaySuffix}"
                                    },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "No nutrients recorded",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Divider()

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Log a Supplement",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = supplementName,
                onValueChange = {
                    supplementName = it
                    errorMessage = null
                },
                label = { Text("Supplement name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Box {
                OutlinedButton(
                    onClick = { isMenuExpanded = true },
                    enabled = availableTypes.isNotEmpty()
                ) {
                    Text("Add nutrient")
                }

                DropdownMenu(
                    expanded = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false }
                ) {
                    availableTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                micronutrientInputs = micronutrientInputs + (type to "")
                                isMenuExpanded = false
                            }
                        )
                    }
                }
            }

            micronutrientInputs.entries
                .sortedBy { it.key.displayName }
                .forEach { (type, value) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = {
                            micronutrientInputs = micronutrientInputs + (type to it)
                            errorMessage = null
                        },
                        label = { Text(type.displayName) },
                        suffix = { Text(type.unit.displaySuffix) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { micronutrientInputs = micronutrientInputs - type }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove nutrient")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

            if (micronutrientInputs.isEmpty()) {
                Text(
                    text = "Add each vitamin or mineral contained in this supplement (e.g., Vitamin C, Iron).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = saveForFuture,
                        onCheckedChange = {
                            saveForFuture = it
                            if (!it) {
                                markDaily = false
                            }
                        }
                    )
                    Text("Save for future")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = markDaily,
                        onCheckedChange = {
                            markDaily = it
                            if (it) {
                                saveForFuture = true
                            }
                        }
                    )
                    Text("Take daily")
                }
            }

            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = {
                    val parsed = micronutrientInputs.mapNotNull { (type, text) ->
                        val amount = text.trim().toDoubleOrNull()
                        if (amount != null && amount > 0) type to amount else null
                    }.toMap()

                    when {
                        supplementName.isBlank() -> errorMessage = "Enter a supplement name"
                        parsed.isEmpty() -> errorMessage = "Add at least one nutrient amount"
                        else -> {
                            onAddSupplement(supplementName.trim(), parsed, saveForFuture, markDaily)
                            supplementName = ""
                            micronutrientInputs = emptyMap()
                            errorMessage = null
                            markDaily = false
                            saveForFuture = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Log Supplement")
            }
        }
    }
    }
}

fun calculateNutrientProgress(total: Double, target: MicronutrientTarget): Float {
    val denominator = when {
        target.min == 0.0 && target.max != null -> target.max
        target.max != null && target.min > 0.0 -> target.min
        target.min > 0.0 -> target.min
        target.max != null -> target.max
        else -> 1.0
    }
    if (denominator <= 0.0) return 0f
    return (total / denominator).toFloat().coerceIn(0f, 1f)
}

@Composable
fun MicronutrientRow(
    type: MicronutrientType,
    total: Double,
    mealAmount: Double,
    supplementAmount: Double,
    sunshineAmount: Double,
    gender: String?,
    progress: Float,
    isOverLimit: Boolean
) {
    // Color palette for status (subtle alphas to match app style)
    val green = androidx.compose.ui.graphics.Color(0xFF10B981)
    val yellow = androidx.compose.ui.graphics.Color(0xFFF59E0B)
    val red = androidx.compose.ui.graphics.Color(0xFFEF4444)

    // Determine subtle container color based on progress towards goal
    val containerColor = when {
        progress >= 1f -> green.copy(alpha = 0.15f)
        progress >= 0.7f -> yellow.copy(alpha = 0.15f)
        else -> red.copy(alpha = 0.12f)
    }

    // Use Surface for clean single-color background without any borders or elevation
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                val target = type.targetForGender(gender)
                val targetLabel = "${target.format()} ${type.unit.displaySuffix}"
                val genderLabel = when (gender?.lowercase()) {
                    "male", "man", "m" -> "Goal for Men"
                    "female", "woman", "f" -> "Goal for Women"
                    else -> "Goal"
                }
                Text(
                    text = "$genderLabel: $targetLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = when {
                    progress >= 1f -> green
                    progress >= 0.7f -> yellow
                    else -> red
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )

            val totalLabel = total.formatMicronutrientAmount()
            val mealLabel = mealAmount.formatMicronutrientAmount()
            val supplementLabel = supplementAmount.formatMicronutrientAmount()
            val sunshineLabel = sunshineAmount.formatMicronutrientAmount()

            Text(
                text = "Total: $totalLabel${type.unit.displaySuffix}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = buildString {
                    append("Meals: $mealLabel${type.unit.displaySuffix}")
                    append(" • Supplements: $supplementLabel${type.unit.displaySuffix}")
                    if (sunshineAmount > 0.0 || type == MicronutrientType.VITAMIN_D) {
                        append(" • Sunshine: $sunshineLabel${MicronutrientUnit.IU.displaySuffix}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

        }
    }
}

