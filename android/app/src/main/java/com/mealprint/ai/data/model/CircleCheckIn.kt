package com.coachie.app.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Circle check-in data class representing a daily check-in for a circle member.
 * Stored in Firestore at: circles/{circleId}/checkins/{yyyy-mm-dd}/{uid}
 */
data class CircleCheckIn(
    @PropertyName("uid")
    val uid: String = "",
    
    @PropertyName("energy")
    val energy: Int = 3, // Energy level 1-5 (1=very low, 5=very high)
    
    @PropertyName("note")
    val note: String? = null, // Optional check-in note
    
    @PropertyName("timestamp")
    @ServerTimestamp
    val timestamp: Date? = null
)

