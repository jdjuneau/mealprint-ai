package com.coachie.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * Semantic color categories for the app.
 * Each category has a specific meaning and carries through to all related screens.
 */
enum class SemanticColorCategory {
    HEALTH_TRACKING,  // Orange - for logs, meals, workouts, sleep, water, weight
    WELLNESS,         // Teal - for meditation, journaling, mindfulness
    HABITS,           // Blue - for habits, habit tracking, progress
    COMMUNITY,        // Purple - for community, circles, social features
    QUESTS,           // Gold/Amber - for quests, challenges, achievements
    INSIGHTS          // Indigo - for insights, analytics, AI recommendations
}

/**
 * Color palette for a semantic category with all shade variants
 */
data class SemanticColorPalette(
    val color10: Color,  // Darkest
    val color20: Color,
    val color30: Color,
    val color40: Color,  // Primary
    val color80: Color,  // Light
    val color90: Color,  // Lightest
    val color100: Color = Color.White
)

/**
 * Get semantic color palette for a category based on gender
 */
@Composable
fun getSemanticColor(category: SemanticColorCategory, isMale: Boolean = false): SemanticColorPalette {
    val gender = LocalGender.current
    val male = isMale || gender.lowercase() == "male"
    
    return remember(category, male) {
        when (category) {
            SemanticColorCategory.HEALTH_TRACKING -> {
                // Orange theme for health tracking/logs
                if (male) {
                    // Masculine orange - bold, strong
                    SemanticColorPalette(
                        color10 = Color(0xFF8B4513),  // Dark brown/orange
                        color20 = Color(0xFFB85A1A),  // Very dark orange
                        color30 = Color(0xFFCC6A1F),  // Dark orange
                        color40 = Color(0xFFE67E22),  // Bold orange (MaleAccent40)
                        color80 = Color(0xFFFFB366),  // Light orange
                        color90 = Color(0xFFFFE5CC)   // Very light orange
                    )
                } else {
                    // Feminine coral/peach - soft, warm, inviting
                    SemanticColorPalette(
                        color10 = Color(0xFFC85A3A),  // Dark coral
                        color20 = Color(0xFFE06A4A),  // Medium-dark coral
                        color30 = Color(0xFFF47A5A),  // Medium coral/peach
                        color40 = Color(0xFFFF8A6A),  // Soft coral/peach
                        color80 = Color(0xFFFFC4A8),  // Light peach
                        color90 = Color(0xFFFFE5D8)   // Very light peach
                    )
                }
            }
            
            SemanticColorCategory.WELLNESS -> {
                // Burgundy/Wine theme for wellness/mindfulness - distinct from blue habits, not pink or green
                if (male) {
                    // Masculine burgundy - deep, calming
                    SemanticColorPalette(
                        color10 = Color(0xFF4A1A1A),  // Very dark burgundy
                        color20 = Color(0xFF5C2525),  // Very dark burgundy
                        color30 = Color(0xFF6E3030),  // Dark burgundy
                        color40 = Color(0xFF803B3B),  // Deep burgundy/wine
                        color80 = Color(0xFFC89D9D),  // Light burgundy
                        color90 = Color(0xFFE5D1D1)   // Very light burgundy
                    )
                } else {
                    // Feminine rose/mauve - soft, calming, elegant
                    SemanticColorPalette(
                        color10 = Color(0xFF8B4A6B),  // Very dark rose
                        color20 = Color(0xFFA85A7B),  // Dark rose
                        color30 = Color(0xFFC56A8B),  // Medium rose/mauve
                        color40 = Color(0xFFE27A9B),  // Soft rose/mauve
                        color80 = Color(0xFFF5B5C9),  // Light rose
                        color90 = Color(0xFFFCE5ED)   // Very light rose
                    )
                }
            }
            
            SemanticColorCategory.HABITS -> {
                // Blue theme for habits
                if (male) {
                    // Masculine blue - strong, reliable
                    SemanticColorPalette(
                        color10 = Color(0xFF0A2E4A),  // Very dark blue
                        color20 = Color(0xFF143D5C),  // Very dark blue
                        color30 = Color(0xFF1A4A73),  // Dark blue
                        color40 = Color(0xFF1E5A8E),  // Deep blue (MalePrimary40)
                        color80 = Color(0xFF9BC5E8),  // Light blue
                        color90 = Color(0xFFD4E8F5)   // Very light blue
                    )
                } else {
                    // Feminine periwinkle - soft, trustworthy, gentle
                    SemanticColorPalette(
                        color10 = Color(0xFF4A5A8B),  // Very dark periwinkle
                        color20 = Color(0xFF5D6A9E),  // Dark periwinkle
                        color30 = Color(0xFF707AB1),  // Medium periwinkle
                        color40 = Color(0xFF838AC4),  // Soft periwinkle
                        color80 = Color(0xFFC4C9E8),  // Light periwinkle
                        color90 = Color(0xFFE1E4F2)   // Very light periwinkle
                    )
                }
            }
            
            SemanticColorCategory.COMMUNITY -> {
                // Purple theme for community/social
                if (male) {
                    // Masculine purple - deep, sophisticated
                    SemanticColorPalette(
                        color10 = Color(0xFF2D1B4E),  // Very dark purple
                        color20 = Color(0xFF3D2568),  // Very dark purple
                        color30 = Color(0xFF4D2F82),  // Dark purple
                        color40 = Color(0xFF5D399C),  // Deep purple
                        color80 = Color(0xFFB8A3D9),  // Light purple
                        color90 = Color(0xFFE0D6F0)   // Very light purple
                    )
                } else {
                    // Feminine lavender - soft, friendly, elegant
                    SemanticColorPalette(
                        color10 = Color(0xFF6B4A7B),  // Very dark lavender
                        color20 = Color(0xFF7D5A8E),  // Dark lavender
                        color30 = Color(0xFF8F6AA1),  // Medium lavender
                        color40 = Color(0xFFA17AB4),  // Soft lavender
                        color80 = Color(0xFFD4B8E1),  // Light lavender
                        color90 = Color(0xFFEBD6F0)   // Very light lavender
                    )
                }
            }
            
            SemanticColorCategory.QUESTS -> {
                // Gold/Amber theme for quests/achievements
                if (male) {
                    // Masculine gold - bold, achievement-oriented
                    SemanticColorPalette(
                        color10 = Color(0xFF6B4E1A),  // Very dark gold
                        color20 = Color(0xFF8B6A2A),  // Very dark gold
                        color30 = Color(0xFFAB863A),  // Dark gold
                        color40 = Color(0xFFCBA24A),  // Bold gold
                        color80 = Color(0xFFE5C99D),  // Light gold
                        color90 = Color(0xFFF2E5D1)   // Very light gold
                    )
                } else {
                    // Feminine champagne - warm, celebratory, elegant
                    SemanticColorPalette(
                        color10 = Color(0xFF9A7A5A),  // Very dark champagne
                        color20 = Color(0xFFB88A6A),  // Dark champagne
                        color30 = Color(0xFFD69A7A),  // Medium champagne
                        color40 = Color(0xFFF4AA8A),  // Soft champagne
                        color80 = Color(0xFFFAD4B8),  // Light champagne
                        color90 = Color(0xFFFDEAD8)   // Very light champagne
                    )
                }
            }
            
            SemanticColorCategory.INSIGHTS -> {
                // Indigo theme for insights/analytics
                if (male) {
                    // Masculine indigo - deep, analytical
                    SemanticColorPalette(
                        color10 = Color(0xFF1A1F4A),  // Very dark indigo
                        color20 = Color(0xFF252A5C),  // Very dark indigo
                        color30 = Color(0xFF30356E),  // Dark indigo
                        color40 = Color(0xFF3B4080),  // Deep indigo
                        color80 = Color(0xFF9DA3C0),  // Light indigo
                        color90 = Color(0xFFCED1E0)   // Very light indigo
                    )
                } else {
                    // Feminine plum - soft, insightful, sophisticated
                    SemanticColorPalette(
                        color10 = Color(0xFF6B4A7B),  // Very dark plum
                        color20 = Color(0xFF7D5A8E),  // Dark plum
                        color30 = Color(0xFF8F6AA1),  // Medium plum
                        color40 = Color(0xFFA17AB4),  // Soft plum
                        color80 = Color(0xFFD4B8E1),  // Light plum
                        color90 = Color(0xFFEBD6F0)   // Very light plum
                    )
                }
            }
        }
    }
}

/**
 * Convenience function to get the primary color (color40) for a category
 */
@Composable
fun getSemanticColorPrimary(category: SemanticColorCategory, isMale: Boolean = false): Color {
    return getSemanticColor(category, isMale).color40
}

