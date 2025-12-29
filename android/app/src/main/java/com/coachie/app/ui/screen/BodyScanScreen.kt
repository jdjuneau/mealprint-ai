package com.coachie.app.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.local.PreferencesManager
import com.coachie.app.data.model.BodyFatComparison
import com.coachie.app.viewmodel.BodyScanUiState
import com.coachie.app.viewmodel.BodyScanViewModel
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScanScreen(
    userId: String? = null,
    onBack: () -> Unit = {},
    onScanComplete: (String) -> Unit = {}, // Callback with photo URL
    viewModel: BodyScanViewModel = viewModel(
        factory = BodyScanViewModel.Factory(
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

    // Request camera permission when screen opens
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Handle successful upload
    LaunchedEffect(uiState) {
        if (uiState is BodyScanUiState.Success) {
            // Navigate back or show success
            onScanComplete((uiState as BodyScanUiState.Success).photoUrl)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Body Scan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                !hasCameraPermission -> {
                    PermissionRequiredContent(
                        onRequestPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                }

                uiState is BodyScanUiState.Idle -> {
                    CameraReadyContent(
                        onTakePhoto = {
                            viewModel.createPhotoFile()?.let { uri ->
                                // TODO: Launch camera with URI
                                // For now, we'll simulate taking a photo
                                viewModel.onPhotoTaken()
                            }
                        }
                    )
                }

                uiState is BodyScanUiState.Analyzing -> {
                    AnalyzingContent()
                }

                uiState is BodyScanUiState.PhotoTaken -> {
                    PhotoCapturedContent(
                        photoFile = (uiState as BodyScanUiState.PhotoTaken).photoFile,
                        onRetake = { viewModel.resetState() },
                        onConfirm = { viewModel.confirmUpload() }
                    )
                }

                uiState is BodyScanUiState.Uploading -> {
                    UploadingContent()
                }

                uiState is BodyScanUiState.Success -> {
                    val successState = uiState as BodyScanUiState.Success
                    SuccessContent(
                        comparison = successState.comparison,
                        onBackToProgress = onBack,
                        onTakeAnother = { viewModel.resetState() }
                    )
                }

                uiState is BodyScanUiState.Error -> {
                    ErrorContent(
                        error = (uiState as BodyScanUiState.Error).message,
                        onRetry = { viewModel.resetState() },
                        onBack = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRequiredContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📷",
            fontSize = 64.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Camera Access Required",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "We need camera access to take your body scan photos for progress tracking.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text("Grant Camera Permission")
        }
    }
}

@Composable
private fun CameraReadyContent(onTakePhoto: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📸",
            fontSize = 64.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Ready for Body Scan",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Position yourself in front of the camera with good lighting. We'll capture your body scan for progress tracking.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tips for Best Results:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text("• Stand against a plain background")
                Text("• Wear form-fitting clothing")
                Text("• Ensure good, even lighting")
                Text("• Stand straight and face the camera")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onTakePhoto,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Camera,
                contentDescription = "Take Photo",
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = "Tap to Capture",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun PhotoCapturedContent(
    photoFile: java.io.File,
    onRetake: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Photo preview
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = photoFile,
                contentDescription = "Captured body scan",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                modifier = Modifier.weight(1f)
            ) {
                Text("Retake")
            }

            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Text("Upload Scan")
            }
        }
    }
}

@Composable
private fun AnalyzingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Analyzing your body measurements...",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Using AI to detect your shoulders, hips, and waist",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun UploadingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Uploading your body scan...",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This may take a few moments",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SuccessContent(
    comparison: BodyFatComparison?,
    onBackToProgress: () -> Unit,
    onTakeAnother: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "Success",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Body Scan Complete!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Show body fat comparison if available
        comparison?.let { comp ->
            BodyFatComparisonCard(comparison = comp)
            Spacer(modifier = Modifier.height(24.dp))
        } ?: run {
            Text(
                text = "Your body scan has been saved and analyzed. Take another scan in the future to see your progress!",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onTakeAnother,
                modifier = Modifier.weight(1f)
            ) {
                Text("Take Another")
            }

            Button(
                onClick = onBackToProgress,
                modifier = Modifier.weight(1f)
            ) {
                Text("View Progress")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyFatComparisonCard(comparison: BodyFatComparison) {
    var sliderPosition by remember { mutableStateOf(0.5f) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = comparison.changeMessage,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Before/After comparison slider
            Text(
                text = "Compare Scans",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Slider for before/after
            Slider(
                value = sliderPosition,
                onValueChange = { sliderPosition = it },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Previous",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sliderPosition < 0.5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Current",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sliderPosition > 0.5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats comparison
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatComparison(
                    label = "Body Fat",
                    previousValue = "${String.format("%.1f", comparison.previousFat)}%",
                    currentValue = "${String.format("%.1f", comparison.currentFat)}%",
                    isPositive = comparison.isFatLoss
                )

                StatComparison(
                    label = "Change",
                    previousValue = "",
                    currentValue = "${String.format("%.1f", comparison.changePercent)}%",
                    isPositive = comparison.isFatLoss
                )
            }
        }
    }
}

@Composable
private fun StatComparison(
    label: String,
    previousValue: String,
    currentValue: String,
    isPositive: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (previousValue.isNotEmpty()) {
            Text(
                text = previousValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = currentValue,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                isPositive -> Color(0xFF4CAF50) // Green for fat loss
                currentValue.contains("-") -> Color(0xFFF44336) // Red for fat gain
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚠️",
            fontSize = 64.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "Upload Failed",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
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
