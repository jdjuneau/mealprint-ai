package com.coachie.app.data.model

/**
 * Enum representing different AI features that can be gated by subscription tier
 */
enum class AIFeature {
    WEEKLY_BLUEPRINT_AI,      // AI-generated weekly meal plans (Pro only)
    MORNING_BRIEF,            // AI morning briefings (Pro only)
    MEAL_RECOMMENDATION,      // AI meal recommendations (Free: limited, Pro: unlimited)
    DAILY_INSIGHT,            // AI daily insights (Free: limited, Pro: unlimited)
    HABIT_SUGGESTIONS,        // AI habit suggestions (Free: limited, Pro: unlimited)
    MONTHLY_INSIGHTS,         // AI monthly insights (Pro only)
    QUEST_GENERATION,         // AI quest generation (Pro only)
    AI_COACH_CHAT             // AI coach chat (Free: limited, Pro: unlimited)
}

