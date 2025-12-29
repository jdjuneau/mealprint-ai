package com.coachie.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mealprint.ai.data.FirebaseRepository
import com.mealprint.ai.data.model.Forum
import com.mealprint.ai.data.model.ForumPost
import com.mealprint.ai.data.model.Recipe
import com.mealprint.ai.ui.components.CoachieCard
import com.mealprint.ai.ui.components.CoachieCardDefaults
import com.mealprint.ai.ui.theme.rememberCoachieGradient
import com.mealprint.ai.viewmodel.AuthState
import com.mealprint.ai.viewmodel.CoachieViewModel
import androidx.compose.material.icons.filled.Save
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumDetailScreen(
    forumId: String,
    onNavigateBack: () -> Unit,
    onNavigateToRecipe: ((String) -> Unit)? = null,
    viewModel: CoachieViewModel = viewModel()
) {
    val gradientBackground = rememberCoachieGradient(endY = 1600f)
    val authState by viewModel.authState.collectAsState()
    val currentUser = (authState as? AuthState.Authenticated)?.user
    val userId = currentUser?.uid ?: return
    val userName = currentUser?.displayName ?: "Anonymous"

    val repository = FirebaseRepository.getInstance()
    var forum by remember { mutableStateOf<Forum?>(null) }
    var posts by remember { mutableStateOf<List<ForumPost>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreatePostDialog by remember { mutableStateOf(false) }
    var sortByUpvotes by remember { mutableStateOf(true) } // Sort by upvotes by default
    var selectedPostForComment by remember { mutableStateOf<String?>(null) }
    var comments by remember { mutableStateOf<Map<String, List<com.coachie.app.data.model.ForumComment>>>(emptyMap()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(forumId) {
        isLoading = true
        try {
            // Load forum details
            val forumResult = repository.getForum(forumId)
            forumResult.onSuccess { loadedForum ->
                forum = loadedForum
                
                // If this is Coachie News and has no posts, call setup function
                if (loadedForum.title == "Coachie News" && loadedForum.postCount == 0) {
                    android.util.Log.d("ForumDetailScreen", "Coachie News has no posts, calling setup function")
                    try {
                        val functions = Firebase.functions
                        val setupFunction = functions.getHttpsCallable("setupCoachieNews")
                        val result = setupFunction.call().await()
                        android.util.Log.d("ForumDetailScreen", "Setup function called successfully: $result")
                        // Reload posts after setup
                        kotlinx.coroutines.delay(2000) // Wait for posts to be created
                    } catch (e: Exception) {
                        android.util.Log.e("ForumDetailScreen", "Failed to call setupCoachieNews", e)
                    }
                }
            }

            // Load forum posts
            val postsResult = repository.getForumPosts(forumId)
            postsResult.onSuccess { 
                posts = if (sortByUpvotes) {
                    // Sort by upvotes (descending), then by creation date (descending)
                    it.sortedWith(compareByDescending<ForumPost> { it.upvoteCount }
                        .thenByDescending { it.createdAt })
                } else {
                    // Sort by creation date (descending)
                    it.sortedByDescending { it.createdAt }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ForumDetailScreen", "Error loading forum", e)
        } finally {
            isLoading = false
        }
    }

    if (showCreatePostDialog) {
        CreatePostDialog(
            forumId = forumId,
            forumTitle = forum?.title ?: "",
            userId = userId,
            userName = userName,
            onDismiss = { showCreatePostDialog = false },
            onPostCreated = {
                showCreatePostDialog = false
                // Refresh posts
                coroutineScope.launch {
                    val postsResult = repository.getForumPosts(forumId)
                    postsResult.onSuccess { 
                        posts = if (sortByUpvotes) {
                            // Sort by upvotes (descending), then by creation date (descending)
                            it.sortedWith(compareByDescending<ForumPost> { it.upvoteCount }
                                .thenByDescending { it.createdAt })
                        } else {
                            // Sort by creation date (descending)
                            it.sortedByDescending { it.createdAt }
                        }
                    }
                }
            }
        )
    }
    
    // Load comments when a post is selected for commenting
    LaunchedEffect(selectedPostForComment) {
        selectedPostForComment?.let { postId ->
            val commentsResult = repository.getForumPostComments(forumId, postId)
            commentsResult.onSuccess { commentList ->
                comments = comments.toMutableMap().apply {
                    put(postId, commentList)
                }
            }
        }
    }
    
    // Comment dialog
    selectedPostForComment?.let { postId ->
        CommentDialog(
            forumId = forumId,
            postId = postId,
            userId = userId,
            userName = userName,
            existingComments = comments[postId] ?: emptyList(),
            onDismiss = { selectedPostForComment = null },
            onCommentAdded = {
                // Reload comments for this post
                coroutineScope.launch {
                    val commentsResult = repository.getForumPostComments(forumId, postId)
                    commentsResult.onSuccess { commentList ->
                        comments = comments.toMutableMap().apply {
                            put(postId, commentList)
                        }
                    }
                    // Refresh posts to update comment count
                    val postsResult = repository.getForumPosts(forumId)
                    postsResult.onSuccess { 
                        posts = if (sortByUpvotes) {
                            it.sortedWith(compareByDescending<ForumPost> { it.upvoteCount }
                                .thenByDescending { it.createdAt })
                        } else {
                            it.sortedByDescending { it.createdAt }
                        }
                    }
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBackground)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
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
                                contentDescription = "Back",
                                tint = Color(0xFF1F2937) // Dark gray for readability
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            forum?.title ?: "Forum",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937), // Dark gray for readability
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showCreatePostDialog = true }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Create Post",
                                tint = Color(0xFF1F2937) // Dark gray for readability
                            )
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
                    // Forum description
                    forum?.let { forum ->
                        item {
                            CoachieCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CoachieCardDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = forum.description,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${forum.postCount} posts • ${forum.category}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        // Sort toggle
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = if (sortByUpvotes) "Top" else "New",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Switch(
                                                checked = sortByUpvotes,
                                                onCheckedChange = { newValue ->
                                                    sortByUpvotes = newValue
                                                    // Re-sort posts with new sort order
                                                    posts = if (newValue) {
                                                        posts.sortedWith(compareByDescending<ForumPost> { it.upvoteCount }
                                                            .thenByDescending { it.createdAt })
                                                    } else {
                                                        posts.sortedByDescending { it.createdAt }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Posts
                    if (posts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = "No posts yet",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Be the first to start a discussion!",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Button(
                                        onClick = { showCreatePostDialog = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("Create First Post")
                                    }
                                }
                            }
                        }
                    } else {
                        items(posts) { post ->
                            ForumPostCard(
                                post = post,
                                currentUserId = userId,
                                onRecipeClick = { recipeId ->
                                    onNavigateToRecipe?.invoke(recipeId) ?: run {
                                        android.util.Log.d("ForumDetailScreen", "Recipe clicked: $recipeId (no navigation handler)")
                                    }
                                },
                                onDelete = {
                                    // Refresh posts after deletion
                                    coroutineScope.launch {
                                        val postsResult = repository.getForumPosts(forumId)
                                        postsResult.onSuccess { 
                                            posts = if (sortByUpvotes) {
                                                it.sortedWith(compareByDescending<ForumPost> { it.upvoteCount }
                                                    .thenByDescending { it.createdAt?.time ?: 0L })
                                            } else {
                                                it.sortedByDescending { it.createdAt?.time ?: 0L }
                                            }
                                        }
                                    }
                                },
                                onLike = {
                                    coroutineScope.launch {
                                        val result = repository.likeForumPost(forumId, post.id, userId)
                                        result.onSuccess {
                                            // Refresh posts
                                            val postsResult = repository.getForumPosts(forumId)
                                            postsResult.onSuccess { 
                                                posts = if (sortByUpvotes) {
                                                    it.sortedWith(compareByDescending<ForumPost> { it.upvoteCount }
                                                        .thenByDescending { it.createdAt })
                                                } else {
                                                    it.sortedByDescending { it.createdAt }
                                                }
                                            }.onFailure { e ->
                                                android.util.Log.e("ForumDetailScreen", "Failed to refresh posts after like", e)
                                            }
                                        }.onFailure { e ->
                                            android.util.Log.e("ForumDetailScreen", "Failed to like post", e)
                                        }
                                    }
                                },
                                onUpvote = {
                                    coroutineScope.launch {
                                        val result = repository.upvoteForumPost(forumId, post.id, userId)
                                        result.onSuccess {
                                            // Refresh posts
                                            val postsResult = repository.getForumPosts(forumId)
                                            postsResult.onSuccess { 
                                                posts = if (sortByUpvotes) {
                                                    it.sortedWith(compareByDescending<ForumPost> { it.upvoteCount }
                                                        .thenByDescending { it.createdAt })
                                                } else {
                                                    it.sortedByDescending { it.createdAt }
                                                }
                                            }.onFailure { e ->
                                                android.util.Log.e("ForumDetailScreen", "Failed to refresh posts after upvote", e)
                                            }
                                        }.onFailure { e ->
                                            android.util.Log.e("ForumDetailScreen", "Failed to upvote post", e)
                                        }
                                    }
                                },
                                onComment = {
                                    // Open comment dialog
                                    selectedPostForComment = post.id
                                },
                                onSaveRecipe = { recipe ->
                                    coroutineScope.launch {
                                        repository.saveRecipeToSavedMeals(userId, recipe).fold(
                                            onSuccess = {
                                                successMessage = "Recipe saved to Quick Select!"
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = successMessage ?: "Recipe saved!",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                            },
                                            onFailure = {
                                                errorMessage = "Failed to save recipe: ${it.message}"
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = errorMessage ?: "Failed to save recipe",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                }
                                            }
                                        )
                                    }
                                },
                                onSaveToRecipes = { recipe ->
                                    coroutineScope.launch {
                                        repository.saveRecipe(userId, recipe).fold(
                                            onSuccess = {
                                                successMessage = "Recipe saved to My Recipes!"
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = successMessage ?: "Recipe saved!",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                }
                                            },
                                            onFailure = {
                                                errorMessage = "Failed to save recipe: ${it.message}"
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = errorMessage ?: "Failed to save recipe",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForumPostCard(
    post: ForumPost,
    currentUserId: String,
    onLike: () -> Unit,
    onUpvote: () -> Unit,
    onComment: () -> Unit = {},
    onSaveRecipe: (Recipe) -> Unit = {},
    onSaveToRecipes: ((Recipe) -> Unit)? = null,
    onRecipeClick: ((String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val repository = remember { FirebaseRepository.getInstance() }
    val scope = rememberCoroutineScope()
    var recipe by remember { mutableStateOf<Recipe?>(null) }
    var isLoadingRecipe by remember { mutableStateOf(false) }
    var isSavingRecipe by remember { mutableStateOf(false) }
    var showDeleteMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    
    val isAuthor = post.authorId == currentUserId

    // Load recipe if recipeId is present
    LaunchedEffect(post.recipeId) {
        post.recipeId?.let { recipeId ->
            isLoadingRecipe = true
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                android.util.Log.d("ForumPostCard", "Loading recipe: $recipeId for user: $currentUserId")
                val result = repository.getSharedRecipe(recipeId)
                result.fold(
                    onSuccess = { loadedRecipe ->
                        android.util.Log.d("ForumPostCard", "Successfully loaded recipe: ${loadedRecipe.name}")
                        recipe = loadedRecipe
                        isLoadingRecipe = false
                    },
                    onFailure = { error ->
                        android.util.Log.e("ForumPostCard", "Failed to load recipe $recipeId: ${error.message}", error)
                        if (error is com.google.firebase.firestore.FirebaseFirestoreException) {
                            android.util.Log.e("ForumPostCard", "Firestore error code: ${error.code}, message: ${error.message}")
                        }
                        isLoadingRecipe = false
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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with author, timestamp, and delete menu
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
                            text = formatRelativeTime(createdAt.time),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Delete menu (only show if user is the author)
                if (isAuthor && onDelete != null) {
                    Box {
                        IconButton(
                            onClick = { showDeleteMenu = true }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showDeleteMenu,
                            onDismissRequest = { showDeleteMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete Post") },
                                onClick = {
                                    showDeleteMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
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
                    onSave = {
                        isSavingRecipe = true
                        onSaveRecipe(attachedRecipe)
                        isSavingRecipe = false
                    },
                    isSaving = isSavingRecipe,
                    onSaveToRecipes = onSaveToRecipes?.let { saveHandler ->
                        {
                            isSavingRecipe = true
                            // Update recipe userId to current user when saving
                            val recipeToSave = attachedRecipe.copy(userId = currentUserId)
                            saveHandler(recipeToSave)
                            isSavingRecipe = false
                        }
                    },
                    isSavingToRecipes = isSavingRecipe
                )
            }

            // Like, Upvote, and Comment section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Upvote button (left side)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onUpvote) {
                        Icon(
                            imageVector = if (post.isUpvotedBy(currentUserId))
                                Icons.Filled.ArrowUpward
                            else
                                Icons.Outlined.ArrowUpward,
                            contentDescription = "Upvote",
                            tint = if (post.isUpvotedBy(currentUserId))
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = post.upvoteCount.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (post.isUpvotedBy(currentUserId)) FontWeight.Bold else FontWeight.Normal,
                        color = if (post.isUpvotedBy(currentUserId))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Like button (right side)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onLike) {
                        Icon(
                            imageVector = if (post.isLikedBy(currentUserId))
                                Icons.Filled.Favorite
                            else
                                Icons.Outlined.FavoriteBorder,
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
                
                // Comment button (center)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onComment) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = "Comment",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (post.commentCount > 0) {
                        Text(
                            text = post.commentCount.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            title = { Text("Delete Post") },
            text = { Text("Are you sure you want to delete this post? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        isDeleting = true
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val result = repository.deleteForumPost(post.forumId, post.id, currentUserId)
                            result.fold(
                                onSuccess = {
                                    isDeleting = false
                                    showDeleteDialog = false
                                    onDelete?.invoke()
                                },
                                onFailure = { error ->
                                    android.util.Log.e("ForumPostCard", "Failed to delete post", error)
                                    isDeleting = false
                                    showDeleteDialog = false
                                }
                            )
                        }
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onError
                        )
                    } else {
                        Text("Delete")
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CreatePostDialog(
    forumId: String,
    forumTitle: String,
    userId: String,
    userName: String,
    onDismiss: () -> Unit,
    onPostCreated: () -> Unit
) {
    var postContent by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    val repository = FirebaseRepository.getInstance()
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = postContent,
                    onValueChange = { postContent = it },
                    label = { Text("What's on your mind?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (postContent.trim().isNotEmpty()) {
                        isPosting = true
                        coroutineScope.launch {
                            try {
                                val post = ForumPost(
                                    authorId = userId,
                                    authorName = userName,
                                    content = postContent.trim(),
                                    forumId = forumId,
                                    forumTitle = forumTitle
                                )
                                android.util.Log.d("CreatePostDialog", "Creating post with forumId: $forumId, authorId: $userId")
                                val result = repository.createForumPost(forumId, post)
                                result.onSuccess {
                                    onPostCreated()
                                }.onFailure { e ->
                                    android.util.Log.e("CreatePostDialog", "Error creating post", e)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("CreatePostDialog", "Error creating post", e)
                            } finally {
                                isPosting = false
                            }
                        }
                    }
                },
                enabled = !isPosting && postContent.trim().isNotEmpty()
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Post")
                }
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
fun CommentDialog(
    forumId: String,
    postId: String,
    userId: String,
    userName: String,
    existingComments: List<com.coachie.app.data.model.ForumComment>,
    onDismiss: () -> Unit,
    onCommentAdded: () -> Unit
) {
    var commentContent by remember { mutableStateOf("") }
    var isPosting by remember { mutableStateOf(false) }
    val repository = FirebaseRepository.getInstance()
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Comments") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Display existing comments
                if (existingComments.isEmpty()) {
                    Text(
                        text = "No comments yet. Be the first to comment!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(existingComments) { comment ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = comment.authorName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    comment.createdAt?.let { createdAt ->
                                        Text(
                                            text = formatRelativeTime(createdAt.time),
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
                }
                
                // Comment input
                OutlinedTextField(
                    value = commentContent,
                    onValueChange = { commentContent = it },
                    label = { Text("Your comment") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (commentContent.trim().isNotEmpty()) {
                        isPosting = true
                        coroutineScope.launch {
                            try {
                                val comment = com.coachie.app.data.model.ForumComment(
                                    authorId = userId,
                                    authorName = userName,
                                    content = commentContent.trim(),
                                    postId = postId
                                )
                                val result = repository.addCommentToForumPost(forumId, postId, comment)
                                result.onSuccess {
                                    commentContent = "" // Clear the input
                                    onCommentAdded()
                                }.onFailure { e ->
                                    android.util.Log.e("CommentDialog", "Error adding comment", e)
                                    isPosting = false
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("CommentDialog", "Error adding comment", e)
                                isPosting = false
                            }
                        }
                    }
                },
                enabled = !isPosting && commentContent.trim().isNotEmpty()
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Post")
                }
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
private fun RecipeAttachmentCard(
    recipe: Recipe,
    onSave: () -> Unit,
    isSaving: Boolean,
    onSaveToRecipes: (() -> Unit)? = null,
    isSavingToRecipes: Boolean = false,
    onRecipeClick: ((String) -> Unit)? = null
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
            // Recipe name and description
            Text(
                text = recipe.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            recipe.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Save buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onSaveToRecipes != null) {
                    Button(
                        onClick = onSaveToRecipes,
                        enabled = !isSavingToRecipes && !isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSavingToRecipes) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("Save Recipe", style = MaterialTheme.typography.labelSmall)
                                    Text("to My Recipes", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = onSave,
                    enabled = !isSaving && !isSavingToRecipes,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                            Text("Quick Select", style = MaterialTheme.typography.labelSmall)
                            Text("to Saved Meals", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                            }
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
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
            
            if (onRecipeClick != null) {
                Text(
                    text = "Tap to view full recipe →",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60 * 1000 -> "Just now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
        else -> {
            val date = java.util.Date(timestamp)
            java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(date)
        }
    }
}
