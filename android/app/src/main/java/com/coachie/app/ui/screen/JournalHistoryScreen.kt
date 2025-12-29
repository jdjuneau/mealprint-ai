package com.coachie.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.model.HealthLog
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import com.coachie.app.viewmodel.JournalHistoryViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: JournalHistoryViewModel = viewModel()
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val accentColor = getSemanticColorPrimary(SemanticColorCategory.WELLNESS, isMale)
    val currentUser = FirebaseAuth.getInstance().currentUser

    // ViewModel states
    val journalEntries by viewModel.journalEntries.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val expandedEntry by viewModel.expandedEntry.collectAsState()

    // Initialize view model
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            viewModel.initialize(uid)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal History", color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBackground)
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Loading your journals...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                journalEntries.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.EditNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No journal entries yet",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your journal entries will appear here after you complete them. This is your private space to reflect and revisit your thoughts.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Your Journal Reflections",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Your private thoughts and reflections, preserved for you to revisit.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Black.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }

                        items(journalEntries) { entry ->
                            JournalEntryCard(
                                entry = entry,
                                isExpanded = expandedEntry == entry.entryId,
                                accentColor = accentColor,
                                onToggleExpand = {
                                    viewModel.toggleExpanded(entry.entryId)
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "✨ Your wins and insights from these journals appear in 'My Wins'",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: HealthLog.JournalEntry,
    isExpanded: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onToggleExpand: () -> Unit
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.95f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header - always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatDate(entry.date),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Primary40
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (entry.isCompleted) Icons.Filled.CheckCircle else Icons.Filled.Schedule,
                            contentDescription = if (entry.isCompleted) "Completed" else "In Progress",
                            tint = if (entry.isCompleted) Color(0xFF10B981) else Color(0xFF6B7280),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${entry.wordCount} words",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6B7280)
                        )
                        if (entry.prompts.isNotEmpty()) {
                            Text(
                                text = "• ${entry.prompts.size} prompts",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Primary40
                    )
                }
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Divider(color = Color.LightGray.copy(alpha = 0.3f))

                    // Prompts section
                    if (entry.prompts.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Today's Prompts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            entry.prompts.forEachIndexed { index, prompt ->
                                Text(
                                    text = "${index + 1}. $prompt",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.DarkGray,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                        Divider(color = Color.LightGray.copy(alpha = 0.3f))
                    }

                    // Conversation section
                    if (entry.conversation.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Your Reflection",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            entry.conversation.forEach { message ->
                                val isUser = message.role == "user"
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isUser)
                                            Primary40.copy(alpha = 0.1f)
                                        else
                                            accentColor.copy(alpha = 0.1f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (!isUser) {
                                            Text(
                                                text = "Coachie",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accentColor,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                        Text(
                                            text = message.content,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.DarkGray,
                                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Footer with completion time
                    entry.completedAt?.let { completedAt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Completed ${formatTime(completedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(dateString: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        dateString // Fallback to original string
    }
}

private fun formatTime(timestamp: Long): String {
    return try {
        val format = SimpleDateFormat("h:mm a", Locale.getDefault())
        format.format(Date(timestamp))
    } catch (e: Exception) {
        "some time ago"
    }
}
