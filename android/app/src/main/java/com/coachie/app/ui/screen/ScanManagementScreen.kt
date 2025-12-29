package com.coachie.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.Scan
import com.coachie.app.viewmodel.ScanManagementUiState
import com.coachie.app.viewmodel.ScanManagementViewModel
import com.coachie.app.ui.components.CoachieCard as Card
import com.coachie.app.ui.components.CoachieCardDefaults as CardDefaults

@Composable
fun ScanManagementScreen(
    userId: String,
    onBack: () -> Unit,
    viewModel: ScanManagementViewModel = viewModel(
        factory = ScanManagementViewModel.Factory(FirebaseRepository.getInstance(), userId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Text(
            text = "Body Scans",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            textAlign = TextAlign.Center
        )

        // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back")
            }

            Button(
                onClick = { /* TODO: Implement camera/photo picker */ },
                modifier = Modifier.weight(1f)
            ) {
                Text("New Scan")
            }
        }

        // Content
        when (uiState) {
            is ScanManagementUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ScanManagementUiState.Success -> {
                val state = uiState as ScanManagementUiState.Success
                ScanList(scans = state.scans)
            }

            is ScanManagementUiState.Error -> {
                val state = uiState as ScanManagementUiState.Error
                ErrorContent(
                    error = state.message,
                    onRetry = { viewModel.loadScans() }
                )
            }
        }
    }
}

@Composable
private fun ScanList(scans: List<Scan>) {
    if (scans.isEmpty()) {
        EmptyState()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(scans) { scan ->
                ScanItem(scan = scan)
            }
        }
    }
}

@Composable
private fun ScanItem(scan: Scan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with date and type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = scan.formattedDateTime,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = scan.scanType.replace("_", " ").replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status indicator
                when {
                    scan.hasAnalysis -> {
                        Text(
                            text = "✅ Analyzed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    scan.processing -> {
                        Text(
                            text = "⏳ Processing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    scan.processingError != null -> {
                        Text(
                            text = "❌ Error",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {
                        Text(
                            text = "📷 Uploaded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Scan data
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                scan.weight?.let {
                    InfoChip(label = "Weight", value = "${it}kg")
                }
                scan.height?.let {
                    InfoChip(label = "Height", value = "${it}cm")
                }
                scan.bodyFatEstimate?.let {
                    InfoChip(label = "Body Fat", value = "%.1f".format(it) + "%")
                }
                scan.bmi?.let {
                    InfoChip(label = "BMI", value = "%.1f".format(it))
                }
            }

            // Body fat category if available
            scan.bodyFatCategory?.let { category ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Category: $category",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (category) {
                        "Essential Fat", "Athletes", "Fitness" -> MaterialTheme.colorScheme.primary
                        "Average" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }

            // Processing error if any
            scan.processingError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Processing Error: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Notes if any
            scan.notes?.let { notes ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Action buttons
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { /* TODO: View full-size image */ },
                    modifier = Modifier.weight(1f),
                    enabled = scan.hasPhoto
                ) {
                    Text("View Photo")
                }

                OutlinedButton(
                    onClick = { /* TODO: Share scan */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📷",
            style = MaterialTheme.typography.displayLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No Body Scans Yet",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Take your first body scan to track your progress over time",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = { /* TODO: Navigate to camera */ }) {
            Text("Take First Scan")
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "⚠️",
            style = MaterialTheme.typography.displayLarge
        )

        Text(
            text = "Unable to load scans",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Button(onClick = onRetry) {
            Text("Try Again")
        }
    }
}
