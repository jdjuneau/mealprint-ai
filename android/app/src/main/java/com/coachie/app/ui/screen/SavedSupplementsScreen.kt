package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.MicronutrientType
import com.coachie.app.data.model.Supplement
import com.coachie.app.data.model.formatMicronutrientAmount
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.viewmodel.MicronutrientTrackerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedSupplementsScreen(
    userId: String,
    onNavigateBack: () -> Unit,
    onSupplementSelected: (Supplement) -> Unit = {},
    firebaseRepository: FirebaseRepository = FirebaseRepository.getInstance()
) {
    val viewModel: MicronutrientTrackerViewModel = viewModel(
        key = userId,
        factory = MicronutrientTrackerViewModel.Factory(
            repository = firebaseRepository,
            userId = userId
        )
    )

    val savedSupplements by viewModel.savedSupplements.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Refresh when screen appears
    LaunchedEffect(userId) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved Supplements") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status message
                if (statusMessage != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = statusMessage!!,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Empty state
                if (savedSupplements.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "No saved supplements yet",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Supplements you save will appear here for quick logging",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    // Supplements list
                    items(savedSupplements) { supplement ->
                        SavedSupplementCard(
                            supplement = supplement,
                            onToggleDaily = { isDaily ->
                                viewModel.toggleSupplementDaily(supplement.id, isDaily)
                            },
                            onDelete = {
                                // Delete supplement
                                coroutineScope.launch {
                                    try {
                                        val result = firebaseRepository.deleteSupplement(userId, supplement.id)
                                        if (result.isSuccess) {
                                            viewModel.refresh()
                                        } else {
                                            android.util.Log.e("SavedSupplementsScreen", "Failed to delete supplement", result.exceptionOrNull())
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("SavedSupplementsScreen", "Error deleting supplement", e)
                                    }
                                }
                            },
                            onSelect = {
                                onSupplementSelected(supplement)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SavedSupplementCard(
    supplement: Supplement,
    onToggleDaily: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    CoachieCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onSelect,
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row with name and delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = supplement.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Show micronutrients
                    if (supplement.micronutrientsTyped.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "No nutrients recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete supplement",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Divider()

            // Daily toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = supplement.isDaily,
                        onCheckedChange = onToggleDaily
                    )
                    Text(
                        text = "Take Daily",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (supplement.isDaily) FontWeight.Bold else FontWeight.Normal
                    )
                }
                
                if (supplement.isDaily) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Daily",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Created date
            if (supplement.createdAt > 0) {
                Text(
                    text = "Saved: ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(supplement.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


