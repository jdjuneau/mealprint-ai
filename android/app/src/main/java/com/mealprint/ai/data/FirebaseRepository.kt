package com.coachie.app.data

import com.mealprint.ai.data.model.DailyLog
import com.mealprint.ai.data.model.Scan
import com.mealprint.ai.data.model.UserProfile
import com.mealprint.ai.data.model.HealthLog
import com.mealprint.ai.data.model.Supplement
import com.mealprint.ai.data.model.SavedMeal
import com.mealprint.ai.data.model.Recipe
import com.mealprint.ai.data.model.RecipeIngredient
import com.mealprint.ai.data.model.Circle
import com.mealprint.ai.data.model.CircleCheckIn
import com.mealprint.ai.data.model.CirclePost
import com.mealprint.ai.data.model.CircleComment
import com.mealprint.ai.data.model.CircleWin
import com.mealprint.ai.data.model.Forum
import com.mealprint.ai.data.model.ForumPost
import com.mealprint.ai.data.model.ForumComment
import com.mealprint.ai.data.model.FriendRequest
import com.mealprint.ai.data.model.Message
import com.mealprint.ai.data.model.Conversation
import com.mealprint.ai.data.model.PublicUserProfile
import com.mealprint.ai.data.StreakService
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.functions.ktx.functions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Firebase Repository for Coachie fitness app.
 * Handles Firestore operations for users, daily logs, and body scans.
 */
class FirebaseRepository {

    private val db: FirebaseFirestore = Firebase.firestore

    // Collections as specified by user
    private val usersCollection = db.collection("users")
    private val logsCollection = db.collection("logs")
    private val scansCollection = db.collection("scans")
    private val circlesCollection = db.collection("circles")
    private val friendRequestsCollection = db.collection("friendRequests")
    private val conversationsCollection = db.collection("conversations")

    private fun supplementsCollection(uid: String) =
        usersCollection.document(uid).collection("supplements")
    
    private fun userCirclesCollection(uid: String) =
        usersCollection.document(uid).collection("circles")
    
    private fun circleCheckInsCollection(circleId: String, date: String) =
        circlesCollection.document(circleId).collection("checkins").document(date).collection("entries")

    // ============================================================================
    // USER PROFILE OPERATIONS
    // ============================================================================

    /**
     * Save user profile to users/{uid}
     */
    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            android.util.Log.d("FirebaseRepository", "Saving user profile with uid: '${profile.uid}'")
            android.util.Log.d("FirebaseRepository", "Profile data: name='${profile.name}', heightCm=${profile.heightCm}, currentWeight=${profile.currentWeight}, goalWeight=${profile.goalWeight}, age=${profile.age}")
            if (profile.uid.isBlank()) {
                throw IllegalArgumentException("Cannot save profile with blank uid")
            }
            // If username is not set, set it to the name (lowercase, no spaces) for search purposes
            val profileToSave = if (profile.username.isNullOrBlank() && profile.name.isNotBlank()) {
                profile.copy(username = profile.name.lowercase().replace(" ", ""))
            } else {
                profile
            }
            
            // Add platform tracking for Android
            val profileWithPlatform = profileToSave.copy(
                platform = "android",
                platforms = (profileToSave.platforms ?: emptyList()).let { existing ->
                    if (!existing.contains("android")) {
                        existing + "android"
                    } else {
                        existing
                    }
                }
            )
            
            // Use merge to preserve existing fields that aren't being updated
            usersCollection.document(profileWithPlatform.uid).set(profileWithPlatform, com.google.firebase.firestore.SetOptions.merge()).await()
            android.util.Log.d("FirebaseRepository", "Profile saved successfully with merge - heightCm=${profileToSave.heightCm}, username=${profileToSave.username}")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to save user profile", e)
            Result.failure(e)
        }
    }

    /**
     * Get user profile from users/{uid}
     */
    /**
     * Safely deserialize UserProfile from a Firestore document, handling subscription tier case mismatch
     */
    private fun safeDeserializeUserProfile(document: com.google.firebase.firestore.DocumentSnapshot, uid: String? = null): UserProfile? {
        return try {
            // Try normal deserialization first
            val profile = document.toObject(UserProfile::class.java)
            profile?.uid = uid ?: document.id
            
            // Manually handle subscription field to avoid case mismatch errors
            val subscriptionData = document.get("subscription") as? Map<*, *>
            if (subscriptionData != null && profile != null) {
                val tierString = (subscriptionData["tier"] as? String) ?: "free"
                val tier = when (tierString.lowercase()) {
                    "pro", "premium" -> com.coachie.app.data.model.SubscriptionTier.PRO
                    else -> com.coachie.app.data.model.SubscriptionTier.FREE
                }
                
                profile.subscription = com.coachie.app.data.model.SubscriptionInfo(
                    tier = tier,
                    expiresAt = (subscriptionData["expiresAt"] as? Number)?.toLong(),
                    isActive = subscriptionData["isActive"] as? Boolean ?: true,
                    purchasedAt = (subscriptionData["purchasedAt"] as? Number)?.toLong(),
                    purchaseToken = subscriptionData["purchaseToken"] as? String,
                    productId = subscriptionData["productId"] as? String
                )
            }
            
            profile
        } catch (e: Exception) {
            // If toObject fails (e.g., due to subscription tier mismatch), manually deserialize
            android.util.Log.w("FirebaseRepository", "toObject failed for user ${uid ?: document.id}, manually deserializing: ${e.message}")
            try {
                val data = document.data ?: emptyMap()
                UserProfile(
                    uid = uid ?: document.id,
                    name = data["name"] as? String ?: "",
                    username = data["username"] as? String,
                    currentWeight = (data["currentWeight"] as? Number)?.toDouble() ?: 0.0,
                    goalWeight = (data["goalWeight"] as? Number)?.toDouble() ?: 0.0,
                    heightCm = (data["heightCm"] as? Number)?.toDouble() ?: 0.0,
                    activityLevel = data["activityLevel"] as? String ?: "",
                    startDate = (data["startDate"] as? Number)?.toLong() ?: 0L,
                    nudgesEnabled = data["nudgesEnabled"] as? Boolean ?: true,
                    fcmToken = data["fcmToken"] as? String,
                    dietaryPreference = data["dietaryPreference"] as? String ?: com.coachie.app.data.model.DietaryPreference.BALANCED.id,
                    age = (data["age"] as? Number)?.toInt() ?: 30,
                    isFirstTimeUser = data["isFirstTimeUser"] as? Boolean ?: true,
                    gender = data["gender"] as? String ?: "",
                    menstrualCycleEnabled = data["menstrualCycleEnabled"] as? Boolean ?: false,
                    averageCycleLength = (data["averageCycleLength"] as? Number)?.toInt() ?: 28,
                    averagePeriodLength = (data["averagePeriodLength"] as? Number)?.toInt() ?: 5,
                    lastPeriodStart = (data["lastPeriodStart"] as? Number)?.toLong(),
                    mealsPerDay = (data["mealsPerDay"] as? Number)?.toInt() ?: 3,
                    snacksPerDay = (data["snacksPerDay"] as? Number)?.toInt() ?: 2,
                    notifications = data["notifications"] as? Map<String, Boolean>,
                    mealTimes = data["mealTimes"] as? Map<String, String>,
                    fcmTokens = (data["fcmTokens"] as? List<*>)?.mapNotNull { it as? String },
                    subscription = null, // Will be set below
                    ftueCompleted = data["ftueCompleted"] as? Boolean ?: false,
                    preferredCookingMethods = (data["preferredCookingMethods"] as? List<*>)?.mapNotNull { it as? String }
                ).also { profile ->
                    // Manually set subscription
                    val subscriptionData = data["subscription"] as? Map<*, *>
                    if (subscriptionData != null) {
                        val tierString = (subscriptionData["tier"] as? String) ?: "free"
                        val tier = when (tierString.lowercase()) {
                            "pro", "premium" -> com.coachie.app.data.model.SubscriptionTier.PRO
                            else -> com.coachie.app.data.model.SubscriptionTier.FREE
                        }
                        
                        profile.subscription = com.coachie.app.data.model.SubscriptionInfo(
                            tier = tier,
                            expiresAt = (subscriptionData["expiresAt"] as? Number)?.toLong(),
                            isActive = subscriptionData["isActive"] as? Boolean ?: true,
                            purchasedAt = (subscriptionData["purchasedAt"] as? Number)?.toLong(),
                            purchaseToken = subscriptionData["purchaseToken"] as? String,
                            productId = subscriptionData["productId"] as? String
                        )
                    }
                }
            } catch (manualError: Exception) {
                android.util.Log.e("FirebaseRepository", "Failed to manually deserialize UserProfile for ${uid ?: document.id}", manualError)
                null
            }
        }
    }

    suspend fun getUserProfile(uid: String): Result<UserProfile?> {
        return try {
            val document = usersCollection.document(uid).get().await()
            if (document.exists()) {
                val profile = safeDeserializeUserProfile(document, uid)
                
                // CRITICAL: Always ensure platform field is set for Android users
                // This ensures existing users get the platform field updated when they use the app
                if (profile != null) {
                    val needsPlatformUpdate = profile.platform != "android" || 
                                             (profile.platforms?.contains("android") != true)
                    
                    if (needsPlatformUpdate) {
                        android.util.Log.d("FirebaseRepository", "Updating platform field for user $uid (was: ${profile.platform})")
                        val updatedProfile = profile.copy(
                            platform = "android",
                            platforms = (profile.platforms ?: emptyList()).let { existing ->
                                if (!existing.contains("android")) {
                                    existing + "android"
                                } else {
                                    existing
                                }
                            }
                        )
                        // Save the updated profile (async, don't wait)
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                usersCollection.document(uid).set(updatedProfile, com.google.firebase.firestore.SetOptions.merge()).await()
                                android.util.Log.d("FirebaseRepository", "✅ Platform field updated for user $uid")
                            } catch (e: Exception) {
                                android.util.Log.e("FirebaseRepository", "Failed to update platform field", e)
                            }
                        }
                        Result.success(updatedProfile)
                    } else {
                        Result.success(profile)
                    }
                } else {
                    Result.success(profile)
                }
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Error getting user profile for $uid", e)
            Result.failure(e)
        }
    }

    /**
     * Get user goals from users/{uid}
     * Goals are stored as a Map<String, Any> in the user's document
     */
    suspend fun getUserGoals(uid: String): Result<Map<String, Any>?> {
        return try {
            val document = usersCollection.document(uid).get().await()
            if (document.exists()) {
                val data = document.data
                // Filter to only goals-related fields
                val goalsData = data?.filterKeys { key ->
                    key in listOf(
                        "selectedGoal", "fitnessLevel", "weeklyWorkouts", "dailySteps",
                        "gender", "goalsSet", "goalsSetDate", "useImperial"
                    )
                }
                Result.success(goalsData)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveUserGoals(uid: String, goals: Map<String, Any?>): Result<Unit> {
        return try {
            val userDoc = usersCollection.document(uid)
            val updateData = goals.toMutableMap()
            updateData["goalsSet"] = true
            updateData["goalsSetDate"] = System.currentTimeMillis()

            userDoc.update(updateData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update FCM token for a user (legacy - single token)
     */
    suspend fun updateUserFcmToken(uid: String, fcmToken: String): Result<Unit> {
        return try {
            usersCollection.document(uid)
                .update("fcmToken", fcmToken)
                .await()
            // Also add to fcmTokens array for compatibility
            registerFCMToken(uid, fcmToken)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Register FCM token (add to fcmTokens array)
     */
    suspend fun registerFCMToken(uid: String, token: String): Result<Unit> {
        return try {
            val profileResult = getUserProfile(uid)
            val profile = profileResult.getOrNull()
            val existingTokens = profile?.fcmTokens?.toMutableList() ?: mutableListOf()
            
            if (!existingTokens.contains(token)) {
                existingTokens.add(token)
                usersCollection.document(uid)
                    .update("fcmTokens", existingTokens)
                    .await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to register FCM token", e)
            Result.failure(e)
        }
    }
    
    /**
     * Remove FCM token (e.g., on logout)
     */
    suspend fun removeFCMToken(uid: String, token: String): Result<Unit> {
        return try {
            val profileResult = getUserProfile(uid)
            val profile = profileResult.getOrNull()
            val existingTokens = profile?.fcmTokens?.toMutableList() ?: mutableListOf()
            
            existingTokens.remove(token)
            usersCollection.document(uid)
                .update("fcmTokens", existingTokens)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to remove FCM token", e)
            Result.failure(e)
        }
    }

    suspend fun updateUserSettings(uid: String, updates: Map<String, Any?>): Result<Unit> {
        if (updates.isEmpty()) return Result.success(Unit)

        return try {
            val docRef = usersCollection.document(uid)
            // CRITICAL: Use set() with merge to ensure it works even if document doesn't exist yet
            docRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            android.util.Log.d("FirebaseRepository", "✅ Successfully updated user settings: ${updates.keys.joinToString()}")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "❌ Failed to update user settings: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ============================================================================
    // DAILY LOG OPERATIONS
    // ============================================================================

    /**
     * Save daily log to logs/{uid}/{date}
     */
    suspend fun saveDailyLog(log: DailyLog): Result<Unit> {
        return try {
            // CRITICAL: Validate uid and date are not empty before attempting to save
            if (log.uid.isBlank()) {
                android.util.Log.e("FirebaseRepository", "❌❌❌ CRITICAL ERROR: DailyLog.uid is empty! Cannot save. Date: ${log.date} ❌❌❌")
                return Result.failure(IllegalArgumentException("DailyLog.uid cannot be empty. Date: ${log.date}"))
            }
            if (log.date.isBlank()) {
                android.util.Log.e("FirebaseRepository", "❌❌❌ CRITICAL ERROR: DailyLog.date is empty! Cannot save. UID: ${log.uid} ❌❌❌")
                return Result.failure(IllegalArgumentException("DailyLog.date cannot be empty. UID: ${log.uid}"))
            }
            
            android.util.Log.d("FirebaseRepository", "Saving daily log for ${log.date}: steps=${log.steps}, calories=${log.caloriesBurned}, water=${log.water}, uid=${log.uid}")
            val updatedLog = log.copy(updatedAt = System.currentTimeMillis())
            
            // CRITICAL: Convert DailyLog to Map and add waterAmount field for cross-platform compatibility
            // Android uses 'water', Web uses 'waterAmount' - save both for seamless sync
            val logData = hashMapOf<String, Any>(
                "uid" to updatedLog.uid,
                "date" to updatedLog.date,
                "updatedAt" to updatedLog.updatedAt,
                "createdAt" to updatedLog.createdAt
            )
            
            updatedLog.weight?.let { logData["weight"] = it }
            updatedLog.steps?.let { logData["steps"] = it }
            updatedLog.caloriesBurned?.let { logData["caloriesBurned"] = it }
            updatedLog.water?.let { 
                logData["water"] = it // Android field name
                logData["waterAmount"] = it // Web field name - CRITICAL for cross-platform sync
            }
            updatedLog.mood?.let { logData["mood"] = it }
            updatedLog.energy?.let { logData["energy"] = it }
            updatedLog.notes?.let { logData["notes"] = it }
            if (updatedLog.micronutrientExtras.isNotEmpty()) {
                logData["micronutrientExtras"] = updatedLog.micronutrientExtras
            }
            if (updatedLog.micronutrientChecklist.isNotEmpty()) {
                logData["micronutrientChecklist"] = updatedLog.micronutrientChecklist
            }
            
            // CRITICAL FIX: Save to users/{uid}/daily/{date} for cross-platform sync (web app expects this path)
            // Also save to logs/{uid}/daily/{date} for backward compatibility
            val usersDailyRef = usersCollection.document(log.uid)
                .collection("daily")
                .document(log.date)
            usersDailyRef.set(logData).await()
            
            // Also save to old path for backward compatibility
            val logsDailyRef = logsCollection.document(log.uid)
                .collection("daily")
                .document(log.date)
            logsDailyRef.set(logData).await()

            android.util.Log.i("FirebaseRepository", "✓✓✓ Successfully saved daily log for ${log.date} to Firestore (both paths) ✓✓✓")

            // Update user's streak after logging
            StreakService.getInstance().updateStreakAfterLog(log.uid, log.date)

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "✗✗✗ FAILED to save daily log for ${log.date} ✗✗✗", e)
            android.util.Log.e("FirebaseRepository", "  Error: ${e.message}")
            android.util.Log.e("FirebaseRepository", "  Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get daily log from users/{uid}/daily/{date} (primary) or logs/{uid}/daily/{date} (fallback)
     * CRITICAL: Web app reads from users/{uid}/daily/{date}, so we prioritize that path
     * Also reads entries subcollection to populate logs array (matching Web structure)
     */
    suspend fun getDailyLog(uid: String, date: String): Result<DailyLog?> {
        return try {
            // Try new path first (users/{uid}/daily/{date}) - matches web app
            val usersDoc = usersCollection.document(uid)
                .collection("daily")
                .document(date)
                .get()
                .await()

            if (usersDoc.exists()) {
                val log = usersDoc.toObject(DailyLog::class.java)
                android.util.Log.d("FirebaseRepository", "Found daily log in users/{uid}/daily/{date}")
                return Result.success(log)
            }
            
            // Fallback to old path (logs/{uid}/daily/{date}) for backward compatibility
            val logsDoc = logsCollection.document(uid)
                .collection("daily")
                .document(date)
                .get()
                .await()

            if (logsDoc.exists()) {
                val log = logsDoc.toObject(DailyLog::class.java)
                android.util.Log.d("FirebaseRepository", "Found daily log in logs/{uid}/daily/{date} (fallback)")
                return Result.success(log)
            }
            
            Result.success(null)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get recent daily logs for a user
     * CRITICAL: Reads from users/{uid}/daily (primary) or logs/{uid}/daily (fallback) for cross-platform sync
     */
    suspend fun getRecentDailyLogs(uid: String, limit: Int = 7): Result<List<DailyLog>> {
        return try {
            // Try new path first (users/{uid}/daily) - matches web app
            val usersQuerySnapshot = usersCollection.document(uid)
                .collection("daily")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val logs = mutableListOf<DailyLog>()
            usersQuerySnapshot.documents.forEach { document ->
                document.toObject(DailyLog::class.java)?.let { logs.add(it) }
            }
            
            // If not enough logs found, try old path for backward compatibility
            if (logs.size < limit) {
                val logsQuerySnapshot = logsCollection.document(uid)
                    .collection("daily")
                    .orderBy("date", Query.Direction.DESCENDING)
                    .limit((limit - logs.size).toLong())
                    .get()
                    .await()
                
                logsQuerySnapshot.documents.forEach { document ->
                    document.toObject(DailyLog::class.java)?.let { log ->
                        // Only add if not already in list (avoid duplicates)
                        if (logs.none { it.date == log.date }) {
                            logs.add(log)
                        }
                    }
                }
            }

            // Sort by date descending
            logs.sortByDescending { it.date }
            Result.success(logs.take(limit))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================================
    // SCAN OPERATIONS
    // ============================================================================

    /**
     * Save scan to scans/{uid}/{timestamp}
     */
    suspend fun saveScan(scan: Scan): Result<Unit> {
        return try {
            scansCollection.document(scan.uid)
                .collection("scans")
                .document(scan.timestamp.toString())
                .set(scan)
                .await()

            // Check for scan-related badges
            StreakService.getInstance().checkScanBadges(scan.uid)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get scan from scans/{uid}/{timestamp}
     */
    suspend fun getScan(uid: String, timestamp: Long): Result<Scan?> {
        return try {
            val document = scansCollection.document(uid)
                .collection("scans")
                .document(timestamp.toString())
                .get()
                .await()

            if (document.exists()) {
                val scan = document.toObject(Scan::class.java)
                Result.success(scan)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get recent scans for a user
     */
    suspend fun getRecentScans(uid: String, limit: Int = 10): Result<List<Scan>> {
        return try {
            val querySnapshot = scansCollection.document(uid)
                .collection("scans")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val scans = querySnapshot.documents.mapNotNull { document ->
                document.toObject(Scan::class.java)
            }

            Result.success(scans)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete scan
     */
    suspend fun deleteScan(uid: String, timestamp: Long): Result<Unit> {
        return try {
            scansCollection.document(uid)
                .collection("scans")
                .document(timestamp.toString())
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================================
    // LEGACY METHODS (for backward compatibility)
    // ============================================================================

    // Keep these for any existing code that might use them
    val workoutsCollection = db.collection("coachie_workouts")
    val exercisesCollection = db.collection("coachie_exercises")
    val workoutPlansCollection = db.collection("coachie_workout_plans")

    fun getUserWorkouts(userId: String) =
        workoutsCollection.whereEqualTo("userId", userId).get()

    fun saveWorkout(workoutId: String, workoutData: Map<String, Any>) =
        workoutsCollection.document(workoutId).set(workoutData)

    // ============================================================================
    // STREAK AND BADGE OPERATIONS
    // ============================================================================

    /**
     * Get user's current streak
     */
    suspend fun getUserStreak(uid: String) = StreakService.getInstance().getUserStreak(uid)

    /**
     * Get user's earned badges
     */
    suspend fun getUserBadges(uid: String) = StreakService.getInstance().getUserBadges(uid)

    /**
     * Get user's achievement progress
     */
    suspend fun getAchievementProgress(uid: String) = StreakService.getInstance().getAchievementProgress(uid)

    /**
     * Mark badge as seen (remove new status)
     */
    suspend fun markBadgeAsSeen(uid: String, badgeType: String) = StreakService.getInstance().markBadgeAsSeen(uid, badgeType)

    // ============================================================================
    // HEALTH LOG OPERATIONS
    // ============================================================================

    /**
     * Save a health log entry to logs/{uid}/{date}/entries/{entryId}
     */
    suspend fun saveWeightLog(uid: String, weightLog: Map<String, Any>): Result<Unit> {
        return try {
            Firebase.firestore.collection("users")
                .document(uid)
                .collection("weight_logs")
                .add(weightLog)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveWaterLog(uid: String, waterLog: Map<String, Any>): Result<Unit> {
        return try {
            Firebase.firestore.collection("users")
                .document(uid)
                .collection("water_logs")
                .add(waterLog)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Recalculates DailyLog.water by summing all WaterLog entries for a given date.
     * This fixes incorrect water totals caused by double-counting bugs.
     * 
     * @param uid User ID
     * @param date Date string in format "yyyy-MM-dd"
     * @return Result with the corrected water amount in ml, or failure if error occurred
     */
    suspend fun recalculateDailyLogWater(uid: String, date: String): Result<Int> {
        return try {
            android.util.Log.d("FirebaseRepository", "🔄 Recalculating DailyLog.water for $uid on $date")
            
            // Get all health logs for the date
            val healthLogsResult = getHealthLogs(uid, date)
            val healthLogs = healthLogsResult.getOrNull() ?: emptyList()
            
            // Filter to only WaterLog entries
            val waterLogs = healthLogs.filterIsInstance<HealthLog.WaterLog>()
            
            // Sum all water amounts
            val totalWaterMl = waterLogs.sumOf { it.ml }
            
            android.util.Log.d("FirebaseRepository", "  Found ${waterLogs.size} WaterLog entries")
            android.util.Log.d("FirebaseRepository", "  Total water from logs: ${totalWaterMl}ml")
            
            // Get existing DailyLog to see current (incorrect) value
            val existingLogResult = getDailyLog(uid, date)
            val existingLog = existingLogResult.getOrNull() ?: DailyLog.createForDate(uid, date)
            val oldWater = existingLog.water ?: 0
            
            android.util.Log.d("FirebaseRepository", "  Old DailyLog.water: ${oldWater}ml")
            android.util.Log.d("FirebaseRepository", "  New DailyLog.water: ${totalWaterMl}ml")
            
            // Update DailyLog with corrected water amount
            val updatedLog = existingLog.copy(
                water = totalWaterMl,
                uid = uid,
                date = date
            )
            
            val saveResult = saveDailyLog(updatedLog)
            if (saveResult.isSuccess) {
                android.util.Log.d("FirebaseRepository", "✅ Successfully recalculated DailyLog.water: ${oldWater}ml → ${totalWaterMl}ml")
                Result.success(totalWaterMl)
            } else {
                android.util.Log.e("FirebaseRepository", "❌ Failed to save recalculated DailyLog.water")
                Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to save recalculated water"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Error recalculating DailyLog.water", e)
            Result.failure(e)
        }
    }

    // Saved Meals functionality
    suspend fun saveSavedMeal(savedMeal: SavedMeal): Result<String> {
        return try {
            // CRITICAL SECURITY: Always use the authenticated user's ID for saved meals
            // Firestore security rule requires: request.auth.uid == request.resource.data.userId
            val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            val currentAuthUserId = currentAuthUser?.uid
            
            if (currentAuthUserId == null) {
                android.util.Log.e("FirebaseRepository", "❌❌❌ CRITICAL ERROR: No authenticated user when saving meal! ❌❌❌")
                return Result.failure(Exception("User must be authenticated to save meals"))
            }
            
            // Always use authenticated user's ID, not the savedMeal.userId (which might be from a shared meal)
            val mealData = hashMapOf(
                "id" to savedMeal.id,
                "userId" to currentAuthUserId, // CRITICAL: Use authenticated user's ID
                "name" to savedMeal.name,
                "foodName" to savedMeal.foodName,
                "calories" to savedMeal.calories,
                "proteinG" to savedMeal.proteinG,
                "carbsG" to savedMeal.carbsG,
                "fatG" to savedMeal.fatG,
                "sugarG" to savedMeal.sugarG,
                "addedSugarG" to savedMeal.addedSugarG,
                "micronutrients" to savedMeal.micronutrients,
                "recipeId" to (savedMeal.recipeId ?: ""), // CRITICAL: Link to recipe so it can be accessed later
                "createdAt" to savedMeal.createdAt,
                "lastUsedAt" to savedMeal.lastUsedAt,
                "useCount" to savedMeal.useCount
            )

            android.util.Log.d("FirebaseRepository", "💾 Saving meal to savedMeals collection")
            android.util.Log.d("FirebaseRepository", "  Meal ID: ${savedMeal.id}")
            android.util.Log.d("FirebaseRepository", "  Meal name: ${savedMeal.name}")
            android.util.Log.d("FirebaseRepository", "  Authenticated user ID: $currentAuthUserId")
            android.util.Log.d("FirebaseRepository", "  Meal userId in data: ${mealData["userId"]}")

            Firebase.firestore.collection("savedMeals").document(savedMeal.id)
                .set(mealData)
                .await()

            android.util.Log.d("FirebaseRepository", "✅✅✅ Successfully saved meal ${savedMeal.id} to savedMeals collection ✅✅✅")
            Result.success(savedMeal.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to save saved meal", e)
            Result.failure(e)
        }
    }

    suspend fun getSavedMeals(userId: String): Result<List<SavedMeal>> {
        return try {
            android.util.Log.d("FirebaseRepository", "🔍 Getting saved meals for userId: $userId")
            val collection = Firebase.firestore.collection("savedMeals")

            val snapshot = try {
                val result = collection
                    .whereEqualTo("userId", userId)
                    .orderBy("lastUsedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()
                android.util.Log.d("FirebaseRepository", "✅ Found ${result.documents.size} saved meals with ordered query")
                result
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    // Suppress the warning - this is expected and handled gracefully
                    // The index warning is harmless - we fall back to unordered query which works fine
                    android.util.Log.d(
                        "FirebaseRepository",
                        "Missing Firestore index for savedMeals (userId + lastUsedAt). Using unordered query (this is fine)."
                    )

                    val result = collection
                        .whereEqualTo("userId", userId)
                        .get()
                        .await()
                    android.util.Log.d("FirebaseRepository", "✅ Found ${result.documents.size} saved meals with unordered query")
                    result
                } else {
                    android.util.Log.e("FirebaseRepository", "❌ Error querying saved meals", e)
                    throw e
                }
            }

            val meals = snapshot.documents.mapNotNull { doc ->
                try {
                    val docUserId = doc.getString("userId") ?: ""
                    android.util.Log.d("FirebaseRepository", "  Parsing meal doc: id=${doc.id}, userId=$docUserId, name=${doc.getString("name")}")
                    
                    val micronutrientsMap = (doc.get("micronutrients") as? Map<*, *>)?.mapNotNull { entry ->
                        val key = entry.key as? String ?: return@mapNotNull null
                        val value = (entry.value as? Number)?.toDouble() ?: 0.0
                        key to value
                    }?.toMap() ?: emptyMap()
                    
                    SavedMeal(
                        id = doc.getString("id") ?: "",
                        userId = docUserId,
                        name = doc.getString("name") ?: "",
                        foodName = doc.getString("foodName") ?: "",
                        calories = doc.getLong("calories")?.toInt() ?: 0,
                        proteinG = doc.getLong("proteinG")?.toInt() ?: 0,
                        carbsG = doc.getLong("carbsG")?.toInt() ?: 0,
                        fatG = doc.getLong("fatG")?.toInt() ?: 0,
                        sugarG = doc.getLong("sugarG")?.toInt() ?: 0,
                        addedSugarG = doc.getLong("addedSugarG")?.toInt() ?: 0,
                        micronutrients = micronutrientsMap,
                        recipeId = doc.getString("recipeId")?.takeIf { it.isNotBlank() }, // CRITICAL: Read recipeId if present
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        lastUsedAt = doc.getLong("lastUsedAt") ?: 0L,
                        useCount = doc.getLong("useCount")?.toInt() ?: 1
                    )
                } catch (e: Exception) {
                    android.util.Log.e("FirebaseRepository", "Error parsing meal doc ${doc.id}", e)
                    null
                }
            }

            android.util.Log.d("FirebaseRepository", "✅✅✅ Returning ${meals.size} saved meals for userId: $userId ✅✅✅")
            meals.forEach { meal ->
                android.util.Log.d("FirebaseRepository", "  - Meal: ${meal.name} (id=${meal.id}, userId=${meal.userId})")
            }
            
            Result.success(meals)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSavedMeal(savedMeal: SavedMeal): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any>(
                "lastUsedAt" to savedMeal.lastUsedAt,
                "useCount" to savedMeal.useCount
            )

            Firebase.firestore.collection("savedMeals").document(savedMeal.id)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSavedMeal(mealId: String): Result<Unit> {
        return try {
            Firebase.firestore.collection("savedMeals").document(mealId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveHealthLog(uid: String, date: String, healthLog: HealthLog): Result<String> {
        return try {
            android.util.Log.d("FirebaseRepository", "Saving health log for $date: type=${healthLog.type}, entryId=${when (healthLog) {
                is HealthLog.JournalEntry -> healthLog.entryId
                is HealthLog.WinEntry -> healthLog.entryId
                is HealthLog.MindfulSession -> healthLog.sessionId
                is HealthLog.MealLog -> healthLog.entryId
                is HealthLog.WorkoutLog -> healthLog.entryId
                is HealthLog.SleepLog -> healthLog.entryId
                is HealthLog.WaterLog -> healthLog.entryId
                is HealthLog.WeightLog -> healthLog.entryId
                is HealthLog.SupplementLog -> healthLog.entryId
                is HealthLog.MoodLog -> healthLog.entryId
                is HealthLog.MeditationLog -> healthLog.entryId
                is HealthLog.SunshineLog -> healthLog.entryId
                is HealthLog.MenstrualLog -> healthLog.entryId
                is HealthLog.BreathingExerciseLog -> healthLog.entryId
            }}")
            
            // Get entryId from health log or generate new one
            val entryId = when (healthLog) {
                is HealthLog.JournalEntry -> healthLog.entryId
                is HealthLog.WinEntry -> healthLog.entryId
                is HealthLog.MindfulSession -> healthLog.sessionId
                is HealthLog.MealLog -> healthLog.entryId
                is HealthLog.WorkoutLog -> healthLog.entryId
                is HealthLog.SleepLog -> healthLog.entryId
                is HealthLog.WaterLog -> healthLog.entryId
                is HealthLog.WeightLog -> healthLog.entryId
                is HealthLog.SupplementLog -> healthLog.entryId
                is HealthLog.MoodLog -> healthLog.entryId
                is HealthLog.MeditationLog -> healthLog.entryId
                is HealthLog.SunshineLog -> healthLog.entryId
                is HealthLog.MenstrualLog -> healthLog.entryId
                is HealthLog.BreathingExerciseLog -> healthLog.entryId
            }

            // Create a map with the type field for Firestore
            val logData = when (healthLog) {
                is HealthLog.MealLog -> {
                    android.util.Log.d("FirebaseRepository", "💾 Saving MealLog: ${healthLog.foodName}, sugar=${healthLog.sugar}g, addedSugar=${healthLog.addedSugar}g")
                    mutableMapOf<String, Any>(
                        "entryId" to healthLog.entryId,
                        "type" to healthLog.type,
                        "foodName" to healthLog.foodName,
                        "calories" to healthLog.calories,
                        "protein" to healthLog.protein,
                        "carbs" to healthLog.carbs,
                        "fat" to healthLog.fat,
                        "sugar" to healthLog.sugar, // CRITICAL: Always include sugar, even if 0
                        "addedSugar" to healthLog.addedSugar, // CRITICAL: Always include addedSugar, even if 0
                        "timestamp" to healthLog.timestamp
                    ).apply {
                        healthLog.photoUrl?.takeIf { it.isNotBlank() }?.let { put("photoUrl", it) }
                        if (healthLog.micronutrients.isNotEmpty()) {
                            put("micronutrients", healthLog.micronutrients)
                        }
                        // CRITICAL: Always include recipeId and servingsConsumed if present
                        healthLog.recipeId?.takeIf { it.isNotBlank() }?.let { put("recipeId", it) }
                        healthLog.servingsConsumed?.let { put("servingsConsumed", it) }
                    }
                }
                is HealthLog.SupplementLog -> mutableMapOf<String, Any>(
                    "entryId" to healthLog.entryId,
                    "type" to healthLog.type,
                    "name" to healthLog.name,
                    "timestamp" to healthLog.timestamp
                ).apply {
                    if (healthLog.micronutrients.isNotEmpty()) {
                        put("micronutrients", HashMap(healthLog.micronutrients))
                    }
                }
                is HealthLog.WorkoutLog -> mapOf(
                    "entryId" to healthLog.entryId,
                    "type" to healthLog.type,
                    "workoutType" to healthLog.workoutType,
                    "durationMin" to healthLog.durationMin,
                    "caloriesBurned" to healthLog.caloriesBurned,
                    "intensity" to healthLog.intensity,
                    "timestamp" to healthLog.timestamp
                )
                is HealthLog.SleepLog -> mapOf(
                    "entryId" to healthLog.entryId,
                    "type" to healthLog.type,
                    "startTime" to healthLog.startTime,
                    "endTime" to healthLog.endTime,
                    "quality" to healthLog.quality,
                    "timestamp" to healthLog.timestamp
                )
                is HealthLog.WaterLog -> mapOf(
                    "entryId" to healthLog.entryId,
                    "type" to healthLog.type,
                    "ml" to healthLog.ml,
                    "timestamp" to healthLog.timestamp
                )
                is HealthLog.MoodLog -> mutableMapOf<String, Any>(
                    "entryId" to healthLog.entryId,
                    "type" to healthLog.type,
                    "level" to healthLog.level,
                    "timestamp" to healthLog.timestamp
                ).apply {
                    healthLog.note?.let { put("note", it) }
                    if (healthLog.emotions.isNotEmpty()) put("emotions", healthLog.emotions)
                    healthLog.energyLevel?.let { put("energyLevel", it) }
                    healthLog.sleepQuality?.let { put("sleepQuality", it) }
                    if (healthLog.triggers.isNotEmpty()) put("triggers", healthLog.triggers)
                    healthLog.stressLevel?.let { put("stressLevel", it) }
                    healthLog.socialInteraction?.let { put("socialInteraction", it) }
                    healthLog.physicalActivity?.let { put("physicalActivity", it) }
                }
                is HealthLog.WeightLog -> mapOf(
                    "entryId" to healthLog.entryId,
                    "type" to healthLog.type,
                    "weight" to healthLog.weight,
                    "unit" to healthLog.unit,
                    "timestamp" to healthLog.timestamp
                )
                is HealthLog.SunshineLog -> mapOf(
                    "entryId" to healthLog.entryId,
                    "type" to healthLog.type,
                    "minutes" to healthLog.minutes,
                    "uvIndex" to healthLog.uvIndex,
                    "bodyCoverage" to healthLog.bodyCoverage,
                    "skinType" to healthLog.skinType,
                    "vitaminDIu" to healthLog.vitaminDIu,
                    "timestamp" to healthLog.timestamp
                )
                is HealthLog.MenstrualLog -> mutableMapOf<String, Any>(
                    "entryId" to healthLog.entryId,
                    "type" to healthLog.type,
                    "isPeriodStart" to healthLog.isPeriodStart,
                    "isPeriodEnd" to healthLog.isPeriodEnd,
                    "timestamp" to healthLog.timestamp
                ).apply {
                    healthLog.flowIntensity?.let { put("flowIntensity", it) }
                    healthLog.painLevel?.let { put("painLevel", it) }
                    healthLog.notes?.let { put("notes", it) }
                    if (healthLog.symptoms.isNotEmpty()) {
                        put("symptoms", healthLog.symptoms)
                    }
                }
                is HealthLog.MeditationLog -> mutableMapOf<String, Any>(
                    "entryId" to healthLog.entryId,
                    "type" to healthLog.type,
                    "durationMinutes" to healthLog.durationMinutes,
                    "meditationType" to healthLog.meditationType,
                    "timestamp" to healthLog.timestamp,
                    "completed" to healthLog.completed
                ).apply {
                    healthLog.moodBefore?.let { put("moodBefore", it) }
                    healthLog.moodAfter?.let { put("moodAfter", it) }
                    healthLog.stressBefore?.let { put("stressBefore", it) }
                    healthLog.stressAfter?.let { put("stressAfter", it) }
                    healthLog.notes?.let { put("notes", it) }
                }
                is HealthLog.MindfulSession -> mutableMapOf<String, Any>(
                    "type" to healthLog.type,
                    "sessionId" to healthLog.sessionId,
                    "title" to healthLog.title,
                    "transcript" to healthLog.transcript,
                    "durationSeconds" to healthLog.durationSeconds,
                    "generatedDate" to healthLog.generatedDate,
                    "personalizedPrompt" to healthLog.personalizedPrompt,
                    "isFavorite" to healthLog.isFavorite,
                    "playedCount" to healthLog.playedCount,
                    "timestamp" to healthLog.timestamp
                ).apply {
                    healthLog.audioUrl?.let { put("audioUrl", it) }
                    healthLog.lastPlayedAt?.let { put("lastPlayedAt", it) }
                    healthLog.notes?.let { put("notes", it) }
                }
                is HealthLog.JournalEntry -> {
                    val data = mutableMapOf<String, Any>(
                        "type" to healthLog.type,
                        "entryId" to healthLog.entryId,
                        "date" to healthLog.date,
                        "prompts" to healthLog.prompts,
                        "conversation" to healthLog.conversation.map { message ->
                            val messageMap = mutableMapOf<String, Any>(
                                "id" to message.id,
                                "role" to message.role,
                                "content" to message.content,
                                "timestamp" to message.timestamp,
                                "isAudio" to message.isAudio
                            )
                            message.audioUrl?.let { messageMap["audioUrl"] = it }
                            messageMap
                        },
                        "startedAt" to healthLog.startedAt,
                        "wordCount" to healthLog.wordCount,
                        "isCompleted" to healthLog.isCompleted,
                        "timestamp" to healthLog.timestamp
                    )
                    healthLog.completedAt?.let { data["completedAt"] = it }
                    healthLog.mood?.let { data["mood"] = it }
                    if (healthLog.insights.isNotEmpty()) data["insights"] = healthLog.insights
                    healthLog.notes?.let { data["notes"] = it }
                    data
                }
                is HealthLog.WinEntry -> {
                    val data = mutableMapOf<String, Any>(
                        "type" to healthLog.type,
                        "entryId" to healthLog.entryId,
                        "journalEntryId" to healthLog.journalEntryId,
                        "date" to healthLog.date,
                        "tags" to healthLog.tags,
                        "timestamp" to healthLog.timestamp
                    )
                    healthLog.moodScore?.let { data["moodScore"] = it }
                    healthLog.win?.let { data["win"] = it }
                    healthLog.gratitude?.let { data["gratitude"] = it }
                    healthLog.mood?.let { data["mood"] = it }
                    data
                }
                is HealthLog.BreathingExerciseLog -> mapOf(
                    "entryId" to healthLog.entryId,
                    "type" to healthLog.type,
                    "durationSeconds" to healthLog.durationSeconds,
                    "exerciseType" to healthLog.exerciseType,
                    "timestamp" to healthLog.timestamp
                ).toMutableMap().apply {
                    healthLog.stressLevelBefore?.let { put("stressLevelBefore", it) }
                    healthLog.stressLevelAfter?.let { put("stressLevelAfter", it) }
                }
            }
            
            // Add platform tracking for Android
            val logDataWithPlatform = logData.toMutableMap().apply {
                put("platform", "android")
            }
            
            // CRITICAL FIX: Save to users/{uid}/daily/{date}/entries/{entryId} for cross-platform sync
            // Also save to logs/{uid}/daily/{date}/entries/{entryId} for backward compatibility
            val usersEntriesRef = usersCollection.document(uid)
                .collection("daily")
                .document(date)
                .collection("entries")
                .document(entryId)
            usersEntriesRef.set(logDataWithPlatform).await()
            
            // Also save to old path for backward compatibility
            val logsEntriesRef = logsCollection.document(uid)
                .collection("daily")
                .document(date)
                .collection("entries")
                .document(entryId)
            logsEntriesRef.set(logDataWithPlatform).await()
            
            android.util.Log.i("FirebaseRepository", "✓✓✓ Successfully saved health log for $date (type=${healthLog.type}, entryId=$entryId) to Firestore ✓✓✓")
            
            // CRITICAL: Update DailyLog document with summary fields for cross-platform sync (Web expects these fields)
            // This ensures data syncs between Android and Web
            try {
                // Read raw document data to get all fields (including caloriesConsumed, workouts, sleepHours that Web uses)
                val usersDailyDoc = usersCollection.document(uid)
                    .collection("daily")
                    .document(date)
                    .get()
                    .await()
                
                val existingData = usersDailyDoc.data ?: emptyMap<String, Any>()
                
                val updates = hashMapOf<String, Any>()
                when (healthLog) {
                    is HealthLog.MealLog -> {
                        val currentCalories = (existingData["caloriesConsumed"] as? Number)?.toInt() ?: 0
                        updates["caloriesConsumed"] = currentCalories + healthLog.calories
                    }
                    is HealthLog.WorkoutLog -> {
                        val currentCaloriesBurned = (existingData["caloriesBurned"] as? Number)?.toInt() ?: 0
                        updates["caloriesBurned"] = currentCaloriesBurned + healthLog.caloriesBurned
                        val currentWorkouts = (existingData["workouts"] as? Number)?.toInt() ?: 0
                        updates["workouts"] = currentWorkouts + 1
                    }
                    is HealthLog.WaterLog -> {
                        val currentWater = (existingData["water"] as? Number)?.toInt() 
                            ?: (existingData["waterAmount"] as? Number)?.toInt() 
                            ?: (existingData["waterMl"] as? Number)?.toInt() 
                            ?: 0
                        val newWater = currentWater + healthLog.ml
                        updates["water"] = newWater
                        updates["waterAmount"] = newWater // Web field name - CRITICAL for cross-platform sync
                    }
                    is HealthLog.SleepLog -> {
                        val hours = (healthLog.endTime - healthLog.startTime) / (1000.0 * 60.0 * 60.0)
                        updates["sleepHours"] = hours
                    }
                    is HealthLog.WeightLog -> {
                        updates["weight"] = healthLog.weightKg
                    }
                    else -> {
                        // Other log types don't update summary fields
                    }
                }
                
                // Update DailyLog document if there are updates
                if (updates.isNotEmpty()) {
                    // Ensure daily log document exists
                    if (!usersDailyDoc.exists()) {
                        val initialData = hashMapOf<String, Any>(
                            "uid" to uid,
                            "date" to date,
                            "createdAt" to System.currentTimeMillis(),
                            "updatedAt" to System.currentTimeMillis()
                        )
                        initialData.putAll(updates)
                        usersCollection.document(uid)
                            .collection("daily")
                            .document(date)
                            .set(initialData)
                            .await()
                        
                        // Also create in old path
                        logsCollection.document(uid)
                            .collection("daily")
                            .document(date)
                            .set(initialData)
                            .await()
                    } else {
                        updates["updatedAt"] = System.currentTimeMillis()
                        val usersDailyRef = usersCollection.document(uid)
                            .collection("daily")
                            .document(date)
                        usersDailyRef.update(updates).await()
                        
                        // Also update old path for backward compatibility
                        val logsDailyRef = logsCollection.document(uid)
                            .collection("daily")
                            .document(date)
                        logsDailyRef.update(updates).await()
                    }
                    
                    android.util.Log.d("FirebaseRepository", "✅ Updated DailyLog summary fields: $updates")
                }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepository", "Error updating DailyLog summary fields (non-critical)", e)
                // Don't fail the entire operation if DailyLog update fails
            }
            
            // Update user's streak after logging health activity
            try {
                StreakService.getInstance().updateStreakAfterLog(uid, date)
                android.util.Log.d("FirebaseRepository", "✓ Streak updated after health log")
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepository", "Error updating streak after health log", e)
            }
            
            // Auto-update quests based on health log type
            try {
                when (healthLog) {
                    is HealthLog.MealLog -> {
                        com.coachie.app.service.QuestAutoCompletionService.onMealLogged(uid)
                        // Auto-complete related habits
                        com.coachie.app.service.HabitAutoCompletionService.onMealLogged(uid)
                        // Mark Today's Focus task as completed
                        markTodaysFocusTaskCompleted(uid, date, com.coachie.app.data.model.ReminderActionType.LOG_MEAL)
                    }
                    is HealthLog.WorkoutLog -> {
                        com.coachie.app.service.QuestAutoCompletionService.onWorkoutLogged(uid)
                        // Auto-complete related habits
                        com.coachie.app.service.HabitAutoCompletionService.onWorkoutLogged(
                            uid, 
                            healthLog.durationMin, 
                            healthLog.caloriesBurned
                        )
                        // Mark Today's Focus task as completed
                        markTodaysFocusTaskCompleted(uid, date, com.coachie.app.data.model.ReminderActionType.LOG_WORKOUT)
                    }
                    is HealthLog.WaterLog -> {
                        // Auto-completion - DailyLog.water is already updated above
                        com.coachie.app.service.QuestAutoCompletionService.onWaterLogged(uid, healthLog.ml)
                        // Auto-complete related habits
                        com.coachie.app.service.HabitAutoCompletionService.onWaterLogged(uid, healthLog.ml)
                    }
                    is HealthLog.SleepLog -> {
                        val hours = (healthLog.endTime - healthLog.startTime) / (1000.0 * 60.0 * 60.0)
                        com.coachie.app.service.QuestAutoCompletionService.onSleepLogged(uid, hours)
                        // Auto-complete related habits
                        com.coachie.app.service.HabitAutoCompletionService.onSleepLogged(uid, hours)
                        // Mark Today's Focus task as completed
                        markTodaysFocusTaskCompleted(uid, date, com.coachie.app.data.model.ReminderActionType.LOG_SLEEP)
                    }
                    is HealthLog.BreathingExerciseLog -> {
                        // Auto-complete related habits
                        com.coachie.app.service.HabitAutoCompletionService.onBreathingExerciseLogged(
                            uid, 
                            healthLog.durationSeconds
                        )
                    }
                    is HealthLog.WeightLog -> {
                        // Mark Today's Focus task as completed
                        markTodaysFocusTaskCompleted(uid, date, com.coachie.app.data.model.ReminderActionType.LOG_WEIGHT)
                    }
                    else -> {
                        // Other log types don't trigger quest updates
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "⚠️ Failed to update quests/habits (non-critical)", e)
            }
            
            android.util.Log.i("FirebaseRepository", "✅✅✅ SUCCESS: Saved health log for $date (type=${healthLog.type}, entryId=$entryId) ✅✅✅")
            Result.success(entryId)
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            android.util.Log.e("FirebaseRepository", "✗✗✗ FAILED to save health log for $date (type=${healthLog.type}) ✗✗✗", e)
            android.util.Log.e("FirebaseRepository", "  Firestore Error Code: ${e.code}")
            android.util.Log.e("FirebaseRepository", "  Error: ${e.message}")
            android.util.Log.e("FirebaseRepository", "  Exception type: ${e.javaClass.simpleName}")
            
            // Check if it's a permission error
            if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                android.util.Log.e("FirebaseRepository", "  ⚠️ PERMISSION DENIED - Check Firestore security rules!")
            }
            
            e.printStackTrace()
            Result.failure(e)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "✗✗✗ FAILED to save health log for $date (type=${healthLog.type}) ✗✗✗", e)
            android.util.Log.e("FirebaseRepository", "  Error: ${e.message}")
            android.util.Log.e("FirebaseRepository", "  Exception type: ${e.javaClass.simpleName}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get all health log entries for a specific date
     * CRITICAL: Reads from users/{uid}/daily/{date}/entries (primary) or logs/{uid}/daily/{date}/entries (fallback)
     */
    suspend fun getHealthLogs(uid: String, date: String): Result<List<HealthLog>> {
        return try {
            android.util.Log.d("FirebaseRepository", "🔍 getHealthLogs: Querying for date=$date, uid=$uid")
            
            val logs = mutableListOf<HealthLog>()
            
            // Try new path first (users/{uid}/daily/{date}/entries) - matches web app
            try {
                val usersSnapshot = usersCollection.document(uid)
                    .collection("daily")
                    .document(date)
                    .collection("entries")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()

                usersSnapshot.documents.forEach { doc ->
                    parseHealthLogFromDocument(doc)?.let { logs.add(it) }
                }
                
                android.util.Log.d("FirebaseRepository", "📊 getHealthLogs: Found ${logs.size} logs in users/{uid}/daily/$date/entries")
                if (logs.isNotEmpty()) {
                    logs.forEach { log ->
                        android.util.Log.d("FirebaseRepository", "  - ${log.javaClass.simpleName}: id=${getHealthLogId(log)}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "⚠️ getHealthLogs: Error querying users path (may need index), trying without orderBy", e)
                // Try without orderBy if index is missing
                try {
                    val usersSnapshot = usersCollection.document(uid)
                        .collection("daily")
                        .document(date)
                        .collection("entries")
                        .get()
                        .await()
                    
                    usersSnapshot.documents.forEach { doc ->
                        parseHealthLogFromDocument(doc)?.let { logs.add(it) }
                    }
                    android.util.Log.d("FirebaseRepository", "📊 getHealthLogs: Found ${logs.size} logs in users path (without orderBy)")
                } catch (e2: Exception) {
                    android.util.Log.w("FirebaseRepository", "⚠️ getHealthLogs: Error querying users path without orderBy", e2)
                }
            }
            
            // If no logs found in new path, try old path for backward compatibility
            if (logs.isEmpty()) {
                android.util.Log.d("FirebaseRepository", "🔄 getHealthLogs: No logs in users path, trying logs/{uid}/daily/$date/entries")
                try {
                    val logsSnapshot = logsCollection.document(uid)
                        .collection("daily")
                        .document(date)
                        .collection("entries")
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .get()
                        .await()
                    
                    android.util.Log.d("FirebaseRepository", "📊 getHealthLogs: Found ${logsSnapshot.documents.size} documents in logs/{uid}/daily/$date/entries")
                    logsSnapshot.documents.forEach { doc ->
                        android.util.Log.d("FirebaseRepository", "  - Document ID: ${doc.id}, type: ${doc.getString("type")}, entryId: ${doc.getString("entryId")}")
                        parseHealthLogFromDocument(doc)?.let { 
                            logs.add(it)
                            val id = when (it) {
                                is HealthLog.MindfulSession -> it.sessionId
                                else -> (it as? HealthLog.JournalEntry)?.entryId 
                                    ?: (it as? HealthLog.WinEntry)?.entryId
                                    ?: (it as? HealthLog.MealLog)?.entryId
                                    ?: (it as? HealthLog.WorkoutLog)?.entryId
                                    ?: (it as? HealthLog.SleepLog)?.entryId
                                    ?: (it as? HealthLog.WaterLog)?.entryId
                                    ?: (it as? HealthLog.WeightLog)?.entryId
                                    ?: (it as? HealthLog.SupplementLog)?.entryId
                                    ?: (it as? HealthLog.MoodLog)?.entryId
                                    ?: (it as? HealthLog.MeditationLog)?.entryId
                                    ?: (it as? HealthLog.SunshineLog)?.entryId
                                    ?: (it as? HealthLog.MenstrualLog)?.entryId
                                    ?: (it as? HealthLog.BreathingExerciseLog)?.entryId
                                    ?: "unknown"
                            }
                            android.util.Log.d("FirebaseRepository", "    ✅ Parsed: ${it.javaClass.simpleName}, id=$id")
                        } ?: android.util.Log.w("FirebaseRepository", "    ❌ Failed to parse document ${doc.id}")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FirebaseRepository", "⚠️ getHealthLogs: Error querying logs path with orderBy, trying without", e)
                    // Try without orderBy if index is missing
                    try {
                        val logsSnapshot = logsCollection.document(uid)
                            .collection("daily")
                            .document(date)
                            .collection("entries")
                            .get()
                            .await()
                        
                        android.util.Log.d("FirebaseRepository", "📊 getHealthLogs: Found ${logsSnapshot.documents.size} documents in logs path (without orderBy)")
                        logsSnapshot.documents.forEach { doc ->
                            android.util.Log.d("FirebaseRepository", "  - Document ID: ${doc.id}, type: ${doc.getString("type")}, entryId: ${doc.getString("entryId")}")
                            parseHealthLogFromDocument(doc)?.let { 
                                logs.add(it)
                                android.util.Log.d("FirebaseRepository", "    ✅ Parsed: ${it.javaClass.simpleName}, id=${getHealthLogId(it)}")
                            } ?: android.util.Log.w("FirebaseRepository", "    ❌ Failed to parse document ${doc.id}")
                        }
                    } catch (e2: Exception) {
                        android.util.Log.e("FirebaseRepository", "❌ getHealthLogs: Error querying logs path without orderBy", e2)
                    }
                }
            }

            android.util.Log.d("FirebaseRepository", "✅ getHealthLogs: Returning ${logs.size} total logs for date=$date")
            val sleepLogs = logs.filterIsInstance<HealthLog.SleepLog>()
            if (sleepLogs.isNotEmpty()) {
                android.util.Log.d("FirebaseRepository", "💤 Found ${sleepLogs.size} sleep log(s):")
                sleepLogs.forEach { sleep ->
                    val durationHours = (sleep.endTime - sleep.startTime) / (1000.0 * 60.0 * 60.0)
                    android.util.Log.d("FirebaseRepository", "  - Sleep: entryId=${sleep.entryId}, startTime=${sleep.startTime}, endTime=${sleep.endTime}, duration=${String.format("%.1f", durationHours)} hours")
                }
            } else {
                android.util.Log.w("FirebaseRepository", "⚠️ No sleep logs found for date=$date (total logs: ${logs.size})")
            }

            Result.success(logs)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "❌ getHealthLogs: Error querying health logs for date=$date", e)
            Result.failure(e)
        }
    }

    /**
     * Get health logs for multiple recent days
     */
    suspend fun getRecentHealthLogs(uid: String, days: Int = 7): Result<List<Pair<String, List<HealthLog>>>> {
        return try {
            val results = mutableListOf<Pair<String, List<HealthLog>>>()

            // Get dates for the last N days
            val calendar = Calendar.getInstance()
            for (i in 0 until days) {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                val healthLogsResult = getHealthLogs(uid, date)
                if (healthLogsResult.isSuccess) {
                    val logs = healthLogsResult.getOrNull() ?: emptyList()
                    if (logs.isNotEmpty()) {
                        results.add(date to logs)
                    }
                }
                calendar.add(Calendar.DAY_OF_YEAR, -1)
            }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the identifier (entryId or sessionId) from a HealthLog
     */
    private fun getHealthLogId(log: HealthLog): String {
        return when (log) {
            is HealthLog.MindfulSession -> log.sessionId
            is HealthLog.JournalEntry -> log.entryId
            is HealthLog.WinEntry -> log.entryId
            is HealthLog.MealLog -> log.entryId
            is HealthLog.WorkoutLog -> log.entryId
            is HealthLog.SleepLog -> log.entryId
            is HealthLog.WaterLog -> log.entryId
            is HealthLog.WeightLog -> log.entryId
            is HealthLog.SupplementLog -> log.entryId
            is HealthLog.MoodLog -> log.entryId
            is HealthLog.MeditationLog -> log.entryId
            is HealthLog.SunshineLog -> log.entryId
            is HealthLog.MenstrualLog -> log.entryId
            is HealthLog.BreathingExerciseLog -> log.entryId
        }
    }

    /**
     * Parse a Firestore document into a HealthLog
     */
    fun parseHealthLogFromDocument(doc: com.google.firebase.firestore.DocumentSnapshot): HealthLog? {
        val type = doc.getString("type") ?: return null
        return when (type) {
                    HealthLog.MealLog.TYPE -> {
                        val micronutrientsAny = doc.get("micronutrients") as? Map<*, *>
                        val micronutrients = micronutrientsAny?.mapNotNull { (key, value) ->
                            val name = key as? String ?: return@mapNotNull null
                            val amount = when (value) {
                                is Number -> value.toDouble()
                                is String -> value.toDoubleOrNull()
                                else -> null
                            } ?: return@mapNotNull null
                            name to amount
                        }?.toMap() ?: emptyMap()
                        HealthLog.MealLog(
                            entryId = doc.getString("entryId") ?: doc.id,
                            foodName = doc.getString("foodName") ?: "",
                            calories = doc.getLong("calories")?.toInt() ?: 0,
                            protein = doc.getLong("protein")?.toInt() ?: 0,
                            carbs = doc.getLong("carbs")?.toInt() ?: 0,
                            fat = doc.getLong("fat")?.toInt() ?: 0,
                            sugar = doc.getLong("sugar")?.toInt() ?: 0,
                            addedSugar = doc.getLong("addedSugar")?.toInt() ?: 0,
                            micronutrients = micronutrients,
                            photoUrl = doc.getString("photoUrl")?.takeIf { it.isNotBlank() },
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.SupplementLog.TYPE -> {
                        val micronutrientsAny = doc.get("micronutrients") as? Map<*, *>
                        val micronutrients = micronutrientsAny?.mapNotNull { (key, value) ->
                            val name = key as? String ?: return@mapNotNull null
                            val amount = when (value) {
                                is Number -> value.toDouble()
                                is String -> value.toDoubleOrNull()
                                else -> null
                            } ?: return@mapNotNull null
                            name to amount
                        }?.toMap() ?: emptyMap()
                        HealthLog.SupplementLog(
                            entryId = doc.getString("entryId") ?: doc.id,
                            name = doc.getString("name") ?: "Supplement",
                            micronutrients = micronutrients,
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.WorkoutLog.TYPE -> {
                        HealthLog.WorkoutLog(
                            entryId = doc.getString("entryId") ?: doc.id,
                            workoutType = doc.getString("workoutType") ?: "",
                            durationMin = doc.getLong("durationMin")?.toInt() ?: 0,
                            caloriesBurned = doc.getLong("caloriesBurned")?.toInt() ?: 0,
                            intensity = doc.getString("intensity") ?: "",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.SleepLog.TYPE -> {
                        HealthLog.SleepLog(
                            entryId = doc.getString("entryId") ?: doc.id,
                            startTime = doc.getLong("startTime") ?: System.currentTimeMillis(),
                            endTime = doc.getLong("endTime") ?: System.currentTimeMillis(),
                            quality = doc.getLong("quality")?.toInt() ?: 3,
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.WaterLog.TYPE -> {
                        HealthLog.WaterLog(
                            entryId = doc.getString("entryId") ?: doc.id,
                            ml = doc.getLong("ml")?.toInt() ?: 0,
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.MoodLog.TYPE -> {
                        val emotionsList = doc.get("emotions") as? List<*> ?: emptyList<Any>()
                        val emotions = emotionsList.mapNotNull { it as? String }

                        val triggersList = doc.get("triggers") as? List<*> ?: emptyList<Any>()
                        val triggers = triggersList.mapNotNull { it as? String }

                        HealthLog.MoodLog(
                            entryId = doc.getString("entryId") ?: doc.id,
                            level = doc.getLong("level")?.toInt() ?: 3,
                            emotions = emotions,
                            energyLevel = doc.getLong("energyLevel")?.toInt(),
                            sleepQuality = doc.getLong("sleepQuality")?.toInt(),
                            triggers = triggers,
                            stressLevel = doc.getLong("stressLevel")?.toInt(),
                            socialInteraction = doc.getString("socialInteraction"),
                            physicalActivity = doc.getString("physicalActivity"),
                            note = doc.getString("note")?.takeIf { it.isNotBlank() },
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.WeightLog.TYPE -> {
                        HealthLog.WeightLog(
                            entryId = doc.getString("entryId") ?: doc.id,
                            weight = doc.getDouble("weight") ?: 0.0,
                            unit = doc.getString("unit") ?: "lbs",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.SunshineLog.TYPE -> {
                        HealthLog.SunshineLog(
                            entryId = doc.getString("entryId") ?: doc.id,
                            minutes = doc.getLong("minutes")?.toInt() ?: 0,
                            uvIndex = doc.getDouble("uvIndex") ?: 0.0,
                            bodyCoverage = doc.getDouble("bodyCoverage") ?: 0.0,
                            skinType = doc.getString("skinType") ?: "medium",
                            vitaminDIu = doc.getDouble("vitaminDIu") ?: 0.0,
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.MenstrualLog.TYPE -> {
                        val symptomsList = doc.get("symptoms") as? List<*> ?: emptyList<Any>()
                        val symptoms = symptomsList.mapNotNull { it as? String }
                        HealthLog.MenstrualLog(
                            entryId = doc.getString("entryId") ?: doc.id,
                            isPeriodStart = doc.getBoolean("isPeriodStart") ?: false,
                            isPeriodEnd = doc.getBoolean("isPeriodEnd") ?: false,
                            flowIntensity = doc.getString("flowIntensity"),
                            symptoms = symptoms,
                            painLevel = doc.getLong("painLevel")?.toInt(),
                            notes = doc.getString("notes")?.takeIf { it.isNotBlank() },
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.MeditationLog.TYPE -> {
                        HealthLog.MeditationLog(
                            entryId = doc.getString("entryId") ?: doc.id,
                            durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 0,
                            meditationType = doc.getString("meditationType") ?: "guided",
                            moodBefore = doc.getLong("moodBefore")?.toInt(),
                            moodAfter = doc.getLong("moodAfter")?.toInt(),
                            stressBefore = doc.getLong("stressBefore")?.toInt(),
                            stressAfter = doc.getLong("stressAfter")?.toInt(),
                            notes = doc.getString("notes")?.takeIf { it.isNotBlank() },
                            completed = doc.getBoolean("completed") ?: true,
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.MindfulSession.TYPE -> {
                        HealthLog.MindfulSession(
                            sessionId = doc.getString("sessionId") ?: "",
                            title = doc.getString("title") ?: "",
                            transcript = doc.getString("transcript") ?: "",
                            audioUrl = doc.getString("audioUrl"),
                            durationSeconds = doc.getLong("durationSeconds")?.toInt() ?: 0,
                            generatedDate = doc.getString("generatedDate") ?: "",
                            personalizedPrompt = doc.getString("personalizedPrompt") ?: "",
                            isFavorite = doc.getBoolean("isFavorite") ?: false,
                            playedCount = doc.getLong("playedCount")?.toInt() ?: 0,
                            lastPlayedAt = doc.getLong("lastPlayedAt"),
                            notes = doc.getString("notes")?.takeIf { it.isNotBlank() },
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.JournalEntry.TYPE -> {
                        val promptsList = doc.get("prompts") as? List<*> ?: emptyList<Any>()
                        val prompts = promptsList.mapNotNull { it as? String }
                        val conversationList = doc.get("conversation") as? List<*> ?: emptyList<Any>()
                        val conversation = conversationList.mapNotNull { msg ->
                            val msgMap = msg as? Map<*, *> ?: return@mapNotNull null
                            HealthLog.ChatMessage(
                                id = msgMap["id"] as? String ?: "",
                                role = msgMap["role"] as? String ?: "user",
                                content = msgMap["content"] as? String ?: "",
                                timestamp = (msgMap["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                isAudio = msgMap["isAudio"] as? Boolean ?: false,
                                audioUrl = msgMap["audioUrl"] as? String
                            )
                        }
                        val insightsList = doc.get("insights") as? List<*> ?: emptyList<Any>()
                        val insights = insightsList.mapNotNull { it as? String }
                        
                        HealthLog.JournalEntry(
                            entryId = doc.getString("entryId") ?: "",
                            date = doc.getString("date") ?: "",
                            prompts = prompts,
                            conversation = conversation,
                            startedAt = doc.getLong("startedAt") ?: System.currentTimeMillis(),
                            completedAt = doc.getLong("completedAt"),
                            wordCount = doc.getLong("wordCount")?.toInt() ?: 0,
                            mood = doc.getString("mood"),
                            insights = insights,
                            isCompleted = doc.getBoolean("isCompleted") ?: false,
                            notes = doc.getString("notes")?.takeIf { it.isNotBlank() },
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.BreathingExerciseLog.TYPE -> {
                        HealthLog.BreathingExerciseLog(
                            entryId = doc.getString("entryId") ?: doc.id,
                            durationSeconds = doc.getLong("durationSeconds")?.toInt() ?: 0,
                            exerciseType = doc.getString("exerciseType") ?: "box_breathing",
                            stressLevelBefore = doc.getLong("stressLevelBefore")?.toInt(),
                            stressLevelAfter = doc.getLong("stressLevelAfter")?.toInt(),
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    HealthLog.WinEntry.TYPE -> {
                        val tagsList = doc.get("tags") as? List<*> ?: emptyList<Any>()
                        val tags = tagsList.mapNotNull { it as? String }
                        
                        HealthLog.WinEntry(
                            entryId = doc.getString("entryId") ?: "",
                            journalEntryId = doc.getString("journalEntryId") ?: "",
                            date = doc.getString("date") ?: "",
                            tags = tags,
                            moodScore = doc.getLong("moodScore")?.toInt(),
                            win = doc.getString("win"),
                            gratitude = doc.getString("gratitude"),
                            mood = doc.getString("mood"),
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    }
                    else -> null
                }
    }

    /**
     * Get health logs by type for a specific date
     */
    suspend fun getHealthLogsByType(uid: String, date: String, type: String): Result<List<HealthLog>> {
        return try {
            val allLogs = getHealthLogs(uid, date).getOrDefault(emptyList())
            val filtered = allLogs.filter { it.type == type }
            Result.success(filtered)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a health log entry
     */
    suspend fun deleteHealthLog(uid: String, date: String, entryId: String): Result<Unit> {
        return try {
            android.util.Log.d("FirebaseRepository", "=== DELETE HEALTH LOG ===")
            android.util.Log.d("FirebaseRepository", "UID: '$uid'")
            android.util.Log.d("FirebaseRepository", "Date: '$date'")
            android.util.Log.d("FirebaseRepository", "Entry ID: '$entryId'")
            
            val docRef = logsCollection.document(uid)
                .collection("daily")
                .document(date)
                .collection("entries")
                .document(entryId)
            
            android.util.Log.d("FirebaseRepository", "Document path: ${docRef.path}")
            
            docRef.delete().await()
            
            android.util.Log.d("FirebaseRepository", "Successfully deleted entry")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to delete entry", e)
            android.util.Log.e("FirebaseRepository", "Error message: ${e.message}")
            Result.failure(e)
        }
    }

    // ============================================================================
    // SUPPLEMENT OPERATIONS
    // ============================================================================

    suspend fun getSupplements(uid: String): Result<List<Supplement>> {
        return try {
            // Validate uid to prevent Firestore path errors
            if (uid.isBlank()) {
                android.util.Log.e("FirebaseRepository", "Cannot load supplements: uid is blank")
                return Result.failure(IllegalArgumentException("User ID cannot be blank"))
            }
            
            android.util.Log.d("FirebaseRepository", "Loading supplements for user: $uid")
            val collection = supplementsCollection(uid)
            android.util.Log.d("FirebaseRepository", "Collection path: ${collection.path}")
            
            val snapshot = collection
                .orderBy("name")
                .get()
                .await()

            android.util.Log.d("FirebaseRepository", "Found ${snapshot.documents.size} supplement documents")

            val supplements = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data
                    android.util.Log.d("FirebaseRepository", "Document ${doc.id} data: $data")
                    
                    // Manual deserialization to ensure we get all fields
                    val supplement = Supplement(
                        id = doc.id,
                        name = data?.get("name") as? String ?: "",
                        micronutrients = (data?.get("micronutrients") as? Map<*, *>)?.mapKeys { 
                            it.key.toString() 
                        }?.mapValues { 
                            (it.value as? Number)?.toDouble() ?: 0.0 
                        } ?: emptyMap(),
                        labelImagePath = data?.get("labelImagePath") as? String,
                        labelText = data?.get("labelText") as? String,
                        isDaily = data?.get("isDaily") as? Boolean ?: false,
                        createdAt = (data?.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis(),
                        updatedAt = (data?.get("updatedAt") as? Number)?.toLong() ?: System.currentTimeMillis()
                    )
                    android.util.Log.d("FirebaseRepository", "Deserialized supplement: ${supplement.name} (id: ${supplement.id})")
                    supplement
                } catch (e: Exception) {
                    android.util.Log.e("FirebaseRepository", "Error deserializing supplement document ${doc.id}", e)
                    null
                }
            }
            
            android.util.Log.d("FirebaseRepository", "Successfully loaded ${supplements.size} supplements")
            Result.success(supplements)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Error loading supplements for user $uid", e)
            Result.failure(e)
        }
    }

    suspend fun upsertSupplement(uid: String, supplement: Supplement): Result<String> {
        return try {
            val collection = supplementsCollection(uid)
            val now = System.currentTimeMillis()
            val docRef = if (supplement.id.isNotBlank()) {
                collection.document(supplement.id)
            } else {
                collection.document()
            }

            // Create a simple map for Firestore to avoid serialization issues
            val data = hashMapOf(
                "id" to docRef.id,
                "name" to supplement.name,
                "micronutrients" to HashMap(supplement.micronutrients),
                "labelImagePath" to supplement.labelImagePath,
                "labelText" to supplement.labelText,
                "isDaily" to supplement.isDaily,
                "createdAt" to if (supplement.id.isNotBlank()) supplement.createdAt else now,
                "updatedAt" to now
            )

            docRef.set(data).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSupplement(uid: String, supplementId: String): Result<Unit> {
        return try {
            supplementsCollection(uid).document(supplementId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================================
    // MENSTRUAL CYCLE DATA
    // ============================================================================

    private fun menstrualCycleCollection(uid: String): CollectionReference {
        if (uid.isBlank()) {
            throw IllegalArgumentException("User ID cannot be empty for menstrual cycle collection")
        }
        return Firebase.firestore.collection("users").document(uid).collection("menstrualCycle")
    }

    suspend fun saveMenstrualCycleData(uid: String, data: com.coachie.app.data.model.MenstrualCycleData): Result<Unit> {
        return try {
            menstrualCycleCollection(uid).document("data").set(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMenstrualCycleData(uid: String): Result<com.coachie.app.data.model.MenstrualCycleData?> {
        return try {
            val doc = menstrualCycleCollection(uid).document("data").get().await()
            if (doc.exists()) {
                val data = doc.toObject(com.coachie.app.data.model.MenstrualCycleData::class.java)
                Result.success(data)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ============================================================================
    // CIRCLE OPERATIONS
    // ============================================================================

    /**
     * Create a new circle
     */
    suspend fun createCircle(circle: Circle): Result<String> {
        return try {
            val docRef = circlesCollection.add(circle).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to create circle", e)
            Result.failure(e)
        }
    }

    /**
     * Get a circle by ID
     */
    suspend fun getCircle(circleId: String): Result<Circle?> {
        return try {
            val doc = circlesCollection.document(circleId).get().await()
            if (doc.exists()) {
                val circle = doc.toObject(Circle::class.java)?.copy(id = doc.id)
                Result.success(circle)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get circle", e)
            Result.failure(e)
        }
    }

    /**
     * Find circles matching a goal and optional tendency
     */
    suspend fun findMatchingCircles(goal: String, tendency: String? = null, excludeUserId: String? = null): Result<List<Circle>> {
        return try {
            // Query by goal first
            var query: Query = circlesCollection.whereEqualTo("goal", goal)
            
            // Filter by tendency if provided
            if (tendency != null) {
                query = query.whereEqualTo("tendency", tendency)
            }
            
            val snapshot = query.get().await()
            val circles = snapshot.documents.mapNotNull { doc ->
                val circle = doc.toObject(Circle::class.java)?.copy(id = doc.id)
                // Exclude circles that are full or already contain the user
                if (circle != null && !circle.isFull && (excludeUserId == null || !circle.isMember(excludeUserId))) {
                    circle
                } else null
            }
            Result.success(circles)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to find matching circles", e)
            Result.failure(e)
        }
    }

    /**
     * Join a circle - adds user to circle members and updates user's circles list
     */
    /**
     * Add a user to a circle's members list (without updating user's circles field)
     * Used when an inviter adds someone to a circle
     */
    suspend fun addMemberToCircle(circleId: String, userId: String): Result<Unit> {
        return try {
            val circleRef = circlesCollection.document(circleId)
            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            
            android.util.Log.d("FirebaseRepository", "Adding member $userId to circle $circleId (inviter: $currentUserId)")
            
            // First, verify the inviter is a member or creator (outside transaction for better error messages)
            val circleDoc = circlesCollection.document(circleId).get().await()
            if (!circleDoc.exists()) {
                android.util.Log.e("FirebaseRepository", "Circle $circleId not found")
                return Result.failure(Exception("Circle not found"))
            }
            
            val circle = circleDoc.toObject(Circle::class.java)
            if (circle == null) {
                android.util.Log.e("FirebaseRepository", "Failed to parse circle $circleId")
                return Result.failure(Exception("Failed to parse circle"))
            }
            
            // Check if inviter is a member or creator
            val isInviterMember = currentUserId != null && (circle.isMember(currentUserId) || circle.createdBy == currentUserId)
            if (!isInviterMember) {
                android.util.Log.e("FirebaseRepository", "Inviter $currentUserId is not a member or creator of circle $circleId")
                return Result.failure(Exception("You must be a member of the circle to invite others"))
            }
            
            // Check if user is already a member
            if (circle.isMember(userId)) {
                android.util.Log.d("FirebaseRepository", "User $userId is already a member of circle $circleId")
                return Result.success(Unit) // Already a member, nothing to do
            }
            
            // Check if circle is full
            if (circle.isFull) {
                android.util.Log.e("FirebaseRepository", "Circle $circleId is full")
                return Result.failure(Exception("Circle is full"))
            }
            
            // Now perform the transaction to add the member
            db.runTransaction { transaction ->
                // READ circle document FIRST
                val circleDocInTransaction = transaction.get(circleRef)
                if (!circleDocInTransaction.exists()) {
                    throw Exception("Circle not found")
                }
                
                val circleInTransaction = circleDocInTransaction.toObject(Circle::class.java)
                if (circleInTransaction == null) {
                    throw Exception("Failed to parse circle")
                }
                
                // Double-check user is not already a member (race condition protection)
                if (circleInTransaction.isMember(userId)) {
                    android.util.Log.d("FirebaseRepository", "User $userId is already a member (race condition check)")
                    return@runTransaction
                }
                
                // Double-check circle is not full
                if (circleInTransaction.isFull) {
                    throw Exception("Circle is full")
                }
                
                // Add user to circle members
                val updatedMembers = circleInTransaction.members + userId
                android.util.Log.d("FirebaseRepository", "Updating circle $circleId members: ${circleInTransaction.members.size} -> ${updatedMembers.size}")
                transaction.update(circleRef, "members", updatedMembers)
            }.await()
            
            android.util.Log.d("FirebaseRepository", "Successfully added member $userId to circle $circleId")
            Result.success(Unit)
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            android.util.Log.e("FirebaseRepository", "Firestore error adding member to circle: ${e.code} - ${e.message}", e)
            e.printStackTrace()
            Result.failure(e)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to add member to circle: ${e.message}", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun joinCircle(circleId: String, userId: String): Result<Unit> {
        return try {
            val circleRef = circlesCollection.document(circleId)
            val userCirclesRef = usersCollection.document(userId)
            
            // Check subscription limit BEFORE transaction
            val userCirclesDoc = userCirclesRef.get().await()
            val currentCircles = userCirclesDoc.get("circles") as? List<String> ?: emptyList()
            val canJoin = com.coachie.app.data.SubscriptionService.canJoinCircle(userId, currentCircles.size)
            
            if (!canJoin) {
                val maxCircles = com.coachie.app.data.SubscriptionService.getMaxCircles(userId)
                throw Exception("Circle limit reached. Free users can join up to $maxCircles circles. Upgrade to Pro for unlimited circles.")
            }
            
            // Use batch write for atomicity
            // CRITICAL: All reads must happen BEFORE all writes in Firestore transactions
            db.runTransaction { transaction ->
                // READ ALL DOCUMENTS FIRST
                val circleDoc = transaction.get(circleRef)
                if (!circleDoc.exists()) {
                    throw Exception("Circle not found")
                }
                
                val circle = circleDoc.toObject(Circle::class.java)
                if (circle == null) {
                    throw Exception("Failed to parse circle")
                }
                
                // Read user circles document BEFORE any writes (re-read in transaction for consistency)
                val userCirclesDocInTransaction = transaction.get(userCirclesRef)
                val currentCirclesInTransaction = userCirclesDocInTransaction.get("circles") as? List<String> ?: emptyList()
                
                // Double-check subscription limit inside transaction (read subscription directly from Firestore)
                // Note: Can't call suspend functions inside transaction, so we read subscription doc directly
                val userDoc = transaction.get(userCirclesRef)
                val subscriptionData = userDoc.get("subscription") as? Map<*, *>
                val hasProAccess = subscriptionData?.get("hasProAccess") as? Boolean ?: false
                val maxCircles = if (hasProAccess) Int.MAX_VALUE else 3
                val canJoinInTransaction = currentCirclesInTransaction.size < maxCircles
                if (!canJoinInTransaction) {
                    throw Exception("Circle limit reached. Free users can join up to 3 circles. Upgrade to Pro for unlimited circles.")
                }
                
                // NOW DO ALL WRITES (after all reads are complete)
                // Check if user is already a member
                val isAlreadyMember = circle.isMember(userId)
                
                if (!isAlreadyMember) {
                    // Only add to circle members if not already a member
                    if (circle.isFull) {
                        throw Exception("Circle is full")
                    }
                    
                    // Add user to circle members
                    val updatedMembers = circle.members + userId
                    transaction.update(circleRef, "members", updatedMembers)
                }
                
                // Always ensure circle is in user's circles list (even if already a member)
                val updatedCircles = if (currentCirclesInTransaction.contains(circleId)) {
                    currentCirclesInTransaction
                } else {
                    currentCirclesInTransaction + circleId
                }
                transaction.update(userCirclesRef, "circles", updatedCircles)
            }.await()
            
            // After transaction completes, create quest for user if circle has a quest
            val circleDoc = circleRef.get().await()
            val circle = circleDoc.toObject(Circle::class.java)
            if (circle != null && circle.hasQuest && circle.questId != null) {
                try {
                    createQuestFromCircle(circle, userId)
                } catch (questError: Exception) {
                    android.util.Log.w("FirebaseRepository", "Failed to create quest from circle (non-critical)", questError)
                    // Don't fail the circle join if quest creation fails
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to join circle", e)
            Result.failure(e)
        }
    }

    /**
     * Add a circle to user's circles list (without modifying circle members)
     * Used when user creates a circle (they're already in members)
     */
    suspend fun addCircleToUser(userId: String, circleId: String): Result<Unit> {
        return try {
            val userRef = usersCollection.document(userId)
            db.runTransaction { transaction ->
                val userDoc = transaction.get(userRef)
                val currentCircles = userDoc.get("circles") as? List<String> ?: emptyList()
                if (!currentCircles.contains(circleId)) {
                    transaction.update(userRef, "circles", currentCircles + circleId)
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to add circle to user", e)
            Result.failure(e)
        }
    }

    /**
     * Remove circle from user's circles list
     */
    suspend fun removeCircleFromUser(userId: String, circleId: String): Result<Unit> {
        return try {
            val userRef = usersCollection.document(userId)
            db.runTransaction { transaction ->
                val userDoc = transaction.get(userRef)
                val currentCircles = userDoc.get("circles") as? List<String> ?: emptyList()
                val updatedCircles = currentCircles.filter { it != circleId }
                transaction.update(userRef, "circles", updatedCircles)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to remove circle from user", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a circle (only creator can delete)
     * Removes circle from all members and deletes the circle document
     */
    suspend fun deleteCircle(circleId: String, userId: String): Result<Unit> {
        return try {
            // First, verify user is the creator
            val circleResult = getCircle(circleId)
            val circle = circleResult.getOrNull()
            
            if (circle == null) {
                return Result.failure(Exception("Circle not found"))
            }
            
            if (circle.createdBy != userId) {
                return Result.failure(Exception("Only the circle creator can delete the circle"))
            }
            
            // Remove circle from all members' user documents
            val members = circle.members
            members.forEach { memberId ->
                removeCircleFromUser(memberId, circleId).fold(
                    onSuccess = { 
                        android.util.Log.d("FirebaseRepository", "Removed circle from user: $memberId")
                    },
                    onFailure = { error ->
                        android.util.Log.w("FirebaseRepository", "Failed to remove circle from user $memberId: ${error.message}")
                        // Continue with deletion even if some removals fail
                    }
                )
            }
            
            // Delete the circle document (subcollections will be handled by Firestore security rules or manual cleanup)
            circlesCollection.document(circleId).delete().await()
            
            android.util.Log.d("FirebaseRepository", "✅ Successfully deleted circle: $circleId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to delete circle", e)
            Result.failure(e)
        }
    }

    /**
     * Get all circles for a user
     */
    suspend fun getUserCircles(userId: String): Result<List<Circle>> {
        return try {
            android.util.Log.d("FirebaseRepository", "Getting circles for user: $userId")
            val userDoc = usersCollection.document(userId).get().await()
            
            // CRITICAL FIX: Check if user document exists (new users may not have a document yet)
            if (!userDoc.exists()) {
                android.util.Log.d("FirebaseRepository", "User document does not exist for $userId, returning empty circles list")
                return Result.success(emptyList())
            }
            
            // Get circles field - handle both List<String> and null/undefined
            val circlesField = userDoc.get("circles")
            val circleIds = when {
                circlesField == null -> {
                    android.util.Log.d("FirebaseRepository", "Circles field is null for user $userId")
                    emptyList()
                }
                circlesField is List<*> -> {
                    circlesField.filterIsInstance<String>()
                }
                else -> {
                    android.util.Log.w("FirebaseRepository", "Circles field has unexpected type: ${circlesField::class.java.simpleName}")
                    emptyList()
                }
            }
            
            android.util.Log.d("FirebaseRepository", "Found ${circleIds.size} circle IDs for user $userId: $circleIds")
            
            if (circleIds.isEmpty()) {
                android.util.Log.d("FirebaseRepository", "No circle IDs found, returning empty list")
                return Result.success(emptyList())
            }
            
            val circles = circleIds.mapNotNull { circleId ->
                try {
                    if (circleId.isBlank()) {
                        android.util.Log.w("FirebaseRepository", "Skipping blank circle ID")
                        return@mapNotNull null
                    }
                    val circleDoc = circlesCollection.document(circleId).get().await()
                    if (circleDoc.exists()) {
                        val circle = circleDoc.toObject(Circle::class.java)?.copy(id = circleDoc.id)
                        android.util.Log.d("FirebaseRepository", "Loaded circle: ${circle?.name} (id: $circleId)")
                        circle
                    } else {
                        android.util.Log.w("FirebaseRepository", "Circle document $circleId does not exist")
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FirebaseRepository", "Error loading circle $circleId", e)
                    null
                }
            }
            
            android.util.Log.d("FirebaseRepository", "Successfully loaded ${circles.size} circles for user $userId")
            Result.success(circles)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get user circles for $userId", e)
            e.printStackTrace()
            // Return empty list instead of failure for new users
            Result.success(emptyList())
        }
    }

    /**
     * Get wins for a circle
     */
    suspend fun getCircleWins(circleId: String, limit: Int = 50): Result<List<CircleWin>> {
        return try {
            val querySnapshot = circlesCollection
                .document(circleId)
                .collection("wins")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val wins = querySnapshot.documents.mapNotNull { document ->
                document.toObject(CircleWin::class.java)?.copy(id = document.id)
            }

            Result.success(wins)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get circle wins for circle $circleId", e)
            Result.failure(e)
        }
    }

    /**
     * Get the most recent win from all user's circles (for Win of the Day)
     */
    suspend fun getMostRecentCircleWin(userId: String): Result<CircleWin?> {
        return try {
            // Get user's circles
            val circlesResult = getUserCircles(userId)
            val circles = circlesResult.getOrNull() ?: emptyList()

            if (circles.isEmpty()) {
                return Result.success(null)
            }

            // Get the most recent win from all circles
            var mostRecentWin: CircleWin? = null
            var mostRecentTimestamp: Long = 0

            for (circle in circles) {
                if (circle.id.isBlank()) continue
                
                val winsResult = getCircleWins(circle.id, limit = 1) // Just get the most recent
                val wins = winsResult.getOrNull() ?: emptyList()
                
                wins.firstOrNull()?.let { win ->
                    val timestamp = win.timestamp?.time ?: 0
                    if (timestamp > mostRecentTimestamp) {
                        mostRecentTimestamp = timestamp
                        mostRecentWin = win
                    }
                }
            }

            Result.success(mostRecentWin)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get most recent circle win", e)
            Result.failure(e)
        }
    }

    /**
     * Get the Win of the Day - the win for TODAY specifically
     * This should only change once per day, not every time the app opens
     * CRITICAL: Returns the win for TODAY's date, not just the most recent win
     * CRITICAL SECURITY: Only returns wins from after the user's account creation date (startDate)
     */
    suspend fun getWinOfTheDay(userId: String): Result<HealthLog.WinEntry?> {
        return try {
            // CRITICAL SECURITY: Get user's account creation date to prevent showing wins from before account creation
            val profileResult = getUserProfile(userId)
            val profile = profileResult.getOrNull()
            
            if (profile == null) {
                android.util.Log.w("FirebaseRepository", "No profile found for user $userId - returning no wins")
                return Result.success(null)
            }
            
            val accountStartTimestamp = if (profile.startDate != null && profile.startDate > 0) {
                profile.startDate
            } else {
                // If no startDate, this is a critical error - account should always have startDate
                android.util.Log.e("FirebaseRepository", "CRITICAL: User $userId has no startDate! This should never happen.")
                // Use current time as fallback to prevent showing any historical data
                System.currentTimeMillis()
            }
            
            val accountStartDate = java.time.Instant.ofEpochMilli(accountStartTimestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            
            // CRITICAL: Get win for TODAY specifically, not just the most recent win
            val today = LocalDate.now()
            val todayString = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            
            android.util.Log.d("FirebaseRepository", "Getting Win of the Day for TODAY: $todayString")
            
            // If today is before account creation, return null
            if (today.isBefore(accountStartDate)) {
                android.util.Log.w("FirebaseRepository", "Today ($todayString) is before account creation ($accountStartDate) - returning no win")
                return Result.success(null)
            }
            
            // Get wins for TODAY only
            val winsResult = getHealthLogsByType(userId, todayString, HealthLog.WinEntry.TYPE)
            val wins = winsResult.getOrNull()?.filterIsInstance<HealthLog.WinEntry>() ?: emptyList()
            
            var winOfTheDay: HealthLog.WinEntry? = null
            var mostRecentTimestamp: Long = 0
            var winsBeforeAccountCreation = 0
            
            // Find the most recent achievement win from TODAY
            wins.forEach { win ->
                if (win.timestamp < accountStartTimestamp) {
                    winsBeforeAccountCreation++
                    android.util.Log.e("FirebaseRepository", "SECURITY ALERT: Found win from BEFORE account creation! Win timestamp: ${win.timestamp}, Account start: $accountStartTimestamp, Win: ${win.win}")
                    // DO NOT include this win - it's from before account creation
                    return@forEach
                }
                
                val isAchievementWin = (win.journalEntryId.isBlank() || win.journalEntryId == "achievement_win") ||
                    (win.tags?.contains("achievement") == true)
                
                if (isAchievementWin && win.timestamp >= accountStartTimestamp) {
                    // Verify the win is actually from today (check date from timestamp)
                    val winDate = java.time.Instant.ofEpochMilli(win.timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    
                    if (winDate == today && win.timestamp > mostRecentTimestamp) {
                        mostRecentTimestamp = win.timestamp
                        winOfTheDay = win
                    }
                }
            }
            
            if (winsBeforeAccountCreation > 0) {
                android.util.Log.e("FirebaseRepository", "CRITICAL SECURITY ISSUE: Found $winsBeforeAccountCreation wins from before account creation for user $userId. This indicates data isolation failure!")
            }
            
            val finalWinOfTheDay = winOfTheDay // Local immutable variable to avoid smart cast issues
            if (finalWinOfTheDay == null) {
                android.util.Log.d("FirebaseRepository", "No win found for today ($todayString)")
            } else {
                android.util.Log.d("FirebaseRepository", "✅ Found Win of the Day for today: ${finalWinOfTheDay.win} (timestamp: ${finalWinOfTheDay.timestamp})")
            }
            
            Result.success(finalWinOfTheDay)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get win of the day", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get the most recent WinEntry from user's achievement-based wins only
     * This filters out journal-extracted wins and only returns achievement wins
     * (wins generated by AchievementWinService based on actual logged activities)
     * CRITICAL SECURITY: Only returns wins from after the user's account creation date (startDate)
     * If wins exist before account creation, this indicates a data isolation bug
     * 
     * NOTE: This is kept for backward compatibility, but getWinOfTheDay() should be used for the dashboard
     */
    suspend fun getMostRecentWinEntry(userId: String): Result<HealthLog.WinEntry?> {
        return try {
            // CRITICAL SECURITY: Get user's account creation date to prevent showing wins from before account creation
            val profileResult = getUserProfile(userId)
            val profile = profileResult.getOrNull()
            
            if (profile == null) {
                android.util.Log.w("FirebaseRepository", "No profile found for user $userId - returning no wins")
                return Result.success(null)
            }
            
            val accountStartTimestamp = if (profile.startDate != null && profile.startDate > 0) {
                profile.startDate
            } else {
                // If no startDate, this is a critical error - account should always have startDate
                android.util.Log.e("FirebaseRepository", "CRITICAL: User $userId has no startDate! This should never happen.")
                // Use current time as fallback to prevent showing any historical data
                System.currentTimeMillis()
            }
            
            val accountStartDate = java.time.Instant.ofEpochMilli(accountStartTimestamp)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            
            android.util.Log.d("FirebaseRepository", "Account start date: ${accountStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE)} (timestamp: $accountStartTimestamp) - will ONLY show wins from this date forward")
            
            // OPTIMIZED: Query backwards from today and stop as soon as we find a win
            // Only check last 7 days (most recent wins are likely recent) - much faster than 30 days
            val endDate = LocalDate.now()
            val maxDaysToCheck = 7 // Reduced from 30 to 7 for faster loading
            val queryStartDate = maxOf(accountStartDate, endDate.minusDays(maxDaysToCheck.toLong()))
            
            var mostRecentWin: HealthLog.WinEntry? = null
            var mostRecentTimestamp: Long = 0
            var winsBeforeAccountCreation = 0
            
            // Query BACKWARDS from today (most recent first) and stop when we find a win
            var currentDate = endDate
            while (!currentDate.isBefore(queryStartDate)) {
                val dateString = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                
                val winsResult = getHealthLogsByType(userId, dateString, HealthLog.WinEntry.TYPE)
                val wins = winsResult.getOrNull()?.filterIsInstance<HealthLog.WinEntry>() ?: emptyList()
                
                // Only include achievement-based wins (empty journalEntryId means it's from AchievementWinService)
                // Also check for "achievement" tag as a fallback
                // CRITICAL SECURITY: Verify the win's timestamp is AFTER account creation
                wins.forEach { win ->
                    // Check if win is from before account creation (SECURITY ISSUE if true)
                    if (win.timestamp < accountStartTimestamp) {
                        winsBeforeAccountCreation++
                        android.util.Log.e("FirebaseRepository", "SECURITY ALERT: Found win from BEFORE account creation! Win timestamp: ${win.timestamp}, Account start: $accountStartTimestamp, Win: ${win.win}")
                        // DO NOT include this win - it's from before account creation
                        return@forEach
                    }
                    
                    val isAchievementWin = (win.journalEntryId.isBlank() || win.journalEntryId == "achievement_win") ||
                        (win.tags?.contains("achievement") == true)
                    
                    if (isAchievementWin && win.timestamp >= accountStartTimestamp) {
                        if (win.timestamp > mostRecentTimestamp) {
                            mostRecentTimestamp = win.timestamp
                            mostRecentWin = win
                        }
                    }
                }
                
                // OPTIMIZATION: If we found a win from today or yesterday, stop searching (we have the most recent)
                if (mostRecentWin != null && (currentDate == endDate || currentDate == endDate.minusDays(1))) {
                    android.util.Log.d("FirebaseRepository", "Found win on recent date ($dateString), stopping search early")
                    break
                }
                
                currentDate = currentDate.minusDays(1)
            }
            
            if (winsBeforeAccountCreation > 0) {
                android.util.Log.e("FirebaseRepository", "CRITICAL SECURITY ISSUE: Found $winsBeforeAccountCreation wins from before account creation for user $userId. This indicates data isolation failure!")
            }
            
            val finalWin = mostRecentWin // Local variable to avoid smart cast issues
            if (finalWin == null) {
                android.util.Log.d("FirebaseRepository", "No wins found after account creation date ${accountStartDate.format(DateTimeFormatter.ISO_LOCAL_DATE)}")
            } else {
                val winDate = java.time.Instant.ofEpochMilli(finalWin.timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                android.util.Log.d("FirebaseRepository", "Found most recent win: ${finalWin.win} (timestamp: ${finalWin.timestamp}, date: $winDate)")
            }
            
            Result.success(mostRecentWin)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get most recent win entry", e)
            Result.failure(e)
        }
    }

    /**
     * Submit a check-in for a circle
     */
    suspend fun submitCheckIn(circleId: String, date: String, userId: String, checkIn: CircleCheckIn): Result<Unit> {
        return try {
            val checkInRef = circleCheckInsCollection(circleId, date).document(userId)
            checkInRef.set(checkIn.copy(uid = userId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to submit check-in", e)
            Result.failure(e)
        }
    }

    // Circle Posts methods
    suspend fun createCirclePost(circleId: String, post: CirclePost): Result<String> {
        return try {
            val docRef = db.collection("circles")
                .document(circleId)
                .collection("posts")
                .document()

            val postWithId = post.copy(id = docRef.id)
            docRef.set(postWithId).await()

            Result.success(docRef.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to create circle post", e)
            Result.failure(e)
        }
    }

    /**
     * Check if user has any circle interactions today (post, like, comment, or check-in)
     * Returns true if user has interacted with any circle today
     */
    suspend fun hasCircleInteractionToday(userId: String, date: String = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))): Result<Boolean> {
        return try {
            val circlesResult = getUserCircles(userId)
            val circles = circlesResult.getOrNull() ?: emptyList()
            
            if (circles.isEmpty()) {
                return Result.success(false)
            }
            
            // Check for check-ins today
            for (circle in circles) {
                val checkInResult = getUserCheckIn(circle.id, date, userId)
                if (checkInResult.isSuccess && checkInResult.getOrNull() != null) {
                    return Result.success(true)
                }
            }
            
            // Check for posts created today by user
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.time
            
            val todayEnd = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 23)
                set(java.util.Calendar.MINUTE, 59)
                set(java.util.Calendar.SECOND, 59)
                set(java.util.Calendar.MILLISECOND, 999)
            }.time
            
            for (circle in circles) {
                val postsResult = getCirclePosts(circle.id)
                val posts = postsResult.getOrNull() ?: emptyList()
                
                // Check if user created a post today
                val hasPostToday = posts.any { post ->
                    post.authorId == userId && post.createdAt?.let { 
                        it.time >= todayStart.time && it.time <= todayEnd.time 
                    } ?: false
                }
                if (hasPostToday) {
                    return Result.success(true)
                }
                
                // Check if user liked any post today (check if any post has user in likes and was created today)
                val hasLikeToday = posts.any { post ->
                    post.likes.contains(userId) && post.createdAt?.let {
                        it.time >= todayStart.time && it.time <= todayEnd.time
                    } ?: false
                }
                if (hasLikeToday) {
                    return Result.success(true)
                }
                
                // Check for comments today (limit to first 10 posts to avoid too many queries)
                for (post in posts.take(10)) {
                    val commentsResult = getPostComments(circle.id, post.id)
                    val comments = commentsResult.getOrNull() ?: emptyList()
                    val hasCommentToday = comments.any { comment ->
                        comment.authorId == userId && comment.createdAt?.let {
                            it.time >= todayStart.time && it.time <= todayEnd.time
                        } ?: false
                    }
                    if (hasCommentToday) {
                        return Result.success(true)
                    }
                }
            }
            
            Result.success(false)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Error checking circle interactions", e)
            Result.failure(e)
        }
    }

    suspend fun getCirclePosts(circleId: String): Result<List<CirclePost>> {
        return try {
            val snapshot = db.collection("circles")
                .document(circleId)
                .collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val posts = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CirclePost::class.java)?.copy(id = doc.id)
            }

            Result.success(posts)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get circle posts", e)
            Result.failure(e)
        }
    }

    suspend fun likeCirclePost(circleId: String, postId: String, userId: String): Result<Unit> {
        return try {
            // First verify user is a member of the circle
            val circleResult = getCircle(circleId)
            val circle = circleResult.getOrNull()
            if (circle == null) {
                return Result.failure(Exception("Circle not found"))
            }
            if (!circle.members.contains(userId)) {
                return Result.failure(Exception("You are not a member of this circle. Please join the circle first."))
            }
            
            val postRef = db.collection("circles")
                .document(circleId)
                .collection("posts")
                .document(postId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                if (!snapshot.exists()) {
                    throw Exception("Post not found")
                }
                val post = snapshot.toObject(CirclePost::class.java)
                if (post != null) {
                    val updatedLikes = if (post.likes.contains(userId)) {
                        post.likes - userId
                    } else {
                        post.likes + userId
                    }
                    // Only update the likes field to ensure rule compliance
                    transaction.update(postRef, "likes", updatedLikes)
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MESSAGING_CRASH", "❌ FAILED TO LIKE POST", e)
            android.util.Log.e("MESSAGING_CRASH", "CircleId: $circleId | PostId: $postId | UserId: $userId", e)
            
            if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                android.util.Log.e("MESSAGING_CRASH", "Firestore Error Code: ${e.code}", e)
                android.util.Log.e("MESSAGING_CRASH", "Firestore Error Message: ${e.message}", e)
            }
            
            // Provide user-friendly error messages
            val errorMessage = when {
                e.message?.contains("not a member", ignoreCase = true) == true -> {
                    e.message ?: "You are not a member of this circle."
                }
                e is com.google.firebase.firestore.FirebaseFirestoreException && 
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    "Permission denied. You may not be a member of this circle. Please try refreshing the circle or contact support if the issue persists."
                }
                e.message?.contains("network", ignoreCase = true) == true -> {
                    "Network error. Please check your internet connection and try again."
                }
                else -> {
                    "Failed to like post: ${e.message ?: "Unknown error"}"
                }
            }
            
            Result.failure(Exception(errorMessage, e))
        }
    }

    suspend fun addCommentToPost(circleId: String, postId: String, comment: CircleComment): Result<String> {
        return try {
            // First verify user is a member of the circle
            val circleResult = getCircle(circleId)
            val circle = circleResult.getOrNull()
            if (circle == null) {
                return Result.failure(Exception("Circle not found"))
            }
            if (!circle.members.contains(comment.authorId)) {
                return Result.failure(Exception("You are not a member of this circle. Please join the circle first."))
            }
            
            val commentRef = db.collection("circles")
                .document(circleId)
                .collection("posts")
                .document(postId)
                .collection("comments")
                .document()

            val commentWithId = comment.copy(id = commentRef.id)
            commentRef.set(commentWithId).await()

            // Update comment count on the post
            val postRef = db.collection("circles")
                .document(circleId)
                .collection("posts")
                .document(postId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                if (!snapshot.exists()) {
                    throw Exception("Post not found")
                }
                val post = snapshot.toObject(CirclePost::class.java)
                if (post != null) {
                    // Only update the commentCount field to ensure rule compliance
                    transaction.update(postRef, "commentCount", post.commentCount + 1)
                }
            }.await()

            Result.success(commentRef.id)
        } catch (e: Exception) {
            android.util.Log.e("MESSAGING_CRASH", "❌ FAILED TO ADD COMMENT", e)
            android.util.Log.e("MESSAGING_CRASH", "CircleId: $circleId | PostId: $postId | AuthorId: ${comment.authorId}", e)
            
            if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                android.util.Log.e("MESSAGING_CRASH", "Firestore Error Code: ${e.code}", e)
                android.util.Log.e("MESSAGING_CRASH", "Firestore Error Message: ${e.message}", e)
            }
            
            // Provide user-friendly error messages
            val errorMessage = when {
                e.message?.contains("not a member", ignoreCase = true) == true -> {
                    e.message ?: "You are not a member of this circle."
                }
                e is com.google.firebase.firestore.FirebaseFirestoreException && 
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    "Permission denied. You may not be a member of this circle. Please try refreshing the circle or contact support if the issue persists."
                }
                e.message?.contains("network", ignoreCase = true) == true -> {
                    "Network error. Please check your internet connection and try again."
                }
                else -> {
                    "Failed to add comment: ${e.message ?: "Unknown error"}"
                }
            }
            
            Result.failure(Exception(errorMessage, e))
        }
    }

    suspend fun getPostComments(circleId: String, postId: String): Result<List<CircleComment>> {
        return try {
            val snapshot = db.collection("circles")
                .document(circleId)
                .collection("posts")
                .document(postId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .await()

            val comments = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CircleComment::class.java)?.copy(id = doc.id)
            }

            Result.success(comments)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get post comments", e)
            Result.failure(e)
        }
    }

    // Forum methods
    suspend fun createForum(forum: Forum): Result<String> {
        return try {
            val docRef = db.collection("forums").document()
            val forumWithId = forum.copy(id = docRef.id)
            docRef.set(forumWithId).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to create forum", e)
            Result.failure(e)
        }
    }

    suspend fun getForums(): Result<List<Forum>> {
        return try {
            // First try with isActive filter
            val snapshot = db.collection("forums")
                .whereEqualTo("isActive", true)
                .get()
                .await()

            android.util.Log.d("FirebaseRepository", "getForums query returned ${snapshot.documents.size} documents with isActive=true")

            var forums = snapshot.documents.mapNotNull { doc ->
                val forum = doc.toObject(Forum::class.java)?.copy(id = doc.id)
                if (forum != null) {
                    android.util.Log.d("FirebaseRepository", "Parsed forum: ${forum.title}, isActive=${forum.isActive}")
                }
                forum
            }

            // If no forums found with filter, try without filter (for backwards compatibility)
            if (forums.isEmpty()) {
                android.util.Log.w("FirebaseRepository", "No forums found with isActive=true, trying without filter...")
                val allSnapshot = db.collection("forums")
                    .get()
                    .await()
                
                android.util.Log.d("FirebaseRepository", "getForums (no filter) returned ${allSnapshot.documents.size} total documents")
                
                forums = allSnapshot.documents.mapNotNull { doc ->
                    val data = doc.data
                    android.util.Log.d("FirebaseRepository", "Forum ${doc.id}: title=${data?.get("title")}, isActive=${data?.get("isActive")}")
                    
                    val forum = doc.toObject(Forum::class.java)?.copy(id = doc.id)
                    // Filter to only active forums (default to true if not set)
                    if (forum != null && (forum.isActive || data?.containsKey("isActive") != true)) {
                        forum
                    } else {
                        null
                    }
                }
            }

            val sortedForums = forums.sortedByDescending { it.lastPostAt?.time ?: 0L } // Sort in memory
            android.util.Log.d("FirebaseRepository", "getForums returning ${sortedForums.size} forums: ${sortedForums.map { it.title }}")
            Result.success(sortedForums)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get forums", e)
            Result.failure(e)
        }
    }

    suspend fun getForum(forumId: String): Result<Forum> {
        return try {
            val doc = db.collection("forums")
                .document(forumId)
                .get()
                .await()

            if (doc.exists()) {
                val forum = doc.toObject(Forum::class.java)?.copy(id = doc.id)
                if (forum != null) {
                    Result.success(forum)
                } else {
                    Result.failure(Exception("Failed to parse forum data"))
                }
            } else {
                Result.failure(Exception("Forum not found"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get forum", e)
            Result.failure(e)
        }
    }

    suspend fun createForumPost(forumId: String, post: ForumPost): Result<String> {
        return try {
            android.util.Log.d("FirebaseRepository", "Creating forum post in forum: $forumId, author: ${post.authorId}")
            val docRef = db.collection("forums")
                .document(forumId)
                .collection("posts")
                .document()

            val postWithId = post.copy(id = docRef.id, forumId = forumId)
            android.util.Log.d("FirebaseRepository", "Post data: authorId=${postWithId.authorId}, content=${postWithId.content.take(50)}...")
            docRef.set(postWithId).await()
            android.util.Log.d("FirebaseRepository", "Post created successfully with ID: ${docRef.id}")

            // Update forum post count and last post time
            val forumRef = db.collection("forums").document(forumId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(forumRef)
                val forum = snapshot.toObject(Forum::class.java)
                if (forum != null) {
                    transaction.update(forumRef,
                        mapOf(
                            "postCount" to forum.postCount + 1,
                            "lastPostAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()

            Result.success(docRef.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to create forum post", e)
            if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                android.util.Log.e("FirebaseRepository", "Firestore Error Code: ${e.code}", e)
                android.util.Log.e("FirebaseRepository", "Firestore Error Message: ${e.message}", e)
            }
            Result.failure(e)
        }
    }

    /**
     * Delete a forum post (only by author)
     */
    suspend fun deleteForumPost(forumId: String, postId: String, userId: String): Result<Unit> {
        return try {
            android.util.Log.d("FirebaseRepository", "Deleting forum post: $postId from forum: $forumId by user: $userId")
            
            // First verify the post exists and user is the author
            val postRef = db.collection("forums")
                .document(forumId)
                .collection("posts")
                .document(postId)
            
            val postDoc = postRef.get().await()
            if (!postDoc.exists()) {
                return Result.failure(Exception("Post not found"))
            }
            
            val post = postDoc.toObject(ForumPost::class.java)
            if (post == null) {
                return Result.failure(Exception("Failed to parse post data"))
            }
            
            // Verify user is the author
            if (post.authorId != userId) {
                return Result.failure(Exception("Only the post author can delete this post"))
            }
            
            // Delete the post and update forum post count
            val forumRef = db.collection("forums").document(forumId)
            db.runTransaction { transaction ->
                // Delete the post
                transaction.delete(postRef)
                
                // Update forum post count
                val snapshot = transaction.get(forumRef)
                val forum = snapshot.toObject(Forum::class.java)
                if (forum != null) {
                    val newPostCount = (forum.postCount ?: 0) - 1
                    transaction.update(forumRef, "postCount", newPostCount.coerceAtLeast(0))
                }
            }.await()
            
            android.util.Log.d("FirebaseRepository", "Post deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to delete forum post", e)
            Result.failure(e)
        }
    }

    suspend fun getForumPosts(forumId: String): Result<List<ForumPost>> {
        return try {
            val snapshot = db.collection("forums")
                .document(forumId)
                .collection("posts")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val posts = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ForumPost::class.java)?.copy(id = doc.id)
            }

            Result.success(posts)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get forum posts", e)
            Result.failure(e)
        }
    }

    suspend fun likeForumPost(forumId: String, postId: String, userId: String): Result<Unit> {
        return try {
            val postRef = db.collection("forums")
                .document(forumId)
                .collection("posts")
                .document(postId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                if (!snapshot.exists()) {
                    throw Exception("Post not found")
                }
                val post = snapshot.toObject(ForumPost::class.java)
                if (post != null) {
                    val updatedLikes = if (post.likes.contains(userId)) {
                        post.likes - userId
                    } else {
                        post.likes + userId
                    }
                    // Only update the likes field to ensure rule compliance
                    transaction.update(postRef, "likes", updatedLikes)
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MESSAGING_CRASH", "❌ FAILED TO LIKE FORUM POST", e)
            android.util.Log.e("MESSAGING_CRASH", "ForumId: $forumId | PostId: $postId | UserId: $userId", e)
            
            if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                android.util.Log.e("MESSAGING_CRASH", "Firestore Error Code: ${e.code}", e)
                android.util.Log.e("MESSAGING_CRASH", "Firestore Error Message: ${e.message}", e)
            }
            
            // Provide user-friendly error messages
            val errorMessage = when {
                e is com.google.firebase.firestore.FirebaseFirestoreException && 
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    "Permission denied. Please try again or contact support if the issue persists."
                }
                e.message?.contains("network", ignoreCase = true) == true -> {
                    "Network error. Please check your internet connection and try again."
                }
                else -> {
                    "Failed to like post: ${e.message ?: "Unknown error"}"
                }
            }
            
            Result.failure(Exception(errorMessage, e))
        }
    }

    suspend fun upvoteForumPost(forumId: String, postId: String, userId: String): Result<Unit> {
        return try {
            val postRef = db.collection("forums")
                .document(forumId)
                .collection("posts")
                .document(postId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                if (!snapshot.exists()) {
                    throw Exception("Post not found")
                }
                val post = snapshot.toObject(ForumPost::class.java)
                if (post != null) {
                    val updatedUpvotes = if (post.upvotes.contains(userId)) {
                        post.upvotes - userId // Remove upvote if already upvoted
                    } else {
                        post.upvotes + userId // Add upvote
                    }
                    // Only update the upvotes field to ensure rule compliance
                    transaction.update(postRef, "upvotes", updatedUpvotes)
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("MESSAGING_CRASH", "❌ FAILED TO UPVOTE FORUM POST", e)
            android.util.Log.e("MESSAGING_CRASH", "ForumId: $forumId | PostId: $postId | UserId: $userId", e)
            
            if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                android.util.Log.e("MESSAGING_CRASH", "Firestore Error Code: ${e.code}", e)
                android.util.Log.e("MESSAGING_CRASH", "Firestore Error Message: ${e.message}", e)
            }
            
            // Provide user-friendly error messages
            val errorMessage = when {
                e is com.google.firebase.firestore.FirebaseFirestoreException && 
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    "Permission denied. Please try again or contact support if the issue persists."
                }
                e.message?.contains("network", ignoreCase = true) == true -> {
                    "Network error. Please check your internet connection and try again."
                }
                else -> {
                    "Failed to upvote post: ${e.message ?: "Unknown error"}"
                }
            }
            
            Result.failure(Exception(errorMessage, e))
        }
    }

    suspend fun addCommentToForumPost(forumId: String, postId: String, comment: ForumComment): Result<String> {
        return try {
            val commentRef = db.collection("forums")
                .document(forumId)
                .collection("posts")
                .document(postId)
                .collection("comments")
                .document()

            val commentWithId = comment.copy(id = commentRef.id)
            commentRef.set(commentWithId).await()

            // Update comment count on the post
            val postRef = db.collection("forums")
                .document(forumId)
                .collection("posts")
                .document(postId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                if (!snapshot.exists()) {
                    throw Exception("Post not found")
                }
                val post = snapshot.toObject(ForumPost::class.java)
                if (post != null) {
                    // Only update the commentCount field to ensure rule compliance
                    transaction.update(postRef, "commentCount", post.commentCount + 1)
                }
            }.await()

            Result.success(commentRef.id)
        } catch (e: Exception) {
            android.util.Log.e("MESSAGING_CRASH", "❌ FAILED TO ADD FORUM COMMENT", e)
            android.util.Log.e("MESSAGING_CRASH", "ForumId: $forumId | PostId: $postId | AuthorId: ${comment.authorId}", e)
            
            if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                android.util.Log.e("MESSAGING_CRASH", "Firestore Error Code: ${e.code}", e)
                android.util.Log.e("MESSAGING_CRASH", "Firestore Error Message: ${e.message}", e)
            }
            
            // Provide user-friendly error messages
            val errorMessage = when {
                e is com.google.firebase.firestore.FirebaseFirestoreException && 
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    "Permission denied. Please try again or contact support if the issue persists."
                }
                e.message?.contains("network", ignoreCase = true) == true -> {
                    "Network error. Please check your internet connection and try again."
                }
                else -> {
                    "Failed to add comment: ${e.message ?: "Unknown error"}"
                }
            }
            
            Result.failure(Exception(errorMessage, e))
        }
    }

    suspend fun getForumPostComments(forumId: String, postId: String): Result<List<ForumComment>> {
        return try {
            val snapshot = db.collection("forums")
                .document(forumId)
                .collection("posts")
                .document(postId)
                .collection("comments")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .get()
                .await()

            val comments = snapshot.documents.mapNotNull { doc ->
                doc.toObject(ForumComment::class.java)?.copy(id = doc.id)
            }

            Result.success(comments)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get forum comments", e)
            Result.failure(e)
        }
    }

    /**
     * Post a recipe to the recipes forum
     */
    suspend fun postRecipeToForum(recipe: Recipe): Result<String> {
        return try {
            android.util.Log.d("FirebaseRepository", "🔄 Posting recipe to forum: ${recipe.name}")
            
            // First, save recipe to sharedRecipes collection so it can be accessed via recipeId
            val sharedRecipe = recipe.copy(
                isShared = true,
                sharedWith = emptyList() // Forum posts are public to all users
            )
            
            val recipeData = hashMapOf<String, Any>(
                "id" to sharedRecipe.id,
                "userId" to sharedRecipe.userId,
                "name" to sharedRecipe.name,
                "description" to (sharedRecipe.description ?: ""),
                "servings" to sharedRecipe.servings,
                "ingredients" to sharedRecipe.ingredients.map { ing ->
                    hashMapOf(
                        "name" to ing.name,
                        "quantity" to ing.quantity,
                        "unit" to ing.unit,
                        "calories" to ing.calories,
                        "proteinG" to ing.proteinG,
                        "carbsG" to ing.carbsG,
                        "fatG" to ing.fatG,
                        "sugarG" to ing.sugarG,
                        "micronutrients" to ing.micronutrients
                    )
                },
                "instructions" to (sharedRecipe.instructions ?: emptyList()),
                "totalCalories" to sharedRecipe.totalCalories,
                "totalProteinG" to sharedRecipe.totalProteinG,
                "totalCarbsG" to sharedRecipe.totalCarbsG,
                "totalFatG" to sharedRecipe.totalFatG,
                "totalSugarG" to sharedRecipe.totalSugarG,
                "micronutrients" to sharedRecipe.micronutrients,
                "isShared" to true,
                "sharedWith" to emptyList<String>(),
                "createdAt" to sharedRecipe.createdAt
            )

            db.collection("sharedRecipes")
                .document(sharedRecipe.id)
                .set(recipeData)
                .await()
            
            android.util.Log.d("FirebaseRepository", "✅ Recipe saved to sharedRecipes: ${sharedRecipe.id}")
            
            // Find Recipe Sharing forum in the forums collection
            val forumsResult = getForums()
            val forums = forumsResult.getOrNull() ?: emptyList()
            
            val recipeForum = forums.find { 
                it.title.equals("Recipe Sharing", ignoreCase = true) || 
                it.title.equals("Recipes", ignoreCase = true)
            }
            
            if (recipeForum == null) {
                android.util.Log.e("FirebaseRepository", "Recipe Sharing forum not found in forums collection")
                throw Exception("Recipe Sharing forum not found. Please ensure the forum exists.")
            }
            
            android.util.Log.d("FirebaseRepository", "Found Recipe Sharing forum: ${recipeForum.id}")
            
            // Get user profile for author name
            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ?: throw Exception("User must be authenticated")
            val userProfile = getUserProfile(currentUserId).getOrNull()
            val authorName = userProfile?.name ?: userProfile?.username ?: "Anonymous"
            
            // Format recipe as forum post content (summary)
            val perServing = recipe.getNutritionPerServing()
            val content = buildString {
                append("**${recipe.name}**\n\n")
                
                recipe.description?.let {
                    append("$it\n\n")
                }
                
                append("**Nutrition (per serving):**\n")
                append("• Calories: ${perServing.calories}\n")
                append("• Protein: ${perServing.proteinG}g\n")
                append("• Carbs: ${perServing.carbsG}g\n")
                append("• Fat: ${perServing.fatG}g\n")
                append("• Servings: ${recipe.servings}\n\n")
                append("Tap to view full recipe with ingredients and instructions!")
            }
            
            // Create forum post with recipeId
            val forumPost = ForumPost(
                id = "",
                title = recipe.name,
                content = content,
                authorId = currentUserId,
                authorName = authorName,
                forumId = recipeForum.id,
                forumTitle = recipeForum.title,
                recipeId = sharedRecipe.id, // CRITICAL: Link to the recipe
                likes = emptyList(),
                upvotes = emptyList(),
                commentCount = 0,
                viewCount = 0,
                isPinned = false,
                tags = emptyList(),
                createdAt = java.util.Date(),
                updatedAt = java.util.Date()
            )
            
            val postResult = createForumPost(recipeForum.id, forumPost)
            postResult.fold(
                onSuccess = { postId ->
                    android.util.Log.d("FirebaseRepository", "✅ Posted recipe to forum '${recipeForum.id}': postId=$postId")
                    Result.success(postId)
                },
                onFailure = { error ->
                    android.util.Log.e("FirebaseRepository", "Failed to create forum post", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to post recipe to forum", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get or create a "Recipe Sharing" circle for the user
     */
    suspend fun getOrCreateRecipeSharingCircle(userId: String): Result<String> {
        return try {
            // First, try to find existing Recipe Sharing circle for this user
            val userCirclesResult = getUserCircles(userId)
            val userCircles = userCirclesResult.getOrNull() ?: emptyList()
            
            val existingCircle = userCircles.find { 
                it.name.equals("Recipe Sharing", ignoreCase = true) || 
                it.name.equals("Recipe Sharing Circle", ignoreCase = true)
            }
            
            if (existingCircle != null) {
                android.util.Log.d("FirebaseRepository", "Found existing Recipe Sharing circle: ${existingCircle.id}")
                return Result.success(existingCircle.id)
            }
            
            // Create new Recipe Sharing circle
            android.util.Log.d("FirebaseRepository", "Creating new Recipe Sharing circle for user: $userId")
            val recipeSharingCircle = Circle(
                id = "",
                name = "Recipe Sharing",
                goal = "Share and discover delicious recipes with your circle",
                members = listOf(userId),
                streak = 0,
                createdBy = userId,
                tendency = null,
                maxMembers = 10, // Allow more members for recipe sharing
                createdAt = java.util.Date(),
                updatedAt = java.util.Date()
            )
            
            val createResult = createCircle(recipeSharingCircle)
            createResult.fold(
                onSuccess = { circleId ->
                    // Add circle to user's circles list
                    addCircleToUser(userId, circleId).fold(
                        onSuccess = {
                            android.util.Log.d("FirebaseRepository", "✅ Created Recipe Sharing circle: $circleId")
                            Result.success(circleId)
                        },
                        onFailure = { error ->
                            android.util.Log.w("FirebaseRepository", "Created circle but failed to add to user: ${error.message}")
                            Result.success(circleId) // Still return success since circle was created
                        }
                    )
                },
                onFailure = { error ->
                    android.util.Log.e("FirebaseRepository", "Failed to create Recipe Sharing circle", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Error getting/creating Recipe Sharing circle", e)
            Result.failure(e)
        }
    }

    /**
     * Post a recipe to a circle
     */
    suspend fun postRecipeToCircle(recipe: Recipe, circleId: String, userId: String): Result<String> {
        return try {
            // First, ensure recipe is shared (so it can be accessed via recipeId)
            val sharedRecipe = recipe.copy(
                isShared = true,
                sharedWith = emptyList() // Circle posts are visible to circle members
            )
            
            // Save to sharedRecipes collection if not already there
            val recipeData = hashMapOf<String, Any>(
                "id" to sharedRecipe.id,
                "userId" to sharedRecipe.userId,
                "name" to sharedRecipe.name,
                "description" to (sharedRecipe.description ?: ""),
                "servings" to sharedRecipe.servings,
                "ingredients" to sharedRecipe.ingredients.map { ing ->
                    hashMapOf(
                        "name" to ing.name,
                        "quantity" to ing.quantity,
                        "unit" to ing.unit,
                        "calories" to ing.calories,
                        "proteinG" to ing.proteinG,
                        "carbsG" to ing.carbsG,
                        "fatG" to ing.fatG,
                        "sugarG" to ing.sugarG,
                        "micronutrients" to ing.micronutrients
                    )
                },
                "instructions" to (sharedRecipe.instructions ?: emptyList()),
                "totalCalories" to sharedRecipe.totalCalories,
                "totalProteinG" to sharedRecipe.totalProteinG,
                "totalCarbsG" to sharedRecipe.totalCarbsG,
                "totalFatG" to sharedRecipe.totalFatG,
                "totalSugarG" to sharedRecipe.totalSugarG,
                "micronutrients" to sharedRecipe.micronutrients,
                "isShared" to true,
                "sharedWith" to emptyList<String>(),
                "createdAt" to sharedRecipe.createdAt
            )

            db.collection("sharedRecipes")
                .document(sharedRecipe.id)
                .set(recipeData)
                .await()

            // Get user profile for author name
            val userProfile = getUserProfile(userId).getOrNull()
            val authorName = userProfile?.name ?: userProfile?.username ?: "Anonymous"
            
            // Create circle post with recipe
            val perServing = recipe.getNutritionPerServing()
            val postContent = buildString {
                append("**${recipe.name}**\n\n")
                recipe.description?.let { append("$it\n\n") }
                append("**Nutrition (per serving):**\n")
                append("• Calories: ${perServing.calories}\n")
                append("• Protein: ${perServing.proteinG}g\n")
                append("• Carbs: ${perServing.carbsG}g\n")
                append("• Fat: ${perServing.fatG}g\n")
                append("• Servings: ${recipe.servings}\n\n")
                append("Tap to view full recipe with ingredients and instructions!")
            }
            
            val circlePost = CirclePost(
                id = "",
                authorId = userId,
                authorName = authorName,
                content = postContent,
                imageUrl = null,
                likes = emptyList(),
                commentCount = 0,
                recipeId = sharedRecipe.id,
                createdAt = java.util.Date(),
                updatedAt = java.util.Date()
            )
            
            val postResult = createCirclePost(circleId, circlePost)
            postResult.fold(
                onSuccess = { postId ->
                    android.util.Log.d("FirebaseRepository", "✅ Posted recipe to circle $circleId: postId=$postId")
                    Result.success(postId)
                },
                onFailure = { error ->
                    android.util.Log.e("FirebaseRepository", "Failed to post recipe to circle", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Error posting recipe to circle", e)
            Result.failure(e)
        }
    }

    /**
     * Get check-ins for a circle on a specific date
     */
    suspend fun getCircleCheckIns(circleId: String, date: String): Result<List<CircleCheckIn>> {
        return try {
            val snapshot = circleCheckInsCollection(circleId, date).get().await()
            val checkIns = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CircleCheckIn::class.java)
            }
            Result.success(checkIns)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get circle check-ins", e)
            Result.failure(e)
        }
    }

    /**
     * Get check-in for a specific user in a circle on a date
     */
    suspend fun getUserCheckIn(circleId: String, date: String, userId: String): Result<CircleCheckIn?> {
        return try {
            val doc = circleCheckInsCollection(circleId, date).document(userId).get().await()
            if (doc.exists()) {
                Result.success(doc.toObject(CircleCheckIn::class.java))
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get user check-in", e)
            Result.failure(e)
        }
    }

    // ============================================================================
    // USER SEARCH & FRIENDS OPERATIONS
    // ============================================================================

    /**
     * Search users by username
     */
    suspend fun searchUsersByUsername(query: String, limit: Int = 20): Result<List<PublicUserProfile>> {
        return try {
            if (query.isBlank()) {
                return Result.success(emptyList())
            }
            
            val searchQuery = query.trim()
            val searchLower = searchQuery.lowercase()
            android.util.Log.d("FirebaseRepository", "Searching for users with query: '$searchQuery' (cross-platform search)")
            
            // CRITICAL FIX: Firestore range queries are case-sensitive, but name/username fields
            // are stored with mixed case (web: "John Doe", Android: "john doe").
            // In Firestore: "A" < "a" < "B" < "b", so "John" < "j" < "john"
            // Strategy: Query from uppercase first char to catch both cases, then filter client-side
            // This ensures we find users from ALL platforms (web, Android, iOS)
            
            val searchFirstChar = searchLower.firstOrNull() ?: return Result.success(emptyList())
            val searchFirstCharUpper = searchFirstChar.uppercaseChar()
            val searchFirstCharLower = searchFirstChar.lowercaseChar()
            
            // Query from uppercase first char to catch both "John" and "john"
            // Upper bound: next uppercase letter (e.g., "J" to "K" catches "John", "john", "Jane", etc.)
            val nextCharUpper = if (searchFirstCharUpper == 'Z') 'Z' else (searchFirstCharUpper.code + 1).toChar()
            val lowerBound = searchFirstCharUpper.toString()
            val upperBound = if (nextCharUpper == 'Z') "Z\uf8ff" else "${nextCharUpper}\uf8ff"
            
            android.util.Log.d("FirebaseRepository", "Query range: name >= '$lowerBound' AND name <= '$upperBound'")
            
            // Search by name - query from uppercase to catch mixed-case names
            val nameResults = try {
                usersCollection
                    .whereGreaterThanOrEqualTo("name", lowerBound)
                    .whereLessThanOrEqualTo("name", upperBound)
                    .limit((limit * 5).toLong()) // Get more results to filter client-side
                    .get()
                    .await()
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Name search failed (may need index): ${e.message}")
                // Fallback: query without range (less efficient but works for cross-platform)
                try {
                    android.util.Log.d("FirebaseRepository", "Trying fallback: querying recent users (limited)")
                    usersCollection
                        .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit((limit * 10).toLong()) // Get more to filter
                        .get()
                        .await()
                } catch (e2: Exception) {
                    android.util.Log.e("FirebaseRepository", "Fallback search also failed: ${e2.message}")
                    return Result.failure(Exception("Search failed: ${e.message}"))
                }
            }
            
            // Also search by username (usernames are usually lowercase, so use lowercase bound)
            val usernameResults = try {
                usersCollection
                    .whereGreaterThanOrEqualTo("username", searchFirstCharLower.toString())
                    .whereLessThanOrEqualTo("username", searchLower + "\uf8ff")
                    .limit((limit * 5).toLong())
                    .get()
                    .await()
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Username search failed: ${e.message}")
                null
            }
            
            // Combine and deduplicate results
            val allDocs = mutableSetOf<String>()
            val userMap = mutableMapOf<String, PublicUserProfile>()
            
            // Process name results
            nameResults.documents.forEach { doc ->
                if (!allDocs.contains(doc.id)) {
                    allDocs.add(doc.id)
                    val profile = safeDeserializeUserProfile(doc)
                    profile?.let {
                        val name = (it.name ?: "").trim()
                        if (name.isNotBlank()) {
                            val username = if (it.username.isNullOrBlank()) name else it.username!!
                            userMap[doc.id] = PublicUserProfile(
                                uid = doc.id,
                                username = username,
                                displayName = name,
                                photoUrl = null
                            )
                        }
                    }
                }
            }
            
            // Process username results
            usernameResults?.documents?.forEach { doc ->
                if (!allDocs.contains(doc.id)) {
                    allDocs.add(doc.id)
                    val profile = safeDeserializeUserProfile(doc)
                    profile?.let {
                        val name = (it.name ?: "").trim()
                        val username = (it.username ?: "").trim()
                        if (name.isNotBlank() || username.isNotBlank()) {
                            userMap[doc.id] = PublicUserProfile(
                                uid = doc.id,
                                username = if (username.isNotBlank()) username else name,
                                displayName = name,
                                photoUrl = null
                            )
                        }
                    }
                }
            }
            
            // CRITICAL: Filter case-insensitively to ensure cross-platform users are found
            // This handles mixed-case names from web/Android/iOS users
            val searchWords = searchLower.split(" ").filter { it.isNotBlank() }
            val filteredUsers = userMap.values.filter { user ->
                val nameLower = user.displayName.lowercase()
                val usernameLower = user.username.lowercase()
                
                // Match if any word in the search query is found in the name or username (case-insensitive)
                searchWords.any { word ->
                    nameLower.contains(word) || usernameLower.contains(word)
                }
            }.take(limit)
            
            android.util.Log.d("FirebaseRepository", "Search completed: found ${filteredUsers.size} matching users (from ${userMap.size} candidates)")
            if (filteredUsers.isEmpty() && userMap.isNotEmpty()) {
                android.util.Log.w("FirebaseRepository", "⚠️ No users matched after filtering. Query: '$searchQuery', Candidates: ${userMap.values.take(5).map { it.displayName }}")
            }
            Result.success(filteredUsers)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to search users", e)
            Result.failure(e)
        }
    }

    /**
     * Get public user profile by username
     */
    suspend fun getUserByUsername(username: String): Result<PublicUserProfile?> {
        return try {
            val snapshot = usersCollection
                .whereEqualTo("username", username.lowercase().trim())
                .limit(1)
                .get()
                .await()
            
            if (snapshot.isEmpty) {
                return Result.success(null)
            }
            
            val doc = snapshot.documents.first()
            val profile = safeDeserializeUserProfile(doc)
            val publicProfile = profile?.let {
                PublicUserProfile(
                    uid = doc.id,
                    username = it.username ?: "",
                    displayName = it.name,
                    photoUrl = null
                )
            }
            
            Result.success(publicProfile)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get user by username", e)
            Result.failure(e)
        }
    }

    /**
     * Send a friend request
     */
    suspend fun sendFriendRequest(fromUserId: String, toUserId: String, message: String? = null, skipFriendCheck: Boolean = false): Result<String> {
        return try {
            // Check if already friends first (skip if caller already checked) - do this outside transaction
            if (!skipFriendCheck) {
                val areFriends = isFriend(fromUserId, toUserId)
                if (areFriends.getOrNull() == true) {
                    return Result.failure(Exception("Already friends"))
                }
            }
            
            // Check if request already exists and create new request atomically
            // Note: Firestore queries can't be used in transactions, so we check first
            val existingRequest = friendRequestsCollection
                .whereEqualTo("fromUserId", fromUserId)
                .whereEqualTo("toUserId", toUserId)
                .whereEqualTo("status", "pending")
                .limit(1)
                .get()
                .await()
            
            if (!existingRequest.isEmpty) {
                return Result.failure(Exception("Friend request already sent"))
            }
            
            // Create the friend request
            val request = FriendRequest(
                fromUserId = fromUserId,
                toUserId = toUserId,
                status = "pending",
                message = message
            )
            
            val docRef = friendRequestsCollection.add(request).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to send friend request", e)
            Result.failure(e)
        }
    }

    /**
     * Accept a friend request
     */
    suspend fun acceptFriendRequest(requestId: String, userId: String): Result<Unit> {
        return try {
            val requestRef = friendRequestsCollection.document(requestId)
            val requestDoc = requestRef.get().await()
            
            if (!requestDoc.exists()) {
                return Result.failure(Exception("Friend request not found"))
            }
            
            val request = requestDoc.toObject(FriendRequest::class.java)
            if (request == null || request.toUserId != userId) {
                return Result.failure(Exception("Not authorized to accept this request"))
            }
            
            // Check if message contains a circle invite
            var circleIdToJoin: String? = null
            request.message?.let { message ->
                // Look for pattern like "You've been invited to join the circle: CircleName"
                // Try to find the circle by name
                if (message.contains("invited to join the circle:", ignoreCase = true)) {
                    val circleNameMatch = Regex("circle:\\s*([^\\n]+)", RegexOption.IGNORE_CASE).find(message)
                    circleNameMatch?.let { match ->
                        val circleName = match.groupValues[1].trim()
                        // Find circle by name
                        val circlesQuery = circlesCollection.whereEqualTo("name", circleName).limit(1).get().await()
                        if (!circlesQuery.isEmpty) {
                            circleIdToJoin = circlesQuery.documents.first().id
                        }
                    }
                }
            }
            
            // Check subscription limit BEFORE transaction if joining a circle
            circleIdToJoin?.let { circleId ->
                val userCirclesRef = usersCollection.document(userId)
                val userCirclesDoc = userCirclesRef.get().await()
                val currentCircles = userCirclesDoc.get("circles") as? List<String> ?: emptyList()
                val canJoin = com.coachie.app.data.SubscriptionService.canJoinCircle(userId, currentCircles.size)
                
                if (!canJoin) {
                    val maxCircles = com.coachie.app.data.SubscriptionService.getMaxCircles(userId)
                    throw Exception("Circle limit reached. Free users can join up to $maxCircles circles. Upgrade to Pro for unlimited circles.")
                }
            }
            
            // Use batch write for atomicity
            // CRITICAL: All reads must happen BEFORE all writes in Firestore transactions
            db.runTransaction { transaction ->
                // READ ALL DOCUMENTS FIRST
                val fromUserFriendsRef = usersCollection.document(request.fromUserId).collection("friends").document(request.toUserId)
                val toUserFriendsRef = usersCollection.document(request.toUserId).collection("friends").document(request.fromUserId)
                
                // If circle invite found, read circle and user documents BEFORE any writes
                var circleDoc: com.google.firebase.firestore.DocumentSnapshot? = null
                var userCirclesDoc: com.google.firebase.firestore.DocumentSnapshot? = null
                var circleData: Circle? = null
                var currentCircles: List<String> = emptyList()
                
                circleIdToJoin?.let { circleId ->
                    val circleRef = circlesCollection.document(circleId)
                    circleDoc = transaction.get(circleRef)
                    if (circleDoc?.exists() == true) {
                        val parsedCircle = circleDoc?.toObject(Circle::class.java)
                        if (parsedCircle != null && !parsedCircle.isMember(userId) && !parsedCircle.isFull) {
                            circleData = parsedCircle
                            // Read user circles document BEFORE writes
                            val userCirclesRef = usersCollection.document(userId)
                            userCirclesDoc = transaction.get(userCirclesRef)
                            currentCircles = userCirclesDoc?.get("circles") as? List<String> ?: emptyList()
                        }
                    }
                }
                
                // NOW DO ALL WRITES (after all reads are complete)
                // Update request status
                transaction.update(requestRef, "status", "accepted")
                
                // Add to both users' friends lists
                transaction.set(fromUserFriendsRef, mapOf("addedAt" to com.google.firebase.Timestamp.now()))
                transaction.set(toUserFriendsRef, mapOf("addedAt" to com.google.firebase.Timestamp.now()))
                
                // If circle invite found, join the circle
                circleIdToJoin?.let { circleId ->
                    val circle = circleData
                    if (circle != null && !circle.isMember(userId) && !circle.isFull) {
                        // Double-check subscription limit inside transaction (read subscription directly from Firestore)
                        // Note: Can't call suspend functions inside transaction, so we read subscription doc directly
                        // Use the userCirclesDoc that was already read above
                        val subscriptionData = userCirclesDoc?.get("subscription") as? Map<*, *>
                        val hasProAccess = subscriptionData?.get("hasProAccess") as? Boolean ?: false
                        val maxCircles = if (hasProAccess) Int.MAX_VALUE else 3
                        val canJoinInTransaction = currentCircles.size < maxCircles
                        if (!canJoinInTransaction) {
                            throw Exception("Circle limit reached. Free users can join up to 3 circles. Upgrade to Pro for unlimited circles.")
                        }
                        
                        val updatedMembers = circle.members + userId
                        val circleRef = circlesCollection.document(circleId)
                        transaction.update(circleRef, "members", updatedMembers)
                        
                        // Add circle to user's circles list
                        if (!currentCircles.contains(circleId)) {
                            val userCirclesRef = usersCollection.document(userId)
                            transaction.update(userCirclesRef, "circles", currentCircles + circleId)
                        }
                    }
                }
            }.await()
            
            // After transaction completes, create quest for user if circle has a quest
            circleIdToJoin?.let { circleId ->
                // Fetch circle again after transaction (circleData was only available inside transaction)
                try {
                    val circleDoc = circlesCollection.document(circleId).get().await()
                    val circle = circleDoc.toObject(Circle::class.java)
                    if (circle != null && circle.hasQuest && circle.questId != null) {
                        try {
                            createQuestFromCircle(circle, userId)
                        } catch (questError: Exception) {
                            android.util.Log.w("FirebaseRepository", "Failed to create quest from circle (non-critical)", questError)
                            // Don't fail the friend request acceptance if quest creation fails
                        }
                    } else {
                        // Circle doesn't have a quest, nothing to do
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FirebaseRepository", "Failed to fetch circle for quest creation (non-critical)", e)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to accept friend request", e)
            Result.failure(e)
        }
    }
    
    /**
     * Create a quest for a user based on circle quest details
     * Called when a user joins a circle that has a quest
     */
    private suspend fun createQuestFromCircle(circle: Circle, userId: String) {
        if (!circle.hasQuest || circle.questId == null || circle.questTitle == null) {
            return
        }
        
        try {
            // Check if user already has this quest
            val existingQuests = db
                .collection("users")
                .document(userId)
                .collection("quests")
                .whereEqualTo("status", "active")
                .get()
                .await()
            
            val alreadyHasQuest = existingQuests.documents.any { doc ->
                val questData = doc.data
                questData?.get("title") == circle.questTitle
            }
            
            if (alreadyHasQuest) {
                android.util.Log.d("FirebaseRepository", "User $userId already has quest '${circle.questTitle}', skipping creation")
                return
            }
            
            // Create the quest for the user
            val questData = hashMapOf<String, Any>(
                "id" to (circle.questId ?: ""),
                "title" to (circle.questTitle ?: ""),
                "description" to (circle.questDescription ?: circle.goal),
                "target" to (circle.questTarget ?: 7),
                "current" to 0,
                "type" to (circle.questType ?: "challenge"),
                "icon" to "group", // Use group icon for circle quests
                "color" to "#3B82F6", // Blue for circle/community
                "status" to "active",
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "circleId" to circle.id // Link quest to circle
            )
            
            db
                .collection("users")
                .document(userId)
                .collection("quests")
                .add(questData)
                .await()
            
            android.util.Log.d("FirebaseRepository", "✅ Created quest '${circle.questTitle}' for user $userId from circle ${circle.id}")
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Error creating quest from circle", e)
            throw e
        }
    }

    /**
     * Reject a friend request
     */
    suspend fun rejectFriendRequest(requestId: String, userId: String): Result<Unit> {
        return try {
            val requestRef = friendRequestsCollection.document(requestId)
            val requestDoc = requestRef.get().await()
            
            if (!requestDoc.exists()) {
                return Result.failure(Exception("Friend request not found"))
            }
            
            val request = requestDoc.toObject(FriendRequest::class.java)
            if (request == null || request.toUserId != userId) {
                return Result.failure(Exception("Not authorized to reject this request"))
            }
            
            requestRef.update("status", "rejected").await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to reject friend request", e)
            Result.failure(e)
        }
    }

    /**
     * Get pending friend requests for a user (both incoming and outgoing)
     */
    suspend fun getPendingFriendRequests(userId: String): Result<List<FriendRequest>> {
        return try {
            android.util.Log.d("FirebaseRepository", "🔍 Getting pending friend requests for user: $userId")
            // Get incoming requests (where user is the recipient)
            val incomingSnapshot = friendRequestsCollection
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("status", "pending")
                .get()
                .await()
            
            android.util.Log.d("FirebaseRepository", "📥 Found ${incomingSnapshot.documents.size} incoming friend requests")
            incomingSnapshot.documents.forEach { doc ->
                val request = doc.toObject(FriendRequest::class.java)
                android.util.Log.d("FirebaseRepository", "   Request ${doc.id}: from=${request?.fromUserId}, message=${request?.message?.take(50)}...")
            }
            
            // Get outgoing requests (where user is the sender)
            val outgoingSnapshot = friendRequestsCollection
                .whereEqualTo("fromUserId", userId)
                .whereEqualTo("status", "pending")
                .get()
                .await()
            
            val allRequests = mutableListOf<FriendRequest>()
            
            // Add incoming requests
            incomingSnapshot.documents.forEach { doc ->
                doc.toObject(FriendRequest::class.java)?.copy(id = doc.id)?.let {
                    allRequests.add(it)
                }
            }
            
            // Add outgoing requests
            outgoingSnapshot.documents.forEach { doc ->
                doc.toObject(FriendRequest::class.java)?.copy(id = doc.id)?.let {
                    allRequests.add(it)
                }
            }
            
            // Sort by creation date (most recent first)
            val sortedRequests = allRequests.sortedByDescending { it.createdAt }
            
            Result.success(sortedRequests)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get pending friend requests", e)
            Result.failure(e)
        }
    }

    /**
     * Get friends list for a user
     */
    suspend fun getFriends(userId: String): Result<List<PublicUserProfile>> {
        return try {
            val snapshot = usersCollection.document(userId).collection("friends").get().await()
            val friendIds = snapshot.documents.map { it.id }
            
            if (friendIds.isEmpty()) {
                return Result.success(emptyList())
            }
            
            // Fetch friend profiles in batches (Firestore limit is 10 for 'in' queries)
            val friends = mutableListOf<PublicUserProfile>()
            friendIds.chunked(10).forEach { batch ->
                val batchSnapshot = usersCollection.whereIn(com.google.firebase.firestore.FieldPath.documentId(), batch).get().await()
                batchSnapshot.documents.forEach { doc ->
                    val profile = safeDeserializeUserProfile(doc)
                    profile?.let {
                        friends.add(
                            PublicUserProfile(
                                uid = doc.id,
                                username = it.username ?: "",
                                displayName = it.name,
                                photoUrl = null
                            )
                        )
                    }
                }
            }
            
            Result.success(friends)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get friends", e)
            Result.failure(e)
        }
    }

    /**
     * Check if two users are friends
     */
    suspend fun isFriend(userId1: String, userId2: String): Result<Boolean> {
        return try {
            val doc = usersCollection.document(userId1).collection("friends").document(userId2).get().await()
            Result.success(doc.exists())
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to check if friends", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a friend
     */
    suspend fun removeFriend(userId: String, friendId: String): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val userFriendsRef = usersCollection.document(userId).collection("friends").document(friendId)
                val friendFriendsRef = usersCollection.document(friendId).collection("friends").document(userId)
                
                transaction.delete(userFriendsRef)
                transaction.delete(friendFriendsRef)
            }.await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to remove friend", e)
            Result.failure(e)
        }
    }

    // ============================================================================
    // MESSAGING OPERATIONS
    // ============================================================================

    /**
     * Get or create conversation ID between two users
     */
    private fun getConversationId(userId1: String, userId2: String): String {
        val sorted = listOf(userId1, userId2).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }

    /**
     * Send a message
     */
    suspend fun sendMessage(senderId: String, receiverId: String, content: String): Result<String> {
        return try {
            // Validate inputs
            if (senderId.isBlank() || receiverId.isBlank()) {
                android.util.Log.e("FirebaseRepository", "Invalid senderId or receiverId")
                return Result.failure(Exception("Invalid user IDs"))
            }
            if (content.isBlank()) {
                android.util.Log.e("FirebaseRepository", "Message content is blank")
                return Result.failure(Exception("Message content cannot be empty"))
            }
            
            val conversationId = getConversationId(senderId, receiverId)
            val participants = listOf(senderId, receiverId).sorted()
            
            val message = Message(
                senderId = senderId,
                receiverId = receiverId,
                content = content,
                read = false
            )
            
            db.runTransaction { transaction ->
                // Create/update conversation FIRST to ensure it exists before creating message
                val conversationRef = conversationsCollection.document(conversationId)
                val conversationDoc = transaction.get(conversationRef)
                
                val unreadCount = if (conversationDoc.exists()) {
                    // Firestore returns numbers as Long, so we need to handle both Long and Int
                    val currentUnreadRaw = conversationDoc.get("unreadCount") as? Map<String, Any> ?: emptyMap()
                    val currentUnread = currentUnreadRaw.mapValues { (_, value) ->
                        when (value) {
                            is Long -> value.toInt()
                            is Int -> value
                            is Number -> value.toInt()
                            else -> 0
                        }
                    }
                    val currentCount = currentUnread[receiverId] ?: 0
                    currentUnread.toMutableMap().apply {
                        put(receiverId, currentCount + 1)
                    }
                } else {
                    mapOf(receiverId to 1)
                }
                
                // Create/update conversation first
                transaction.set(
                    conversationRef,
                    Conversation(
                        id = conversationId,
                        participants = participants,
                        lastMessage = content,
                        unreadCount = unreadCount
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                
                // Then create message (conversation now exists)
                val messagesRef = conversationsCollection.document(conversationId).collection("messages")
                val messageRef = messagesRef.document()
                transaction.set(messageRef, message)
            }.await()
            
            Result.success(conversationId)
        } catch (e: Exception) {
            android.util.Log.e("MESSAGING_CRASH", "❌ FAILED TO SEND MESSAGE IN REPOSITORY", e)
            android.util.Log.e("MESSAGING_CRASH", "SenderId: $senderId | ReceiverId: $receiverId", e)
            android.util.Log.e("MESSAGING_CRASH", "Content: ${content.take(50)}...", e)
            
            if (e is com.google.firebase.firestore.FirebaseFirestoreException) {
                android.util.Log.e("MESSAGING_CRASH", "Firestore Error Code: ${e.code}", e)
                android.util.Log.e("MESSAGING_CRASH", "Firestore Error Message: ${e.message}", e)
            }
            
            // Provide user-friendly error messages
            val errorMessage = when {
                e is com.google.firebase.firestore.FirebaseFirestoreException && 
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    "Permission denied. Please check your account permissions."
                }
                e.message?.contains("network", ignoreCase = true) == true -> {
                    "Network error. Please check your internet connection and try again."
                }
                else -> {
                    "Failed to send message: ${e.message ?: "Unknown error"}"
                }
            }
            
            Result.failure(Exception(errorMessage, e))
        }
    }

    /**
     * Get messages for a conversation
     */
    suspend fun getMessages(conversationId: String, limit: Int = 50): Result<List<Message>> {
        return try {
            var usedOrderBy = true
            val snapshot = try {
                conversationsCollection
                    .document(conversationId)
                    .collection("messages")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get()
                    .await()
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    android.util.Log.w(
                        "FirebaseRepository",
                        "Missing Firestore index for messages (createdAt). Falling back to unordered query.",
                        e
                    )
                    usedOrderBy = false
                    // Fallback: get messages without ordering
                    conversationsCollection
                        .document(conversationId)
                        .collection("messages")
                        .limit(limit.toLong())
                        .get()
                        .await()
                } else {
                    throw e
                }
            }
            
            val messages = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Message::class.java)?.copy(id = doc.id)
            }
            
            // Sort manually if we used the fallback query
            val sortedMessages = if (!usedOrderBy) {
                messages.sortedByDescending { it.createdAt?.time ?: 0L }
            } else {
                messages
            }.reversed() // Reverse to get chronological order
            
            Result.success(sortedMessages)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get messages", e)
            
            // Provide user-friendly error messages
            val errorMessage = when {
                e is com.google.firebase.firestore.FirebaseFirestoreException && 
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    "Permission denied. Please check your account permissions."
                }
                e.message?.contains("index", ignoreCase = true) == true -> {
                    "A database index is required. Please contact support or try again later."
                }
                else -> {
                    "Failed to load messages: ${e.message ?: "Unknown error"}"
                }
            }
            
            Result.failure(Exception(errorMessage, e))
        }
    }

    /**
     * Get all conversations for a user
     */
    suspend fun getConversations(userId: String): Result<List<Conversation>> {
        return try {
            var usedOrderBy = true
            val snapshot = try {
                conversationsCollection
                    .whereArrayContains("participants", userId)
                    .orderBy("lastMessageAt", Query.Direction.DESCENDING)
                    .get()
                    .await()
            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION) {
                    android.util.Log.w(
                        "FirebaseRepository",
                        "Missing Firestore index for conversations (participants + lastMessageAt). Falling back to unordered query.",
                        e
                    )
                    usedOrderBy = false
                    // Fallback: get conversations without ordering
                    conversationsCollection
                        .whereArrayContains("participants", userId)
                        .get()
                        .await()
                } else {
                    throw e
                }
            }
            
            val conversations = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Conversation::class.java)?.copy(id = doc.id)
            }
            
            // Sort manually if we used the fallback query
            val sortedConversations = if (!usedOrderBy) {
                conversations.sortedByDescending { it.lastMessageAt }
            } else {
                conversations
            }
            
            Result.success(sortedConversations)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get conversations", e)
            
            // Provide user-friendly error messages
            val errorMessage = when {
                e is com.google.firebase.firestore.FirebaseFirestoreException && 
                e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                    "Permission denied. Please check your account permissions."
                }
                e.message?.contains("index", ignoreCase = true) == true -> {
                    "A database index is required. Please contact support or try again later."
                }
                else -> {
                    "Failed to load conversations: ${e.message ?: "Unknown error"}"
                }
            }
            
            Result.failure(Exception(errorMessage, e))
        }
    }

    /**
     * Mark messages as read
     */
    suspend fun markMessagesAsRead(conversationId: String, userId: String): Result<Unit> {
        return try {
            db.runTransaction { transaction ->
                val conversationRef = conversationsCollection.document(conversationId)
                val conversationDoc = transaction.get(conversationRef)
                
                if (conversationDoc.exists()) {
                    // Firestore returns numbers as Long, so we need to handle both Long and Int
                    val unreadCountRaw = conversationDoc.get("unreadCount") as? Map<String, Any> ?: emptyMap()
                    val unreadCount = unreadCountRaw.mapValues { (_, value) ->
                        when (value) {
                            is Long -> value.toInt()
                            is Int -> value
                            is Number -> value.toInt()
                            else -> 0
                        }
                    }
                    val updatedUnread = unreadCount.toMutableMap().apply {
                        put(userId, 0)
                    }
                    transaction.update(conversationRef, "unreadCount", updatedUnread)
                }
                
                // Mark all unread messages as read
                // Note: We can't query inside a transaction, so we'll mark messages as read
                // by updating the conversation's unreadCount, which is already done above
                // Individual message reads will be handled separately if needed
            }.await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to mark messages as read", e)
            Result.failure(e)
        }
    }

    /**
     * Invite user to circle
     */
    suspend fun inviteUserToCircle(circleId: String, inviterId: String, inviteeId: String): Result<Unit> {
        return try {
            // First, check if users are already friends (outside transaction)
            val areFriends = isFriend(inviterId, inviteeId).getOrNull() == true
            
            if (areFriends) {
                // If already friends, directly add them to the circle using addMemberToCircle
                // (We can't use joinCircle because that tries to update the invitee's user document,
                //  which requires the invitee to be authenticated, not the inviter)
                android.util.Log.d("FirebaseRepository", "Users are already friends, directly adding to circle")
                val addResult = addMemberToCircle(circleId, inviteeId)
                if (addResult.isFailure) {
                    android.util.Log.e("FirebaseRepository", "Failed to add member to circle: ${addResult.exceptionOrNull()?.message}")
                    return addResult
                }
                return Result.success(Unit)
            } else {
                // If not friends, send friend request with circle invitation message
                // First verify circle exists and get its name
                val circleDoc = circlesCollection.document(circleId).get().await()
                if (!circleDoc.exists()) {
                    return Result.failure(Exception("Circle not found"))
                }
                
                val circle = circleDoc.toObject(Circle::class.java)
                if (circle == null) {
                    return Result.failure(Exception("Failed to parse circle"))
                }
                
                // Validate inviter is a member
                if (!circle.isMember(inviterId)) {
                    return Result.failure(Exception("You must be a member of the circle to invite others"))
                }
                
                // Validate circle is not full
                if (circle.isFull) {
                    return Result.failure(Exception("Circle is full"))
                }
                
                // Validate invitee is not already a member
                if (circle.isMember(inviteeId)) {
                    return Result.failure(Exception("User is already a member of this circle"))
                }
                
                // Send friend request with circle invitation message
                val message = "You've been invited to join the circle: ${circle.name}\n\nJoin here: coachie://circle_detail/$circleId"
                android.util.Log.d("FirebaseRepository", "📧 Sending circle invite: circle='${circle.name}' (id=$circleId), from=$inviterId, to=$inviteeId")
                val friendRequestResult = sendFriendRequest(inviterId, inviteeId, message, skipFriendCheck = true)
                
                friendRequestResult.fold(
                    onSuccess = { requestId ->
                        android.util.Log.d("FirebaseRepository", "✅ Circle invite sent successfully as friend request: requestId=$requestId")
                        android.util.Log.d("FirebaseRepository", "   Message: $message")
                        Result.success(Unit)
                    },
                    onFailure = { friendRequestError ->
                        android.util.Log.e("FirebaseRepository", "❌ Failed to send friend request for circle invite", friendRequestError)
                        android.util.Log.e("FirebaseRepository", "   Error: ${friendRequestError.message}")
                        android.util.Log.e("FirebaseRepository", "   Circle: ${circle.name} (id=$circleId)")
                        android.util.Log.e("FirebaseRepository", "   From: $inviterId, To: $inviteeId")
                        Result.failure(friendRequestError)
                    }
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to invite user to circle", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get recipes shared with the current user by their friends
     */
    suspend fun getSharedRecipes(userId: String): Result<List<Recipe>> {
        return try {
            android.util.Log.d("FirebaseRepository", "========== GETTING SHARED RECIPES ==========")
            android.util.Log.d("FirebaseRepository", "User ID: $userId")
            
            // Query for recipes where userId is in sharedWith array OR user is the owner
            // We need two queries because Firestore doesn't support OR queries directly
            android.util.Log.d("FirebaseRepository", "Query 1: Recipes where userId is in sharedWith array")
            android.util.Log.d("FirebaseRepository", "Looking for userId: '$userId' in sharedWith array")
            val sharedWithMeSnapshot = try {
                val snapshot = db.collection("sharedRecipes")
                    .whereArrayContains("sharedWith", userId)
                    .get()
                    .await()
                android.util.Log.d("FirebaseRepository", "✅ Query 1 succeeded: Found ${snapshot.documents.size} recipes")
                snapshot
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepository", "❌ ERROR in sharedWith query: ${e.message}", e)
                android.util.Log.e("FirebaseRepository", "Exception type: ${e.javaClass.simpleName}")
                e.printStackTrace()
                // Return empty QuerySnapshot on query error
                null
            }
            
            android.util.Log.d("FirebaseRepository", "Query 2: Recipes where userId equals owner")
            val myRecipesSnapshot = try {
                val snapshot = db.collection("sharedRecipes")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                android.util.Log.d("FirebaseRepository", "✅ Query 2 succeeded: Found ${snapshot.documents.size} recipes")
                snapshot
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepository", "ERROR in userId query: ${e.message}", e)
                android.util.Log.e("FirebaseRepository", "Exception type: ${e.javaClass.simpleName}")
                e.printStackTrace()
                // Return empty QuerySnapshot on query error
                null
            }
            
            // Convert QuerySnapshot to list for counting
            val sharedWithMeList = sharedWithMeSnapshot?.documents ?: emptyList()
            val myRecipesList = myRecipesSnapshot?.documents ?: emptyList()
            
            android.util.Log.d("FirebaseRepository", "Found ${sharedWithMeList.size} recipes shared with me")
            android.util.Log.d("FirebaseRepository", "Found ${myRecipesList.size} recipes I own")
            
            // Log details of each recipe found
            sharedWithMeList.forEach { doc ->
                val data = doc.data
                val sharedWith = data?.get("sharedWith") as? List<*>
                val docUserId = data?.get("userId") as? String
                val sharedWithStrings = sharedWith?.mapNotNull { it as? String } ?: emptyList()
                val containsUserId = sharedWithStrings.contains(userId)
                android.util.Log.d("FirebaseRepository", "  Recipe shared with me: ${doc.id}, name=${data?.get("name")}, userId=$docUserId")
                android.util.Log.d("FirebaseRepository", "    sharedWith array: $sharedWithStrings")
                android.util.Log.d("FirebaseRepository", "    Contains userId '$userId': $containsUserId")
            }
            myRecipesList.forEach { doc ->
                val data = doc.data
                android.util.Log.d("FirebaseRepository", "  Recipe I own: ${doc.id}, name=${data?.get("name")}, userId=${data?.get("userId")}")
            }
            
            // Combine results and remove duplicates
            val allDocs = (sharedWithMeList + myRecipesList).distinctBy { it.id }
            
            android.util.Log.d("FirebaseRepository", "Total unique recipes: ${allDocs.size}")
            
            // ALSO: Try to get ALL recipes in the collection to see what's actually there
            try {
                val allRecipesSnapshot = db.collection("sharedRecipes").get().await()
                android.util.Log.d("FirebaseRepository", "========== DEBUG: ALL RECIPES IN COLLECTION ==========")
                android.util.Log.d("FirebaseRepository", "Total documents in sharedRecipes collection: ${allRecipesSnapshot.documents.size}")
                android.util.Log.d("FirebaseRepository", "Looking for userId: '$userId'")
                allRecipesSnapshot.documents.forEach { doc ->
                    val data = doc.data
                    val sharedWith = data?.get("sharedWith") as? List<*>
                    val docUserId = data?.get("userId") as? String
                    val recipeName = data?.get("name") as? String
                    android.util.Log.d("FirebaseRepository", "  Recipe ${doc.id}: name='$recipeName', userId='$docUserId'")
                    if (sharedWith != null) {
                        val sharedWithStrings = sharedWith.mapNotNull { it as? String }
                        android.util.Log.d("FirebaseRepository", "    sharedWith array (${sharedWith.size} items): $sharedWithStrings")
                        android.util.Log.d("FirebaseRepository", "    Looking for userId='$userId' in sharedWith array")
                        val containsUserId = sharedWithStrings.contains(userId)
                        android.util.Log.d("FirebaseRepository", "    ✅ Contains userId: $containsUserId")
                        if (!containsUserId && userId == docUserId) {
                            android.util.Log.w("FirebaseRepository", "    ⚠️ Recipe owned by user but not in sharedWith array - this recipe won't appear in shared recipes list!")
                        }
                    } else {
                        android.util.Log.w("FirebaseRepository", "    ⚠️ sharedWith is null or not a list")
                    }
                }
                android.util.Log.d("FirebaseRepository", "================================================")
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepository", "ERROR getting all recipes for debug: ${e.message}", e)
            }
            
            // Debug: Log all documents to see what's in the collection
            allDocs.forEach { doc ->
                val data = doc.data
                val sharedWith = data?.get("sharedWith") as? List<*>
                val docUserId = data?.get("userId") as? String
                android.util.Log.d("FirebaseRepository", "Recipe ${doc.id}: name=${data?.get("name")}, userId=$docUserId, sharedWith=$sharedWith")
            }
            
            val recipes = allDocs.mapNotNull { doc ->
                val data = doc.data ?: emptyMap()
                
                // Filter out recipes where user is in hiddenByOwners array
                val hiddenByOwners = (data["hiddenByOwners"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                if (hiddenByOwners.contains(userId)) {
                    android.util.Log.d("FirebaseRepository", "Skipping recipe ${doc.id} - user $userId is in hiddenByOwners")
                    return@mapNotNull null
                }
                
                val recipe = parseRecipeFromMap(data, doc.id)
                if (recipe != null) {
                    android.util.Log.d("FirebaseRepository", "Parsed recipe: ${recipe.name} from user: ${recipe.userId}, sharedWith: ${recipe.sharedWith}")
                } else {
                    android.util.Log.w("FirebaseRepository", "Failed to parse recipe from doc: ${doc.id}, data: ${doc.data}")
                }
                recipe
            }
            
            android.util.Log.d("FirebaseRepository", "Successfully parsed ${recipes.size} recipes")
            android.util.Log.d("FirebaseRepository", "==========================================")
            Result.success(recipes)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "CRITICAL ERROR: Failed to get shared recipes", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get a single recipe by ID from sharedRecipes collection
     */
    suspend fun getSharedRecipe(recipeId: String): Result<Recipe> {
        return try {
            val doc = db.collection("sharedRecipes").document(recipeId).get().await()
            if (doc.exists()) {
                val recipe = parseRecipeFromMap(doc.data ?: emptyMap(), doc.id)
                if (recipe != null) {
                    Result.success(recipe)
                } else {
                    Result.failure(Exception("Failed to parse recipe data"))
                }
            } else {
                Result.failure(Exception("Recipe not found"))
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get shared recipe", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a shared recipe from user's view
     * - If user is not the owner: removes user from sharedWith array
     * - If user is the owner: adds user to hiddenByOwners array
     * The recipe remains in the shared collection for others to see
     */
    suspend fun deleteSharedRecipe(userId: String, recipeId: String): Result<Unit> {
        return try {
            val doc = db.collection("sharedRecipes").document(recipeId).get().await()
            if (!doc.exists()) {
                return Result.failure(Exception("Recipe not found"))
            }
            
            val data = doc.data ?: return Result.failure(Exception("Recipe data is null"))
            val recipeUserId = data["userId"] as? String
            
            val updateMap = hashMapOf<String, Any>()
            
            if (recipeUserId == userId) {
                // User is the owner - add to hiddenByOwners array
                val hiddenByOwners = (data["hiddenByOwners"] as? List<*>)?.mapNotNull { it as? String }?.toMutableList()
                    ?: mutableListOf()
                
                if (!hiddenByOwners.contains(userId)) {
                    hiddenByOwners.add(userId)
                    updateMap["hiddenByOwners"] = hiddenByOwners
                    android.util.Log.d("FirebaseRepository", "Added owner $userId to hiddenByOwners for recipe $recipeId")
                }
            } else {
                // User is not the owner - remove from sharedWith array
                val sharedWith = (data["sharedWith"] as? List<*>)?.mapNotNull { it as? String }?.toMutableList()
                    ?: mutableListOf()
                
                if (sharedWith.contains(userId)) {
                    sharedWith.remove(userId)
                    updateMap["sharedWith"] = sharedWith
                    android.util.Log.d("FirebaseRepository", "Removed user $userId from sharedWith for recipe $recipeId. Remaining: $sharedWith")
                } else {
                    android.util.Log.d("FirebaseRepository", "User $userId was not in sharedWith array for recipe $recipeId")
                }
            }
            
            // Update the document if there are changes
            if (updateMap.isNotEmpty()) {
                db.collection("sharedRecipes").document(recipeId)
                    .update(updateMap)
                    .await()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to remove shared recipe from user's view", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a shared meal from user's view
     * - If user is not the owner: removes user from sharedWith array
     * - If user is the owner: adds user to hiddenByOwners array
     * The meal remains in the shared collection for others to see
     * Checks both sharedMeals and sharedSavedMeals collections
     */
    suspend fun deleteSharedMeal(userId: String, mealId: String): Result<Unit> {
        return try {
            // Try sharedMeals first
            var doc = db.collection("sharedMeals").document(mealId).get().await()
            var collectionName = "sharedMeals"
            
            // If not found, try sharedSavedMeals
            if (!doc.exists()) {
                doc = db.collection("sharedSavedMeals").document(mealId).get().await()
                collectionName = "sharedSavedMeals"
            }
            
            if (!doc.exists()) {
                return Result.failure(Exception("Meal not found"))
            }
            
            val data = doc.data ?: return Result.failure(Exception("Meal data is null"))
            val mealUserId = data["userId"] as? String
            
            val updateMap = hashMapOf<String, Any>()
            
            if (mealUserId == userId) {
                // User is the owner - add to hiddenByOwners array
                val hiddenByOwners = (data["hiddenByOwners"] as? List<*>)?.mapNotNull { it as? String }?.toMutableList()
                    ?: mutableListOf()
                
                if (!hiddenByOwners.contains(userId)) {
                    hiddenByOwners.add(userId)
                    updateMap["hiddenByOwners"] = hiddenByOwners
                    android.util.Log.d("FirebaseRepository", "Added owner $userId to hiddenByOwners for meal $mealId in $collectionName")
                }
            } else {
                // User is not the owner - remove from sharedWith array
                val sharedWith = (data["sharedWith"] as? List<*>)?.mapNotNull { it as? String }?.toMutableList()
                    ?: mutableListOf()
                
                if (sharedWith.contains(userId)) {
                    sharedWith.remove(userId)
                    updateMap["sharedWith"] = sharedWith
                    android.util.Log.d("FirebaseRepository", "Removed user $userId from sharedWith for meal $mealId in $collectionName. Remaining: $sharedWith")
                } else {
                    android.util.Log.d("FirebaseRepository", "User $userId was not in sharedWith array for meal $mealId")
                }
            }
            
            // Update the document if there are changes
            if (updateMap.isNotEmpty()) {
                db.collection(collectionName).document(mealId)
                    .update(updateMap)
                    .await()
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to remove shared meal from user's view", e)
            Result.failure(e)
        }
    }

    /**
     * Get shared meals for a user (from both sharedMeals and sharedSavedMeals collections)
     */
    suspend fun getSharedMeals(userId: String): Result<List<Map<String, Any>>> {
        return try {
            android.util.Log.d("FirebaseRepository", "========== GETTING SHARED MEALS ==========")
            android.util.Log.d("FirebaseRepository", "User ID: $userId")
            
            // Query sharedMeals collection (from MealCaptureViewModel)
            val sharedMeals1 = try {
                val snapshot1 = db.collection("sharedMeals")
                    .whereArrayContains("sharedWith", userId)
                    .get()
                    .await()
                android.util.Log.d("FirebaseRepository", "✅ Found ${snapshot1.documents.size} meals in sharedMeals (sharedWith)")
                snapshot1.documents.map { it.data ?: emptyMap() }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepository", "Error querying sharedMeals (sharedWith)", e)
                emptyList()
            }
            
            val sharedMeals2 = try {
                val snapshot2 = db.collection("sharedMeals")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                android.util.Log.d("FirebaseRepository", "✅ Found ${snapshot2.documents.size} meals in sharedMeals (owner)")
                snapshot2.documents.map { it.data ?: emptyMap() }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepository", "Error querying sharedMeals (owner)", e)
                emptyList()
            }
            
            // Query sharedSavedMeals collection (from SavedMealsViewModel)
            val sharedSavedMeals1 = try {
                val snapshot1 = db.collection("sharedSavedMeals")
                    .whereArrayContains("sharedWith", userId)
                    .get()
                    .await()
                android.util.Log.d("FirebaseRepository", "✅ Found ${snapshot1.documents.size} meals in sharedSavedMeals (sharedWith)")
                snapshot1.documents.map { it.data ?: emptyMap() }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepository", "Error querying sharedSavedMeals (sharedWith)", e)
                emptyList()
            }
            
            val sharedSavedMeals2 = try {
                val snapshot2 = db.collection("sharedSavedMeals")
                    .whereEqualTo("userId", userId)
                    .get()
                    .await()
                android.util.Log.d("FirebaseRepository", "✅ Found ${snapshot2.documents.size} meals in sharedSavedMeals (owner)")
                snapshot2.documents.map { it.data ?: emptyMap() }
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepository", "Error querying sharedSavedMeals (owner)", e)
                emptyList()
            }
            
            // Combine all results and remove duplicates by ID
            val allMeals = (sharedMeals1 + sharedMeals2 + sharedSavedMeals1 + sharedSavedMeals2)
                .distinctBy { it["id"] as? String ?: "" }
                .filter { meal ->
                    // Filter out meals where user is in hiddenByOwners array
                    val hiddenByOwners = (meal["hiddenByOwners"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    if (hiddenByOwners.contains(userId)) {
                        android.util.Log.d("FirebaseRepository", "Skipping meal ${meal["id"]} - user $userId is in hiddenByOwners")
                        false
                    } else {
                        true
                    }
                }
            
            android.util.Log.d("FirebaseRepository", "Total unique shared meals (after filtering hidden): ${allMeals.size}")
            android.util.Log.d("FirebaseRepository", "==========================================")
            
            Result.success(allMeals)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "CRITICAL ERROR: Failed to get shared meals", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Save a recipe to user's personal recipes collection (users/{userId}/recipes/{recipeId})
     * This allows recipes to be retrieved, modified, and shared later
     */
    suspend fun saveRecipe(userId: String, recipe: Recipe): Result<String> {
        return try {
            val recipeData = hashMapOf(
                "id" to recipe.id,
                "userId" to recipe.userId,
                "name" to recipe.name,
                "description" to recipe.description,
                "servings" to recipe.servings,
                "ingredients" to recipe.ingredients.map { ing ->
                    hashMapOf(
                        "name" to ing.name,
                        "quantity" to ing.quantity,
                        "unit" to ing.unit,
                        "calories" to ing.calories,
                        "proteinG" to ing.proteinG,
                        "carbsG" to ing.carbsG,
                        "fatG" to ing.fatG,
                        "sugarG" to ing.sugarG,
                        "micronutrients" to ing.micronutrients
                    )
                },
                "instructions" to (recipe.instructions ?: emptyList()),
                "photoUrl" to recipe.photoUrl,
                "totalCalories" to recipe.totalCalories,
                "totalProteinG" to recipe.totalProteinG,
                "totalCarbsG" to recipe.totalCarbsG,
                "totalFatG" to recipe.totalFatG,
                "totalSugarG" to recipe.totalSugarG,
                "totalAddedSugarG" to recipe.totalAddedSugarG,
                "micronutrients" to recipe.micronutrients,
                "createdAt" to recipe.createdAt,
                "isShared" to recipe.isShared,
                "sharedWith" to recipe.sharedWith
            )

            usersCollection.document(userId)
                .collection("recipes")
                .document(recipe.id)
                .set(recipeData)
                .await()

            android.util.Log.d("FirebaseRepository", "Recipe saved successfully: ${recipe.id}")
            Result.success(recipe.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to save recipe", e)
            Result.failure(e)
        }
    }

    /**
     * Get a recipe by ID from user's personal recipes
     */
    suspend fun getRecipe(userId: String, recipeId: String): Result<Recipe?> {
        return try {
            val doc = usersCollection.document(userId)
                .collection("recipes")
                .document(recipeId)
                .get()
                .await()

            if (doc.exists()) {
                val data = doc.data ?: return Result.success(null)
                val recipe = parseRecipeFromMap(data, doc.id)
                Result.success(recipe)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get recipe", e)
            Result.failure(e)
        }
    }

    /**
     * Get all recipes for a user
     */
    suspend fun getUserRecipes(userId: String): Result<List<Recipe>> {
        return try {
            val snapshot = usersCollection.document(userId)
                .collection("recipes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            val recipes = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                parseRecipeFromMap(data, doc.id)
            }

            Result.success(recipes)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get user recipes", e)
            Result.failure(e)
        }
    }

    /**
     * Get the most recent brief for a user based on current time
     * Briefs are stored as: users/{userId}/briefs/{briefType}_{date}
     * - morning_{date} - stored at 9 AM
     * - afternoon_{date} - stored at 2 PM
     * - evening_{date} - stored at 6 PM
     * 
     * Logic:
     * - Before 9 AM: show yesterday's evening brief (or today's if it exists)
     * - 9 AM - 2 PM: show today's morning brief
     * - 2 PM - 6 PM: show today's afternoon brief
     * - After 6 PM: show today's evening brief
     */
    suspend fun getMostRecentBrief(userId: String): Result<String?> {
        return try {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val timeInMinutes = hour * 60 + minute
            
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            
            val briefsCollection = usersCollection.document(userId).collection("briefs")
            
            // Determine which brief to show based on current time
            val briefType: String
            val date: String
            
            when {
                timeInMinutes < 540 -> { // Before 9 AM
                    // Try yesterday's evening brief first, then today's if it exists
                    briefType = "evening"
                    date = yesterday
                }
                timeInMinutes < 840 -> { // 9 AM - 2 PM
                    briefType = "morning"
                    date = today
                }
                timeInMinutes < 1080 -> { // 2 PM - 6 PM
                    briefType = "afternoon"
                    date = today
                }
                else -> { // After 6 PM
                    briefType = "evening"
                    date = today
                }
            }
            
            // Try to get the determined brief
            val briefDocId = "${briefType}_${date}"
            val briefDoc = briefsCollection.document(briefDocId).get().await()
            
            if (briefDoc.exists()) {
                val brief = briefDoc.getString("brief")
                android.util.Log.d("FirebaseRepository", "Found brief: $briefDocId")
                Result.success(brief)
            } else {
                // If before 9 AM and yesterday's evening brief doesn't exist, try today's evening
                if (timeInMinutes < 540 && briefType == "evening" && date == yesterday) {
                    val todayEveningDocId = "evening_${today}"
                    val todayEveningDoc = briefsCollection.document(todayEveningDocId).get().await()
                    if (todayEveningDoc.exists()) {
                        val brief = todayEveningDoc.getString("brief")
                        android.util.Log.d("FirebaseRepository", "Found fallback brief: $todayEveningDocId")
                        Result.success(brief)
                    } else {
                        android.util.Log.d("FirebaseRepository", "No brief found for $briefDocId or $todayEveningDocId")
                        Result.success(null)
                    }
                } else {
                    android.util.Log.d("FirebaseRepository", "No brief found for $briefDocId")
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get most recent brief", e)
            Result.failure(e)
        }
    }

    /**
     * Update an existing recipe
     */
    suspend fun updateRecipe(userId: String, recipe: Recipe): Result<String> {
        return try {
            val recipeData = hashMapOf(
                "name" to recipe.name,
                "description" to recipe.description,
                "servings" to recipe.servings,
                "ingredients" to recipe.ingredients.map { ing ->
                    hashMapOf(
                        "name" to ing.name,
                        "quantity" to ing.quantity,
                        "unit" to ing.unit,
                        "calories" to ing.calories,
                        "proteinG" to ing.proteinG,
                        "carbsG" to ing.carbsG,
                        "fatG" to ing.fatG,
                        "sugarG" to ing.sugarG,
                        "micronutrients" to ing.micronutrients
                    )
                },
                "instructions" to (recipe.instructions ?: emptyList()),
                "photoUrl" to recipe.photoUrl,
                "totalCalories" to recipe.totalCalories,
                "totalProteinG" to recipe.totalProteinG,
                "totalCarbsG" to recipe.totalCarbsG,
                "totalFatG" to recipe.totalFatG,
                "totalSugarG" to recipe.totalSugarG,
                "totalAddedSugarG" to recipe.totalAddedSugarG,
                "micronutrients" to recipe.micronutrients,
                "isShared" to recipe.isShared,
                "sharedWith" to recipe.sharedWith
            )

            usersCollection.document(userId)
                .collection("recipes")
                .document(recipe.id)
                .update(recipeData as Map<String, Any>)
                .await()

            android.util.Log.d("FirebaseRepository", "Recipe updated successfully: ${recipe.id}")
            Result.success(recipe.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to update recipe", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a recipe from user's personal recipes collection
     */
    suspend fun deleteRecipe(userId: String, recipeId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .collection("recipes")
                .document(recipeId)
                .delete()
                .await()

            android.util.Log.d("FirebaseRepository", "Recipe deleted successfully: $recipeId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to delete recipe", e)
            Result.failure(e)
        }
    }

    /**
     * Save a recipe to saved meals (preserves all macros and micros)
     */
    suspend fun saveRecipeToSavedMeals(userId: String, recipe: Recipe): Result<String> {
        return try {
            val savedMeal = recipe.toSavedMeal(userId)
            saveSavedMeal(savedMeal)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to save recipe to saved meals", e)
            Result.failure(e)
        }
    }

    /**
     * Helper function to parse Recipe from Firestore data
     */
    private fun parseRecipeFromMap(data: Map<String, Any>, docId: String): Recipe? {
        return try {
            val ingredients = (data["ingredients"] as? List<*>)?.mapNotNull { ing ->
                val ingMap = ing as? Map<*, *> ?: return@mapNotNull null
                val micronutrientsMap = (ingMap["micronutrients"] as? Map<*, *>)?.mapNotNull { entry ->
                    val key = entry.key as? String ?: return@mapNotNull null
                    val value = (entry.value as? Number)?.toDouble() ?: 0.0
                    key to value
                }?.toMap() ?: emptyMap()
                
                RecipeIngredient(
                    name = ingMap["name"] as? String ?: "",
                    quantity = (ingMap["quantity"] as? Number)?.toDouble() ?: 0.0,
                    unit = ingMap["unit"] as? String ?: "",
                    calories = (ingMap["calories"] as? Number)?.toInt() ?: 0,
                    proteinG = (ingMap["proteinG"] as? Number)?.toInt() ?: 0,
                    carbsG = (ingMap["carbsG"] as? Number)?.toInt() ?: 0,
                    fatG = (ingMap["fatG"] as? Number)?.toInt() ?: 0,
                    sugarG = (ingMap["sugarG"] as? Number)?.toInt() ?: 0,
                    micronutrients = micronutrientsMap
                )
            } ?: emptyList()

            val instructions = (data["instructions"] as? List<*>)?.mapNotNull {
                it as? String
            } ?: emptyList()

            val micronutrients = (data["micronutrients"] as? Map<*, *>)?.mapNotNull { entry ->
                val key = entry.key as? String ?: return@mapNotNull null
                val value = (entry.value as? Number)?.toDouble() ?: 0.0
                key to value
            }?.toMap() ?: emptyMap()

            val sharedWith = (data["sharedWith"] as? List<*>)?.mapNotNull {
                it as? String
            } ?: emptyList()

            Recipe(
                id = docId,
                userId = data["userId"] as? String ?: "",
                name = data["name"] as? String ?: "",
                description = data["description"] as? String,
                servings = (data["servings"] as? Number)?.toInt() ?: 1,
                ingredients = ingredients,
                instructions = instructions,
                photoUrl = data["photoUrl"] as? String,
                totalCalories = (data["totalCalories"] as? Number)?.toInt() ?: 0,
                totalProteinG = (data["totalProteinG"] as? Number)?.toInt() ?: 0,
                totalCarbsG = (data["totalCarbsG"] as? Number)?.toInt() ?: 0,
                totalFatG = (data["totalFatG"] as? Number)?.toInt() ?: 0,
                totalSugarG = (data["totalSugarG"] as? Number)?.toInt() ?: 0,
                micronutrients = micronutrients,
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                isShared = data["isShared"] as? Boolean ?: false,
                sharedWith = sharedWith
            )
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to parse recipe from map", e)
            null
        }
    }

    // ============================================================================
    // ACCOUNT MANAGEMENT
    // ============================================================================

    /**
     * Clear all user data but keep the account
     * This deletes all health logs, daily logs, habits, recipes, etc. but preserves the user profile
     */
    suspend fun clearUserData(userId: String): Result<Unit> {
        return try {
            // CRITICAL SECURITY: Always use the authenticated user's ID, never trust the parameter
            val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (authenticatedUserId == null) {
                android.util.Log.e("FirebaseRepository", "❌❌❌ CRITICAL SECURITY ERROR: clearUserData called but no authenticated user! ❌❌❌")
                return Result.failure(IllegalStateException("User must be authenticated to clear data"))
            }
            
            // CRITICAL SECURITY: Validate that the userId parameter matches the authenticated user
            if (userId != authenticatedUserId) {
                android.util.Log.e("FirebaseRepository", "❌❌❌ CRITICAL SECURITY ERROR: clearUserData userId mismatch! ❌❌❌")
                android.util.Log.e("FirebaseRepository", "Requested userId: '$userId'")
                android.util.Log.e("FirebaseRepository", "Authenticated userId: '$authenticatedUserId'")
                android.util.Log.e("FirebaseRepository", "ABORTING DATA CLEAR TO PREVENT DELETING WRONG USER'S DATA!")
                return Result.failure(SecurityException("Cannot clear data for a different user. Authenticated user: $authenticatedUserId, Requested: $userId"))
            }
            
            // Use the authenticated user ID (not the parameter) for all operations
            val safeUserId = authenticatedUserId
            android.util.Log.i("FirebaseRepository", "✅ SECURITY CHECK PASSED: Clearing data for authenticated user: $safeUserId")
            android.util.Log.d("FirebaseRepository", "Clearing all data for user: $safeUserId")
            
            // Delete all health logs
            val healthLogsRef = usersCollection.document(safeUserId).collection("healthLogs")
            val healthLogsSnapshot = healthLogsRef.get().await()
            healthLogsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Delete all daily logs
            val dailyLogsRef = usersCollection.document(safeUserId).collection("dailyLogs")
            val dailyLogsSnapshot = dailyLogsRef.get().await()
            dailyLogsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Delete all personal recipes (stored in users/{userId}/recipes)
            val recipesRef = usersCollection.document(safeUserId).collection("recipes")
            val recipesSnapshot = recipesRef.get().await()
            recipesSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            android.util.Log.d("FirebaseRepository", "Deleted ${recipesSnapshot.documents.size} personal recipes")
            
            // Delete all shared recipes created by this user (stored in top-level sharedRecipes collection)
            try {
                val sharedRecipesRef = Firebase.firestore.collection("sharedRecipes")
                val sharedRecipesSnapshot = sharedRecipesRef
                    .whereEqualTo("userId", safeUserId)
                    .get()
                    .await()
                sharedRecipesSnapshot.documents.forEach { doc ->
                    doc.reference.delete().await()
                }
                android.util.Log.d("FirebaseRepository", "Deleted ${sharedRecipesSnapshot.documents.size} shared recipes")
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete shared recipes (may not exist or permission issue)", e)
            }
            
            // Delete all saved meals - CRITICAL: Saved meals are in top-level collection, not subcollection!
            val savedMealsRef = Firebase.firestore.collection("savedMeals")
            val savedMealsSnapshot = savedMealsRef
                .whereEqualTo("userId", safeUserId)
                .get()
                .await()
            savedMealsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            android.util.Log.d("FirebaseRepository", "Deleted ${savedMealsSnapshot.documents.size} saved meals from top-level collection")
            
            // Delete all supplements
            val supplementsRef = supplementsCollection(safeUserId)
            val supplementsSnapshot = supplementsRef.get().await()
            supplementsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Delete all weekly plans
            val weeklyPlansRef = usersCollection.document(safeUserId).collection("weeklyPlans")
            val weeklyPlansSnapshot = weeklyPlansRef.get().await()
            weeklyPlansSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Delete all weekly blueprints (new structure)
            val weeklyBlueprintsRef = usersCollection.document(safeUserId).collection("weeklyBlueprints")
            val weeklyBlueprintsSnapshot = weeklyBlueprintsRef.get().await()
            weeklyBlueprintsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Delete all scores (flow scores)
            val scoresRef = usersCollection.document(safeUserId).collection("scores")
            val scoresSnapshot = scoresRef.get().await()
            scoresSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Delete all streaks
            val streaksRef = usersCollection.document(safeUserId).collection("streaks")
            val streaksSnapshot = streaksRef.get().await()
            streaksSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Delete all badges
            val badgesRef = usersCollection.document(safeUserId).collection("badges")
            val badgesSnapshot = badgesRef.get().await()
            badgesSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Delete all daily logs in new structure (users/{userId}/daily/{date})
            val dailyRef = usersCollection.document(safeUserId).collection("daily")
            val dailySnapshot = dailyRef.get().await()
            dailySnapshot.documents.forEach { dateDoc ->
                // Delete entries subcollection for each date
                val entriesRef = dateDoc.reference.collection("entries")
                val entriesSnapshot = entriesRef.get().await()
                entriesSnapshot.documents.forEach { entryDoc ->
                    entryDoc.reference.delete().await()
                }
                // Delete the date document itself
                dateDoc.reference.delete().await()
            }
            
            // Delete all logs in old structure (logs/{userId}/daily/{date})
            try {
                val logsDailyRef = logsCollection.document(safeUserId).collection("daily")
                val logsDailySnapshot = logsDailyRef.get().await()
                logsDailySnapshot.documents.forEach { dateDoc ->
                    // Delete entries subcollection for each date
                    val entriesRef = dateDoc.reference.collection("entries")
                    val entriesSnapshot = entriesRef.get().await()
                    entriesSnapshot.documents.forEach { entryDoc ->
                        entryDoc.reference.delete().await()
                    }
                    // Delete the date document itself
                    dateDoc.reference.delete().await()
                }
                // Also delete the parent logs/{userId} document if it exists
                val logsUserDoc = logsCollection.document(safeUserId)
                if (logsUserDoc.get().await().exists()) {
                    logsUserDoc.delete().await()
                }
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete logs collection (may not exist)", e)
            }
            
            // Delete all insights
            val insightsRef = usersCollection.document(safeUserId).collection("insights")
            val insightsSnapshot = insightsRef.get().await()
            insightsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Delete all habits (if stored in Firestore)
            val habitsRef = usersCollection.document(safeUserId).collection("habits")
            val habitsSnapshot = habitsRef.get().await()
            habitsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Delete all habit completions
            val habitCompletionsRef = usersCollection.document(safeUserId).collection("habitCompletions")
            val habitCompletionsSnapshot = habitCompletionsRef.get().await()
            habitCompletionsSnapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            
            // Delete all scans (scans are stored at /scans/{userId}/scans/{timestamp})
            // Wrap in try-catch to handle permission errors gracefully
            try {
                val userScansRef = scansCollection.document(safeUserId).collection("scans")
                val scansSnapshot = userScansRef.get().await()
                scansSnapshot.documents.forEach { doc ->
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete scans (may not have permission or no scans exist)", e)
                // Continue with data clearing even if scans deletion fails
            }
            
            // Delete all weight logs
            try {
                val weightLogsRef = usersCollection.document(safeUserId).collection("weight_logs")
                val weightLogsSnapshot = weightLogsRef.get().await()
                weightLogsSnapshot.documents.forEach { doc ->
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete weight logs", e)
            }
            
            // Delete all water logs
            try {
                val waterLogsRef = usersCollection.document(safeUserId).collection("water_logs")
                val waterLogsSnapshot = waterLogsRef.get().await()
                waterLogsSnapshot.documents.forEach { doc ->
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete water logs", e)
            }
            
            // Delete all menstrual cycle data
            try {
                val menstrualCycleRef = usersCollection.document(safeUserId).collection("menstrualCycle")
                val menstrualCycleSnapshot = menstrualCycleRef.get().await()
                menstrualCycleSnapshot.documents.forEach { doc ->
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete menstrual cycle data", e)
            }
            
            // Delete all friends (friends collection)
            try {
                val friendsRef = usersCollection.document(safeUserId).collection("friends")
                val friendsSnapshot = friendsRef.get().await()
                friendsSnapshot.documents.forEach { doc ->
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete friends", e)
            }
            
            // Delete profile subcollection if it exists
            try {
                val profileRef = usersCollection.document(safeUserId).collection("profile")
                val profileSnapshot = profileRef.get().await()
                profileSnapshot.documents.forEach { doc ->
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete profile subcollection", e)
            }
            
            // Reset profile to default state - DELETE ALL DATA FIELDS
            val profileRef = usersCollection.document(safeUserId)
            val profileDoc = profileRef.get().await()
            if (profileDoc.exists()) {
                val data = profileDoc.data ?: emptyMap()
                // Build a map of all fields to delete (except essential account fields)
                val fieldsToDelete = data.keys.filter { key ->
                    // Keep only essential account fields
                    key !in listOf("uid", "email", "name", "username", "createdAt", "isFirstTimeUser", "startDate")
                }.associateWith { com.google.firebase.firestore.FieldValue.delete() }
                
                // CRITICAL: Update startDate FIRST to prevent Google Fit from syncing old data
                // This must be done BEFORE any potential Google Fit sync happens
                val newStartDate = System.currentTimeMillis()
                profileRef.update("startDate", newStartDate).await()
                android.util.Log.d("FirebaseRepository", "Updated startDate to $newStartDate to prevent Google Fit from syncing old data")
                
                // Update with deletions and reset essential fields
                // Clear goalsSet so user goes through FTUE again
                // CRITICAL: Clear Google Fit connection - it must be user-specific, not device-wide
                val updates = mutableMapOf<String, Any>(
                    "startDate" to newStartDate,
                    "isFirstTimeUser" to true, // Mark as first time user to trigger FTUE
                    "goalsSet" to false, // Clear goals so FTUE is triggered
                )
                updates.putAll(fieldsToDelete)
                
                profileRef.update(updates).await()
                android.util.Log.d("FirebaseRepository", "Cleared ${fieldsToDelete.size} fields from user profile")
            } else {
                // Even if profile doesn't exist, create it with new startDate
                val newStartDate = System.currentTimeMillis()
                usersCollection.document(safeUserId).set(
                    mapOf(
                        "uid" to safeUserId,
                        "startDate" to newStartDate,
                        "isFirstTimeUser" to true,
                        "goalsSet" to false
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
                android.util.Log.d("FirebaseRepository", "Created profile with new startDate: $newStartDate")
            }
            
            android.util.Log.i("FirebaseRepository", "✅ Successfully cleared all data for authenticated user: $safeUserId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to clear user data", e)
            Result.failure(e)
        }
    }

    /**
     * Delete user account completely
     * This clears ALL data and deletes the user profile document
     * Note: Firebase Auth account deletion must be done client-side by the authenticated user
     */
    suspend fun deleteUserAccount(userId: String): Result<Unit> {
        return try {
            // CRITICAL SECURITY: Always use the authenticated user's ID, never trust the parameter
            val authenticatedUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (authenticatedUserId == null) {
                android.util.Log.e("FirebaseRepository", "❌❌❌ CRITICAL SECURITY ERROR: deleteUserAccount called but no authenticated user! ❌❌❌")
                return Result.failure(IllegalStateException("User must be authenticated to delete account"))
            }
            
            // CRITICAL SECURITY: Validate that the userId parameter matches the authenticated user
            if (userId != authenticatedUserId) {
                android.util.Log.e("FirebaseRepository", "❌❌❌ CRITICAL SECURITY ERROR: deleteUserAccount userId mismatch! ❌❌❌")
                android.util.Log.e("FirebaseRepository", "Requested userId: '$userId'")
                android.util.Log.e("FirebaseRepository", "Authenticated userId: '$authenticatedUserId'")
                android.util.Log.e("FirebaseRepository", "ABORTING ACCOUNT DELETION TO PREVENT DELETING WRONG USER'S ACCOUNT!")
                return Result.failure(SecurityException("Cannot delete account for a different user. Authenticated user: $authenticatedUserId, Requested: $userId"))
            }
            
            // Use the authenticated user ID (not the parameter) for all operations
            val safeUserId = authenticatedUserId
            android.util.Log.i("FirebaseRepository", "✅ SECURITY CHECK PASSED: Deleting account for authenticated user: $safeUserId")
            android.util.Log.d("FirebaseRepository", "COMPLETELY DELETING account for user: $safeUserId")
            
            // First, clear ALL user data (this deletes all collections)
            clearUserData(safeUserId).getOrElse {
                android.util.Log.w("FirebaseRepository", "Failed to clear user data, continuing with account deletion", it)
            }
            
            // Delete user profile document COMPLETELY (this is the main user document)
            try {
                usersCollection.document(safeUserId).delete().await()
                android.util.Log.d("FirebaseRepository", "Deleted user profile document")
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete user profile document (may not exist)", e)
            }
            
            // Remove user from all circles (remove from circle members)
            try {
                val circlesSnapshot = circlesCollection.get().await()
                circlesSnapshot.documents.forEach { circleDoc ->
                    try {
                        val circleData = circleDoc.data
                        val members = circleData?.get("members") as? List<*> ?: emptyList<Any>()
                        val updatedMembers: List<*> = members.filter { member: Any? -> member != safeUserId }
                        if (updatedMembers.size != members.size) {
                            circleDoc.reference.update("members", updatedMembers).await()
                            android.util.Log.d("FirebaseRepository", "Removed user from circle ${circleDoc.id}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("FirebaseRepository", "Failed to remove user from circle ${circleDoc.id}", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to remove user from circles", e)
            }
            
            // Delete all friend requests (sent and received)
            try {
                val sentRequestsSnapshot = friendRequestsCollection
                    .whereEqualTo("fromUserId", safeUserId)
                    .get()
                    .await()
                sentRequestsSnapshot.documents.forEach { doc ->
                    doc.reference.delete().await()
                }
                
                val receivedRequestsSnapshot = friendRequestsCollection
                    .whereEqualTo("toUserId", safeUserId)
                    .get()
                    .await()
                receivedRequestsSnapshot.documents.forEach { doc ->
                    doc.reference.delete().await()
                }
                android.util.Log.d("FirebaseRepository", "Deleted friend requests")
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete friend requests", e)
            }
            
            // Delete all conversations where user is a participant
            try {
                val conversationsSnapshot1 = conversationsCollection
                    .whereEqualTo("participant1Id", safeUserId)
                    .get()
                    .await()
                conversationsSnapshot1.documents.forEach { doc ->
                    // Delete all messages in the conversation first
                    try {
                        val messagesRef = doc.reference.collection("messages")
                        val messages = messagesRef.get().await()
                        messages.documents.forEach { messageDoc ->
                            messageDoc.reference.delete().await()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("FirebaseRepository", "Failed to delete messages from conversation ${doc.id}", e)
                    }
                    // Delete the conversation
                    doc.reference.delete().await()
                }
                
                val conversationsSnapshot2 = conversationsCollection
                    .whereEqualTo("participant2Id", safeUserId)
                    .get()
                    .await()
                conversationsSnapshot2.documents.forEach { doc ->
                    // Delete all messages in the conversation first
                    try {
                        val messagesRef = doc.reference.collection("messages")
                        val messages = messagesRef.get().await()
                        messages.documents.forEach { messageDoc ->
                            messageDoc.reference.delete().await()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("FirebaseRepository", "Failed to delete messages from conversation ${doc.id}", e)
                    }
                    // Delete the conversation
                    doc.reference.delete().await()
                }
                android.util.Log.d("FirebaseRepository", "Deleted conversations and messages")
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete conversations", e)
            }
            
            // Delete the entire logs/{userId} document if it exists
            try {
                val logsUserDoc = logsCollection.document(safeUserId)
                if (logsUserDoc.get().await().exists()) {
                    logsUserDoc.delete().await()
                    android.util.Log.d("FirebaseRepository", "Deleted logs/{userId} document")
                }
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete logs/{userId} document", e)
            }
            
            // Delete the entire scans/{userId} document if it exists
            try {
                val scansUserDoc = scansCollection.document(safeUserId)
                if (scansUserDoc.get().await().exists()) {
                    scansUserDoc.delete().await()
                    android.util.Log.d("FirebaseRepository", "Deleted scans/{userId} document")
                }
            } catch (e: Exception) {
                android.util.Log.w("FirebaseRepository", "Failed to delete scans/{userId} document", e)
            }
            
            android.util.Log.d("FirebaseRepository", "✅ COMPLETELY DELETED all account data for user: $userId")
            android.util.Log.d("FirebaseRepository", "⚠️ NOTE: Firebase Auth account deletion must be done client-side")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to delete user account", e)
            Result.failure(e)
        }
    }

    // ============================================================================
    // COMPANION OBJECT
    // ============================================================================
    companion object {
        // Singleton pattern for repository
        @Volatile
        private var instance: FirebaseRepository? = null

        fun getInstance() = instance ?: synchronized(this) {
            instance ?: FirebaseRepository().also { instance = it }
        }
    }

    // ============================================================================
    // TODAY'S FOCUS TASKS OPERATIONS
    // ============================================================================

    /**
     * Mark Today's Focus tasks as completed based on action type
     * This is called when health logs are saved to auto-complete corresponding tasks
     */
    private suspend fun markTodaysFocusTaskCompleted(userId: String, date: String, actionType: com.coachie.app.data.model.ReminderActionType) {
        try {
            // Query ALL tasks for today (not just incomplete ones) to find matching tasks
            val snapshot = usersCollection.document(userId)
                .collection("todaysFocusTasks")
                .whereEqualTo("date", date)
                .whereEqualTo("actionType", actionType.name)
                .get()
                .await()
            
            // Find tasks that match the action type and are not yet completed
            val tasksToComplete = snapshot.documents.filter { doc ->
                val data = doc.data ?: return@filter false
                val completedAt = data["completedAt"] as? com.google.firebase.Timestamp
                completedAt == null // Only mark incomplete tasks
            }
            
            if (tasksToComplete.isNotEmpty()) {
                android.util.Log.d("FirebaseRepository", "Marking ${tasksToComplete.size} Today's Focus task(s) as completed for actionType=$actionType")
                for (doc in tasksToComplete) {
                    val taskTitle = doc.data?.get("title") as? String ?: "Unknown"
                    val completeResult = completeTodaysFocusTask(userId, doc.id)
                    if (completeResult.isSuccess) {
                        android.util.Log.d("FirebaseRepository", "✅ Marked Today's Focus task '$taskTitle' as completed")
                    } else {
                        android.util.Log.w("FirebaseRepository", "⚠️ Failed to mark task '$taskTitle' as completed: ${completeResult.exceptionOrNull()?.message}")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("FirebaseRepository", "⚠️ Error marking Today's Focus task as completed (non-critical)", e)
            // Don't fail the health log save if task marking fails
        }
    }
    
    /**
     * Get Today's Focus tasks for a specific date
     * Returns only incomplete tasks (completed tasks are filtered out)
     */
    suspend fun getTodaysFocusTasks(userId: String, date: String): Result<List<com.coachie.app.data.model.TodaysFocusTask>> {
        return try {
            val snapshot = usersCollection.document(userId)
                .collection("todaysFocusTasks")
                .whereEqualTo("date", date)
                .get()
                .await()

            val tasks = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                parseTodaysFocusTaskFromMap(data, doc.id)
            }.filter { !it.isCompleted } // Only return incomplete tasks

            Result.success(tasks)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to get today's focus tasks", e)
            Result.failure(e)
        }
    }

    /**
     * Save a Today's Focus task
     */
    suspend fun saveTodaysFocusTask(userId: String, task: com.coachie.app.data.model.TodaysFocusTask): Result<String> {
        return try {
            val taskData = hashMapOf<String, Any>(
                "userId" to task.userId,
                "date" to task.date,
                "type" to task.type.name,
                "title" to task.title,
                "description" to task.description,
                "actionType" to task.actionType.name,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ).apply {
                if (task.actionData.isNotEmpty()) {
                    put("actionData", task.actionData)
                }
                task.estimatedDuration?.let { put("estimatedDuration", it) }
                put("priority", task.priority)
                task.completedAt?.let { put("completedAt", com.google.firebase.Timestamp(Date(it.time))) }
            }

            val docRef = if (task.id.isNotBlank()) {
                usersCollection.document(userId)
                    .collection("todaysFocusTasks")
                    .document(task.id)
            } else {
                usersCollection.document(userId)
                    .collection("todaysFocusTasks")
                    .document()
            }

            docRef.set(taskData).await()
            android.util.Log.d("FirebaseRepository", "Saved today's focus task: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to save today's focus task", e)
            Result.failure(e)
        }
    }

    /**
     * Mark a Today's Focus task as completed
     */
    suspend fun completeTodaysFocusTask(userId: String, taskId: String): Result<Unit> {
        return try {
            usersCollection.document(userId)
                .collection("todaysFocusTasks")
                .document(taskId)
                .update(
                    "completedAt", com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "updatedAt", com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                .await()
            android.util.Log.d("FirebaseRepository", "Completed today's focus task: $taskId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to complete today's focus task", e)
            Result.failure(e)
        }
    }

    /**
     * Check if all Today's Focus tasks are completed for a date
     */
    suspend fun areAllTodaysFocusTasksCompleted(userId: String, date: String): Result<Boolean> {
        return try {
            val snapshot = usersCollection.document(userId)
                .collection("todaysFocusTasks")
                .whereEqualTo("date", date)
                .get()
                .await()

            if (snapshot.isEmpty) {
                Result.success(false) // No tasks = not all completed
            } else {
                val allCompleted = snapshot.documents.all { doc ->
                    val data = doc.data
                    val completedAt = data?.get("completedAt") as? com.google.firebase.Timestamp
                    completedAt != null
                }
                Result.success(allCompleted)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Failed to check if all tasks completed", e)
            Result.failure(e)
        }
    }

    /**
     * Parse TodaysFocusTask from Firestore document
     */
    private fun parseTodaysFocusTaskFromMap(data: Map<String, Any>, id: String): com.coachie.app.data.model.TodaysFocusTask? {
        return try {
            val typeStr = data["type"] as? String ?: return null
            val type = try {
                com.coachie.app.data.model.TodaysFocusTaskType.valueOf(typeStr)
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepository", "Invalid task type: $typeStr", e)
                return null
            }

            val actionTypeStr = data["actionType"] as? String ?: return null
            val actionType = try {
                com.coachie.app.data.model.ReminderActionType.valueOf(actionTypeStr)
            } catch (e: Exception) {
                android.util.Log.e("FirebaseRepository", "Invalid action type: $actionTypeStr", e)
                return null
            }

            val createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate() ?: Date()
            val completedAt = (data["completedAt"] as? com.google.firebase.Timestamp)?.toDate()
            val updatedAt = (data["updatedAt"] as? com.google.firebase.Timestamp)?.toDate() ?: createdAt

            com.coachie.app.data.model.TodaysFocusTask(
                id = id,
                userId = data["userId"] as? String ?: "",
                date = data["date"] as? String ?: "",
                type = type,
                title = data["title"] as? String ?: "",
                description = data["description"] as? String ?: "",
                actionType = actionType,
                actionData = (data["actionData"] as? Map<String, Any>) ?: emptyMap(),
                estimatedDuration = (data["estimatedDuration"] as? Long)?.toInt(),
                priority = (data["priority"] as? Long)?.toInt() ?: 0,
                completedAt = completedAt,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
        } catch (e: Exception) {
            android.util.Log.e("FirebaseRepository", "Error parsing TodaysFocusTask", e)
            null
        }
    }
}
