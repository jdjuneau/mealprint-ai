package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coachie.app.data.FirebaseRepository
import com.coachie.app.data.model.Circle
import com.coachie.app.data.model.CircleCheckIn
import com.coachie.app.data.model.Recipe
import com.coachie.app.ui.components.SharePlatformDialog
import com.coachie.app.data.model.CirclePost
import com.coachie.app.data.model.CircleComment
import com.coachie.app.ui.components.CoachieCard
import com.coachie.app.ui.components.CoachieCardDefaults
import com.coachie.app.ui.theme.Accent40
import com.coachie.app.ui.theme.Secondary40
import com.coachie.app.ui.theme.rememberCoachieGradient
import com.coachie.app.ui.theme.LocalGender
import com.coachie.app.ui.theme.SemanticColorCategory
import com.coachie.app.ui.theme.getSemanticColorPrimary
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleDetailScreen(
    onNavigateBack: () -> Unit,
    circleId: String,
    userId: String,
    onNavigateToInvite: ((String) -> Unit)? = null,
    onNavigateToRecipe: ((String) -> Unit)? = null
) {
    val repository = FirebaseRepository.getInstance()
    var circle by remember { mutableStateOf<Circle?>(null) }
    var checkIns by remember { mutableStateOf<List<CircleCheckIn>>(emptyList()) }
    var userCheckIn by remember { mutableStateOf<CircleCheckIn?>(null) }
    var posts by remember { mutableStateOf<List<CirclePost>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCheckInDialog by remember { mutableStateOf(false) }
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var selectedPostForComment by remember { mutableStateOf<String?>(null) }
    var comments by remember { mutableStateOf<Map<String, List<CircleComment>>>(emptyMap()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val shareService = remember { com.coachie.app.service.ShareService.getInstance(context) }
    
    // Check if user is the circle creator
    val isCreator = circle?.createdBy == userId
    
    // Handle saving recipe from post
    fun handleSaveRecipe(recipe: Recipe) {
        scope.launch {
            val result = repository.saveRecipe(userId, recipe)
            result.fold(
                onSuccess = {
                    android.widget.Toast.makeText(context, "Recipe saved to My Recipes!", android.widget.Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    android.util.Log.e("CircleDetailScreen", "Failed to save recipe", error)
                    android.widget.Toast.makeText(context, "Failed to save recipe: ${error.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    
    // Get semantic color for Community (purple theme)
    val gender = LocalGender.current
    val isMale = gender.lowercase() == "male"
    val communityColor = getSemanticColorPrimary(SemanticColorCategory.COMMUNITY, isMale)

    LaunchedEffect(circleId, userId, today) {
        isLoading = true
        errorMessage = null
        
        // Load circle
        val circleResult = repository.getCircle(circleId)
        circleResult.onSuccess {
            circle = it
        }.onFailure {
            errorMessage = it.message
        }
        
        // Load check-ins for today
        val checkInsResult = repository.getCircleCheckIns(circleId, today)
        checkInsResult.onSuccess {
            checkIns = it
        }

        // Load user's check-in
        val userCheckInResult = repository.getUserCheckIn(circleId, today, userId)
        userCheckInResult.onSuccess {
            userCheckIn = it
        }

        // Load posts
        val postsResult = repository.getCirclePosts(circleId)
        postsResult.onSuccess {
            posts = it
        }

        isLoading = false
    }

    fun handleSubmitCheckIn(energy: Int, note: String?) {
        scope.launch {
            val checkIn = CircleCheckIn(
                uid = userId,
                energy = energy,
                note = note?.takeIf { it.isNotBlank() }
            )
            val result = repository.submitCheckIn(circleId, today, userId, checkIn)
            result.onSuccess {
                userCheckIn = checkIn
                checkIns = checkIns.filter { it.uid != userId } + checkIn
                showCheckInDialog = false
            }.onFailure {
                errorMessage = it.message
            }
        }
    }

    fun handleCreatePost(content: String) {
        scope.launch {
            // Get user profile for author name
            val profileResult = repository.getUserProfile(userId)
            val authorName = profileResult.getOrNull()?.name ?: "Anonymous"

            val post = CirclePost(
                authorId = userId,
                authorName = authorName,
                content = content
            )
            val result = repository.createCirclePost(circleId, post)
            result.onSuccess { postId ->
                val newPost = post.copy(id = postId)
                posts = listOf(newPost) + posts // Add to top
                showCreatePostDialog = false
            }.onFailure {
                errorMessage = it.message
            }
        }
    }

    fun handleLikePost(postId: String) {
        scope.launch {
            val result = repository.likeCirclePost(circleId, postId, userId)
            result.onSuccess {
                // Refresh posts to get updated like counts
                val postsResult = repository.getCirclePosts(circleId)
                postsResult.onSuccess {
                    posts = it
                }
            }.onFailure {
                errorMessage = it.message
            }
        }
    }

    fun handleAddComment(postId: String, content: String) {
        scope.launch {
            // Get user profile for author name
            val profileResult = repository.getUserProfile(userId)
            val authorName = profileResult.getOrNull()?.name ?: "Anonymous"

            val comment = CircleComment(
                authorId = userId,
                authorName = authorName,
                content = content
            )
            val result = repository.addCommentToPost(circleId, postId, comment)
            result.onSuccess { commentId ->
                // Reload comments for this post
                val commentsResult = repository.getPostComments(circleId, postId)
                commentsResult.onSuccess { commentList ->
                    comments = comments.toMutableMap().apply {
                        put(postId, commentList)
                    }
                    // Refresh posts to update comment count
                    val postsResult = repository.getCirclePosts(circleId)
                    postsResult.onSuccess {
                        posts = it
                    }
                }
            }.onFailure {
                errorMessage = it.message
            }
        }
    }

    // Load comments when a post is selected for commenting
    LaunchedEffect(selectedPostForComment) {
        selectedPostForComment?.let { postId ->
            if (comments[postId] == null) {
                val commentsResult = repository.getPostComments(circleId, postId)
                commentsResult.onSuccess { commentList ->
                    comments = comments.toMutableMap().apply {
                        put(postId, commentList)
                    }
                }
            }
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
                                tint = communityColor
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            circle?.name ?: "Circle",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = communityColor
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        // Share circle accomplishment button
                        IconButton(onClick = {
                            showShareDialog = true
                        }) {
                            Icon(
                                Icons.Filled.Share,
                                "Share Accomplishment",
                                tint = communityColor
                            )
                        }
                        // Invite friend button - always visible and prominent
                        if (onNavigateToInvite != null) {
                            IconButton(
                                onClick = { 
                                    onNavigateToInvite.invoke(circleId)
                                }
                            ) {
                                Icon(
                                    Icons.Filled.PersonAdd,
                                    "Invite Friend",
                                    tint = communityColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else {
                            // Show disabled button if callback is not available
                            IconButton(
                                onClick = { 
                                    android.util.Log.e("CircleDetailScreen", "onNavigateToInvite callback is null!")
                                },
                                enabled = false
                            ) {
                                Icon(
                                    Icons.Filled.PersonAdd,
                                    "Invite Friend",
                                    tint = communityColor.copy(alpha = 0.3f),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        IconButton(onClick = { showCreatePostDialog = true }) {
                            Icon(
                                androidx.compose.material.icons.Icons.Filled.Add,
                                "Create Post",
                                tint = communityColor
                            )
                        }
                        // Delete circle menu (only for creator)
                        if (isCreator) {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        "More options",
                                        tint = communityColor
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                "Delete Circle",
                                                color = Color(0xFFD32F2F)
                                            )
                                        },
                                        onClick = {
                                            showMenu = false
                                            showDeleteDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = null,
                                                tint = Color(0xFFD32F2F)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
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
            } else if (errorMessage != null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp)
                ) {
                    item {
                        CoachieCard(
                            modifier = Modifier.fillMaxWidth(),
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
                                Icon(
                                    imageVector = Icons.Filled.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Error",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = errorMessage ?: "An unknown error occurred",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF1F2937) // Dark gray for readability
                                    )
                                }
                                IconButton(
                                    onClick = { errorMessage = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color(0xFF1F2937),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        bottom = 16.dp
                    )
                ) {
                    circle?.let { circleData ->
                        // Circle info
                        item {
                            CircleInfoCard(circle = circleData)
                        }
                        
                        // Invite friend button - prominent in main content
                        if (onNavigateToInvite != null) {
                            item {
                                Button(
                                    onClick = { onNavigateToInvite.invoke(circleId) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = communityColor
                                    )
                                ) {
                                    Icon(
                                        Icons.Filled.PersonAdd,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Invite Friend to Circle")
                                }
                            }
                        }
                        
                        // Check-in section
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Today's Check-ins",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (userCheckIn == null) {
                                    Button(
                                        onClick = { showCheckInDialog = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = communityColor
                                        )
                                    ) {
                                        Text("Check In")
                                    }
                                }
                            }
                        }
                        
                        // User's check-in
                        userCheckIn?.let { checkIn ->
                            item {
                                CheckInCard(
                                    checkIn = checkIn,
                                    isOwnCheckIn = true
                                )
                            }
                        }
                        
                        // Other members' check-ins
                        val otherCheckIns = checkIns.filter { it.uid != userId }
                        if (otherCheckIns.isEmpty() && userCheckIn == null) {
                            item {
                                Text(
                                    text = "No check-ins yet today. Be the first!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            items(otherCheckIns) { checkIn ->
                                CheckInCard(
                                    checkIn = checkIn,
                                    isOwnCheckIn = false
                                )
                            }
                        }

                        // Posts section
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Circle Posts",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (posts.isEmpty()) {
                            item {
                                Text(
                                    text = "No posts yet. Be the first to share!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            items(posts) { post ->
                                CirclePostCard(
                                    post = post,
                                    currentUserId = userId,
                                    onLike = { handleLikePost(post.id) },
                                    onCommentClick = { selectedPostForComment = post.id },
                                    comments = comments[post.id] ?: emptyList(),
                                    onRecipeClick = { recipeId ->
                                        onNavigateToRecipe?.invoke(recipeId) ?: run {
                                            android.util.Log.d("CircleDetailScreen", "Recipe clicked: $recipeId (no navigation handler)")
                                        }
                                    },
                                    onSaveRecipe = { recipe -> handleSaveRecipe(recipe) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Check-in dialog
    if (showCheckInDialog) {
        CheckInDialog(
            onDismiss = { showCheckInDialog = false },
            onSubmit = { energy, note -> handleSubmitCheckIn(energy, note) }
        )
    }

    // Create post dialog
    if (showCreatePostDialog) {
        CreatePostDialog(
            onDismiss = { showCreatePostDialog = false },
            onSubmit = { content -> handleCreatePost(content) }
        )
    }

    // Comment dialog
    selectedPostForComment?.let { postId ->
        CommentDialog(
            postId = postId,
            comments = comments[postId] ?: emptyList(),
            onDismiss = { selectedPostForComment = null },
            onSubmit = { content -> handleAddComment(postId, content) }
        )
    }
    
    // Share dialog
    // Delete circle confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (!isDeleting) {
                    showDeleteDialog = false
                }
            },
            title = { 
                Text(
                    "Delete Circle",
                    color = Color(0xFFD32F2F)
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${circle?.name}\"? This action cannot be undone. All posts, check-ins, and member data will be permanently deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            isDeleting = true
                            val result = repository.deleteCircle(circleId, userId)
                            result.fold(
                                onSuccess = {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Circle deleted successfully",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                    onNavigateBack()
                                },
                                onFailure = { error ->
                                    android.util.Log.e("CircleDetailScreen", "Failed to delete circle", error)
                                    android.widget.Toast.makeText(
                                        context,
                                        "Failed to delete circle: ${error.message}",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    isDeleting = false
                                    showDeleteDialog = false
                                }
                            )
                        }
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White
                        )
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showShareDialog) {
        SharePlatformDialog(
            onDismiss = { showShareDialog = false },
            onShareToPlatform = { platform ->
                showShareDialog = false
                scope.launch {
                    val accomplishment = circle?.goal ?: "Join me in this challenge!"
                    val memberCount = circle?.members?.size ?: 0
                    val maxMembers = circle?.maxMembers ?: 0
                    shareService.shareCircleAccomplishment(
                        circleName = circle?.name ?: "Circle",
                        accomplishment = accomplishment,
                        memberCount = memberCount,
                        maxMembers = maxMembers,
                        platform = platform
                    )
                }
            }
        )
    }
}


@Composable
fun CircleInfoCard(circle: Circle) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
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
            Text(
                text = circle.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = circle.goal,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        text = "${circle.members.size}/${circle.maxMembers} members",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
}

@Composable
fun CheckInCard(
    checkIn: CircleCheckIn,
    isOwnCheckIn: Boolean
) {
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = if (isOwnCheckIn) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            }
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
                    text = if (isOwnCheckIn) "Your Check-in" else "Member Check-in",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                // Energy level indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(5) { index ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = if (index < checkIn.energy) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
            checkIn.note?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CheckInDialog(
    onDismiss: () -> Unit,
    onSubmit: (Int, String?) -> Unit
) {
    var energyLevel by remember { mutableStateOf(3) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily Check-in") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("How's your energy level today?")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) { index ->
                        val level = index + 1
                        FilterChip(
                            selected = energyLevel == level,
                            onClick = { energyLevel = level },
                            label = { Text("$level") }
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Optional note") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(energyLevel, note.takeIf { it.isNotBlank() }) }) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CirclePostCard(
    post: CirclePost,
    currentUserId: String,
    onLike: () -> Unit,
    onCommentClick: () -> Unit,
    comments: List<CircleComment> = emptyList(),
    onRecipeClick: ((String) -> Unit)? = null,
    onSaveRecipe: ((Recipe) -> Unit)? = null
) {
    val repository = remember { FirebaseRepository.getInstance() }
    val scope = rememberCoroutineScope()
    var recipe by remember { mutableStateOf<Recipe?>(null) }
    var isLoadingRecipe by remember { mutableStateOf(false) }
    var isSavingRecipe by remember { mutableStateOf(false) }
    
    // Load recipe if post has recipeId
    LaunchedEffect(post.recipeId) {
        post.recipeId?.let { recipeId ->
            isLoadingRecipe = true
            scope.launch(Dispatchers.IO) {
                // Try personal recipes first, then shared recipes
                val result = repository.getRecipe(currentUserId, recipeId)
                result.fold(
                    onSuccess = { loadedRecipe ->
                        recipe = loadedRecipe
                        isLoadingRecipe = false
                    },
                    onFailure = {
                        // Try shared recipes
                        val sharedResult = repository.getSharedRecipe(recipeId)
                        sharedResult.fold(
                            onSuccess = { sharedRecipe ->
                                recipe = sharedRecipe
                                isLoadingRecipe = false
                            },
                            onFailure = {
                                android.util.Log.w("CirclePostCard", "Failed to load recipe: ${it.message}")
                                isLoadingRecipe = false
                            }
                        )
                    }
                )
            }
        }
    }
    CoachieCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CoachieCardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with author and timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = post.authorName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    post.createdAt?.let { createdAt ->
                        Text(
                            text = formatRelativeTime(createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Post content
            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Recipe attachment if present
            if (isLoadingRecipe) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else recipe?.let { attachedRecipe ->
                RecipeAttachmentCard(
                    recipe = attachedRecipe,
                    onRecipeClick = onRecipeClick,
                    onSaveToRecipes = onSaveRecipe?.let { saveHandler ->
                        {
                            isSavingRecipe = true
                            // Update recipe userId to current user when saving
                            val recipeToSave = attachedRecipe.copy(userId = currentUserId)
                            saveHandler(recipeToSave)
                            isSavingRecipe = false
                        }
                    },
                    isSaving = isSavingRecipe
                )
            }

            // Like and comment section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Like button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onLike) {
                            Icon(
                                imageVector = if (post.isLikedBy(currentUserId))
                                    androidx.compose.material.icons.Icons.Filled.Favorite
                                else
                                    androidx.compose.material.icons.Icons.Outlined.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (post.isLikedBy(currentUserId))
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = post.likes.size.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Comment button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onCommentClick) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "Comments",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = post.commentCount.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Show comments preview (first 2 comments)
            if (comments.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    comments.take(2).forEach { comment ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = comment.authorName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = comment.content,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (comments.size > 2) {
                        TextButton(onClick = onCommentClick) {
                            Text(
                                text = "View all ${comments.size} comments",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeAttachmentCard(
    recipe: Recipe,
    onRecipeClick: ((String) -> Unit)?,
    onSaveToRecipes: (() -> Unit)? = null,
    isSaving: Boolean = false
) {
    val perServing = recipe.getNutritionPerServing()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onRecipeClick != null) {
                onRecipeClick?.invoke(recipe.id)
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipe.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    recipe.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (onSaveToRecipes != null) {
                    Button(
                        onClick = onSaveToRecipes,
                        enabled = !isSaving,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Save Recipe")
                        }
                    }
                }
            }
            
            Divider()
            
            // Nutrition info
            Text(
                text = "Nutrition (per serving):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Calories: ${perServing.calories}", style = MaterialTheme.typography.bodySmall)
                Text("Protein: ${perServing.proteinG}g", style = MaterialTheme.typography.bodySmall)
                Text("Carbs: ${perServing.carbsG}g", style = MaterialTheme.typography.bodySmall)
                Text("Fat: ${perServing.fatG}g", style = MaterialTheme.typography.bodySmall)
            }
            
            // Ingredients preview (first 3)
            if (recipe.ingredients.isNotEmpty()) {
                Text(
                    text = "Ingredients:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                recipe.ingredients.take(3).forEach { ingredient ->
                    Text(
                        text = "• ${ingredient.quantity} ${ingredient.unit} ${ingredient.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (recipe.ingredients.size > 3) {
                    Text(
                        text = "... and ${recipe.ingredients.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontStyle = FontStyle.Italic
                    )
                }
            }
            
            if (onRecipeClick != null) {
                Text(
                    text = "Tap to view full recipe →",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun CommentDialog(
    postId: String,
    comments: List<CircleComment>,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comments") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Display existing comments
                if (comments.isEmpty()) {
                    Text(
                        text = "No comments yet. Be the first to comment!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    comments.forEach { comment ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = comment.authorName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                comment.createdAt?.let { createdAt ->
                                    Text(
                                        text = formatRelativeTime(createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = comment.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                
                // Comment input
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Add a comment...") },
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (content.isNotBlank()) {
                        onSubmit(content)
                        content = ""
                    }
                },
                enabled = content.isNotBlank()
            ) {
                Text("Post")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun CreatePostDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Post") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Share something with your circle!")
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("What's on your mind?") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(content) },
                enabled = content.isNotBlank()
            ) {
                Text("Post")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatRelativeTime(date: Date): String {
    val now = Date()
    val diffInMillis = now.time - date.time
    val diffInMinutes = diffInMillis / (1000 * 60)
    val diffInHours = diffInMinutes / 60
    val diffInDays = diffInHours / 24

    return when {
        diffInMinutes < 1 -> "Just now"
        diffInMinutes < 60 -> "$diffInMinutes minutes ago"
        diffInHours < 24 -> "$diffInHours hours ago"
        diffInDays < 7 -> "$diffInDays days ago"
        else -> {
            val formatter = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
            formatter.format(date)
        }
    }
}

