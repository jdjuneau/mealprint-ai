package com.coachie.app.data.ai

/**
 * Predefined coaching prompt templates for different fitness scenarios.
 * These templates use placeholders that get replaced with user-specific data.
 *
 * Placeholders:
 * - {name}: User's display name
 * - {progress}: Progress percentage or achievement status
 * - {data}: Contextual data (e.g., protein amount, water amount, streak count)
 * - {tip}: Specific actionable advice or tip
 */
object CoachPrompts {

    /**
     * Morning motivation prompt to start the day positively
     */
    val MORNING_NUDGE = """
        Good morning {name}! 🌅

        Today is another opportunity to get closer to your goals. You've made {progress} progress so far - that's something to be proud of!

        Here's your tip for today: {tip}

        Remember, every healthy choice you make today builds momentum toward your goals. You've got this! 💪
    """.trimIndent()

    /**
     * Gentle reminder when user misses a meal or logging opportunity
     */
    val MISSED_MEAL = """
        Hey {name}, I noticed you haven't logged a meal or snack recently. 📝

        You're {progress} of the way to your goals, and staying on track with your nutrition is key to success.

        Quick tip: {tip}

        Small, consistent actions add up to big results. Let's get back on track together! 🌱
    """.trimIndent()

    /**
     * Celebration prompt for maintaining streaks or achieving milestones
     */
    val CELEBRATE_STREAK = """
        Fantastic work {name}! 🎉

        You've maintained your streak and are now {progress} toward your goals! That's the kind of consistency that creates lasting change.

        As a reward for your dedication, here's a helpful tip: {tip}

        Keep up the momentum - you're building habits that will serve you for life! 🏆
    """.trimIndent()

    /**
     * Gentle, encouraging reminder for users who need motivation
     */
    val GENTLE_REMINDER = """
        Hi {name}, just checking in on your progress. 🤗

        You're currently {progress} toward your fitness goals. Every step forward counts, even the small ones.

        Remember: {tip}

        Progress isn't always linear, but consistency always wins. I'm here cheering you on! 💪
    """.trimIndent()

    /**
     * Sleep nudge for insufficient sleep (< 6 hours)
     */
    val SLEEP_LOW = """
        Early night tonight? Your energy needs it. 😴

        Rest is just as important as exercise for reaching your goals. Aim for 7-9 hours of quality sleep tonight.

        {tip}
    """.trimIndent()

    /**
     * Sleep nudge for excellent sleep (> 9 hours)
     */
    val SLEEP_EXCELLENT = """
        Great recovery! Keep it up. 🌙

        Quality sleep is a superpower for fitness. You're giving your body the rest it needs to perform at its best.

        {tip}
    """.trimIndent()

    /**
     * Protein deficit prompt - when user has low protein intake
     */
    val PROTEIN_DEFICIT = """
        Hey {name}! 💪

        I noticed you're only getting {data} of protein today. Protein is essential for muscle recovery and keeping you full.

        Quick tip: {tip}

        Your body needs protein to repair and grow stronger. Let's boost those protein levels! 🥚
    """.trimIndent()

    /**
     * Recovery needed prompt - when user has intense workouts or low sleep
     */
    val RECOVERY_NEEDED = """
        {name}, your body needs recovery! 🛌

        You've been working hard ({data}), but rest is crucial for progress. Make sure you're giving yourself time to recover.

        Recovery tip: {tip}

        Remember: rest days are just as important as workout days. Your muscles grow when you rest! 💪
    """.trimIndent()

    /**
     * Streak celebration prompt - when user maintains consistent logging
     */
    val STREAK_CELEBRATION = """
        Amazing work, {name}! 🔥

        You've logged {data} days in a row! That's incredible consistency - this is how lasting change happens.

        Celebration tip: {tip}

        Keep this momentum going! Every day you log builds stronger habits. You're unstoppable! 🏆
    """.trimIndent()

    /**
     * Hydration reminder prompt - when user has low water intake
     */
    val HYDRATION_REMINDER = """
        {name}, time to hydrate! 💧

        You've only had {data} of water today. Staying hydrated boosts energy, metabolism, and workout performance.

        Hydration tip: {tip}

        Your body needs water to function at its best. Let's drink up! 🚰
    """.trimIndent()

    /**
     * Energy boost prompt - when user needs motivation or energy
     */
    val ENERGY_BOOST = """
        Let's boost your energy, {name}! ⚡

        {data} - You've got this! Small actions lead to big results.

        Energy tip: {tip}

        Take a moment to reset, hydrate, and refocus. You're capable of amazing things today! 🌟
    """.trimIndent()

    /**
     * Helper function to replace placeholders in prompts with actual values
     */
    fun fillPrompt(prompt: String, name: String, progress: String, tip: String): String {
        return prompt
            .replace("{name}", name)
            .replace("{progress}", progress)
            .replace("{tip}", tip)
    }

    /**
     * Fill prompt with name, data, and tip (for prompts that use {data} instead of {progress})
     */
    fun fillPromptWithData(prompt: String, name: String, data: String, tip: String): String {
        return prompt
            .replace("{name}", name)
            .replace("{data}", data)
            .replace("{tip}", tip)
    }

    /**
     * Get a random tip based on the prompt type
     */
    fun getRandomTipForPrompt(promptType: String): String {
        return when (promptType) {
            MORNING_NUDGE -> MORNING_TIPS.random()
            MISSED_MEAL -> MEAL_TIPS.random()
            CELEBRATE_STREAK -> CELEBRATION_TIPS.random()
            STREAK_CELEBRATION -> STREAK_CELEBRATION_TIPS.random()
            GENTLE_REMINDER -> MOTIVATION_TIPS.random()
            SLEEP_LOW -> SLEEP_TIPS.random()
            SLEEP_EXCELLENT -> SLEEP_EXCELLENT_TIPS.random()
            PROTEIN_DEFICIT -> PROTEIN_TIPS.random()
            RECOVERY_NEEDED -> RECOVERY_TIPS.random()
            HYDRATION_REMINDER -> HYDRATION_TIPS.random()
            ENERGY_BOOST -> ENERGY_BOOST_TIPS.random()
            else -> MOTIVATION_TIPS.random()
        }
    }

    /**
     * Fill sleep prompt (simplified version without name/progress)
     */
    fun fillSleepPrompt(prompt: String, tip: String): String {
        return prompt.replace("{tip}", tip)
    }

    // Tip collections for different scenarios
    private val MORNING_TIPS = listOf(
        "Start your day with a big glass of water to hydrate and kickstart your metabolism",
        "Plan your meals for the day - preparation leads to better choices",
        "Set a specific time for your workout to build consistency",
        "Write down 3 things you're grateful for to start with a positive mindset",
        "Prepare healthy snacks in advance to avoid impulsive eating"
    )

    private val MEAL_TIPS = listOf(
        "Try meal prepping on weekends to make healthy eating easier during the week",
        "Keep healthy snacks like fruits, nuts, or yogurt easily accessible",
        "Drink water 30 minutes before meals to help with portion control",
        "Use smaller plates to naturally eat smaller portions",
        "Include protein with every meal to stay fuller longer"
    )

    private val CELEBRATION_TIPS = listOf(
        "Treat yourself to a non-food reward like new workout clothes or a relaxing activity",
        "Share your success with a friend or family member for extra motivation",
        "Take progress photos monthly to visually track your transformation",
        "Try a new healthy recipe to keep things exciting",
        "Increase your water intake goal by 250ml as a new challenge"
    )

    private val MOTIVATION_TIPS = listOf(
        "Break your big goal into smaller, achievable daily targets",
        "Focus on how you feel rather than just the scale number",
        "Find a workout buddy or accountability partner",
        "Track non-scale victories like better sleep or more energy",
        "Remember that every expert was once a beginner - you're on the right path"
    )

    private val SLEEP_TIPS = listOf(
        "Avoid screens 1 hour before bed to improve sleep quality",
        "Create a consistent bedtime routine to signal your body it's time to rest",
        "Keep your bedroom cool and dark for optimal sleep",
        "Limit caffeine after 2 PM to avoid disrupting your sleep cycle",
        "Try relaxation techniques like deep breathing or meditation before bed"
    )

    private val SLEEP_EXCELLENT_TIPS = listOf(
        "Your consistent sleep schedule is helping your body recover and build muscle",
        "Quality sleep improves your metabolism and helps regulate hunger hormones",
        "Well-rested workouts are more effective - you're maximizing your gains",
        "Good sleep supports your immune system and keeps you healthy",
        "You're setting yourself up for success with this sleep routine"
    )

    private val PROTEIN_TIPS = listOf(
        "Add eggs, Greek yogurt, or lean chicken to your next meal",
        "Include protein-rich snacks like nuts or protein bars between meals",
        "Start your day with a protein-packed breakfast to set the tone",
        "Aim for 20-30g of protein per meal for optimal muscle support",
        "Plant-based options like beans, lentils, and tofu are great protein sources"
    )

    private val RECOVERY_TIPS = listOf(
        "Take a rest day or do light stretching instead of intense workouts",
        "Focus on sleep - aim for 7-9 hours of quality rest tonight",
        "Try a foam rolling session or gentle yoga to aid recovery",
        "Stay hydrated and eat nutrient-dense foods to support recovery",
        "Listen to your body - sometimes the best workout is rest"
    )

    private val STREAK_CELEBRATION_TIPS = listOf(
        "Track your non-scale victories - improved energy, better sleep, stronger performance",
        "Share your streak with friends or family for accountability and support",
        "Set a new milestone goal - can you reach 10 days? 30 days?",
        "Reflect on how logging has helped you stay consistent and aware",
        "Your consistency is building powerful habits that will serve you long-term"
    )

    private val HYDRATION_TIPS = listOf(
        "Keep a water bottle nearby and sip throughout the day",
        "Set hourly reminders to drink water until it becomes a habit",
        "Add flavor with lemon, cucumber, or mint if plain water is boring",
        "Drink a glass of water before each meal to boost hydration",
        "Track your water intake - seeing progress motivates you to reach your goal"
    )

    private val ENERGY_BOOST_TIPS = listOf(
        "Take a 10-minute walk outside to refresh your mind and body",
        "Eat a protein-rich snack to stabilize blood sugar and boost energy",
        "Practice deep breathing or meditation for 5 minutes to reset",
        "Stay hydrated - even mild dehydration can cause fatigue",
        "Get some natural light exposure - sunlight helps regulate energy levels"
    )
}
