package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.Circle
import com.coachie.app.data.model.Forum
import com.coachie.app.data.model.ForumPost
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCircleJoin: () -> Unit,
    onNavigateToCircleCreate: () -> Unit,
    onNavigateToCircleDetail: (String) -> Unit,
    onNavigateToForumDetail: (String) -> Unit = {},
    onNavigateToUserSearch: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToMessages: () -> Unit = {},
    onNavigateToSharedRecipes: () -> Unit = {},
    userId: String
) {
    val repository = FirebaseRepository.getInstance()
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    // Use semantic color for Community (purple theme)
    val accentColor = getSemanticColorPrimary(SemanticColorCategory.COMMUNITY, isMale)
    var circles by remember { mutableStateOf<List<Circle>>(emptyList()) }
    var forums by remember { mutableStateOf<List<Forum>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Circles, 1 = Forums, 2 = News
    
    // Track which circles have new activity (posts in last 24 hours)
    var circlesWithActivity by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    // Track pending friend requests count
    var pendingFriendRequestsCount by remember { mutableStateOf(0) }
    
    // Track unread messages count
    var unreadMessagesCount by remember { mutableStateOf(0) }
    
    // Track new shared recipes count (recipes shared in last 24 hours)
    var newSharedRecipesCount by remember { mutableStateOf(0) }

    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val scope = rememberCoroutineScope()
    
    // Check for pending friend requests, unread messages, and new shared recipes
    LaunchedEffect(userId) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // Check friend requests
            val requestsResult = repository.getPendingFriendRequests(userId)
            requestsResult.onSuccess { requests ->
                withContext(Dispatchers.Main) {
                    pendingFriendRequestsCount = requests.count { it.toUserId == userId && it.status == "pending" }
                }
            }
            
            // Check unread messages
            val conversationsResult = repository.getConversations(userId)
            conversationsResult.onSuccess { conversations ->
                val totalUnread = conversations.sumOf { it.getUnreadCount(userId) }
                withContext(Dispatchers.Main) {
                    unreadMessagesCount = totalUnread
                    android.util.Log.d("CommunityScreen", "Unread messages count: $totalUnread")
                }
            }
            
            // Check new shared recipes (recipes shared WITH user in last 24 hours, not owned by user)
            val recipesResult = repository.getSharedRecipes(userId)
            recipesResult.onSuccess { recipes ->
                val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                // Only count recipes that were shared WITH the user (not owned by user) and created in last 24 hours
                val newRecipes = recipes.count { recipe ->
                    recipe.userId != userId && // Not owned by user (shared with user)
                    recipe.createdAt >= oneDayAgo && // Created in last 24 hours (createdAt is Long timestamp)
                    recipe.sharedWith.contains(userId) // Actually shared with this user
                }
                withContext(Dispatchers.Main) {
                    newSharedRecipesCount = newRecipes
                    android.util.Log.d("CommunityScreen", "New shared recipes count: $newRecipes")
                }
            }
        }
        
        // Refresh activity counts periodically
        while (true) {
            kotlinx.coroutines.delay(10000) // Check every 10 seconds
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                // Refresh unread messages
                repository.getConversations(userId).onSuccess { conversations ->
                    val totalUnread = conversations.sumOf { it.getUnreadCount(userId) }
                    withContext(Dispatchers.Main) {
                        unreadMessagesCount = totalUnread
                    }
                }
                
                // Refresh friend requests
                repository.getPendingFriendRequests(userId).onSuccess { requests ->
                    withContext(Dispatchers.Main) {
                        pendingFriendRequestsCount = requests.count { it.toUserId == userId && it.status == "pending" }
                    }
                }
                
                // Refresh new shared recipes
                repository.getSharedRecipes(userId).onSuccess { recipes ->
                    val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                    // Only count recipes that were shared WITH the user (not owned by user) and created in last 24 hours
                    val newRecipes = recipes.count { recipe ->
                        recipe.userId != userId && // Not owned by user (shared with user)
                        recipe.createdAt >= oneDayAgo && // Created in last 24 hours (createdAt is Long timestamp)
                        recipe.sharedWith.contains(userId) // Actually shared with this user
                    }
                    withContext(Dispatchers.Main) {
                        newSharedRecipesCount = newRecipes
                    }
                }
            }
        }
    }

    suspend fun createDefaultForums(): Boolean {
        android.util.Log.d("CommunityScreen", "Creating default forums - checking existing first")

        // First check what forums already exist (case-insensitive check)
        val existingForumsResult = repository.getForums()
        val existingTitles = existingForumsResult.getOrNull()?.map { it.title.lowercase().trim() }?.toSet() ?: emptySet()
        android.util.Log.d("CommunityScreen", "Existing forums (${existingTitles.size}): $existingTitles")

        // Define default forums in the desired display order
        val defaultForums = listOf(
            Forum(
                title = "Coachie News",
                description = "App updates, new feature announcements, roadmaps, and development updates from the Coachie team",
                category = "news",
                createdBy = "system",
                createdByName = "Coachie Team",
                isActive = true
            ),
            Forum(
                title = "Feature Requests",
                description = "Suggest new features and improvements for Coachie",
                category = "feature_request",
                createdBy = "system",
                createdByName = "Coachie Team",
                isActive = true
            ),
            Forum(
                title = "Bugs & Feedback",
                description = "Report bugs and provide feedback about the app",
                category = "bugs_feedback",
                createdBy = "system",
                createdByName = "Coachie Team",
                isActive = true
            ),
            Forum(
                title = "Recipe Sharing",
                description = "Share your favorite recipes, meal ideas, and nutrition tips with the community",
                category = "recipes",
                createdBy = "system",
                createdByName = "Coachie Team",
                isActive = true
            ),
            Forum(
                title = "General Discussion",
                description = "Talk about anything fitness or wellness related",
                category = "general",
                createdBy = "system",
                createdByName = "Coachie Team",
                isActive = true
            )
        )

        var allSuccessful = true
        var createdCount = 0

        for (forum in defaultForums) {
            val normalizedTitle = forum.title.lowercase().trim()
            if (normalizedTitle in existingTitles) {
                android.util.Log.d("CommunityScreen", "Forum ${forum.title} already exists (normalized: $normalizedTitle), skipping")
                continue
            }

            android.util.Log.d("CommunityScreen", "Creating forum: ${forum.title}")
            val result = repository.createForum(forum)
            result.onSuccess { forumId ->
                android.util.Log.d("CommunityScreen", "Created forum ${forum.title} with ID: $forumId")
                createdCount++
            }.onFailure { error ->
                android.util.Log.e("CommunityScreen", "Failed to create forum ${forum.title}", error)
                allSuccessful = false
            }
        }

        android.util.Log.d("CommunityScreen", "Created $createdCount forums, success: $allSuccessful")

        // Wait a moment for Firestore to index the new documents
        if (createdCount > 0) {
            kotlinx.coroutines.delay(1000) // 1 second delay
        }

        // Always reload forums after attempting creation
        android.util.Log.d("CommunityScreen", "Reloading forums after creation attempt")
        val forumsResult = repository.getForums()
        forumsResult.onSuccess {
            android.util.Log.d("CommunityScreen", "Reloaded ${it.size} forums: ${it.map { f -> f.title }}")
            // Deduplicate, filter, and order forums
            val deduplicated = it.distinctBy { it.title.lowercase().trim() }
            val defaultForumTitles = setOf("Coachie News", "Feature Requests", "Bugs & Feedback", "Recipe Sharing", "General Discussion")
            val filtered = deduplicated.filter { defaultForumTitles.contains(it.title) }
            val ordered = filtered.sortedBy { forum ->
                when (forum.title) {
                    "Coachie News" -> 0
                    "Feature Requests" -> 1
                    "Bugs & Feedback" -> 2
                    "Recipe Sharing" -> 3
                    "General Discussion" -> 4
                    else -> 999
                }
            }
            android.util.Log.d("CommunityScreen", "After filtering and ordering: ${ordered.size} forums: ${ordered.map { it.title }}")
            forums = ordered
            
            // If still no forums found, try querying without filter to debug
            if (it.isEmpty() && createdCount > 0) {
                android.util.Log.w("CommunityScreen", "No forums found after creation, trying debug query...")
                try {
                    // Use repository's getForums which now has fallback logic
                    val debugResult = repository.getForums()
                    debugResult.onSuccess { debugForums ->
                        android.util.Log.d("CommunityScreen", "Debug query found ${debugForums.size} forums")
                        debugForums.forEach { forum ->
                            android.util.Log.d("CommunityScreen", "Forum ${forum.id}: title=${forum.title}, isActive=${forum.isActive}")
                        }
                    }.onFailure { error ->
                        android.util.Log.e("CommunityScreen", "Debug query failed", error)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CommunityScreen", "Debug query failed", e)
                }
            }
        }.onFailure { error ->
            android.util.Log.e("CommunityScreen", "Failed to reload forums", error)
            allSuccessful = false
        }

        return allSuccessful
    }

    // Load circles and check for new activity
    LaunchedEffect(userId) {
        isLoading = true
        errorMessage = null
        android.util.Log.d("CommunityScreen", "Loading circles for user: $userId")

        // Initial load - always succeed (returns empty list for new users)
        try {
            val circleResult = repository.getUserCircles(userId)
            circleResult.onSuccess { loadedCircles ->
                withContext(Dispatchers.Main) {
                    circles = loadedCircles
                    isLoading = false
                    android.util.Log.d("CommunityScreen", "Successfully loaded ${loadedCircles.size} circles")
                }
                
                // Check for new activity in each circle (posts in last 24 hours)
                val activitySet = mutableSetOf<String>()
                val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                
                loadedCircles.forEach { circle ->
                    if (circle.id.isNotBlank()) {
                        try {
                            val postsResult = repository.getCirclePosts(circle.id)
                            postsResult.onSuccess { posts ->
                                // Check if any post was created in the last 24 hours
                                val hasNewActivity = posts.any { post ->
                                    post.createdAt?.time?.let { it >= oneDayAgo } ?: false
                                }
                                if (hasNewActivity) {
                                    activitySet.add(circle.id)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("CommunityScreen", "Error checking activity for circle ${circle.id}", e)
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    circlesWithActivity = activitySet
                    android.util.Log.d("CommunityScreen", "Circles with new activity: ${activitySet.size}")
                }
            }.onFailure {
                // This should rarely happen now since getUserCircles returns empty list for new users
                android.util.Log.w("CommunityScreen", "getUserCircles returned failure (unexpected): ${it.message}")
                withContext(Dispatchers.Main) {
                    circles = emptyList()
                    isLoading = false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CommunityScreen", "Exception loading circles", e)
            withContext(Dispatchers.Main) {
                circles = emptyList()
                isLoading = false
            }
        }
        
        // Auto-refresh every 30 seconds while on this screen
        while (true) {
            kotlinx.coroutines.delay(30000) // 30 seconds
            repository.getUserCircles(userId).onSuccess { updatedCircles ->
                withContext(Dispatchers.Main) {
                    circles = updatedCircles
                    android.util.Log.d("CommunityScreen", "Refreshed circles: ${updatedCircles.size}")
                }
                
                // Re-check activity for refreshed circles
                val activitySet = mutableSetOf<String>()
                val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                
                updatedCircles.forEach { circle ->
                    if (circle.id.isNotBlank()) {
                        try {
                            val postsResult = repository.getCirclePosts(circle.id)
                            postsResult.onSuccess { posts ->
                                val hasNewActivity = posts.any { post ->
                                    post.createdAt?.time?.let { it >= oneDayAgo } ?: false
                                }
                                if (hasNewActivity) {
                                    activitySet.add(circle.id)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("CommunityScreen", "Error checking activity for circle ${circle.id}", e)
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    circlesWithActivity = activitySet
                }
            }.onFailure {
                // Silently fail on refresh - don't update error message
                android.util.Log.w("CommunityScreen", "Failed to refresh circles", it)
            }
        }
    }
    
    // Force refresh circles when screen becomes visible (e.g., after accepting invite)
    // This will trigger a reload when the user navigates back to this screen
    LaunchedEffect(Unit) {
        // Small delay to ensure any pending operations complete, then refresh
        kotlinx.coroutines.delay(1000)
        scope.launch {
            repository.getUserCircles(userId).onSuccess { updatedCircles ->
                circles = updatedCircles
                android.util.Log.d("CommunityScreen", "Force refreshed circles: ${updatedCircles.size}")
            }.onFailure {
                android.util.Log.w("CommunityScreen", "Failed to force refresh circles", it)
            }
        }
    }

    // ALWAYS create default forums first, then load
    LaunchedEffect(Unit) {
        android.util.Log.d("CommunityScreen", "FORCING creation of default forums")
        val created = createDefaultForums()
        android.util.Log.d("CommunityScreen", "Default forums creation result: $created")
        
        // Wait a bit for Firestore to index
        kotlinx.coroutines.delay(2000)
        
        // Now load forums
        val forumsResult = repository.getForums()
        forumsResult.onSuccess {
            android.util.Log.d("CommunityScreen", "Loaded ${it.size} forums before deduplication")
            
            // Deduplicate forums by title (case-insensitive) - keep the first one
            val deduplicatedForums = it.distinctBy { it.title.lowercase().trim() }
            android.util.Log.d("CommunityScreen", "After deduplication: ${deduplicatedForums.size} forums")
            
            if (it.size != deduplicatedForums.size) {
                android.util.Log.w("CommunityScreen", "Removed ${it.size - deduplicatedForums.size} duplicate forums")
            }
            
            // Filter to only show the default forums and sort them in the desired order
            val defaultForumTitles = setOf("Coachie News", "Feature Requests", "Bugs & Feedback", "Recipe Sharing", "General Discussion")
            val filteredForums = deduplicatedForums.filter { forum ->
                defaultForumTitles.contains(forum.title)
            }
            
            // Sort in the desired order: Coachie News, Feature Requests, Bugs & Feedback, Recipe Sharing, General Discussion
            val orderedForums = filteredForums.sortedBy { forum ->
                when (forum.title) {
                    "Coachie News" -> 0
                    "Feature Requests" -> 1
                    "Bugs & Feedback" -> 2
                    "Recipe Sharing" -> 3
                    "General Discussion" -> 4
                    else -> 999 // Any other forums go to the end
                }
            }
            
            android.util.Log.d("CommunityScreen", "Final forums list: ${orderedForums.map { it.title }}")
            forums = orderedForums
        }.onFailure { error ->
            android.util.Log.e("CommunityScreen", "Failed to load forums", error)
            errorMessage = error.message
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
                CoachieCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CoachieCardDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.95f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "Back",
                                tint = accentColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Community",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Messages icon with unread badge
                        Box {
                            IconButton(onClick = onNavigateToMessages) {
                                Icon(
                                    Icons.Filled.Message,
                                    "Messages",
                                    tint = accentColor
                                )
                            }
                            if (unreadMessagesCount > 0) {
                                Badge(
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = if (unreadMessagesCount > 99) "99+" else unreadMessagesCount.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        // Friends icon with friend request badge
                        Box {
                            IconButton(onClick = onNavigateToFriends) {
                                Icon(
                                    Icons.Filled.People,
                                    "Friends",
                                    tint = accentColor
                                )
                            }
                            if (pendingFriendRequestsCount > 0) {
                                Badge(
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = if (pendingFriendRequestsCount > 99) "99+" else pendingFriendRequestsCount.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        // Shared Recipes icon with new recipes badge
                        Box {
                            IconButton(onClick = onNavigateToSharedRecipes) {
                                Icon(
                                    Icons.Filled.Restaurant,
                                    "Shared Recipes",
                                    tint = accentColor
                                )
                            }
                            if (newSharedRecipesCount > 0) {
                                Badge(
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = if (newSharedRecipesCount > 99) "99+" else newSharedRecipesCount.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onNavigateToCircleJoin) {
                            Icon(
                                Icons.Filled.Add,
                                "Find Circle",
                                tint = accentColor
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                // Only show FAB on Circles tab (tab 0)
                if (selectedTab == 0) {
                    FloatingActionButton(
                        onClick = onNavigateToCircleCreate,
                        containerColor = accentColor,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Filled.Add, "Create Circle")
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Circles", color = MaterialTheme.colorScheme.onSurface) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Forums", color = MaterialTheme.colorScheme.onSurface) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("News", color = MaterialTheme.colorScheme.onSurface) }
                    )
                }

                // Content
                when (selectedTab) {
                    0 -> {
                        // Circles content - simplified for now
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(
                                top = 16.dp,
                                bottom = 16.dp
                            )
                        ) {
                            if (isLoading) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            } else if (errorMessage != null) {
                                item {
                                    Text("Error: $errorMessage", color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (circles.isEmpty()) "My Circles" else "My Circles (${circles.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    // "My Circles" is already displayed, just scroll to top or refresh
                                                    // This ensures the section is visible when navigated from dashboard
                                                }
                                        )
                                        if (circles.isNotEmpty()) {
                                            // Show "View All" button if there are circles
                                            TextButton(
                                                onClick = {
                                                    // Navigate to first circle or show all circles
                                                    if (circles.isNotEmpty() && circles[0].id.isNotBlank()) {
                                                        onNavigateToCircleDetail(circles[0].id)
                                                    }
                                                },
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = accentColor
                                                )
                                            ) {
                                                Text("View")
                                            }
                                        }
                                        Button(
                                            onClick = onNavigateToCircleCreate,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = accentColor
                                            )
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Create")
                                        }
                                    }
                                }
                                
                                if (circles.isEmpty()) {
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 32.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Text(
                                                "No circles yet. Join or create one!",
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Button(
                                                onClick = onNavigateToCircleJoin,
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = accentColor
                                                )
                                            ) {
                                                Icon(Icons.Filled.Group, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Find a Circle")
                                            }
                                        }
                                    }
                                }
                                items(circles) { circle ->
                                    if (circle.id.isBlank()) {
                                        // Skip circles with blank IDs
                                        android.util.Log.w("CommunityScreen", "Skipping circle with blank ID: ${circle.name}")
                                        return@items
                                    }
                                    val hasNewActivity = circlesWithActivity.contains(circle.id)
                                    CoachieCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                android.util.Log.d("CommunityScreen", "=========================================")
                                                android.util.Log.d("CommunityScreen", "Circle clicked: ${circle.id}, name: ${circle.name}")
                                                android.util.Log.d("CommunityScreen", "Calling onNavigateToCircleDetail with circleId: ${circle.id}")
                                                try {
                                                    if (circle.id.isNotBlank()) {
                                                        onNavigateToCircleDetail(circle.id)
                                                        android.util.Log.d("CommunityScreen", "Navigation call completed successfully")
                                                    } else {
                                                        android.util.Log.e("CommunityScreen", "Cannot navigate - circle ID is blank")
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("CommunityScreen", "Error during navigation", e)
                                                }
                                                android.util.Log.d("CommunityScreen", "=========================================")
                                            },
                                        colors = CoachieCardDefaults.colors()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    circle.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                
                                                // Activity indicator badge
                                                if (hasNewActivity) {
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(8.dp)
                                                                .background(
                                                                    color = MaterialTheme.colorScheme.error,
                                                                    shape = androidx.compose.foundation.shape.CircleShape
                                                                )
                                                        )
                                                        Text(
                                                            text = "New activity",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.error,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                circle.goal,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "${circle.members.size}/${circle.maxMembers} members",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Forums content with proper UI
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(
                                top = 16.dp,
                                bottom = 16.dp
                            )
                        ) {
                            item {
                                Text(
                                    "Community Forums",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (isLoading) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            } else if (forums.isEmpty()) {
                                item {
                                    Text(
                                        "No forums available yet.",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                items(forums) { forum ->
                                    CoachieCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onNavigateToForumDetail(forum.id)
                                            },
                                        colors = CoachieCardDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = forum.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = forum.description,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${forum.postCount} posts",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (forum.lastPostAt != null) {
                                                    Text(
                                                        text = "Last post: ${java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(forum.lastPostAt)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // News content
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(
                                top = 16.dp,
                                bottom = 16.dp
                            )
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Article,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        "Wellness News",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            
                            item {
                                Text(
                                    "Stay updated with the latest wellness tips, research, and community highlights.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Placeholder news items - in the future, these would come from Firestore or an API
                            item {
                                NewsCard(
                                    title = "Welcome to Coachie News!",
                                    content = "This is where you'll find the latest wellness tips, research updates, and community highlights. News items will be added regularly to keep you informed and inspired on your wellness journey.",
                                    date = "Today",
                                    accentColor = accentColor
                                )
                            }
                            
                            item {
                                NewsCard(
                                    title = "5 Tips for Better Sleep",
                                    content = "Quality sleep is essential for overall wellness. Try establishing a consistent bedtime routine, limiting screen time before bed, and creating a comfortable sleep environment.",
                                    date = "Recent",
                                    accentColor = accentColor
                                )
                            }
                            
                            item {
                                NewsCard(
                                    title = "Building Healthy Habits",
                                    content = "Small, consistent actions lead to lasting change. Start with one habit at a time and build from there. Remember, progress over perfection!",
                                    date = "Recent",
                                    accentColor = accentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewsCard(
    title: String,
    content: String,
    date: String,
    accentColor: Color
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
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
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = date,
                    style = MaterialTheme.typography.bodySmall,
                    color = accentColor.copy(alpha = 0.7f)
                )
            }
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CircleCard(
    circle: Circle,
    onClick: () -> Unit
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
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
                    text = circle.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.Group,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${circle.members.size}/${circle.maxMembers}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = circle.goal,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (circle.streak > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🔥",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "${circle.streak} day streak",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyCirclesState(
    onJoinClick: () -> Unit,
    onCreateClick: () -> Unit,
    accentColor: Color
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "👥",
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = "No Circles Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Join a circle to connect with others working toward similar goals, or create your own!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onCreateClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor
                    )
                ) {
                    Text("Create Circle")
                }
                OutlinedButton(
                    onClick = onJoinClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Find a Circle")
                }
            }
        }
    }
}

