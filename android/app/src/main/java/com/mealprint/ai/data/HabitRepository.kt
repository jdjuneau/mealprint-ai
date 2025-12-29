package com.coachie.app.data

import com.mealprint.ai.data.model.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HabitRepository @Inject constructor() {

    private val firestore = FirebaseFirestore.getInstance()

    // Habit operations
    suspend fun createHabit(userId: String, habit: Habit): Result<String> {
        return try {
            val docRef = firestore.collection("users")
                .document(userId)
                .collection("habits")
                .document()

            val habitWithId = habit.copy(id = docRef.id, userId = userId)
            docRef.set(habitWithId).await()
            
            android.util.Log.d("HabitRepository", "Habit saved to Firestore: ${habitWithId.title} (id: ${docRef.id}, userId: $userId, isActive: ${habitWithId.isActive})")

            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateHabit(userId: String, habit: Habit): Result<Unit> {
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("habits")
                .document(habit.id)
                .set(habit)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteHabit(userId: String, habitId: String): Result<Unit> {
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("habits")
                .document(habitId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getHabits(userId: String): Flow<List<Habit>> = callbackFlow {
        var listener: com.google.firebase.firestore.ListenerRegistration? = null
        var useFallback = false
        
        // Get ALL habits first, then filter in memory to include:
        // 1. Habits where isActive = true
        // 2. Habits where isActive field doesn't exist (default to true)
        // This ensures we don't miss any habits
        try {
            listener = firestore.collection("users")
                .document(userId)
                .collection("habits")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("HabitRepository", "Error loading habits", error)
                        // If index is missing, try without orderBy
                        if (error is com.google.firebase.firestore.FirebaseFirestoreException &&
                            error.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.FAILED_PRECONDITION &&
                            !useFallback) {
                            useFallback = true
                            listener?.remove()
                            
                            // Try without orderBy
                            listener = firestore.collection("users")
                                .document(userId)
                                .collection("habits")
                                .addSnapshotListener { fallbackSnapshot, fallbackError ->
                                    if (fallbackError != null) {
                                        android.util.Log.e("HabitRepository", "Fallback query also failed", fallbackError)
                                        trySend(emptyList())
                                        return@addSnapshotListener
                                    }
                                    
                                    val allHabits = fallbackSnapshot?.documents?.mapNotNull { doc ->
                                        val habit = doc.toObject(Habit::class.java)?.copy(id = doc.id)
                                        if (habit != null) {
                                            android.util.Log.d("HabitRepository", "Loaded habit (fallback): ${habit.title} (id: ${habit.id}, isActive: ${habit.isActive}, userId: ${habit.userId})")
                                        }
                                        habit
                                    } ?: emptyList()
                                    
                                    // Filter to only active habits (or where isActive is not set)
                                    val activeHabits = allHabits.filter { habit ->
                                        // Check if isActive field exists in the document
                                        val doc = fallbackSnapshot?.documents?.find { it.id == habit.id }
                                        val hasIsActiveField = doc?.contains("isActive") ?: false
                                        // Include if isActive is true OR if the field doesn't exist (defaults to true)
                                        habit.isActive || !hasIsActiveField
                                    }
                                    
                                    // Sort by createdAt in memory
                                    val sortedHabits = activeHabits.sortedByDescending { it.createdAt ?: java.util.Date(0) }
                                    android.util.Log.d("HabitRepository", "getHabits Flow (fallback) emitted ${sortedHabits.size} active habits out of ${allHabits.size} total for userId: $userId")
                                    trySend(sortedHabits)
                                }
                        } else {
                            trySend(emptyList())
                        }
                        return@addSnapshotListener
                    }

                    val allHabits = snapshot?.documents?.mapNotNull { doc ->
                        val habit = doc.toObject(Habit::class.java)?.copy(id = doc.id)
                        if (habit != null) {
                            android.util.Log.d("HabitRepository", "Loaded habit: ${habit.title} (id: ${habit.id}, isActive: ${habit.isActive}, userId: ${habit.userId})")
                        }
                        habit
                    } ?: emptyList()

                    // Filter to only active habits (or where isActive field doesn't exist)
                    val activeHabits = allHabits.filter { habit ->
                        // Check if isActive field exists in the document
                        val doc = snapshot?.documents?.find { it.id == habit.id }
                        val hasIsActiveField = doc?.contains("isActive") ?: false
                        // Include if isActive is true OR if the field doesn't exist (defaults to true)
                        habit.isActive || !hasIsActiveField
                    }

                    // Sort by createdAt in memory
                    val sortedHabits = activeHabits.sortedByDescending { it.createdAt ?: java.util.Date(0) }
                    android.util.Log.d("HabitRepository", "getHabits Flow emitted ${sortedHabits.size} active habits out of ${allHabits.size} total for userId: $userId")
                    trySend(sortedHabits)
                }
        } catch (e: Exception) {
            android.util.Log.e("HabitRepository", "Exception setting up listener", e)
            // If listener setup fails, try simple query
            try {
                listener = firestore.collection("users")
                    .document(userId)
                    .collection("habits")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            android.util.Log.e("HabitRepository", "Simple query failed", error)
                            trySend(emptyList())
                            return@addSnapshotListener
                        }
                        
                        val allHabits = snapshot?.documents?.mapNotNull { doc ->
                            doc.toObject(Habit::class.java)?.copy(id = doc.id)
                        } ?: emptyList()
                        
                        // Filter to active habits
                        val activeHabits = allHabits.filter { habit ->
                            val doc = snapshot?.documents?.find { it.id == habit.id }
                            val hasIsActiveField = doc?.contains("isActive") ?: false
                            habit.isActive || !hasIsActiveField
                        }
                        
                        // Sort by createdAt in memory
                        val sortedHabits = activeHabits.sortedByDescending { it.createdAt ?: java.util.Date(0) }
                        android.util.Log.d("HabitRepository", "getHabits Flow (simple) emitted ${sortedHabits.size} active habits for userId: $userId")
                        trySend(sortedHabits)
                    }
            } catch (e2: Exception) {
                android.util.Log.e("HabitRepository", "All query attempts failed", e2)
                trySend(emptyList())
            }
        }

        awaitClose { listener?.remove() }
    }

    suspend fun getHabit(userId: String, habitId: String): Result<Habit?> {
        return try {
            val doc = firestore.collection("users")
                .document(userId)
                .collection("habits")
                .document(habitId)
                .get()
                .await()

            val habit = doc.toObject(Habit::class.java)?.copy(id = doc.id)
            Result.success(habit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHabitById(userId: String, habitId: String): Result<Habit?> = getHabit(userId, habitId)

    // Completion operations
    suspend fun completeHabit(userId: String, habitId: String, value: Int = 1, notes: String? = null): Result<String> {
        return try {
            android.util.Log.i("HabitRepository", "🚀 STARTING habit completion: userId=$userId, habitId=$habitId, value=$value")
            
            if (userId.isBlank() || habitId.isBlank()) {
                val error = IllegalArgumentException("Invalid userId or habitId: userId='$userId', habitId='$habitId'")
                android.util.Log.e("HabitRepository", "❌ Invalid parameters", error)
                return Result.failure(error)
            }
            
            // Get habit title for completion record
            val habitResult = getHabit(userId, habitId)
            val habitTitle = habitResult.getOrNull()?.title ?: "Habit"
            
            val completion = HabitCompletion(
                userId = userId,
                habitId = habitId,
                habitTitle = habitTitle,
                value = value,
                notes = notes,
                completedAt = Date()
            )

            val docRef = firestore.collection("users")
                .document(userId)
                .collection("completions")
                .document()

            val completionWithId = completion.copy(id = docRef.id)
            android.util.Log.d("HabitRepository", "📝 Saving completion to Firestore: ${docRef.path}")
            android.util.Log.d("HabitRepository", "   Completion data: userId=$userId, habitId=$habitId, value=$value, completedAt=${completion.completedAt}")
            
            docRef.set(completionWithId).await()
            android.util.Log.i("HabitRepository", "✅ HABIT COMPLETION SAVED SUCCESSFULLY! ID: ${docRef.id}")

            // Update habit stats
            try {
                android.util.Log.d("HabitRepository", "Updating habit stats...")
                updateHabitStats(userId, habitId)
                android.util.Log.d("HabitRepository", "✅ Habit stats updated")
            } catch (e: Exception) {
                android.util.Log.w("HabitRepository", "⚠️ Failed to update habit stats (non-critical)", e)
                // Don't fail the completion if stats update fails
            }
            
            // Auto-update quests for habit completion
            try {
                com.coachie.app.service.QuestAutoCompletionService.onHabitCompleted(userId, habitId, habitTitle)
            } catch (e: Exception) {
                android.util.Log.w("HabitRepository", "⚠️ Failed to update quests (non-critical)", e)
            }

            Result.success(docRef.id)
        } catch (e: Exception) {
            android.util.Log.e("HabitRepository", "❌ FAILED to complete habit", e)
            android.util.Log.e("HabitRepository", "Error type: ${e.javaClass.simpleName}, message: ${e.message}, cause: ${e.cause}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun markHabitMissed(userId: String, habitId: String, reason: String? = null): Result<String> {
        return try {
            val miss = HabitMiss(
                userId = userId,
                habitId = habitId,
                reason = reason,
                missedAt = Date()
            )

            val docRef = firestore.collection("users")
                .document(userId)
                .collection("misses")
                .document()

            val missWithId = miss.copy(id = docRef.id)
            docRef.set(missWithId).await()

            // Update habit stats
            updateHabitStats(userId, habitId)

            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun updateHabitStats(userId: String, habitId: String) {
        try {
            // Get habit
            val habitResult = getHabit(userId, habitId)
            if (habitResult.isFailure) return

            val habit = habitResult.getOrNull() ?: return

            // Calculate stats
            val completions = getCompletionsForHabit(userId, habitId)
            val misses = getMissesForHabit(userId, habitId)

            val totalCompletions = completions.size
            val totalAttempts = completions.size + misses.size
            val successRate = if (totalAttempts > 0) (completions.size.toDouble() / totalAttempts) * 100 else 0.0

            // Calculate streak
            val currentStreak = calculateCurrentStreak(completions)
            val longestStreak = maxOf(currentStreak, habit.longestStreak)

            val updatedHabit = habit.copy(
                streakCount = currentStreak,
                longestStreak = longestStreak,
                totalCompletions = totalCompletions,
                successRate = successRate,
                lastCompletedAt = completions.maxByOrNull { it.completedAt }?.completedAt
            )

            updateHabit(userId, updatedHabit)
        } catch (e: Exception) {
            // Log error but don't throw
            e.printStackTrace()
        }
    }

    private suspend fun getCompletionsForHabit(userId: String, habitId: String): List<HabitCompletion> {
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("completions")
                .whereEqualTo("habitId", habitId)
                .orderBy("completedAt", Query.Direction.DESCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(HabitCompletion::class.java)?.copy(id = it.id) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getMissesForHabit(userId: String, habitId: String): List<HabitMiss> {
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("misses")
                .whereEqualTo("habitId", habitId)
                .orderBy("missedAt", Query.Direction.DESCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(HabitMiss::class.java)?.copy(id = it.id) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun calculateCurrentStreak(completions: List<HabitCompletion>): Int {
        if (completions.isEmpty()) return 0

        val sortedCompletions = completions.sortedByDescending { it.completedAt }
        var streak = 0
        var currentDate = Date()

        for (completion in sortedCompletions) {
            val completionDate = completion.completedAt

            // Check if completion is for today or yesterday relative to current date
            val daysDiff = ((currentDate.time - completionDate.time) / (1000 * 60 * 60 * 24)).toInt()

            if (daysDiff <= 1) {
                streak++
                // Set current date to completion date for next iteration
                currentDate = completionDate
            } else {
                break // Streak broken
            }
        }

        return streak
    }

    // User profile operations
    suspend fun getUserHabitProfile(userId: String): Result<UserHabitProfile?> {
        return try {
            val doc = firestore.collection("users")
                .document(userId)
                .collection("profile")
                .document("habits")
                .get()
                .await()

            val profile = doc.toObject(UserHabitProfile::class.java)
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserHabitProfile(userId: String, profile: UserHabitProfile): Result<Unit> {
        return try {
            firestore.collection("users")
                .document(userId)
                .collection("profile")
                .document("habits")
                .set(profile)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Analytics
    fun getRecentCompletions(userId: String, days: Int = 7): Flow<List<HabitCompletion>> = callbackFlow {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        val startDate = calendar.time

        val listener = firestore.collection("users")
            .document(userId)
            .collection("completions")
            .whereGreaterThanOrEqualTo("completedAt", startDate)
            .orderBy("completedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val completions = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(HabitCompletion::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                trySend(completions)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get all habits for a user (returns Flow for real-time updates)
     */
    fun getUserHabits(userId: String): Flow<List<Habit>> = getHabits(userId)

    /**
     * Get recent completions for a user (returns Flow for real-time updates)
     */
    fun getUserCompletions(userId: String, days: Int = 7): Flow<List<HabitCompletion>> = getRecentCompletions(userId, days)

    companion object {
        @Volatile
        private var instance: HabitRepository? = null

        fun getInstance(): HabitRepository {
            return instance ?: synchronized(this) {
                instance ?: HabitRepository().also { instance = it }
            }
        }
    }
}
