package com.coachie.app.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coachie.app.data.model.HealthLog
import com.coachie.app.ui.theme.Primary40
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import com.coachie.app.viewmodel.MyWinsViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyWinsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MyWinsViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val accentColor = getSemanticColorPrimary(SemanticColorCategory.WELLNESS, isMale)

    // Get current user
    val currentUser = FirebaseAuth.getInstance().currentUser

    // ViewModel states
    val wins by viewModel.wins.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val showCalendar by viewModel.showCalendar.collectAsState()
    val showConfetti by viewModel.showConfetti.collectAsState()
    val confettiMessage by viewModel.confettiMessage.collectAsState()

    // Initialize view model
    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            viewModel.initialize(uid)
        }
    }

    // Auto-scroll to top when new data loads
    LaunchedEffect(wins.size) {
        if (wins.isNotEmpty()) {
            delay(100)
            listState.animateScrollToItem(0)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("My Wins") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Calendar toggle
                        IconButton(onClick = { viewModel.toggleCalendar() }) {
                            Icon(
                                imageVector = if (showCalendar) Icons.Filled.CalendarViewMonth else Icons.Filled.CalendarToday,
                                contentDescription = if (showCalendar) "Hide calendar" else "Show calendar"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent,
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(accentColor, Color.White)
                )
            )
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search your wins...") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                        }
                    ),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary40,
                        unfocusedBorderColor = Color.LightGray
                    )
                )

                // Calendar picker (when enabled)
                AnimatedVisibility(
                    visible = showCalendar,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    CalendarPicker(
                        selectedDate = selectedDate,
                        onDateSelected = { viewModel.selectDate(it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Wins timeline
                if (isLoading && wins.isEmpty()) {
                    LoadingState()
                } else if (wins.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(wins) { winEntry ->
                            WinCard(winEntry = winEntry, accentColor = accentColor)
                        }

                        // Load more indicator
                        if (isLoading && wins.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Primary40)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Confetti overlay for bad days
        if (showConfetti) {
            ConfettiOverlay(
                message = confettiMessage,
                onDismiss = { viewModel.dismissConfetti() }
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = Primary40,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading your wins...",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your wins will appear here after journaling",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Complete your evening journal to see your daily wins and gratitudes extracted automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WinCard(winEntry: HealthLog.WinEntry, accentColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Date and mood indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(winEntry.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )

                // Mood indicator
                winEntry.moodScore?.let { score ->
                    MoodIndicator(score = score, mood = winEntry.mood)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Win section
            winEntry.win?.let { win ->
                WinItem(
                    icon = Icons.Filled.EmojiEvents,
                    title = "Win",
                    content = win,
                    color = Primary40
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Gratitude section
            winEntry.gratitude?.let { gratitude ->
                WinItem(
                    icon = Icons.Filled.Favorite,
                    title = "Gratitude",
                    content = gratitude,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Tags
            if (winEntry.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    winEntry.tags.forEach { tag ->
                        TagChip(tag = tag)
                    }
                }
            }
        }
    }
}

@Composable
private fun WinItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = color,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.DarkGray,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.2f
            )
        }
    }
}

@Composable
private fun TagChip(tag: String) {
    Surface(
        color = Color.LightGray.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = "#$tag",
            style = MaterialTheme.typography.labelSmall,
            color = Color.DarkGray,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MoodIndicator(score: Int, mood: String?) {
    val (color, emoji) = when (score) {
        5 -> Color(0xFF4CAF50) to "😊" // Excellent
        4 -> Color(0xFF8BC34A) to "🙂" // Good
        3 -> Color(0xFFFFC107) to "😐" // Neutral
        2 -> Color(0xFFFF9800) to "😕" // Difficult
        1 -> Color(0xFFF44336) to "😢" // Challenging
        else -> Color.Gray to "🤔"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = emoji, fontSize = 16.sp)
        Text(
            text = mood ?: "Unknown",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CalendarPicker(
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    // Simple date picker - in a real app you'd use a proper calendar component
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { onDateSelected(null) },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (selectedDate == null) Primary40 else Color.Gray
            )
        ) {
            Text("All Dates")
        }

        OutlinedButton(
            onClick = { onDateSelected(LocalDate.now()) },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (selectedDate == LocalDate.now()) Primary40 else Color.Gray
            )
        ) {
            Text("Today")
        }

        OutlinedButton(
            onClick = { onDateSelected(LocalDate.now().minusDays(7)) },
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (selectedDate == LocalDate.now().minusDays(7)) Primary40 else Color.Gray
            )
        ) {
            Text("Last Week")
        }
    }
}

@Composable
private fun ConfettiOverlay(message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Confetti animation would go here in a real implementation
            Text(
                text = "🎉",
                fontSize = 64.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tap anywhere to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatDate(dateString: String): String {
    return try {
        val date = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE)
        date.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
    } catch (e: Exception) {
        dateString
    }
}
