package com.coachie.app.data.model

/**
 * Data class representing a chat message in the Coachie chat system.
 *
 * @param id Unique identifier for the message
 * @param content The text content of the message
 * @param isFromUser True if message is from user, false if from Coachie AI
 * @param timestamp Timestamp when message was sent (milliseconds)
 * @param messageType Type of message (regular chat, daily nudge, etc.)
 */
data class ChatMessage(
    val id: String = "",
    val content: String = "",
    val isFromUser: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: MessageType = MessageType.CHAT
) {
    enum class MessageType {
        CHAT,       // Regular conversation messages
        DAILY_NUDGE // Automated daily motivation messages
    }

    /**
     * Formats the timestamp into a readable time string for display
     */
    fun getFormattedTime(): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return format.format(date)
    }

    /**
     * Checks if this message is from today
     */
    fun isFromToday(): Boolean {
        val today = java.time.LocalDate.now()
        val messageDate = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
        return messageDate == today
    }
}
