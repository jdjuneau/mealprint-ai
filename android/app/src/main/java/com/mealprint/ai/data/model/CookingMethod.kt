package com.coachie.app.data.model

/**
 * Cooking methods that users can select for meal generation
 */
enum class CookingMethod(
    val id: String,
    val displayName: String,
    val description: String
) {
    GRILL("grill", "Grill", "Grilling on outdoor or indoor grill"),
    BBQ("bbq", "BBQ", "Low and slow barbecue smoking"),
    SOUS_VIDE("sous_vide", "Sous Vide", "Precision temperature cooking"),
    PRESSURE_COOK("pressure_cook", "Pressure Cook", "Pressure cooker/Instant Pot"),
    SMOKE("smoke", "Smoke", "Hot or cold smoking"),
    SLOW_COOK("slow_cook", "Slow Cook", "Crock pot/slow cooker"),
    BAKE("bake", "Bake", "Oven baking"),
    ROAST("roast", "Roast", "Oven roasting"),
    SAUTE("saute", "Sauté", "Pan sautéing"),
    STIR_FRY("stir_fry", "Stir-Fry", "Wok stir-frying"),
    BRAISE("braise", "Braise", "Braising in liquid"),
    STEAM("steam", "Steam", "Steaming"),
    POACH("poach", "Poach", "Poaching in liquid"),
    PAN_SEAR("pan_sear", "Pan Sear", "Pan searing"),
    AIR_FRY("air_fry", "Air Fry", "Air fryer cooking"),
    RAW("raw", "Raw", "No cooking required");

    companion object {
        fun fromId(id: String): CookingMethod? {
            return values().find { it.id == id }
        }
    }
}

