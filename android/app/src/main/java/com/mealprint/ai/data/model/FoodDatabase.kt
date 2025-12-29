package com.coachie.app.data.model

import org.json.JSONObject
import com.mealprint.ai.data.model.MicronutrientType

/**
 * Simple food database with nutritional information per 100g
 */
data class FoodItem(
    val name: String,
    val calories: Int, // per 100g
    val protein: Double, // grams per 100g
    val carbs: Double, // grams per 100g
    val fat: Double, // grams per 100g
    val commonPortions: List<Portion> = emptyList(),
    val micronutrients: Map<MicronutrientType, Double> = emptyMap(), // per 100g
    val sugar: Double = 0.0, // grams per 100g
    val addedSugar: Double = 0.0 // grams per 100g
)

data class Portion(
    val name: String, // e.g., "1 breast", "1 cup", "100g"
    val grams: Double, // equivalent grams
    val description: String = "" // optional description
)

object FoodDatabase {

    // API integration for expanded food database
    private val USDA_API_KEY: String
        get() = com.coachie.app.data.Secrets.getUSDAApiKey()
    private const val USDA_BASE_URL = "https://api.nal.usda.gov/fdc/v1"

    private val foods = mapOf(
        // ===== PROTEIN FOODS =====
        "chicken breast" to FoodItem(
            name = "Chicken Breast",
            calories = 165,
            protein = 31.0,
            carbs = 0.0,
            fat = 3.6,
            commonPortions = listOf(
                Portion("1 breast (100g)", 100.0),
                Portion("4 oz piece", 113.0),
                Portion("6 oz piece", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 13.8,
                MicronutrientType.VITAMIN_B6 to 0.6,
                MicronutrientType.SELENIUM to 27.0,
                MicronutrientType.PHOSPHORUS to 228.0,
                MicronutrientType.POTASSIUM to 256.0,
                MicronutrientType.ZINC to 1.0
            )
        ),
        "chicken" to FoodItem(
            name = "Chicken Breast",
            calories = 165,
            protein = 31.0,
            carbs = 0.0,
            fat = 3.6,
            commonPortions = listOf(
                Portion("1 breast (100g)", 100.0),
                Portion("4 oz piece", 113.0),
                Portion("6 oz piece", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 13.8,
                MicronutrientType.VITAMIN_B6 to 0.6,
                MicronutrientType.SELENIUM to 27.0,
                MicronutrientType.PHOSPHORUS to 228.0,
                MicronutrientType.POTASSIUM to 256.0,
                MicronutrientType.ZINC to 1.0
            )
        ),
        "chicken thigh" to FoodItem("Chicken Thigh", 209, 26.0, 0.0, 10.9, listOf(
            Portion("1 thigh (62g)", 62.0),
            Portion("4 oz piece", 113.0)
        ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 7.0,
                MicronutrientType.VITAMIN_B6 to 0.5,
                MicronutrientType.SELENIUM to 20.0,
                MicronutrientType.PHOSPHORUS to 194.0,
                MicronutrientType.POTASSIUM to 239.0,
                MicronutrientType.ZINC to 1.8
            )
        ),
        "chicken drumstick" to FoodItem("Chicken Drumstick", 155, 24.0, 0.0, 6.4, listOf(
            Portion("1 drumstick (49g)", 49.0), Portion("4 oz piece", 113.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 6.0,
                MicronutrientType.VITAMIN_B6 to 0.3,
                MicronutrientType.SELENIUM to 22.0,
                MicronutrientType.PHOSPHORUS to 163.0,
                MicronutrientType.POTASSIUM to 209.0,
                MicronutrientType.ZINC to 1.7
            )
        ),
        "chicken wing" to FoodItem("Chicken Wing", 203, 24.0, 0.0, 12.0, listOf(
            Portion("1 wing (34g)", 34.0), Portion("3 wings (102g)", 102.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 5.8,
                MicronutrientType.VITAMIN_B6 to 0.3,
                MicronutrientType.SELENIUM to 21.0,
                MicronutrientType.PHOSPHORUS to 141.0,
                MicronutrientType.POTASSIUM to 181.0,
                MicronutrientType.ZINC to 1.4
            )
        ),

        "turkey breast" to FoodItem("Turkey Breast", 135, 30.0, 0.0, 1.0, listOf(
            Portion("4 oz piece", 113.0), Portion("6 oz piece", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 12.0,
                MicronutrientType.VITAMIN_B6 to 0.8,
                MicronutrientType.SELENIUM to 32.0,
                MicronutrientType.PHOSPHORUS to 233.0,
                MicronutrientType.POTASSIUM to 293.0,
                MicronutrientType.ZINC to 1.4
            )
        ),
        "turkey" to FoodItem("Turkey Breast", 135, 30.0, 0.0, 1.0, listOf(
            Portion("4 oz piece", 113.0), Portion("6 oz piece", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 12.0,
                MicronutrientType.VITAMIN_B6 to 0.8,
                MicronutrientType.SELENIUM to 32.0,
                MicronutrientType.PHOSPHORUS to 233.0,
                MicronutrientType.POTASSIUM to 293.0,
                MicronutrientType.ZINC to 1.4
            )
        ),
        "ground turkey" to FoodItem("Ground Turkey (93% lean)", 150, 27.0, 0.0, 3.0, listOf(
            Portion("4 oz serving", 113.0), Portion("1 lb", 454.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 9.0,
                MicronutrientType.VITAMIN_B6 to 0.6,
                MicronutrientType.SELENIUM to 29.0,
                MicronutrientType.PHOSPHORUS to 206.0,
                MicronutrientType.POTASSIUM to 263.0,
                MicronutrientType.ZINC to 2.4
            )
        ),

        "beef" to FoodItem(
            name = "Ground Beef (80% lean)",
            calories = 254,
            protein = 25.0,
            carbs = 0.0,
            fat = 17.0,
            commonPortions = listOf(
                Portion("3 oz patty", 85.0),
                Portion("4 oz serving", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.6,
                MicronutrientType.ZINC to 5.0,
                MicronutrientType.VITAMIN_B12 to 2.5,
                MicronutrientType.VITAMIN_B3 to 5.7,
                MicronutrientType.PHOSPHORUS to 180.0,
                MicronutrientType.POTASSIUM to 318.0
            )
        ),
        "ground beef" to FoodItem(
            name = "Ground Beef (80% lean)",
            calories = 254,
            protein = 25.0,
            carbs = 0.0,
            fat = 17.0,
            commonPortions = listOf(
                Portion("3 oz patty", 85.0),
                Portion("4 oz serving", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.6,
                MicronutrientType.ZINC to 5.0,
                MicronutrientType.VITAMIN_B12 to 2.5,
                MicronutrientType.VITAMIN_B3 to 5.7,
                MicronutrientType.PHOSPHORUS to 180.0,
                MicronutrientType.POTASSIUM to 318.0
            )
        ),
        "sirloin steak" to FoodItem("Sirloin Steak", 250, 26.0, 0.0, 15.0, listOf(
            Portion("4 oz piece", 113.0), Portion("6 oz piece", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.8,
                MicronutrientType.ZINC to 5.6,
                MicronutrientType.VITAMIN_B12 to 1.8,
                MicronutrientType.VITAMIN_B3 to 6.0,
                MicronutrientType.PHOSPHORUS to 200.0,
                MicronutrientType.POTASSIUM to 325.0
            )
        ),
        "ribeye steak" to FoodItem("Ribeye Steak", 292, 24.0, 0.0, 22.0, listOf(
            Portion("4 oz piece", 113.0), Portion("6 oz piece", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.5,
                MicronutrientType.ZINC to 5.3,
                MicronutrientType.VITAMIN_B12 to 2.1,
                MicronutrientType.VITAMIN_B3 to 4.8,
                MicronutrientType.PHOSPHORUS to 170.0,
                MicronutrientType.POTASSIUM to 280.0
            )
        ),
        "filet mignon" to FoodItem("Filet Mignon", 267, 26.0, 0.0, 17.0, listOf(
            Portion("4 oz piece", 113.0), Portion("6 oz piece", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.IRON to 3.1,
                MicronutrientType.ZINC to 4.8,
                MicronutrientType.VITAMIN_B12 to 1.9,
                MicronutrientType.VITAMIN_B3 to 6.2,
                MicronutrientType.PHOSPHORUS to 198.0,
                MicronutrientType.POTASSIUM to 309.0
            )
        ),
        "bison" to FoodItem(
            name = "Bison (ground, 90% lean)",
            calories = 143,
            protein = 21.0,
            carbs = 0.0,
            fat = 7.0,
            commonPortions = listOf(
                Portion("4 oz serving", 113.0),
                Portion("6 oz serving", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.8,
                MicronutrientType.ZINC to 5.0,
                MicronutrientType.VITAMIN_B12 to 2.5,
                MicronutrientType.SELENIUM to 28.0
            )
        ),
        "duck breast" to FoodItem(
            name = "Duck Breast (roasted, skinless)",
            calories = 201,
            protein = 24.5,
            carbs = 0.0,
            fat = 11.0,
            commonPortions = listOf(
                Portion("4 oz portion", 113.0),
                Portion("1 breast", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.7,
                MicronutrientType.ZINC to 1.4,
                MicronutrientType.SELENIUM to 24.0,
                MicronutrientType.VITAMIN_B3 to 5.0
            )
        ),
        "lamb chop" to FoodItem(
            name = "Lamb Chop (broiled)",
            calories = 282,
            protein = 25.6,
            carbs = 0.0,
            fat = 19.6,
            commonPortions = listOf(
                Portion("1 chop (85g)", 85.0),
                Portion("2 chops", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.1,
                MicronutrientType.ZINC to 4.0,
                MicronutrientType.VITAMIN_B12 to 2.9,
                MicronutrientType.PHOSPHORUS to 210.0
            )
        ),
        "venison" to FoodItem(
            name = "Venison Steak",
            calories = 158,
            protein = 30.0,
            carbs = 0.0,
            fat = 3.2,
            commonPortions = listOf(
                Portion("4 oz steak", 113.0),
                Portion("6 oz steak", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 4.0,
                MicronutrientType.ZINC to 4.3,
                MicronutrientType.SELENIUM to 12.0,
                MicronutrientType.VITAMIN_B12 to 2.0
            )
        ),

        "pork tenderloin" to FoodItem(
            name = "Pork Tenderloin",
            calories = 143,
            protein = 26.0,
            carbs = 0.0,
            fat = 3.5,
            commonPortions = listOf(
                Portion("4 oz piece", 113.0),
                Portion("6 oz piece", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B1 to 0.7,
                MicronutrientType.VITAMIN_B6 to 0.5,
                MicronutrientType.SELENIUM to 36.0,
                MicronutrientType.PHOSPHORUS to 247.0,
                MicronutrientType.POTASSIUM to 421.0,
                MicronutrientType.ZINC to 2.1
            )
        ),
        "pork chop" to FoodItem("Pork Chop", 231, 25.0, 0.0, 13.0, listOf(
            Portion("4 oz piece", 113.0), Portion("6 oz piece", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B1 to 0.6,
                MicronutrientType.VITAMIN_B6 to 0.4,
                MicronutrientType.SELENIUM to 34.0,
                MicronutrientType.PHOSPHORUS to 228.0,
                MicronutrientType.POTASSIUM to 379.0,
                MicronutrientType.ZINC to 1.9
            )
        ),
        "bacon" to FoodItem("Bacon", 541, 37.0, 1.4, 42.0, listOf(
            Portion("1 slice (8g)", 8.0), Portion("2 slices (16g)", 16.0), Portion("3 slices (24g)", 24.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 9.0,
                MicronutrientType.VITAMIN_B6 to 0.3,
                MicronutrientType.SELENIUM to 25.0,
                MicronutrientType.PHOSPHORUS to 143.0,
                MicronutrientType.POTASSIUM to 173.0,
                MicronutrientType.ZINC to 1.3
            )
        ),
        "ham" to FoodItem(
            name = "Ham (cooked, lean)",
            calories = 145,
            protein = 18.5,
            carbs = 1.5,
            fat = 6.0,
            commonPortions = listOf(
                Portion("1 slice (28g)", 28.0),
                Portion("2 slices (56g)", 56.0),
                Portion("3 oz serving", 85.0),
                Portion("4 oz serving", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B1 to 0.4,
                MicronutrientType.VITAMIN_B3 to 4.0,
                MicronutrientType.VITAMIN_B6 to 0.3,
                MicronutrientType.VITAMIN_B12 to 0.6,
                MicronutrientType.SELENIUM to 19.0,
                MicronutrientType.PHOSPHORUS to 196.0,
                MicronutrientType.POTASSIUM to 287.0,
                MicronutrientType.ZINC to 1.5,
                MicronutrientType.IRON to 0.8
            )
        ),
        "ground lamb" to FoodItem(
            name = "Ground Lamb",
            calories = 282,
            protein = 25.0,
            carbs = 0.0,
            fat = 20.0,
            commonPortions = listOf(
                Portion("4 oz serving", 113.0),
                Portion("1 lb", 454.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 1.9,
                MicronutrientType.ZINC to 4.2,
                MicronutrientType.VITAMIN_B12 to 2.6,
                MicronutrientType.VITAMIN_B3 to 6.5,
                MicronutrientType.PHOSPHORUS to 206.0,
                MicronutrientType.SELENIUM to 26.0
            )
        ),
        "ground pork" to FoodItem(
            name = "Ground Pork",
            calories = 263,
            protein = 26.0,
            carbs = 0.0,
            fat = 17.0,
            commonPortions = listOf(
                Portion("4 oz serving", 113.0),
                Portion("1 lb", 454.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B1 to 0.7,
                MicronutrientType.VITAMIN_B6 to 0.5,
                MicronutrientType.SELENIUM to 35.0,
                MicronutrientType.PHOSPHORUS to 240.0,
                MicronutrientType.POTASSIUM to 400.0,
                MicronutrientType.ZINC to 2.3
            )
        ),
        "pork shoulder" to FoodItem(
            name = "Pork Shoulder (roasted)",
            calories = 242,
            protein = 27.0,
            carbs = 0.0,
            fat = 14.0,
            commonPortions = listOf(
                Portion("4 oz serving", 113.0),
                Portion("6 oz serving", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B1 to 0.6,
                MicronutrientType.VITAMIN_B6 to 0.4,
                MicronutrientType.SELENIUM to 32.0,
                MicronutrientType.PHOSPHORUS to 220.0,
                MicronutrientType.POTASSIUM to 380.0,
                MicronutrientType.ZINC to 2.0
            )
        ),
        "beef brisket" to FoodItem(
            name = "Beef Brisket (cooked)",
            calories = 250,
            protein = 28.0,
            carbs = 0.0,
            fat = 15.0,
            commonPortions = listOf(
                Portion("4 oz serving", 113.0),
                Portion("6 oz serving", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.9,
                MicronutrientType.ZINC to 5.5,
                MicronutrientType.VITAMIN_B12 to 2.2,
                MicronutrientType.VITAMIN_B3 to 6.2,
                MicronutrientType.PHOSPHORUS to 195.0,
                MicronutrientType.POTASSIUM to 330.0
            )
        ),
        "beef short ribs" to FoodItem(
            name = "Beef Short Ribs (braised)",
            calories = 295,
            protein = 26.0,
            carbs = 0.0,
            fat = 21.0,
            commonPortions = listOf(
                Portion("4 oz serving", 113.0),
                Portion("1 rib (85g)", 85.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.7,
                MicronutrientType.ZINC to 5.2,
                MicronutrientType.VITAMIN_B12 to 2.0,
                MicronutrientType.VITAMIN_B3 to 5.8,
                MicronutrientType.PHOSPHORUS to 185.0,
                MicronutrientType.POTASSIUM to 310.0
            )
        ),
        "lamb leg" to FoodItem(
            name = "Lamb Leg (roasted)",
            calories = 258,
            protein = 25.0,
            carbs = 0.0,
            fat = 17.0,
            commonPortions = listOf(
                Portion("4 oz serving", 113.0),
                Portion("6 oz serving", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.0,
                MicronutrientType.ZINC to 4.0,
                MicronutrientType.VITAMIN_B12 to 2.8,
                MicronutrientType.VITAMIN_B3 to 6.8,
                MicronutrientType.PHOSPHORUS to 208.0,
                MicronutrientType.SELENIUM to 25.0
            )
        ),
        "ground bison" to FoodItem(
            name = "Ground Bison (90% lean)",
            calories = 143,
            protein = 21.0,
            carbs = 0.0,
            fat = 7.0,
            commonPortions = listOf(
                Portion("4 oz serving", 113.0),
                Portion("1 lb", 454.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.8,
                MicronutrientType.ZINC to 5.0,
                MicronutrientType.VITAMIN_B12 to 2.5,
                MicronutrientType.SELENIUM to 28.0,
                MicronutrientType.PHOSPHORUS to 195.0
            )
        ),
        "elk" to FoodItem(
            name = "Elk (ground, 90% lean)",
            calories = 173,
            protein = 22.0,
            carbs = 0.0,
            fat = 8.0,
            commonPortions = listOf(
                Portion("4 oz serving", 113.0),
                Portion("6 oz serving", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 3.2,
                MicronutrientType.ZINC to 4.5,
                MicronutrientType.VITAMIN_B12 to 2.3,
                MicronutrientType.SELENIUM to 30.0,
                MicronutrientType.PHOSPHORUS to 210.0
            )
        ),
        "rabbit" to FoodItem(
            name = "Rabbit (roasted)",
            calories = 173,
            protein = 33.0,
            carbs = 0.0,
            fat = 3.5,
            commonPortions = listOf(
                Portion("4 oz serving", 113.0),
                Portion("6 oz serving", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 4.5,
                MicronutrientType.ZINC to 2.3,
                MicronutrientType.VITAMIN_B12 to 6.5,
                MicronutrientType.VITAMIN_B3 to 11.0,
                MicronutrientType.PHOSPHORUS to 240.0,
                MicronutrientType.SELENIUM to 38.0
            )
        ),
        "oxtail" to FoodItem(
            name = "Oxtail (braised)",
            calories = 262,
            protein = 30.0,
            carbs = 0.0,
            fat = 14.0,
            commonPortions = listOf(
                Portion("4 oz serving", 113.0),
                Portion("1 piece (85g)", 85.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 3.5,
                MicronutrientType.ZINC to 4.8,
                MicronutrientType.VITAMIN_B12 to 2.8,
                MicronutrientType.CALCIUM to 14.0,
                MicronutrientType.PHOSPHORUS to 200.0
            )
        ),

        "salmon" to FoodItem(
            name = "Salmon",
            calories = 208,
            protein = 25.0,
            carbs = 0.0,
            fat = 12.0,
            commonPortions = listOf(
                Portion("3 oz fillet", 85.0),
                Portion("4 oz piece", 113.0),
                Portion("6 oz piece", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_D to 526.0,
                MicronutrientType.VITAMIN_B12 to 3.2,
                MicronutrientType.SELENIUM to 36.0,
                MicronutrientType.VITAMIN_B3 to 7.0,
                MicronutrientType.PHOSPHORUS to 277.0,
                MicronutrientType.POTASSIUM to 363.0,
                MicronutrientType.VITAMIN_B6 to 0.6,
                MicronutrientType.MAGNESIUM to 29.0
            )
        ),
        "tuna" to FoodItem(
            name = "Tuna (canned in water)",
            calories = 144,
            protein = 32.0,
            carbs = 0.0,
            fat = 1.0,
            commonPortions = listOf(
                Portion("3 oz can", 85.0),
                Portion("5 oz can", 142.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 18.0,
                MicronutrientType.VITAMIN_B12 to 2.5,
                MicronutrientType.SELENIUM to 80.0,
                MicronutrientType.VITAMIN_D to 269.0,
                MicronutrientType.PHOSPHORUS to 232.0,
                MicronutrientType.POTASSIUM to 237.0,
                MicronutrientType.VITAMIN_B6 to 0.2,
                MicronutrientType.MAGNESIUM to 33.0
            )
        ),
        "tilapia" to FoodItem("Tilapia", 128, 26.0, 0.0, 2.7, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 3.5,
                MicronutrientType.VITAMIN_B12 to 2.2,
                MicronutrientType.SELENIUM to 57.0,
                MicronutrientType.PHOSPHORUS to 170.0,
                MicronutrientType.POTASSIUM to 302.0,
                MicronutrientType.MAGNESIUM to 27.0
            )
        ),
        "cod" to FoodItem("Cod", 82, 18.0, 0.0, 0.7, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 2.1,
                MicronutrientType.VITAMIN_B6 to 0.1,
                MicronutrientType.SELENIUM to 33.0,
                MicronutrientType.PHOSPHORUS to 138.0,
                MicronutrientType.POTASSIUM to 244.0,
                MicronutrientType.MAGNESIUM to 23.0
            )
        ),
        "pollock" to FoodItem("Pollock", 92, 19.4, 0.0, 1.0, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 3.4,
                MicronutrientType.VITAMIN_B6 to 0.28,
                MicronutrientType.VITAMIN_B12 to 3.1,
                MicronutrientType.SELENIUM to 39.8,
                MicronutrientType.PHOSPHORUS to 240.6,
                MicronutrientType.POTASSIUM to 356.0,
                MicronutrientType.MAGNESIUM to 73.1
            )
        ),
        "shrimp" to FoodItem("Shrimp", 85, 20.0, 0.3, 0.2, listOf(
            Portion("3 oz serving", 85.0), Portion("4 oz serving", 113.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 1.8,
                MicronutrientType.VITAMIN_B12 to 1.1,
                MicronutrientType.SELENIUM to 42.0,
                MicronutrientType.PHOSPHORUS to 161.0,
                MicronutrientType.POTASSIUM to 98.0,
                MicronutrientType.MAGNESIUM to 26.0,
                MicronutrientType.ZINC to 1.3
            )
        ),
        "lobster" to FoodItem("Lobster", 89, 19.0, 0.5, 0.9, listOf(
            Portion("1 lobster tail (25g)", 25.0), Portion("6 oz serving", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 1.3,
                MicronutrientType.VITAMIN_B12 to 0.8,
                MicronutrientType.SELENIUM to 48.0,
                MicronutrientType.PHOSPHORUS to 115.0,
                MicronutrientType.POTASSIUM to 135.0,
                MicronutrientType.MAGNESIUM to 22.0,
                MicronutrientType.ZINC to 3.8
            )
        ),
        "halibut" to FoodItem("Halibut", 111, 23.0, 0.0, 1.6, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 6.0,
                MicronutrientType.VITAMIN_B6 to 0.4,
                MicronutrientType.VITAMIN_B12 to 1.1,
                MicronutrientType.SELENIUM to 46.0,
                MicronutrientType.PHOSPHORUS to 238.0,
                MicronutrientType.POTASSIUM to 490.0,
                MicronutrientType.MAGNESIUM to 26.0
            )
        ),
        "mackerel" to FoodItem("Mackerel", 205, 19.0, 0.0, 14.0, listOf(
            Portion("3 oz fillet", 85.0), Portion("4 oz fillet", 113.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 6.4,
                MicronutrientType.VITAMIN_B6 to 0.4,
                MicronutrientType.VITAMIN_B12 to 16.0,
                MicronutrientType.VITAMIN_D to 643.0,
                MicronutrientType.SELENIUM to 36.0,
                MicronutrientType.PHOSPHORUS to 217.0,
                MicronutrientType.POTASSIUM to 314.0
            )
        ),
        "sardines" to FoodItem("Sardines (canned in oil)", 208, 24.6, 0.0, 11.5, listOf(
            Portion("1 can (3.75 oz)", 106.0), Portion("4 oz serving", 113.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 5.2,
                MicronutrientType.VITAMIN_B12 to 8.9,
                MicronutrientType.VITAMIN_D to 272.0,
                MicronutrientType.CALCIUM to 382.0,
                MicronutrientType.SELENIUM to 52.0,
                MicronutrientType.PHOSPHORUS to 490.0,
                MicronutrientType.POTASSIUM to 397.0
            )
        ),
        "trout" to FoodItem("Trout", 119, 20.8, 0.0, 3.5, listOf(
            Portion("3 oz fillet", 85.0), Portion("4 oz fillet", 113.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 5.4,
                MicronutrientType.VITAMIN_B6 to 0.4,
                MicronutrientType.VITAMIN_B12 to 4.5,
                MicronutrientType.VITAMIN_D to 759.0,
                MicronutrientType.SELENIUM to 12.6,
                MicronutrientType.PHOSPHORUS to 271.0,
                MicronutrientType.POTASSIUM to 481.0
            )
        ),
        "sea bass" to FoodItem("Sea Bass", 97, 18.4, 0.0, 2.0, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 4.2,
                MicronutrientType.VITAMIN_B6 to 0.3,
                MicronutrientType.VITAMIN_B12 to 2.2,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 194.0,
                MicronutrientType.POTASSIUM to 256.0,
                MicronutrientType.MAGNESIUM to 32.0
            )
        ),
        "mahi mahi" to FoodItem("Mahi Mahi", 85, 19.0, 0.0, 0.7, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 6.1,
                MicronutrientType.VITAMIN_B6 to 0.4,
                MicronutrientType.VITAMIN_B12 to 1.3,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 220.0,
                MicronutrientType.POTASSIUM to 436.0,
                MicronutrientType.MAGNESIUM to 30.0
            )
        ),
        "snapper" to FoodItem("Snapper", 100, 20.5, 0.0, 1.3, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 2.4,
                MicronutrientType.VITAMIN_B6 to 0.2,
                MicronutrientType.VITAMIN_B12 to 3.5,
                MicronutrientType.SELENIUM to 49.0,
                MicronutrientType.PHOSPHORUS to 198.0,
                MicronutrientType.POTASSIUM to 522.0,
                MicronutrientType.MAGNESIUM to 37.0
            )
        ),
        "swordfish" to FoodItem("Swordfish", 144, 19.8, 0.0, 6.7, listOf(
            Portion("3 oz steak", 85.0), Portion("4 oz steak", 113.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 9.2,
                MicronutrientType.VITAMIN_B6 to 0.4,
                MicronutrientType.VITAMIN_B12 to 1.6,
                MicronutrientType.SELENIUM to 68.0,
                MicronutrientType.PHOSPHORUS to 304.0,
                MicronutrientType.POTASSIUM to 418.0,
                MicronutrientType.MAGNESIUM to 29.0
            )
        ),
        "haddock" to FoodItem("Haddock", 82, 18.0, 0.0, 0.7, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 3.0,
                MicronutrientType.VITAMIN_B6 to 0.3,
                MicronutrientType.VITAMIN_B12 to 2.1,
                MicronutrientType.SELENIUM to 31.5,
                MicronutrientType.PHOSPHORUS to 278.0,
                MicronutrientType.POTASSIUM to 351.0,
                MicronutrientType.MAGNESIUM to 26.0
            )
        ),
        "anchovies" to FoodItem("Anchovies (canned)", 131, 28.9, 0.0, 1.3, listOf(
            Portion("1 oz (5-6 fillets)", 28.0), Portion("3 oz serving", 85.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 14.0,
                MicronutrientType.VITAMIN_B12 to 0.6,
                MicronutrientType.CALCIUM to 147.0,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 174.0,
                MicronutrientType.POTASSIUM to 383.0
            )
        ),
        "crab" to FoodItem("Crab", 87, 18.1, 0.0, 1.1, listOf(
            Portion("3 oz meat", 85.0), Portion("1 crab leg (134g)", 134.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 2.7,
                MicronutrientType.VITAMIN_B6 to 0.2,
                MicronutrientType.VITAMIN_B12 to 10.0,
                MicronutrientType.SELENIUM to 37.0,
                MicronutrientType.PHOSPHORUS to 150.0,
                MicronutrientType.POTASSIUM to 259.0,
                MicronutrientType.ZINC to 7.6,
                MicronutrientType.COPPER to 0.8
            )
        ),
        "crab meat" to FoodItem("Crab", 87, 18.1, 0.0, 1.1, listOf(
            Portion("3 oz meat", 85.0), Portion("1 crab leg (134g)", 134.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 2.7,
                MicronutrientType.VITAMIN_B6 to 0.2,
                MicronutrientType.VITAMIN_B12 to 10.0,
                MicronutrientType.SELENIUM to 37.0,
                MicronutrientType.PHOSPHORUS to 150.0,
                MicronutrientType.POTASSIUM to 259.0,
                MicronutrientType.ZINC to 7.6,
                MicronutrientType.COPPER to 0.8
            )
        ),
        "scallops" to FoodItem(
            name = "Scallops (sea scallops)",
            calories = 88,
            protein = 16.8,
            carbs = 3.7,
            fat = 0.7,
            commonPortions = listOf(
                Portion("3 oz (3-4 large)", 85.0),
                Portion("4 oz serving", 113.0),
                Portion("6 oz serving", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 1.4,
                MicronutrientType.SELENIUM to 18.2,
                MicronutrientType.PHOSPHORUS to 338.0,
                MicronutrientType.POTASSIUM to 314.0,
                MicronutrientType.MAGNESIUM to 37.0,
                MicronutrientType.ZINC to 0.9
            )
        ),
        "mussels" to FoodItem(
            name = "Mussels (cooked)",
            calories = 86,
            protein = 11.9,
            carbs = 3.7,
            fat = 2.2,
            commonPortions = listOf(
                Portion("3 oz (about 9 mussels)", 85.0),
                Portion("1 cup", 150.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 20.4,
                MicronutrientType.IRON to 5.7,
                MicronutrientType.SELENIUM to 44.8,
                MicronutrientType.MANGANESE to 3.4,
                MicronutrientType.PHOSPHORUS to 197.0,
                MicronutrientType.ZINC to 1.6
            )
        ),
        "oysters" to FoodItem(
            name = "Oysters (raw)",
            calories = 68,
            protein = 7.0,
            carbs = 3.9,
            fat = 2.5,
            commonPortions = listOf(
                Portion("3 oz (about 6 medium)", 85.0),
                Portion("1 cup", 248.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.ZINC to 74.0,
                MicronutrientType.VITAMIN_B12 to 16.2,
                MicronutrientType.COPPER to 4.5,
                MicronutrientType.SELENIUM to 63.7,
                MicronutrientType.IRON to 5.1,
                MicronutrientType.VITAMIN_D to 320.0
            )
        ),
        "clams" to FoodItem(
            name = "Clams (cooked)",
            calories = 148,
            protein = 25.6,
            carbs = 5.1,
            fat = 1.9,
            commonPortions = listOf(
                Portion("3 oz (about 9 clams)", 85.0),
                Portion("1 cup", 227.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 84.1,
                MicronutrientType.IRON to 28.0,
                MicronutrientType.SELENIUM to 64.0,
                MicronutrientType.VITAMIN_C to 22.1,
                MicronutrientType.PHOSPHORUS to 338.0,
                MicronutrientType.ZINC to 2.3
            )
        ),
        "octopus" to FoodItem(
            name = "Octopus (cooked)",
            calories = 164,
            protein = 29.8,
            carbs = 4.4,
            fat = 2.1,
            commonPortions = listOf(
                Portion("3 oz serving", 85.0),
                Portion("4 oz serving", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 36.0,
                MicronutrientType.SELENIUM to 89.6,
                MicronutrientType.IRON to 9.5,
                MicronutrientType.PHOSPHORUS to 279.0,
                MicronutrientType.POTASSIUM to 630.0,
                MicronutrientType.ZINC to 3.4
            )
        ),
        "squid" to FoodItem(
            name = "Squid (calamari, cooked)",
            calories = 175,
            protein = 32.5,
            carbs = 7.6,
            fat = 1.4,
            commonPortions = listOf(
                Portion("3 oz serving", 85.0),
                Portion("4 oz serving", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 1.1,
                MicronutrientType.SELENIUM to 44.8,
                MicronutrientType.PHOSPHORUS to 221.0,
                MicronutrientType.POTASSIUM to 246.0,
                MicronutrientType.VITAMIN_B3 to 2.2,
                MicronutrientType.ZINC to 1.5
            )
        ),
        "sea bass" to FoodItem("Sea Bass", 97, 18.4, 0.0, 2.0, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 4.2,
                MicronutrientType.VITAMIN_B6 to 0.3,
                MicronutrientType.VITAMIN_B12 to 2.2,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 194.0,
                MicronutrientType.POTASSIUM to 256.0,
                MicronutrientType.MAGNESIUM to 32.0
            )
        ),
        "red snapper" to FoodItem(
            name = "Red Snapper",
            calories = 100,
            protein = 20.5,
            carbs = 0.0,
            fat = 1.3,
            commonPortions = listOf(
                Portion("4 oz fillet", 113.0),
                Portion("6 oz fillet", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 2.4,
                MicronutrientType.VITAMIN_B6 to 0.2,
                MicronutrientType.VITAMIN_B12 to 3.5,
                MicronutrientType.SELENIUM to 49.0,
                MicronutrientType.PHOSPHORUS to 198.0,
                MicronutrientType.POTASSIUM to 522.0,
                MicronutrientType.MAGNESIUM to 37.0
            )
        ),
        "flounder" to FoodItem(
            name = "Flounder",
            calories = 91,
            protein = 18.8,
            carbs = 0.0,
            fat = 1.2,
            commonPortions = listOf(
                Portion("4 oz fillet", 113.0),
                Portion("6 oz fillet", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 1.8,
                MicronutrientType.VITAMIN_B12 to 1.1,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 184.0,
                MicronutrientType.POTASSIUM to 197.0,
                MicronutrientType.MAGNESIUM to 22.0
            )
        ),
        "sole" to FoodItem(
            name = "Sole (Dover)",
            calories = 70,
            protein = 15.2,
            carbs = 0.0,
            fat = 0.6,
            commonPortions = listOf(
                Portion("4 oz fillet", 113.0),
                Portion("6 oz fillet", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 1.5,
                MicronutrientType.VITAMIN_B12 to 1.1,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 184.0,
                MicronutrientType.POTASSIUM to 197.0
            )
        ),
        "monkfish" to FoodItem(
            name = "Monkfish",
            calories = 76,
            protein = 16.6,
            carbs = 0.0,
            fat = 0.8,
            commonPortions = listOf(
                Portion("4 oz fillet", 113.0),
                Portion("6 oz fillet", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 1.5,
                MicronutrientType.VITAMIN_B12 to 1.0,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 180.0,
                MicronutrientType.POTASSIUM to 190.0
            )
        ),
        "branzino" to FoodItem(
            name = "Branzino (European Sea Bass)",
            calories = 97,
            protein = 18.4,
            carbs = 0.0,
            fat = 2.0,
            commonPortions = listOf(
                Portion("4 oz fillet", 113.0),
                Portion("6 oz fillet", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 4.2,
                MicronutrientType.VITAMIN_B6 to 0.3,
                MicronutrientType.VITAMIN_B12 to 2.2,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 194.0,
                MicronutrientType.POTASSIUM to 256.0
            )
        ),
        "barramundi" to FoodItem(
            name = "Barramundi",
            calories = 92,
            protein = 19.0,
            carbs = 0.0,
            fat = 1.3,
            commonPortions = listOf(
                Portion("4 oz fillet", 113.0),
                Portion("6 oz fillet", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 2.0,
                MicronutrientType.VITAMIN_B12 to 1.5,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 200.0,
                MicronutrientType.POTASSIUM to 300.0
            )
        ),
        "rainbow trout" to FoodItem(
            name = "Rainbow Trout",
            calories = 119,
            protein = 20.8,
            carbs = 0.0,
            fat = 3.5,
            commonPortions = listOf(
                Portion("3 oz fillet", 85.0),
                Portion("4 oz fillet", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 5.4,
                MicronutrientType.VITAMIN_B6 to 0.4,
                MicronutrientType.VITAMIN_B12 to 4.5,
                MicronutrientType.VITAMIN_D to 759.0,
                MicronutrientType.SELENIUM to 12.6,
                MicronutrientType.PHOSPHORUS to 271.0,
                MicronutrientType.POTASSIUM to 481.0
            )
        ),
        "black cod" to FoodItem(
            name = "Black Cod (Sablefish)",
            calories = 195,
            protein = 13.0,
            carbs = 0.0,
            fat = 15.0,
            commonPortions = listOf(
                Portion("4 oz fillet", 113.0),
                Portion("6 oz fillet", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 3.0,
                MicronutrientType.VITAMIN_B12 to 1.5,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 180.0,
                MicronutrientType.POTASSIUM to 250.0
            )
        ),
        "yellowtail" to FoodItem(
            name = "Yellowtail (Hamachi)",
            calories = 146,
            protein = 24.0,
            carbs = 0.0,
            fat = 5.0,
            commonPortions = listOf(
                Portion("3 oz serving", 85.0),
                Portion("4 oz serving", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 8.0,
                MicronutrientType.VITAMIN_B12 to 2.0,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 220.0,
                MicronutrientType.POTASSIUM to 350.0
            )
        ),
        "crawfish" to FoodItem(
            name = "Crawfish (cooked)",
            calories = 82,
            protein = 16.0,
            carbs = 0.0,
            fat = 1.2,
            commonPortions = listOf(
                Portion("3 oz serving", 85.0),
                Portion("1 cup", 150.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 2.0,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 184.0,
                MicronutrientType.POTASSIUM to 296.0,
                MicronutrientType.ZINC to 1.3
            )
        ),
        "langoustine" to FoodItem(
            name = "Langoustine (cooked)",
            calories = 112,
            protein = 20.0,
            carbs = 0.0,
            fat = 2.0,
            commonPortions = listOf(
                Portion("3 oz serving", 85.0),
                Portion("4 oz serving", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 1.0,
                MicronutrientType.SELENIUM to 36.5,
                MicronutrientType.PHOSPHORUS to 200.0,
                MicronutrientType.POTASSIUM to 300.0,
                MicronutrientType.ZINC to 1.5
            )
        ),
        "tuna steak" to FoodItem("Tuna Steak (fresh)", 144, 30.0, 0.0, 1.0, listOf(
            Portion("3 oz steak", 85.0), Portion("4 oz steak", 113.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 22.0,
                MicronutrientType.VITAMIN_B6 to 0.9,
                MicronutrientType.VITAMIN_B12 to 2.5,
                MicronutrientType.SELENIUM to 90.0,
                MicronutrientType.PHOSPHORUS to 333.0,
                MicronutrientType.POTASSIUM to 484.0,
                MicronutrientType.MAGNESIUM to 64.0
            )
        ),
        "arctic char" to FoodItem("Arctic Char", 138, 22.0, 0.0, 5.0, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 5.5,
                MicronutrientType.VITAMIN_B12 to 4.4,
                MicronutrientType.VITAMIN_D to 820.0,
                MicronutrientType.SELENIUM to 14.5,
                MicronutrientType.PHOSPHORUS to 270.0,
                MicronutrientType.POTASSIUM to 420.0
            )
        ),
        "perch" to FoodItem("Perch", 91, 19.4, 0.0, 0.9, listOf(
            Portion("3 oz fillet", 85.0), Portion("4 oz fillet", 113.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 1.5,
                MicronutrientType.VITAMIN_B12 to 2.2,
                MicronutrientType.SELENIUM to 16.0,
                MicronutrientType.PHOSPHORUS to 257.0,
                MicronutrientType.POTASSIUM to 269.0,
                MicronutrientType.MAGNESIUM to 30.0
            )
        ),
        "grouper" to FoodItem("Grouper", 92, 19.4, 0.0, 1.0, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 1.0,
                MicronutrientType.VITAMIN_B6 to 0.3,
                MicronutrientType.VITAMIN_B12 to 0.9,
                MicronutrientType.SELENIUM to 46.0,
                MicronutrientType.PHOSPHORUS to 143.0,
                MicronutrientType.POTASSIUM to 475.0,
                MicronutrientType.MAGNESIUM to 29.0
            )
        ),

        "tofu" to FoodItem("Tofu (firm)", 76, 8.1, 1.9, 4.8, listOf(
            Portion("4 oz piece", 113.0), Portion("8 oz block", 227.0)),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 201.0,
                MicronutrientType.IRON to 2.7,
                MicronutrientType.MAGNESIUM to 37.0,
                MicronutrientType.PHOSPHORUS to 95.0,
                MicronutrientType.POTASSIUM to 121.0,
                MicronutrientType.ZINC to 1.5
            )
        ),
        "tempeh" to FoodItem("Tempeh", 193, 19.0, 7.6, 10.8, listOf(
            Portion("4 oz piece", 113.0), Portion("8 oz piece", 227.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 5.0,
                MicronutrientType.VITAMIN_B6 to 0.2,
                MicronutrientType.IRON to 2.0,
                MicronutrientType.MAGNESIUM to 81.0,
                MicronutrientType.PHOSPHORUS to 266.0,
                MicronutrientType.POTASSIUM to 412.0,
                MicronutrientType.ZINC to 1.1
            )
        ),
        "chicken thigh" to FoodItem("Chicken Thigh", 209, 26.0, 0.0, 10.9, listOf(
            Portion("1 thigh (62g)", 62.0),
            Portion("4 oz piece", 113.0)
        ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 7.0,
                MicronutrientType.VITAMIN_B6 to 0.5,
                MicronutrientType.SELENIUM to 20.0
            )
        ),
        "chicken drumstick" to FoodItem("Chicken Drumstick", 155, 24.0, 0.0, 6.4, listOf(
            Portion("1 drumstick (49g)", 49.0), Portion("4 oz piece", 113.0))),
        "chicken wing" to FoodItem("Chicken Wing", 203, 24.0, 0.0, 12.0, listOf(
            Portion("1 wing (34g)", 34.0), Portion("3 wings (102g)", 102.0))),

        "turkey breast" to FoodItem("Turkey Breast", 135, 30.0, 0.0, 1.0, listOf(
            Portion("4 oz piece", 113.0), Portion("6 oz piece", 170.0))),
        "turkey" to FoodItem("Turkey Breast", 135, 30.0, 0.0, 1.0, listOf(
            Portion("4 oz piece", 113.0), Portion("6 oz piece", 170.0))),
        "ground turkey" to FoodItem("Ground Turkey (93% lean)", 150, 27.0, 0.0, 3.0, listOf(
            Portion("4 oz serving", 113.0), Portion("1 lb", 454.0))),

        "beef" to FoodItem(
            name = "Ground Beef (80% lean)",
            calories = 254,
            protein = 25.0,
            carbs = 0.0,
            fat = 17.0,
            commonPortions = listOf(
                Portion("3 oz patty", 85.0),
                Portion("4 oz serving", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.6,
                MicronutrientType.ZINC to 5.0,
                MicronutrientType.VITAMIN_B12 to 2.5
            )
        ),
        "ground beef" to FoodItem(
            name = "Ground Beef (80% lean)",
            calories = 254,
            protein = 25.0,
            carbs = 0.0,
            fat = 17.0,
            commonPortions = listOf(
                Portion("3 oz patty", 85.0),
                Portion("4 oz serving", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 2.6,
                MicronutrientType.ZINC to 5.0,
                MicronutrientType.VITAMIN_B12 to 2.5
            )
        ),
        "sirloin steak" to FoodItem("Sirloin Steak", 250, 26.0, 0.0, 15.0, listOf(
            Portion("4 oz piece", 113.0), Portion("6 oz piece", 170.0))),
        "ribeye steak" to FoodItem("Ribeye Steak", 292, 24.0, 0.0, 22.0, listOf(
            Portion("4 oz piece", 113.0), Portion("6 oz piece", 170.0))),
        "filet mignon" to FoodItem("Filet Mignon", 267, 26.0, 0.0, 17.0, listOf(
            Portion("4 oz piece", 113.0), Portion("6 oz piece", 170.0))),

        "pork tenderloin" to FoodItem(
            name = "Pork Tenderloin",
            calories = 143,
            protein = 26.0,
            carbs = 0.0,
            fat = 3.5,
            commonPortions = listOf(
                Portion("4 oz piece", 113.0),
                Portion("6 oz piece", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B1 to 0.7,
                MicronutrientType.VITAMIN_B6 to 0.5,
                MicronutrientType.SELENIUM to 36.0
            )
        ),
        "pork chop" to FoodItem("Pork Chop", 231, 25.0, 0.0, 13.0, listOf(
            Portion("4 oz piece", 113.0), Portion("6 oz piece", 170.0))),
        "bacon" to FoodItem("Bacon", 541, 37.0, 1.4, 42.0, listOf(
            Portion("1 slice (8g)", 8.0), Portion("2 slices (16g)", 16.0), Portion("3 slices (24g)", 24.0))),

        "salmon" to FoodItem(
            name = "Salmon",
            calories = 208,
            protein = 25.0,
            carbs = 0.0,
            fat = 12.0,
            commonPortions = listOf(
                Portion("3 oz fillet", 85.0),
                Portion("4 oz piece", 113.0),
                Portion("6 oz piece", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_D to 526.0,
                MicronutrientType.VITAMIN_B12 to 3.2,
                MicronutrientType.SELENIUM to 36.0
            )
        ),
        "tuna" to FoodItem(
            name = "Tuna (canned in water)",
            calories = 144,
            protein = 32.0,
            carbs = 0.0,
            fat = 1.0,
            commonPortions = listOf(
                Portion("3 oz can", 85.0),
                Portion("5 oz can", 142.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 18.0,
                MicronutrientType.VITAMIN_B12 to 2.5,
                MicronutrientType.SELENIUM to 80.0,
                MicronutrientType.VITAMIN_D to 269.0
            )
        ),
        "tilapia" to FoodItem("Tilapia", 128, 26.0, 0.0, 2.7, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0))),
        "cod" to FoodItem("Cod", 82, 18.0, 0.0, 0.7, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0))),
        "pollock" to FoodItem("Pollock", 92, 19.4, 0.0, 1.0, listOf(
            Portion("4 oz fillet", 113.0), Portion("6 oz fillet", 170.0))),
        "shrimp" to FoodItem("Shrimp", 85, 20.0, 0.3, 0.2, listOf(
            Portion("3 oz serving", 85.0), Portion("4 oz serving", 113.0))),
        "lobster" to FoodItem("Lobster", 89, 19.0, 0.5, 0.9, listOf(
            Portion("1 lobster tail (25g)", 25.0), Portion("6 oz serving", 170.0))),

        "tofu" to FoodItem("Tofu (firm)", 76, 8.1, 1.9, 4.8, listOf(
            Portion("4 oz piece", 113.0), Portion("8 oz block", 227.0))),
        "tempeh" to FoodItem("Tempeh", 193, 19.0, 7.6, 10.8, listOf(
            Portion("4 oz piece", 113.0), Portion("8 oz piece", 227.0))),
        "lentils" to FoodItem(
            name = "Lentils (cooked)",
            calories = 116,
            protein = 9.0,
            carbs = 20.0,
            fat = 0.4,
            commonPortions = listOf(
                Portion("1/2 cup cooked", 99.0),
                Portion("1 cup cooked", 198.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B9 to 181.0,
                MicronutrientType.IRON to 3.3,
                MicronutrientType.MAGNESIUM to 36.0,
                MicronutrientType.PHOSPHORUS to 180.0,
                MicronutrientType.POTASSIUM to 369.0,
                MicronutrientType.VITAMIN_B1 to 0.17,
                MicronutrientType.VITAMIN_B6 to 0.17,
                MicronutrientType.ZINC to 1.3
            )
        ),
        "black beans" to FoodItem(
            name = "Black Beans (cooked)",
            calories = 132,
            protein = 8.9,
            carbs = 23.0,
            fat = 0.5,
            commonPortions = listOf(
                Portion("1/2 cup cooked", 86.0),
                Portion("1 cup cooked", 172.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B9 to 149.0,
                MicronutrientType.IRON to 2.1,
                MicronutrientType.MAGNESIUM to 70.0,
                MicronutrientType.PHOSPHORUS to 152.0,
                MicronutrientType.POTASSIUM to 355.0,
                MicronutrientType.VITAMIN_B1 to 0.24,
                MicronutrientType.VITAMIN_B6 to 0.07,
                MicronutrientType.ZINC to 1.1
            )
        ),
        "chickpeas" to FoodItem(
            name = "Chickpeas (cooked)",
            calories = 164,
            protein = 7.6,
            carbs = 27.0,
            fat = 2.6,
            commonPortions = listOf(
                Portion("1/2 cup cooked", 82.0),
                Portion("1 cup cooked", 164.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B9 to 172.0,
                MicronutrientType.IRON to 2.9,
                MicronutrientType.MAGNESIUM to 48.0,
                MicronutrientType.PHOSPHORUS to 142.0,
                MicronutrientType.POTASSIUM to 291.0,
                MicronutrientType.VITAMIN_B1 to 0.12,
                MicronutrientType.VITAMIN_B6 to 0.14,
                MicronutrientType.ZINC to 1.1
            )
        ),
        "kidney beans" to FoodItem("Kidney Beans (cooked)", 127, 8.7, 22.0, 0.5, listOf(
            Portion("1/2 cup cooked", 88.0), Portion("1 cup cooked", 177.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B9 to 130.0,
                MicronutrientType.IRON to 2.2,
                MicronutrientType.MAGNESIUM to 45.0,
                MicronutrientType.PHOSPHORUS to 137.0,
                MicronutrientType.POTASSIUM to 358.0,
                MicronutrientType.VITAMIN_B1 to 0.16,
                MicronutrientType.VITAMIN_B6 to 0.08,
                MicronutrientType.ZINC to 1.0
            )
        ),

        "pinto beans" to FoodItem("Pinto Beans (cooked)", 143, 9.0, 26.0, 0.4, listOf(
            Portion("1/2 cup cooked", 86.0), Portion("1 cup cooked", 171.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B9 to 172.0,
                MicronutrientType.IRON to 2.5,
                MicronutrientType.MAGNESIUM to 50.0,
                MicronutrientType.PHOSPHORUS to 139.0,
                MicronutrientType.POTASSIUM to 373.0,
                MicronutrientType.VITAMIN_B1 to 0.21,
                MicronutrientType.VITAMIN_B6 to 0.13,
                MicronutrientType.ZINC to 1.1
            )
        ),
        "cannellini beans" to FoodItem("Cannellini Beans (cooked)", 142, 9.2, 25.0, 0.5, listOf(
            Portion("1/2 cup cooked", 88.0), Portion("1 cup cooked", 177.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B9 to 127.0,
                MicronutrientType.IRON to 3.7,
                MicronutrientType.MAGNESIUM to 70.0,
                MicronutrientType.PHOSPHORUS to 175.0,
                MicronutrientType.POTASSIUM to 354.0,
                MicronutrientType.VITAMIN_B1 to 0.15,
                MicronutrientType.VITAMIN_B6 to 0.07,
                MicronutrientType.ZINC to 1.1
            )
        ),
        "edamame" to FoodItem(
            name = "Edamame (steamed)",
            calories = 121,
            protein = 12.0,
            carbs = 11.0,
            fat = 5.0,
            commonPortions = listOf(
                Portion("1/2 cup shelled", 75.0),
                Portion("1 cup shelled", 150.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_K to 26.0,
                MicronutrientType.VITAMIN_B9 to 311.0,
                MicronutrientType.IRON to 2.3,
                MicronutrientType.MAGNESIUM to 65.0,
                MicronutrientType.POTASSIUM to 436.0
            )
        ),
        "black eyed peas" to FoodItem(
            name = "Black-Eyed Peas (cooked)",
            calories = 116,
            protein = 7.7,
            carbs = 21.0,
            fat = 0.5,
            commonPortions = listOf(
                Portion("1/2 cup cooked", 85.0),
                Portion("1 cup cooked", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B9 to 209.0,
                MicronutrientType.IRON to 2.3,
                MicronutrientType.MAGNESIUM to 47.0,
                MicronutrientType.POTASSIUM to 278.0,
                MicronutrientType.VITAMIN_B1 to 0.21
            )
        ),
        "quinoa" to FoodItem("Quinoa (cooked)", 120, 4.4, 21.3, 1.9, listOf(
            Portion("1/2 cup cooked", 92.0), Portion("1 cup cooked", 185.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 0.4,
                MicronutrientType.VITAMIN_B6 to 0.1,
                MicronutrientType.IRON to 1.5,
                MicronutrientType.MAGNESIUM to 64.0,
                MicronutrientType.PHOSPHORUS to 152.0,
                MicronutrientType.POTASSIUM to 172.0,
                MicronutrientType.VITAMIN_B1 to 0.11,
                MicronutrientType.ZINC to 1.1
            )
        ),

        "eggs" to FoodItem(
            name = "Egg",
            calories = 155,
            protein = 13.0,
            carbs = 1.1,
            fat = 11.0,
            commonPortions = listOf(
                Portion("1 large egg", 50.0),
                Portion("2 large eggs", 100.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_D to 82.0,
                MicronutrientType.VITAMIN_B12 to 1.1,
                MicronutrientType.VITAMIN_B7 to 20.0,
                MicronutrientType.SELENIUM to 30.0
            )
        ),
        "egg" to FoodItem(
            name = "Egg",
            calories = 155,
            protein = 13.0,
            carbs = 1.1,
            fat = 11.0,
            commonPortions = listOf(
                Portion("1 large egg", 50.0),
                Portion("2 large eggs", 100.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_D to 82.0,
                MicronutrientType.VITAMIN_B12 to 1.1,
                MicronutrientType.VITAMIN_B7 to 20.0,
                MicronutrientType.SELENIUM to 30.0
            )
        ),
        "egg whites" to FoodItem("Egg Whites", 52, 11.0, 0.7, 0.2, listOf(
            Portion("1 large egg white", 33.0), Portion("3 large egg whites", 99.0))),

        "greek yogurt" to FoodItem(
            name = "Greek Yogurt (plain)",
            calories = 59,
            protein = 10.0,
            carbs = 3.6,
            fat = 0.4,
            commonPortions = listOf(
                Portion("6 oz container", 170.0),
                Portion("1 cup (245g)", 245.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 110.0,
                MicronutrientType.VITAMIN_B2 to 0.6,
                MicronutrientType.VITAMIN_B12 to 1.4,
                MicronutrientType.PHOSPHORUS to 144.0,
                MicronutrientType.POTASSIUM to 141.0,
                MicronutrientType.ZINC to 0.52
            ),
            sugar = 3.6,
            addedSugar = 0.0
        ),
        "yogurt" to FoodItem(
            name = "Greek Yogurt (plain)",
            calories = 59,
            protein = 10.0,
            carbs = 3.6,
            fat = 0.4,
            commonPortions = listOf(
                Portion("6 oz container", 170.0),
                Portion("1 cup (245g)", 245.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 110.0,
                MicronutrientType.VITAMIN_B2 to 0.6,
                MicronutrientType.VITAMIN_B12 to 1.4,
                MicronutrientType.PHOSPHORUS to 144.0,
                MicronutrientType.POTASSIUM to 141.0,
                MicronutrientType.ZINC to 0.52
            ),
            sugar = 3.6,
            addedSugar = 0.0
        ),
        "cottage cheese" to FoodItem("Cottage Cheese (low fat)", 85, 12.0, 2.7, 2.3, listOf(
            Portion("1/2 cup (113g)", 113.0), Portion("1 cup (226g)", 226.0)),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 61.0,
                MicronutrientType.PHOSPHORUS to 134.0,
                MicronutrientType.POTASSIUM to 104.0,
                MicronutrientType.VITAMIN_B12 to 0.7,
                MicronutrientType.VITAMIN_B2 to 0.2,
                MicronutrientType.SELENIUM to 9.0
            ),
            sugar = 2.7,
            addedSugar = 0.0),
        "cheddar cheese" to FoodItem(
            name = "Cheddar Cheese",
            calories = 403,
            protein = 7.0,
            carbs = 1.3,
            fat = 33.0,
            commonPortions = listOf(
                Portion("1 oz slice", 28.0),
                Portion("1 cup shredded", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 721.0,
                MicronutrientType.VITAMIN_A to 265.0,
                MicronutrientType.PHOSPHORUS to 465.0,
                MicronutrientType.ZINC to 3.1,
                MicronutrientType.VITAMIN_B12 to 0.8,
                MicronutrientType.VITAMIN_B2 to 0.4
            )
        ),
        "mozzarella cheese" to FoodItem("Mozzarella Cheese", 302, 22.0, 2.2, 22.0, listOf(
            Portion("1 oz piece", 28.0), Portion("1 cup shredded", 113.0)),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 505.0,
                MicronutrientType.PHOSPHORUS to 354.0,
                MicronutrientType.ZINC to 2.9,
                MicronutrientType.VITAMIN_B12 to 0.7,
                MicronutrientType.VITAMIN_A to 137.0,
                MicronutrientType.SELENIUM to 17.0
            )),
        "cream cheese" to FoodItem(
            name = "Cream Cheese",
            calories = 342,
            protein = 6.2,
            carbs = 4.1,
            fat = 34.0,
            commonPortions = listOf(
                Portion("1 tbsp", 14.0),
                Portion("2 tbsp", 28.0),
                Portion("1 oz (2 tbsp)", 28.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 98.0,
                MicronutrientType.VITAMIN_A to 308.0,
                MicronutrientType.PHOSPHORUS to 98.0,
                MicronutrientType.VITAMIN_B2 to 0.14
            )
        ),
        "heavy cream" to FoodItem(
            name = "Heavy Whipping Cream",
            calories = 340,
            protein = 2.1,
            carbs = 2.9,
            fat = 36.0,
            commonPortions = listOf(
                Portion("1 tbsp", 15.0),
                Portion("1/4 cup", 60.0),
                Portion("1/2 cup", 120.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 66.0,
                MicronutrientType.VITAMIN_A to 123.0,
                MicronutrientType.VITAMIN_D to 47.0,
                MicronutrientType.POTASSIUM to 105.0
            )
        ),
        "sour cream" to FoodItem(
            name = "Sour Cream",
            calories = 214,
            protein = 2.4,
            carbs = 4.6,
            fat = 20.0,
            commonPortions = listOf(
                Portion("1 tbsp", 12.0),
                Portion("2 tbsp", 24.0),
                Portion("1/4 cup", 60.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 110.0,
                MicronutrientType.VITAMIN_A to 166.0,
                MicronutrientType.PHOSPHORUS to 137.0,
                MicronutrientType.POTASSIUM to 141.0
            )
        ),
        "half and half" to FoodItem(
            name = "Half and Half",
            calories = 123,
            protein = 3.1,
            carbs = 4.5,
            fat = 10.7,
            commonPortions = listOf(
                Portion("1 tbsp", 15.0),
                Portion("2 tbsp", 30.0),
                Portion("1/4 cup", 60.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 105.0,
                MicronutrientType.VITAMIN_A to 131.0,
                MicronutrientType.VITAMIN_D to 52.0,
                MicronutrientType.POTASSIUM to 142.0
            )
        ),
        "whipped cream" to FoodItem(
            name = "Whipped Cream",
            calories = 257,
            protein = 2.0,
            carbs = 12.5,
            fat = 22.2,
            commonPortions = listOf(
                Portion("2 tbsp", 20.0),
                Portion("1/4 cup", 30.0),
                Portion("1 cup", 120.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 70.0,
                MicronutrientType.VITAMIN_A to 191.0,
                MicronutrientType.POTASSIUM to 127.0
            )
        ),
        "ricotta cheese" to FoodItem(
            name = "Ricotta Cheese (whole milk)",
            calories = 174,
            protein = 11.3,
            carbs = 3.0,
            fat = 13.0,
            commonPortions = listOf(
                Portion("1/4 cup", 62.0),
                Portion("1/2 cup", 124.0),
                Portion("1 cup", 248.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 207.0,
                MicronutrientType.PHOSPHORUS to 158.0,
                MicronutrientType.VITAMIN_A to 120.0,
                MicronutrientType.POTASSIUM to 105.0
            )
        ),
        "mascarpone" to FoodItem(
            name = "Mascarpone",
            calories = 435,
            protein = 4.6,
            carbs = 4.6,
            fat = 47.0,
            commonPortions = listOf(
                Portion("2 tbsp", 30.0),
                Portion("1/4 cup", 60.0),
                Portion("1/2 cup", 120.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 143.0,
                MicronutrientType.VITAMIN_A to 338.0,
                MicronutrientType.PHOSPHORUS to 98.0
            )
        ),
        "goat cheese" to FoodItem(
            name = "Goat Cheese",
            calories = 364,
            protein = 21.0,
            carbs = 0.1,
            fat = 30.0,
            commonPortions = listOf(
                Portion("1 oz log", 28.0),
                Portion("2 oz", 56.0),
                Portion("1/4 cup crumbled", 38.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 298.0,
                MicronutrientType.PHOSPHORUS to 375.0,
                MicronutrientType.VITAMIN_A to 407.0,
                MicronutrientType.POTASSIUM to 158.0
            )
        ),
        "feta cheese" to FoodItem(
            name = "Feta Cheese",
            calories = 264,
            protein = 14.2,
            carbs = 4.1,
            fat = 21.5,
            commonPortions = listOf(
                Portion("1 oz", 28.0),
                Portion("1/4 cup crumbled", 38.0),
                Portion("1/2 cup", 76.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 493.0,
                MicronutrientType.PHOSPHORUS to 337.0,
                MicronutrientType.ZINC to 2.3,
                MicronutrientType.VITAMIN_B12 to 1.7
            )
        ),
        "blue cheese" to FoodItem(
            name = "Blue Cheese",
            calories = 353,
            protein = 21.4,
            carbs = 2.3,
            fat = 28.7,
            commonPortions = listOf(
                Portion("1 oz", 28.0),
                Portion("1/4 cup crumbled", 34.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 528.0,
                MicronutrientType.PHOSPHORUS to 387.0,
                MicronutrientType.ZINC to 2.5,
                MicronutrientType.VITAMIN_A to 198.0
            )
        ),
        "buttermilk" to FoodItem(
            name = "Buttermilk",
            calories = 40,
            protein = 3.3,
            carbs = 4.8,
            fat = 0.9,
            commonPortions = listOf(
                Portion("1 cup", 245.0),
                Portion("1/2 cup", 122.0),
                Portion("1/4 cup", 61.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 116.0,
                MicronutrientType.VITAMIN_B12 to 0.5,
                MicronutrientType.PHOSPHORUS to 89.0,
                MicronutrientType.POTASSIUM to 135.0,
                MicronutrientType.VITAMIN_B2 to 0.2
            )
        ),
        "evaporated milk" to FoodItem(
            name = "Evaporated Milk",
            calories = 134,
            protein = 6.8,
            carbs = 10.0,
            fat = 7.6,
            commonPortions = listOf(
                Portion("1/2 cup", 126.0),
                Portion("1 cup", 252.0),
                Portion("1 can (12 fl oz)", 354.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 246.0,
                MicronutrientType.VITAMIN_D to 79.0,
                MicronutrientType.VITAMIN_A to 74.0,
                MicronutrientType.PHOSPHORUS to 203.0,
                MicronutrientType.POTASSIUM to 303.0
            )
        ),
        "condensed milk" to FoodItem(
            name = "Sweetened Condensed Milk",
            calories = 321,
            protein = 8.0,
            carbs = 54.0,
            fat = 8.7,
            commonPortions = listOf(
                Portion("2 tbsp", 38.0),
                Portion("1/4 cup", 76.0),
                Portion("1 can (14 oz)", 396.0)
            ),
            sugar = 54.0,
            addedSugar = 54.0,
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 284.0,
                MicronutrientType.VITAMIN_D to 79.0,
                MicronutrientType.VITAMIN_A to 74.0,
                MicronutrientType.PHOSPHORUS to 253.0
            )
        ),
        "clotted cream" to FoodItem(
            name = "Clotted Cream",
            calories = 592,
            protein = 2.5,
            carbs = 3.0,
            fat = 65.0,
            commonPortions = listOf(
                Portion("1 tbsp", 15.0),
                Portion("2 tbsp", 30.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 65.0,
                MicronutrientType.VITAMIN_A to 592.0,
                MicronutrientType.VITAMIN_D to 47.0
            )
        ),
        "crème fraîche" to FoodItem(
            name = "Crème Fraîche",
            calories = 350,
            protein = 2.0,
            carbs = 3.0,
            fat = 38.0,
            commonPortions = listOf(
                Portion("1 tbsp", 15.0),
                Portion("2 tbsp", 30.0),
                Portion("1/4 cup", 60.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 60.0,
                MicronutrientType.VITAMIN_A to 350.0,
                MicronutrientType.PHOSPHORUS to 50.0
            )
        ),
        "swiss cheese" to FoodItem(
            name = "Swiss Cheese",
            calories = 380,
            protein = 27.0,
            carbs = 5.4,
            fat = 27.0,
            commonPortions = listOf(
                Portion("1 oz slice", 28.0),
                Portion("1 cup shredded", 108.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 791.0,
                MicronutrientType.VITAMIN_B12 to 3.3,
                MicronutrientType.PHOSPHORUS to 575.0,
                MicronutrientType.ZINC to 4.4,
                MicronutrientType.VITAMIN_A to 253.0
            )
        ),
        "provolone cheese" to FoodItem(
            name = "Provolone Cheese",
            calories = 351,
            protein = 25.6,
            carbs = 2.1,
            fat = 26.6,
            commonPortions = listOf(
                Portion("1 oz slice", 28.0),
                Portion("1 cup shredded", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 756.0,
                MicronutrientType.VITAMIN_B12 to 1.5,
                MicronutrientType.PHOSPHORUS to 486.0,
                MicronutrientType.ZINC to 3.5
            )
        ),
        "gouda cheese" to FoodItem(
            name = "Gouda Cheese",
            calories = 356,
            protein = 25.0,
            carbs = 2.2,
            fat = 27.0,
            commonPortions = listOf(
                Portion("1 oz slice", 28.0),
                Portion("1 cup shredded", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 700.0,
                MicronutrientType.VITAMIN_B12 to 1.5,
                MicronutrientType.PHOSPHORUS to 546.0,
                MicronutrientType.ZINC to 3.9
            )
        ),
        "brie cheese" to FoodItem(
            name = "Brie Cheese",
            calories = 334,
            protein = 20.8,
            carbs = 0.5,
            fat = 27.7,
            commonPortions = listOf(
                Portion("1 oz wedge", 28.0),
                Portion("2 oz", 56.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 184.0,
                MicronutrientType.VITAMIN_B12 to 1.7,
                MicronutrientType.PHOSPHORUS to 188.0,
                MicronutrientType.VITAMIN_A to 174.0
            )
        ),
        "camembert cheese" to FoodItem(
            name = "Camembert Cheese",
            calories = 300,
            protein = 20.0,
            carbs = 0.5,
            fat = 24.0,
            commonPortions = listOf(
                Portion("1 oz wedge", 28.0),
                Portion("2 oz", 56.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 388.0,
                MicronutrientType.VITAMIN_B12 to 1.3,
                MicronutrientType.PHOSPHORUS to 347.0,
                MicronutrientType.VITAMIN_A to 241.0
            )
        ),
        "parmesan cheese" to FoodItem(
            name = "Parmesan Cheese (grated)",
            calories = 431,
            protein = 38.0,
            carbs = 4.1,
            fat = 29.0,
            commonPortions = listOf(
                Portion("1 tbsp grated", 5.0),
                Portion("1 oz", 28.0),
                Portion("1/4 cup grated", 25.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 1184.0,
                MicronutrientType.VITAMIN_B12 to 1.2,
                MicronutrientType.PHOSPHORUS to 694.0,
                MicronutrientType.ZINC to 2.8,
                MicronutrientType.VITAMIN_A to 207.0
            )
        ),
        "ghee" to FoodItem(
            name = "Ghee",
            calories = 900,
            protein = 0.0,
            carbs = 0.0,
            fat = 100.0,
            commonPortions = listOf(
                Portion("1 tsp", 4.7),
                Portion("1 tbsp", 14.0),
                Portion("1/4 cup", 56.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_A to 3069.0,
                MicronutrientType.VITAMIN_E to 2.8
            )
        ),
        "parmesan cheese" to FoodItem("Parmesan Cheese", 431, 38.0, 3.2, 29.0, listOf(
            Portion("1 tbsp grated", 5.0), Portion("1/4 cup grated", 25.0)),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 1109.0,
                MicronutrientType.PHOSPHORUS to 864.0,
                MicronutrientType.ZINC to 3.9,
                MicronutrientType.VITAMIN_A to 92.0,
                MicronutrientType.SELENIUM to 34.0,
                MicronutrientType.VITAMIN_B12 to 1.2
            )),

        // ===== GRAINS & CARBOHYDRATES =====
        "white rice" to FoodItem("White Rice (cooked)", 130, 2.7, 28.0, 0.3, listOf(
            Portion("1/2 cup cooked", 79.0), Portion("1 cup cooked", 158.0))),
        "rice" to FoodItem("White Rice (cooked)", 130, 2.7, 28.0, 0.3, listOf(
            Portion("1/2 cup cooked", 79.0), Portion("1 cup cooked", 158.0))),
        "brown rice" to FoodItem("Brown Rice (cooked)", 111, 2.6, 23.0, 0.9, listOf(
            Portion("1/2 cup cooked", 98.0), Portion("1 cup cooked", 195.0))),
        "quinoa" to FoodItem("Quinoa (cooked)", 120, 4.4, 21.3, 1.9, listOf(
            Portion("1/2 cup cooked", 92.0), Portion("1 cup cooked", 185.0))),
        "oats" to FoodItem(
            name = "Oats",
            calories = 379,
            protein = 13.0,
            carbs = 67.0,
            fat = 6.9,
            commonPortions = listOf(
                Portion("1/2 cup dry", 40.0),
                Portion("1 cup dry", 80.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B1 to 0.5,
                MicronutrientType.IRON to 4.7,
                MicronutrientType.MAGNESIUM to 177.0
            )
        ),
        "oatmeal" to FoodItem("Oatmeal (cooked)", 68, 2.4, 12.0, 1.4, listOf(
            Portion("1/2 cup cooked", 117.0), Portion("1 cup cooked", 234.0))),

        "whole wheat pasta" to FoodItem("Whole Wheat Pasta (cooked)", 149, 7.5, 29.0, 1.3, listOf(
            Portion("1 cup cooked", 140.0), Portion("2 oz dry", 57.0))),
        "pasta" to FoodItem("Whole Wheat Pasta (cooked)", 149, 7.5, 29.0, 1.3, listOf(
            Portion("1 cup cooked", 140.0), Portion("2 oz dry", 57.0))),
        "spaghetti" to FoodItem("Spaghetti (cooked)", 157, 5.8, 31.0, 0.9, listOf(
            Portion("1 cup cooked", 140.0), Portion("2 oz dry", 57.0))),
        "couscous" to FoodItem("Couscous (cooked)", 112, 3.8, 23.0, 0.2, listOf(
            Portion("1/2 cup cooked", 82.0), Portion("1 cup cooked", 164.0))),
        "barley" to FoodItem("Barley (cooked)", 123, 2.3, 28.0, 0.4, listOf(
            Portion("1/2 cup cooked", 79.0), Portion("1 cup cooked", 158.0))),

        "whole wheat bread" to FoodItem("Whole Wheat Bread", 247, 13.0, 41.0, 3.5, listOf(
            Portion("1 slice", 45.0), Portion("2 slices", 90.0))),
        "bread" to FoodItem("Whole Wheat Bread", 247, 13.0, 41.0, 3.5, listOf(
            Portion("1 slice", 45.0), Portion("2 slices", 90.0)),
            sugar = 4.0,
            addedSugar = 2.0),
        "white bread" to FoodItem("White Bread", 265, 9.0, 49.0, 3.2, listOf(
            Portion("1 slice", 45.0), Portion("2 slices", 90.0)),
            sugar = 5.7,
            addedSugar = 3.0),
        "sourdough bread" to FoodItem("Sourdough Bread", 289, 9.0, 56.0, 1.4, listOf(
            Portion("1 slice", 45.0), Portion("2 slices", 90.0))),
        "bagel" to FoodItem("Bagel", 250, 10.0, 48.0, 1.5, listOf(
            Portion("1 medium bagel", 100.0), Portion("1 large bagel", 120.0))),
        "english muffin" to FoodItem("English Muffin", 227, 8.0, 44.0, 1.8, listOf(
            Portion("1 muffin", 57.0))),

        "sweet potato" to FoodItem(
            name = "Sweet Potato (baked)",
            calories = 90,
            protein = 2.0,
            carbs = 20.7,
            fat = 0.1,
            commonPortions = listOf(
                Portion("1 medium (114g)", 114.0),
                Portion("1 large (180g)", 180.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_A to 961.0,
                MicronutrientType.VITAMIN_C to 2.4,
                MicronutrientType.POTASSIUM to 337.0
            )
        ),
        "potato" to FoodItem(
            name = "Potato (baked)",
            calories = 94,
            protein = 2.0,
            carbs = 21.0,
            fat = 0.1,
            commonPortions = listOf(
                Portion("1 medium (173g)", 173.0),
                Portion("1 large (299g)", 299.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.POTASSIUM to 425.0,
                MicronutrientType.VITAMIN_C to 19.7,
                MicronutrientType.VITAMIN_B6 to 0.3
            )
        ),
        "baked potato" to FoodItem(
            name = "Baked Potato",
            calories = 94,
            protein = 2.0,
            carbs = 21.0,
            fat = 0.1,
            commonPortions = listOf(
                Portion("1 medium (173g)", 173.0),
                Portion("1 large (299g)", 299.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.POTASSIUM to 425.0,
                MicronutrientType.VITAMIN_C to 19.7,
                MicronutrientType.VITAMIN_B6 to 0.3
            )
        ),

        // ===== VEGETABLES =====
        "broccoli" to FoodItem(
            name = "Broccoli",
            calories = 34,
            protein = 2.8,
            carbs = 7.0,
            fat = 0.4,
            commonPortions = listOf(
                Portion("1 cup chopped", 91.0),
                Portion("1 cup florets", 71.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 89.0,
                MicronutrientType.VITAMIN_K to 101.0,
                MicronutrientType.VITAMIN_B9 to 63.0,
                MicronutrientType.VITAMIN_A to 31.0,
                MicronutrientType.POTASSIUM to 316.0, // ~316mg per 100g raw, ~293mg per 100g cooked
                MicronutrientType.CALCIUM to 47.0, // CRITICAL: Broccoli has significant calcium (~47mg per 100g raw, ~40mg per 100g cooked)
                MicronutrientType.MAGNESIUM to 21.0,
                MicronutrientType.PHOSPHORUS to 66.0
            )
        ),
        "spinach" to FoodItem(
            name = "Spinach",
            calories = 23,
            protein = 2.9,
            carbs = 3.6,
            fat = 0.4,
            commonPortions = listOf(
                Portion("1 cup raw", 30.0),
                Portion("2 cups raw", 60.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_A to 469.0,
                MicronutrientType.VITAMIN_K to 482.0,
                MicronutrientType.VITAMIN_B9 to 194.0,
                MicronutrientType.IRON to 2.7,
                MicronutrientType.MAGNESIUM to 79.0,
                MicronutrientType.VITAMIN_C to 28.0,
                MicronutrientType.CALCIUM to 99.0,
                MicronutrientType.POTASSIUM to 558.0
            )
        ),
        "kale" to FoodItem(
            name = "Kale",
            calories = 49,
            protein = 4.3,
            carbs = 8.8,
            fat = 0.9,
            commonPortions = listOf(
                Portion("1 cup chopped", 67.0),
                Portion("2 cups chopped", 134.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_A to 481.0,
                MicronutrientType.VITAMIN_C to 120.0,
                MicronutrientType.VITAMIN_K to 704.0,
                MicronutrientType.CALCIUM to 254.0,
                MicronutrientType.IRON to 1.7,
                MicronutrientType.MAGNESIUM to 33.0,
                MicronutrientType.POTASSIUM to 329.0,
                MicronutrientType.VITAMIN_B6 to 0.27
            )
        ),

        "sweet potato" to FoodItem(
            name = "Sweet Potato (baked)",
            calories = 90,
            protein = 2.0,
            carbs = 20.7,
            fat = 0.1,
            commonPortions = listOf(
                Portion("1 medium (114g)", 114.0),
                Portion("1 large (180g)", 180.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_A to 961.0,
                MicronutrientType.VITAMIN_C to 2.4,
                MicronutrientType.POTASSIUM to 337.0,
                MicronutrientType.MANGANESE to 0.3,
                MicronutrientType.VITAMIN_B6 to 0.21,
                MicronutrientType.IRON to 0.6,
                MicronutrientType.MAGNESIUM to 25.0
            )
        ),

        "carrots" to FoodItem(
            name = "Carrot",
            calories = 41,
            protein = 0.9,
            carbs = 10.0,
            fat = 0.2,
            commonPortions = listOf(
                Portion("1 medium carrot", 61.0),
                Portion("1 cup chopped", 128.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_A to 835.0,
                MicronutrientType.VITAMIN_K to 13.0,
                MicronutrientType.VITAMIN_C to 5.9,
                MicronutrientType.POTASSIUM to 320.0,
                MicronutrientType.MANGANESE to 0.14,
                MicronutrientType.VITAMIN_B6 to 0.14,
                MicronutrientType.IRON to 0.3
            )
        ),

        "bell pepper" to FoodItem(
            name = "Bell Pepper",
            calories = 24,
            protein = 0.9,
            carbs = 6.0,
            fat = 0.3,
            commonPortions = listOf(
                Portion("1 medium pepper", 119.0),
                Portion("1 cup sliced", 92.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 128.0,
                MicronutrientType.VITAMIN_A to 157.0,
                MicronutrientType.VITAMIN_B6 to 0.22,
                MicronutrientType.POTASSIUM to 211.0,
                MicronutrientType.MANGANESE to 0.12,
                MicronutrientType.VITAMIN_B9 to 46.0,
                MicronutrientType.IRON to 0.4
            )
        ),
        "arugula" to FoodItem(
            name = "Arugula",
            calories = 25,
            protein = 2.6,
            carbs = 3.7,
            fat = 0.7,
            commonPortions = listOf(
                Portion("1 cup raw", 20.0),
                Portion("2 cups raw", 40.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_K to 109.0,
                MicronutrientType.VITAMIN_A to 119.0,
                MicronutrientType.VITAMIN_C to 15.0,
                MicronutrientType.CALCIUM to 160.0,
                MicronutrientType.VITAMIN_B9 to 97.0
            )
        ),
        "eggplant" to FoodItem(
            name = "Eggplant (roasted)",
            calories = 33,
            protein = 1.0,
            carbs = 8.0,
            fat = 0.2,
            commonPortions = listOf(
                Portion("1 cup cubes", 82.0),
                Portion("1 medium eggplant", 458.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.MANGANESE to 0.25,
                MicronutrientType.POTASSIUM to 123.0,
                MicronutrientType.VITAMIN_B1 to 0.04,
                MicronutrientType.VITAMIN_B6 to 0.08
            )
        ),
        "beets" to FoodItem(
            name = "Beets (boiled)",
            calories = 44,
            protein = 1.7,
            carbs = 10.0,
            fat = 0.2,
            commonPortions = listOf(
                Portion("1 cup sliced", 170.0),
                Portion("1 beet (82g)", 82.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B9 to 109.0,
                MicronutrientType.POTASSIUM to 305.0,
                MicronutrientType.MANGANESE to 0.3,
                MicronutrientType.VITAMIN_C to 4.0
            )
        ),
        "butternut squash" to FoodItem(
            name = "Butternut Squash (roasted)",
            calories = 45,
            protein = 1.0,
            carbs = 12.0,
            fat = 0.1,
            commonPortions = listOf(
                Portion("1 cup cubes", 140.0),
                Portion("1/2 squash", 200.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_A to 558.0,
                MicronutrientType.VITAMIN_C to 21.0,
                MicronutrientType.POTASSIUM to 352.0,
                MicronutrientType.MAGNESIUM to 34.0
            )
        ),
        "sweet corn" to FoodItem(
            name = "Sweet Corn (cooked)",
            calories = 96,
            protein = 3.4,
            carbs = 21.0,
            fat = 1.5,
            commonPortions = listOf(
                Portion("1 ear (90g)", 90.0),
                Portion("1 cup kernels", 154.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.POTASSIUM to 287.0,
                MicronutrientType.MAGNESIUM to 37.0,
                MicronutrientType.VITAMIN_B6 to 0.1,
                MicronutrientType.VITAMIN_C to 7.0
            )
        ),
        "kimchi" to FoodItem(
            name = "Kimchi",
            calories = 23,
            protein = 2.0,
            carbs = 4.0,
            fat = 0.6,
            commonPortions = listOf(
                Portion("1/2 cup", 85.0),
                Portion("1 cup", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_K to 43.0,
                MicronutrientType.VITAMIN_C to 25.0,
                MicronutrientType.VITAMIN_A to 30.0
            )
        ),
        "sauerkraut" to FoodItem(
            name = "Sauerkraut",
            calories = 19,
            protein = 0.9,
            carbs = 4.3,
            fat = 0.1,
            commonPortions = listOf(
                Portion("1/2 cup", 75.0),
                Portion("1 cup", 150.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 15.0,
                MicronutrientType.VITAMIN_K to 13.0,
                MicronutrientType.SODIUM to 661.0
            )
        ),

        "tomato" to FoodItem("Tomato", 18, 0.9, 3.9, 0.2, listOf(
            Portion("1 medium tomato", 123.0), Portion("1 cup chopped", 180.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 14.0,
                MicronutrientType.VITAMIN_A to 42.0,
                MicronutrientType.VITAMIN_K to 7.9,
                MicronutrientType.POTASSIUM to 237.0,
                MicronutrientType.VITAMIN_B9 to 15.0,
                MicronutrientType.MAGNESIUM to 11.0,
                MicronutrientType.PHOSPHORUS to 24.0
            )
        ),

        "zucchini" to FoodItem("Zucchini", 17, 1.2, 3.1, 0.3, listOf(
            Portion("1 medium zucchini", 196.0), Portion("1 cup sliced", 113.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 17.9,
                MicronutrientType.VITAMIN_A to 10.0,
                MicronutrientType.VITAMIN_K to 4.3,
                MicronutrientType.POTASSIUM to 261.0,
                MicronutrientType.MANGANESE to 0.18,
                MicronutrientType.VITAMIN_B6 to 0.16,
                MicronutrientType.MAGNESIUM to 18.0
            )
        ),

        "asparagus" to FoodItem("Asparagus", 20, 2.2, 3.9, 0.1, listOf(
            Portion("5 spears", 60.0), Portion("1 cup", 134.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 5.6,
                MicronutrientType.VITAMIN_K to 41.6,
                MicronutrientType.VITAMIN_B9 to 52.0,
                MicronutrientType.POTASSIUM to 202.0,
                MicronutrientType.VITAMIN_B6 to 0.09,
                MicronutrientType.VITAMIN_B1 to 0.14,
                MicronutrientType.IRON to 2.1
            )
        ),

        "green beans" to FoodItem("Green Beans", 31, 1.8, 7.0, 0.3, listOf(
            Portion("1 cup cooked", 125.0), Portion("1 cup raw", 100.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 12.2,
                MicronutrientType.VITAMIN_K to 43.0,
                MicronutrientType.VITAMIN_A to 35.0,
                MicronutrientType.POTASSIUM to 211.0,
                MicronutrientType.VITAMIN_B9 to 33.0,
                MicronutrientType.MAGNESIUM to 25.0,
                MicronutrientType.IRON to 1.0
            )
        ),

        "peas" to FoodItem("Green Peas", 81, 5.4, 14.0, 0.4, listOf(
            Portion("1/2 cup cooked", 80.0), Portion("1 cup cooked", 160.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 13.0,
                MicronutrientType.VITAMIN_K to 24.8,
                MicronutrientType.VITAMIN_A to 38.0,
                MicronutrientType.POTASSIUM to 244.0,
                MicronutrientType.VITAMIN_B9 to 65.0,
                MicronutrientType.MANGANESE to 0.41,
                MicronutrientType.IRON to 1.5
            )
        ),

        "cauliflower" to FoodItem("Cauliflower", 25, 1.9, 5.0, 0.3, listOf(
            Portion("1 cup chopped", 107.0), Portion("1 cup florets", 62.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 48.2,
                MicronutrientType.VITAMIN_K to 15.5,
                MicronutrientType.VITAMIN_B6 to 0.18,
                MicronutrientType.POTASSIUM to 303.0,
                MicronutrientType.VITAMIN_B9 to 57.0,
                MicronutrientType.MAGNESIUM to 15.0,
                MicronutrientType.PHOSPHORUS to 44.0
            )
        ),

        "brussels sprouts" to FoodItem("Brussels Sprouts", 43, 3.4, 9.0, 0.4, listOf(
            Portion("1 cup cooked", 156.0), Portion("1 sprout (19g)", 19.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 85.0,
                MicronutrientType.VITAMIN_K to 177.0,
                MicronutrientType.VITAMIN_A to 38.0,
                MicronutrientType.POTASSIUM to 389.0,
                MicronutrientType.VITAMIN_B9 to 65.0,
                MicronutrientType.VITAMIN_B6 to 0.22,
                MicronutrientType.IRON to 1.4
            )
        ),
        "lettuce" to FoodItem("Lettuce", 15, 1.4, 2.8, 0.2, listOf(
            Portion("1 cup shredded", 28.0), Portion("2 cups shredded", 56.0))),
        "cucumber" to FoodItem("Cucumber", 15, 0.7, 3.6, 0.1, listOf(
            Portion("1 medium cucumber", 301.0), Portion("1 cup sliced", 119.0))),
        "tomato" to FoodItem("Tomato", 18, 0.9, 3.9, 0.2, listOf(
            Portion("1 medium tomato", 123.0), Portion("1 cup chopped", 180.0))),
        "cherry tomato" to FoodItem("Cherry Tomato", 18, 0.9, 3.9, 0.2, listOf(
            Portion("1 tomato", 17.0), Portion("10 tomatoes", 170.0))),
        "bell pepper" to FoodItem(
            name = "Bell Pepper",
            calories = 24,
            protein = 0.9,
            carbs = 6.0,
            fat = 0.3,
            commonPortions = listOf(
                Portion("1 medium pepper", 119.0),
                Portion("1 cup sliced", 92.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 128.0,
                MicronutrientType.VITAMIN_A to 157.0
            )
        ),
        "carrot" to FoodItem(
            name = "Carrot",
            calories = 41,
            protein = 0.9,
            carbs = 10.0,
            fat = 0.2,
            commonPortions = listOf(
                Portion("1 medium carrot", 61.0),
                Portion("1 cup chopped", 128.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_A to 835.0,
                MicronutrientType.VITAMIN_K to 13.0,
                MicronutrientType.VITAMIN_C to 5.9
            )
        ),
        "onion" to FoodItem("Onion", 40, 1.1, 9.3, 0.1, listOf(
            Portion("1 medium onion", 110.0), Portion("1 cup chopped", 160.0))),
        "garlic" to FoodItem("Garlic", 149, 6.4, 33.0, 0.5, listOf(
            Portion("1 clove", 3.0), Portion("1 tbsp minced", 9.0))),
        "mushroom" to FoodItem("Mushroom", 22, 3.1, 3.3, 0.3, listOf(
            Portion("1 cup sliced", 70.0), Portion("1 cup whole", 87.0))),
        "zucchini" to FoodItem("Zucchini", 17, 1.2, 3.1, 0.3, listOf(
            Portion("1 medium zucchini", 196.0), Portion("1 cup sliced", 113.0))),
        "avocado" to FoodItem(
            name = "Avocado",
            calories = 160,
            protein = 2.0,
            carbs = 8.5,
            fat = 14.7,
            commonPortions = listOf(
                Portion("1/2 medium avocado", 68.0),
                Portion("1 medium avocado", 136.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_E to 2.1,
                MicronutrientType.POTASSIUM to 485.0,
                MicronutrientType.VITAMIN_B9 to 81.0
            )
        ),
        "asparagus" to FoodItem("Asparagus", 20, 2.2, 3.9, 0.1, listOf(
            Portion("5 spears", 60.0), Portion("1 cup", 134.0))),
        "green beans" to FoodItem("Green Beans", 31, 1.8, 7.0, 0.3, listOf(
            Portion("1 cup cooked", 125.0), Portion("1 cup raw", 100.0))),
        "peas" to FoodItem("Green Peas", 81, 5.4, 14.0, 0.4, listOf(
            Portion("1/2 cup cooked", 80.0), Portion("1 cup cooked", 160.0))),

        // ===== FRUITS =====
        "apple" to FoodItem("Apple", 52, 0.3, 14.0, 0.2, listOf(
            Portion("1 medium", 182.0), Portion("1 small", 129.0), Portion("1 large", 223.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 4.6,
                MicronutrientType.POTASSIUM to 107.0,
                MicronutrientType.VITAMIN_B6 to 0.04,
                MicronutrientType.MAGNESIUM to 5.0,
                MicronutrientType.PHOSPHORUS to 11.0
            ),
            sugar = 10.4,
            addedSugar = 0.0),
        "banana" to FoodItem(
            name = "Banana",
            calories = 89,
            protein = 1.1,
            carbs = 23.0,
            fat = 0.3,
            commonPortions = listOf(
                Portion("1 medium", 118.0),
                Portion("1 small", 101.0),
                Portion("1 large", 136.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.POTASSIUM to 358.0,
                MicronutrientType.VITAMIN_B6 to 0.4,
                MicronutrientType.MAGNESIUM to 27.0,
                MicronutrientType.VITAMIN_C to 8.7,
                MicronutrientType.MANGANESE to 0.27,
                MicronutrientType.PHOSPHORUS to 22.0
            ),
            sugar = 12.2,
            addedSugar = 0.0
        ),
        "orange" to FoodItem(
            name = "Orange",
            calories = 49,
            protein = 0.9,
            carbs = 12.0,
            fat = 0.1,
            commonPortions = listOf(
                Portion("1 medium", 131.0),
                Portion("1 small", 96.0),
                Portion("1 large", 184.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 53.0,
                MicronutrientType.VITAMIN_B9 to 30.0,
                MicronutrientType.POTASSIUM to 181.0,
                MicronutrientType.VITAMIN_A to 11.0,
                MicronutrientType.CALCIUM to 40.0,
                MicronutrientType.PHOSPHORUS to 14.0
            ),
            sugar = 9.4,
            addedSugar = 0.0
        ),

        "strawberries" to FoodItem("Strawberries", 32, 0.7, 7.7, 0.3, listOf(
            Portion("1 cup whole", 144.0), Portion("8 medium berries", 144.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 58.8,
                MicronutrientType.MANGANESE to 0.39,
                MicronutrientType.VITAMIN_B9 to 24.0,
                MicronutrientType.POTASSIUM to 153.0,
                MicronutrientType.MAGNESIUM to 13.0,
                MicronutrientType.PHOSPHORUS to 24.0
            ),
            sugar = 4.9,
            addedSugar = 0.0),
        "blueberries" to FoodItem("Blueberries", 57, 0.7, 14.0, 0.3, listOf(
            Portion("1 cup", 148.0), Portion("1/2 cup", 74.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 9.7,
                MicronutrientType.MANGANESE to 0.34,
                MicronutrientType.VITAMIN_K to 19.3,
                MicronutrientType.POTASSIUM to 77.0,
                MicronutrientType.VITAMIN_B6 to 0.05,
                MicronutrientType.IRON to 0.3
            ),
            sugar = 10.0,
            addedSugar = 0.0),
        "raspberries" to FoodItem("Raspberries", 52, 1.2, 12.0, 0.7, listOf(
            Portion("1 cup", 123.0), Portion("1/2 cup", 62.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 26.2,
                MicronutrientType.MANGANESE to 0.67,
                MicronutrientType.VITAMIN_K to 7.8,
                MicronutrientType.POTASSIUM to 151.0,
                MicronutrientType.MAGNESIUM to 22.0,
                MicronutrientType.VITAMIN_B9 to 21.0
            ),
            sugar = 4.4,
            addedSugar = 0.0),
        "blackberries" to FoodItem(
            name = "Blackberries",
            calories = 43,
            protein = 1.4,
            carbs = 10.2,
            fat = 0.5,
            commonPortions = listOf(
                Portion("1 cup", 144.0),
                Portion("1/2 cup", 72.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 21.0,
                MicronutrientType.VITAMIN_K to 20.0,
                MicronutrientType.MANGANESE to 0.6,
                MicronutrientType.VITAMIN_B9 to 25.0
            ),
            sugar = 4.9, // grams per 100g (7g per cup/144g)
            addedSugar = 0.0
        ),
        "pomegranate" to FoodItem(
            name = "Pomegranate Arils",
            calories = 83,
            protein = 1.7,
            carbs = 19.0,
            fat = 1.2,
            commonPortions = listOf(
                Portion("1/2 cup", 87.0),
                Portion("1 cup", 174.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 10.0,
                MicronutrientType.POTASSIUM to 236.0,
                MicronutrientType.VITAMIN_B9 to 38.0,
                MicronutrientType.VITAMIN_K to 16.0
            )
        ),
        "figs" to FoodItem(
            name = "Figs (fresh)",
            calories = 74,
            protein = 0.8,
            carbs = 19.0,
            fat = 0.3,
            commonPortions = listOf(
                Portion("1 medium fig", 50.0),
                Portion("2 figs", 100.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.POTASSIUM to 232.0,
                MicronutrientType.CALCIUM to 35.0,
                MicronutrientType.VITAMIN_K to 4.7,
                MicronutrientType.VITAMIN_B6 to 0.1
            )
        ),
        "dates" to FoodItem(
            name = "Dates (Medjool)",
            calories = 277,
            protein = 1.8,
            carbs = 75.0,
            fat = 0.2,
            commonPortions = listOf(
                Portion("1 date", 24.0),
                Portion("2 dates", 48.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.POTASSIUM to 696.0,
                MicronutrientType.MAGNESIUM to 54.0,
                MicronutrientType.VITAMIN_B6 to 0.25,
                MicronutrientType.IRON to 0.9
            ),
            sugar = 66.5,
            addedSugar = 0.0
        ),

        "avocado" to FoodItem(
            name = "Avocado",
            calories = 160,
            protein = 2.0,
            carbs = 8.5,
            fat = 14.7,
            commonPortions = listOf(
                Portion("1/2 medium avocado", 68.0),
                Portion("1 medium avocado", 136.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_E to 2.1,
                MicronutrientType.POTASSIUM to 485.0,
                MicronutrientType.VITAMIN_B9 to 81.0,
                MicronutrientType.VITAMIN_C to 10.0,
                MicronutrientType.MAGNESIUM to 29.0,
                MicronutrientType.VITAMIN_B6 to 0.26,
                MicronutrientType.IRON to 0.6
            )
        ),

        "kiwi" to FoodItem(
            name = "Kiwi",
            calories = 42,
            protein = 0.8,
            carbs = 10.0,
            fat = 0.4,
            commonPortions = listOf(
                Portion("1 medium kiwi", 76.0),
                Portion("2 medium kiwi", 152.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 92.0,
                MicronutrientType.POTASSIUM to 312.0,
                MicronutrientType.VITAMIN_K to 40.3,
                MicronutrientType.VITAMIN_B9 to 25.0,
                MicronutrientType.MAGNESIUM to 17.0,
                MicronutrientType.PHOSPHORUS to 34.0
            )
        ),
        "strawberries" to FoodItem("Strawberries", 32, 0.7, 7.7, 0.3, listOf(
            Portion("1 cup whole", 144.0), Portion("8 medium berries", 144.0))),
        "blueberries" to FoodItem("Blueberries", 57, 0.7, 14.0, 0.3, listOf(
            Portion("1 cup", 148.0), Portion("1/2 cup", 74.0))),
        "raspberries" to FoodItem("Raspberries", 52, 1.2, 12.0, 0.7, listOf(
            Portion("1 cup", 123.0), Portion("1/2 cup", 62.0))),
        "grapes" to FoodItem("Grapes", 69, 0.7, 18.0, 0.2, listOf(
            Portion("1 cup", 151.0), Portion("10 grapes", 49.0)),
            sugar = 16.0,
            addedSugar = 0.0),
        "pineapple" to FoodItem("Pineapple", 50, 0.5, 13.0, 0.1, listOf(
            Portion("1 cup chunks", 165.0), Portion("1 slice", 37.0)),
            sugar = 9.9,
            addedSugar = 0.0),
        "mango" to FoodItem("Mango", 60, 0.8, 15.0, 0.4, listOf(
            Portion("1 medium mango", 168.0), Portion("1 cup pieces", 165.0)),
            sugar = 13.7,
            addedSugar = 0.0),
        "kiwi" to FoodItem(
            name = "Kiwi",
            calories = 42,
            protein = 0.8,
            carbs = 10.0,
            fat = 0.4,
            commonPortions = listOf(
                Portion("1 medium kiwi", 76.0),
                Portion("2 medium kiwi", 152.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 92.0,
                MicronutrientType.POTASSIUM to 312.0
            )
        ),
        "peach" to FoodItem("Peach", 39, 0.9, 9.5, 0.3, listOf(
            Portion("1 medium peach", 150.0)),
            sugar = 8.4,
            addedSugar = 0.0),
        "pear" to FoodItem("Pear", 57, 0.4, 15.0, 0.1, listOf(
            Portion("1 medium pear", 178.0)),
            sugar = 10.0,
            addedSugar = 0.0),
        "plum" to FoodItem("Plum", 46, 0.7, 11.0, 0.3, listOf(
            Portion("1 medium plum", 66.0))),
        "cherries" to FoodItem("Cherries", 63, 1.1, 16.0, 0.2, listOf(
            Portion("1 cup", 154.0), Portion("10 cherries", 68.0)),
            sugar = 12.8,
            addedSugar = 0.0),
        "watermelon" to FoodItem("Watermelon", 30, 0.6, 7.6, 0.2, listOf(
            Portion("1 cup cubes", 152.0), Portion("1 wedge", 286.0)),
            sugar = 6.2,
            addedSugar = 0.0),
        "cantaloupe" to FoodItem("Cantaloupe", 34, 0.8, 8.2, 0.2, listOf(
            Portion("1 cup cubes", 160.0), Portion("1/4 melon", 134.0)),
            sugar = 7.9,
            addedSugar = 0.0),
        "honeydew" to FoodItem("Honeydew Melon", 36, 0.5, 9.1, 0.1, listOf(
            Portion("1 cup cubes", 170.0), Portion("1/8 melon", 134.0))),

        // ===== BEVERAGES =====
        "water" to FoodItem("Water", 0, 0.0, 0.0, 0.0, listOf(
            Portion("1 cup (8 fl oz)", 237.0), Portion("16 fl oz bottle", 473.0))),
        "coffee" to FoodItem("Coffee (black)", 2, 0.3, 0.0, 0.0, listOf(
            Portion("1 cup (8 fl oz)", 237.0), Portion("12 fl oz", 355.0))),
        "black coffee" to FoodItem("Coffee (black)", 2, 0.3, 0.0, 0.0, listOf(
            Portion("1 cup (8 fl oz)", 237.0), Portion("12 fl oz", 355.0))),
        "tea" to FoodItem("Tea (unsweetened)", 2, 0.0, 0.5, 0.0, listOf(
            Portion("1 cup (8 fl oz)", 237.0), Portion("12 fl oz", 355.0))),
        "green tea" to FoodItem("Green Tea (unsweetened)", 2, 0.0, 0.5, 0.0, listOf(
            Portion("1 cup (8 fl oz)", 237.0), Portion("12 fl oz", 355.0))),
        "black tea" to FoodItem("Black Tea (unsweetened)", 2, 0.0, 0.5, 0.0, listOf(
            Portion("1 cup (8 fl oz)", 237.0), Portion("12 fl oz", 355.0))),

        "orange juice" to FoodItem("Orange Juice", 45, 0.7, 10.4, 0.1, listOf(
            Portion("1 cup (8 fl oz)", 248.0), Portion("12 fl oz", 355.0)),
            sugar = 8.4,
            addedSugar = 0.0),
        "apple juice" to FoodItem("Apple Juice", 46, 0.1, 11.3, 0.1, listOf(
            Portion("1 cup (8 fl oz)", 248.0), Portion("12 fl oz", 355.0)),
            sugar = 9.6,
            addedSugar = 0.0),
        "cranberry juice" to FoodItem("Cranberry Juice", 46, 0.4, 12.2, 0.1, listOf(
            Portion("1 cup (8 fl oz)", 253.0), Portion("12 fl oz", 355.0))),
        "grape juice" to FoodItem("Grape Juice", 60, 0.4, 15.0, 0.1, listOf(
            Portion("1 cup (8 fl oz)", 253.0), Portion("12 fl oz", 355.0))),
        "pineapple juice" to FoodItem("Pineapple Juice", 53, 0.4, 13.0, 0.1, listOf(
            Portion("1 cup (8 fl oz)", 250.0), Portion("12 fl oz", 355.0))),
        "tomato juice" to FoodItem("Tomato Juice", 17, 0.8, 4.2, 0.1, listOf(
            Portion("1 cup (8 fl oz)", 243.0), Portion("12 fl oz", 355.0))),

        "milk" to FoodItem(
            name = "Milk (whole)",
            calories = 61,
            protein = 3.2,
            carbs = 4.8,
            fat = 3.3,
            commonPortions = listOf(
                Portion("1 cup (8 fl oz)", 244.0),
                Portion("12 fl oz", 366.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 113.0,
                MicronutrientType.VITAMIN_D to 124.0,
                MicronutrientType.VITAMIN_B12 to 0.9
            ),
            sugar = 4.8,
            addedSugar = 0.0
        ),
        "whole milk" to FoodItem(
            name = "Milk (whole)",
            calories = 61,
            protein = 3.2,
            carbs = 4.8,
            fat = 3.3,
            commonPortions = listOf(
                Portion("1 cup (8 fl oz)", 244.0),
                Portion("12 fl oz", 366.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 113.0,
                MicronutrientType.VITAMIN_D to 124.0,
                MicronutrientType.VITAMIN_B12 to 0.9
            )
        ),
        "skim milk" to FoodItem("Milk (skim)", 34, 3.4, 5.1, 0.1, listOf(
            Portion("1 cup (8 fl oz)", 244.0), Portion("12 fl oz", 366.0)),
            sugar = 5.1,
            addedSugar = 0.0),
        "low fat milk" to FoodItem("Milk (2%)", 50, 3.3, 4.8, 2.0, listOf(
            Portion("1 cup (8 fl oz)", 244.0), Portion("12 fl oz", 366.0)),
            sugar = 4.8,
            addedSugar = 0.0),
        "almond milk" to FoodItem(
            name = "Almond Milk (unsweetened)",
            calories = 13,
            protein = 0.4,
            carbs = 0.3,
            fat = 1.1,
            commonPortions = listOf(
                Portion("1 cup (8 fl oz)", 240.0),
                Portion("12 fl oz", 355.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 120.0,
                MicronutrientType.VITAMIN_D to 100.0
            )
        ),
        "soy milk" to FoodItem("Soy Milk (unsweetened)", 33, 2.9, 1.5, 1.8, listOf(
            Portion("1 cup (8 fl oz)", 243.0), Portion("12 fl oz", 355.0))),
        "oat milk" to FoodItem("Oat Milk (unsweetened)", 36, 0.3, 6.6, 0.5, listOf(
            Portion("1 cup (8 fl oz)", 240.0), Portion("12 fl oz", 355.0))),
        "coconut milk" to FoodItem("Coconut Milk (light)", 48, 0.2, 1.2, 4.8, listOf(
            Portion("1 cup (8 fl oz)", 240.0), Portion("12 fl oz", 355.0))),

        "protein shake" to FoodItem("Protein Shake (whey)", 120, 24.0, 3.0, 2.0, listOf(
            Portion("1 scoop (30g)", 30.0), Portion("1 serving (240ml)", 270.0))),
        "smoothie" to FoodItem("Fruit Smoothie", 150, 5.0, 30.0, 2.0, listOf(
            Portion("12 fl oz", 355.0), Portion("16 fl oz", 473.0)),
            sugar = 25.0,
            addedSugar = 5.0),
        "energy drink" to FoodItem("Energy Drink", 45, 0.0, 11.0, 0.0, listOf(
            Portion("8 fl oz can", 237.0), Portion("12 fl oz can", 355.0), Portion("16 fl oz can", 473.0)),
            sugar = 11.0,
            addedSugar = 11.0),
        "sports drink" to FoodItem("Sports Drink", 32, 0.0, 8.0, 0.0, listOf(
            Portion("8 fl oz", 237.0), Portion("12 fl oz", 355.0), Portion("20 fl oz", 591.0)),
            sugar = 8.0,
            addedSugar = 8.0),
        "soda" to FoodItem("Cola Soda", 42, 0.0, 10.6, 0.0, listOf(
            Portion("12 fl oz can", 355.0), Portion("20 fl oz bottle", 591.0)),
            sugar = 10.6,
            addedSugar = 10.6),
        "cola" to FoodItem("Cola Soda", 42, 0.0, 10.6, 0.0, listOf(
            Portion("12 fl oz can", 355.0), Portion("20 fl oz bottle", 591.0)),
            sugar = 10.6,
            addedSugar = 10.6),
        "beer" to FoodItem("Beer (light)", 29, 0.2, 1.6, 0.0, listOf(
            Portion("12 fl oz bottle", 355.0), Portion("16 fl oz can", 473.0))),
        "wine" to FoodItem("Red Wine", 85, 0.1, 2.6, 0.0, listOf(
            Portion("5 fl oz glass", 148.0), Portion("25 fl oz bottle", 737.0))),
        "red wine" to FoodItem("Red Wine", 85, 0.1, 2.6, 0.0, listOf(
            Portion("5 fl oz glass", 148.0), Portion("25 fl oz bottle", 737.0))),
        "white wine" to FoodItem("White Wine", 82, 0.1, 2.6, 0.0, listOf(
            Portion("5 fl oz glass", 148.0), Portion("25 fl oz bottle", 737.0))),

        // ===== NUTS & SEEDS =====
        "almonds" to FoodItem(
            name = "Almonds",
            calories = 579,
            protein = 21.0,
            carbs = 22.0,
            fat = 49.0,
            commonPortions = listOf(
                Portion("1 oz (23 almonds)", 28.0),
                Portion("1/4 cup", 30.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_E to 25.6,
                MicronutrientType.MAGNESIUM to 268.0,
                MicronutrientType.ZINC to 3.1,
                MicronutrientType.CALCIUM to 269.0,
                MicronutrientType.IRON to 3.7,
                MicronutrientType.POTASSIUM to 733.0,
                MicronutrientType.PHOSPHORUS to 481.0,
                MicronutrientType.VITAMIN_B2 to 1.1
            )
        ),
        "walnuts" to FoodItem("Walnuts", 654, 15.0, 14.0, 65.0, listOf(
            Portion("1 oz (14 halves)", 28.0), Portion("1/4 cup", 29.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_E to 0.7,
                MicronutrientType.MAGNESIUM to 158.0,
                MicronutrientType.PHOSPHORUS to 346.0,
                MicronutrientType.COPPER to 1.6,
                MicronutrientType.MANGANESE to 3.4,
                MicronutrientType.POTASSIUM to 441.0,
                MicronutrientType.IRON to 2.9
            )),
        "peanuts" to FoodItem(
            name = "Peanuts",
            calories = 567,
            protein = 26.0,
            carbs = 16.0,
            fat = 49.0,
            commonPortions = listOf(
                Portion("1 oz", 28.0),
                Portion("1/4 cup", 37.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B3 to 12.0,
                MicronutrientType.MAGNESIUM to 168.0,
                MicronutrientType.ZINC to 3.3,
                MicronutrientType.PHOSPHORUS to 376.0,
                MicronutrientType.POTASSIUM to 705.0,
                MicronutrientType.COPPER to 1.1,
                MicronutrientType.MANGANESE to 1.9,
                MicronutrientType.VITAMIN_B6 to 1.3
            )
        ),
        "cashews" to FoodItem("Cashews", 553, 18.0, 30.0, 44.0, listOf(
            Portion("1 oz", 28.0), Portion("1/4 cup", 34.0)),
            micronutrients = mapOf(
                MicronutrientType.MAGNESIUM to 292.0,
                MicronutrientType.PHOSPHORUS to 593.0,
                MicronutrientType.COPPER to 2.2,
                MicronutrientType.MANGANESE to 1.7,
                MicronutrientType.IRON to 6.7,
                MicronutrientType.ZINC to 5.8,
                MicronutrientType.SELENIUM to 19.9
            )),
        "pistachios" to FoodItem("Pistachios", 562, 20.0, 28.0, 45.0, listOf(
            Portion("1 oz (49 kernels)", 28.0), Portion("1/4 cup", 30.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B6 to 1.7,
                MicronutrientType.PHOSPHORUS to 490.0,
                MicronutrientType.POTASSIUM to 1025.0,
                MicronutrientType.COPPER to 1.3,
                MicronutrientType.MANGANESE to 1.2,
                MicronutrientType.VITAMIN_B1 to 0.9
            )),
        "pecans" to FoodItem(
            name = "Pecans",
            calories = 691,
            protein = 9.2,
            carbs = 13.9,
            fat = 72.0,
            commonPortions = listOf(
                Portion("1 oz (19 halves)", 28.0),
                Portion("1/4 cup", 30.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_E to 1.4,
                MicronutrientType.MAGNESIUM to 121.0,
                MicronutrientType.PHOSPHORUS to 277.0,
                MicronutrientType.POTASSIUM to 410.0,
                MicronutrientType.COPPER to 1.2,
                MicronutrientType.MANGANESE to 4.5,
                MicronutrientType.ZINC to 4.5,
                MicronutrientType.VITAMIN_B1 to 0.7
            )
        ),
        "chia seeds" to FoodItem("Chia Seeds", 486, 17.0, 42.0, 31.0, listOf(
            Portion("1 tbsp", 12.0), Portion("1 oz", 28.0)),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 631.0,
                MicronutrientType.PHOSPHORUS to 860.0,
                MicronutrientType.POTASSIUM to 407.0,
                MicronutrientType.MAGNESIUM to 335.0,
                MicronutrientType.IRON to 7.7,
                MicronutrientType.ZINC to 4.6,
                MicronutrientType.COPPER to 0.9
            )),
        "flax seeds" to FoodItem("Flax Seeds", 534, 18.0, 29.0, 42.0, listOf(
            Portion("1 tbsp", 7.0), Portion("1 oz", 28.0)),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B1 to 1.6,
                MicronutrientType.CALCIUM to 255.0,
                MicronutrientType.IRON to 5.7,
                MicronutrientType.MAGNESIUM to 392.0,
                MicronutrientType.PHOSPHORUS to 642.0,
                MicronutrientType.POTASSIUM to 813.0
            )),
        "pumpkin seeds" to FoodItem(
            name = "Pumpkin Seeds",
            calories = 559,
            protein = 30.0,
            carbs = 11.0,
            fat = 49.0,
            commonPortions = listOf(
                Portion("1 oz", 28.0),
                Portion("1/4 cup", 30.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.MAGNESIUM to 592.0,
                MicronutrientType.ZINC to 7.8,
                MicronutrientType.IRON to 8.8,
                MicronutrientType.PHOSPHORUS to 1233.0,
                MicronutrientType.MANGANESE to 4.5,
                MicronutrientType.COPPER to 1.3,
                MicronutrientType.POTASSIUM to 919.0
            )
        ),
        "sunflower seeds" to FoodItem(
            name = "Sunflower Seeds",
            calories = 584,
            protein = 21.0,
            carbs = 20.0,
            fat = 51.0,
            commonPortions = listOf(
                Portion("1 oz", 28.0),
                Portion("1/4 cup", 35.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_E to 35.0,
                MicronutrientType.MAGNESIUM to 325.0,
                MicronutrientType.SELENIUM to 53.0,
                MicronutrientType.PHOSPHORUS to 660.0,
                MicronutrientType.IRON to 6.8,
                MicronutrientType.ZINC to 5.3,
                MicronutrientType.COPPER to 1.8,
                MicronutrientType.VITAMIN_B6 to 1.3
            )
        ),
        "brazil nuts" to FoodItem(
            name = "Brazil Nuts",
            calories = 659,
            protein = 14.0,
            carbs = 11.0,
            fat = 67.0,
            commonPortions = listOf(
                Portion("1 oz", 28.0),
                Portion("1/4 cup", 34.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.SELENIUM to 1917.0,
                MicronutrientType.MAGNESIUM to 376.0,
                MicronutrientType.PHOSPHORUS to 725.0,
                MicronutrientType.COPPER to 1.7,
                MicronutrientType.ZINC to 4.1,
                MicronutrientType.VITAMIN_E to 5.7,
                MicronutrientType.CALCIUM to 160.0
            )
        ),
        "almond butter" to FoodItem(
            name = "Almond Butter",
            calories = 614,
            protein = 21.0,
            carbs = 19.0,
            fat = 55.0,
            commonPortions = listOf(
                Portion("1 tbsp", 16.0),
                Portion("2 tbsp", 32.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_E to 24.0,
                MicronutrientType.MAGNESIUM to 279.0,
                MicronutrientType.POTASSIUM to 746.0,
                MicronutrientType.PHOSPHORUS to 420.0
            )
        ),
        "cashew butter" to FoodItem(
            name = "Cashew Butter",
            calories = 587,
            protein = 18.0,
            carbs = 27.0,
            fat = 43.0,
            commonPortions = listOf(
                Portion("1 tbsp", 16.0),
                Portion("2 tbsp", 32.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.MAGNESIUM to 292.0,
                MicronutrientType.IRON to 6.0,
                MicronutrientType.ZINC to 5.0,
                MicronutrientType.COPPER to 2.2
            )
        ),
        "tahini" to FoodItem(
            name = "Tahini",
            calories = 595,
            protein = 17.0,
            carbs = 21.0,
            fat = 54.0,
            commonPortions = listOf(
                Portion("1 tbsp", 15.0),
                Portion("2 tbsp", 30.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 426.0,
                MicronutrientType.IRON to 5.6,
                MicronutrientType.MAGNESIUM to 95.0,
                MicronutrientType.PHOSPHORUS to 595.0
            )
        ),

        // ===== FAST FOOD & PROCESSED =====
        "pizza slice" to FoodItem("Pizza Slice (cheese)", 285, 12.0, 36.0, 10.0, listOf(
            Portion("1 slice (107g)", 107.0), Portion("2 slices", 214.0))),
        "hamburger" to FoodItem("Hamburger", 295, 17.0, 30.0, 13.0, listOf(
            Portion("1 medium burger", 150.0))),
        "french fries" to FoodItem("French Fries", 312, 3.4, 41.0, 15.0, listOf(
            Portion("Small serving", 71.0), Portion("Medium serving", 117.0))),
        "chicken nuggets" to FoodItem("Chicken Nuggets", 297, 16.0, 16.0, 18.0, listOf(
            Portion("6 nuggets", 102.0), Portion("10 nuggets", 170.0))),
        "potato chips" to FoodItem("Potato Chips", 536, 7.0, 53.0, 35.0, listOf(
            Portion("1 oz serving", 28.0), Portion("1 cup", 35.0))),
        "chocolate bar" to FoodItem("Milk Chocolate Bar", 535, 7.7, 59.0, 30.0, listOf(
            Portion("1.5 oz bar", 43.0), Portion("3.5 oz bar", 100.0)),
            sugar = 51.5,
            addedSugar = 51.5),
        "ice cream" to FoodItem("Vanilla Ice Cream", 207, 3.5, 24.0, 11.0, listOf(
            Portion("1/2 cup", 66.0), Portion("1 cup", 132.0)),
            sugar = 21.2,
            addedSugar = 21.2),
        "cookies" to FoodItem("Chocolate Chip Cookie", 492, 6.0, 68.0, 23.0, listOf(
            Portion("1 medium cookie", 16.0), Portion("3 cookies", 48.0)),
            sugar = 36.0,
            addedSugar = 36.0),
        "cake" to FoodItem("Chocolate Cake", 389, 5.0, 61.0, 16.0, listOf(
            Portion("1 slice", 100.0), Portion("1 piece", 80.0)),
            sugar = 47.0,
            addedSugar = 47.0),
        "donut" to FoodItem("Glazed Donut", 452, 6.0, 51.0, 25.0, listOf(
            Portion("1 medium donut", 60.0), Portion("1 large donut", 100.0)),
            sugar = 22.0,
            addedSugar = 22.0),

        // ===== CONDIMENTS & SAUCES =====
        "olive oil" to FoodItem("Olive Oil", 884, 0.0, 0.0, 100.0, listOf(
            Portion("1 tsp", 4.5), Portion("1 tbsp", 13.5), Portion("1/4 cup", 54.0))),
        "butter" to FoodItem("Butter", 717, 0.9, 0.1, 81.0, listOf(
            Portion("1 tsp", 5.0), Portion("1 tbsp", 14.0), Portion("1/4 cup", 57.0))),
        "peanut butter" to FoodItem("Peanut Butter", 588, 24.0, 20.0, 50.0, listOf(
            Portion("1 tbsp", 16.0), Portion("2 tbsp", 32.0))),
        "honey" to FoodItem("Honey", 304, 0.3, 82.0, 0.0, listOf(
            Portion("1 tsp", 7.0), Portion("1 tbsp", 21.0)),
            sugar = 82.0,
            addedSugar = 82.0),
        "maple syrup" to FoodItem("Maple Syrup", 260, 0.0, 67.0, 0.0, listOf(
            Portion("1 tsp", 5.0), Portion("1 tbsp", 20.0)),
            sugar = 67.0,
            addedSugar = 67.0),
        "ketchup" to FoodItem("Ketchup", 20, 1.0, 5.0, 0.1, listOf(
            Portion("1 tsp", 5.0), Portion("1 tbsp", 17.0)),
            sugar = 3.7,
            addedSugar = 3.7),
        "mustard" to FoodItem("Mustard", 66, 4.0, 8.0, 4.0, listOf(
            Portion("1 tsp", 5.0), Portion("1 tbsp", 15.0))),
        "mayonnaise" to FoodItem("Mayonnaise", 680, 1.0, 1.0, 75.0, listOf(
            Portion("1 tsp", 5.0), Portion("1 tbsp", 14.0))),
        "soy sauce" to FoodItem("Soy Sauce", 53, 8.0, 4.9, 0.1, listOf(
            Portion("1 tsp", 6.0), Portion("1 tbsp", 18.0))),
        "hot sauce" to FoodItem("Hot Sauce", 6, 0.4, 1.3, 0.0, listOf(
            Portion("1 tsp", 5.0), Portion("1 tbsp", 17.0))),
        
        // ===== SPICES =====
        "cumin" to FoodItem(
            name = "Cumin (ground)",
            calories = 375,
            protein = 17.8,
            carbs = 44.2,
            fat = 22.3,
            commonPortions = listOf(
                Portion("1 tsp", 2.1),
                Portion("1 tbsp", 6.4)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 66.4,
                MicronutrientType.MANGANESE to 3.3,
                MicronutrientType.CALCIUM to 931.0,
                MicronutrientType.MAGNESIUM to 366.0,
                MicronutrientType.PHOSPHORUS to 499.0,
                MicronutrientType.POTASSIUM to 1788.0,
                MicronutrientType.ZINC to 4.8
            )
        ),
        "paprika" to FoodItem(
            name = "Paprika (ground)",
            calories = 282,
            protein = 14.1,
            carbs = 54.0,
            fat = 12.9,
            commonPortions = listOf(
                Portion("1 tsp", 2.3),
                Portion("1 tbsp", 7.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_A to 2463.0,
                MicronutrientType.VITAMIN_E to 29.1,
                MicronutrientType.VITAMIN_C to 71.0,
                MicronutrientType.IRON to 21.1,
                MicronutrientType.MANGANESE to 1.6,
                MicronutrientType.VITAMIN_B6 to 2.1
            )
        ),
        "turmeric" to FoodItem(
            name = "Turmeric (ground)",
            calories = 354,
            protein = 7.8,
            carbs = 64.9,
            fat = 9.9,
            commonPortions = listOf(
                Portion("1 tsp", 2.5),
                Portion("1 tbsp", 7.5)
            ),
            micronutrients = mapOf(
                MicronutrientType.MANGANESE to 7.8,
                MicronutrientType.IRON to 41.4,
                MicronutrientType.POTASSIUM to 2080.0,
                MicronutrientType.MAGNESIUM to 193.0,
                MicronutrientType.PHOSPHORUS to 268.0,
                MicronutrientType.VITAMIN_B6 to 1.8
            )
        ),
        "cinnamon" to FoodItem(
            name = "Cinnamon (ground)",
            calories = 247,
            protein = 4.0,
            carbs = 80.6,
            fat = 1.2,
            commonPortions = listOf(
                Portion("1 tsp", 2.6),
                Portion("1 tbsp", 7.8)
            ),
            micronutrients = mapOf(
                MicronutrientType.MANGANESE to 4.3,
                MicronutrientType.CALCIUM to 1002.0,
                MicronutrientType.IRON to 8.2,
                MicronutrientType.POTASSIUM to 431.0
            )
        ),
        "cayenne pepper" to FoodItem(
            name = "Cayenne Pepper (ground)",
            calories = 318,
            protein = 12.0,
            carbs = 56.6,
            fat = 17.3,
            commonPortions = listOf(
                Portion("1/4 tsp", 0.5),
                Portion("1 tsp", 2.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_A to 41610.0,
                MicronutrientType.VITAMIN_C to 76.4,
                MicronutrientType.VITAMIN_E to 29.8,
                MicronutrientType.VITAMIN_B6 to 2.5,
                MicronutrientType.VITAMIN_K to 80.3,
                MicronutrientType.POTASSIUM to 2014.0,
                MicronutrientType.MANGANESE to 2.0
            )
        ),
        "chili powder" to FoodItem(
            name = "Chili Powder",
            calories = 282,
            protein = 13.5,
            carbs = 49.7,
            fat = 14.3,
            commonPortions = listOf(
                Portion("1 tsp", 2.7),
                Portion("1 tbsp", 8.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_A to 1500.0,
                MicronutrientType.IRON to 19.1,
                MicronutrientType.MANGANESE to 1.2,
                MicronutrientType.POTASSIUM to 1950.0,
                MicronutrientType.VITAMIN_C to 14.0
            )
        ),
        "garlic powder" to FoodItem(
            name = "Garlic Powder",
            calories = 331,
            protein = 16.6,
            carbs = 72.7,
            fat = 0.7,
            commonPortions = listOf(
                Portion("1 tsp", 3.1),
                Portion("1 tbsp", 9.3)
            ),
            micronutrients = mapOf(
                MicronutrientType.MANGANESE to 1.7,
                MicronutrientType.VITAMIN_B6 to 1.7,
                MicronutrientType.VITAMIN_C to 1.2,
                MicronutrientType.SELENIUM to 14.2,
                MicronutrientType.PHOSPHORUS to 169.0
            )
        ),
        "onion powder" to FoodItem(
            name = "Onion Powder",
            calories = 341,
            protein = 10.4,
            carbs = 79.1,
            fat = 1.0,
            commonPortions = listOf(
                Portion("1 tsp", 2.4),
                Portion("1 tbsp", 7.2)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 21.0,
                MicronutrientType.POTASSIUM to 1050.0,
                MicronutrientType.MANGANESE to 0.4,
                MicronutrientType.VITAMIN_B6 to 0.3
            )
        ),
        "oregano" to FoodItem(
            name = "Oregano (dried)",
            calories = 265,
            protein = 9.0,
            carbs = 68.9,
            fat = 4.3,
            commonPortions = listOf(
                Portion("1 tsp", 1.0),
                Portion("1 tbsp", 3.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_K to 621.7,
                MicronutrientType.IRON to 36.8,
                MicronutrientType.MANGANESE to 4.9,
                MicronutrientType.CALCIUM to 1597.0,
                MicronutrientType.VITAMIN_E to 18.3
            )
        ),
        "thyme" to FoodItem(
            name = "Thyme (dried)",
            calories = 276,
            protein = 9.1,
            carbs = 63.9,
            fat = 7.4,
            commonPortions = listOf(
                Portion("1 tsp", 1.0),
                Portion("1 tbsp", 3.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_K to 1714.5,
                MicronutrientType.IRON to 123.6,
                MicronutrientType.MANGANESE to 7.9,
                MicronutrientType.CALCIUM to 1890.0,
                MicronutrientType.VITAMIN_C to 160.0
            )
        ),
        "rosemary" to FoodItem(
            name = "Rosemary (dried)",
            calories = 331,
            protein = 4.9,
            carbs = 64.1,
            fat = 15.2,
            commonPortions = listOf(
                Portion("1 tsp", 1.4),
                Portion("1 tbsp", 4.2)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 29.2,
                MicronutrientType.CALCIUM to 1280.0,
                MicronutrientType.MANGANESE to 0.9,
                MicronutrientType.MAGNESIUM to 220.0,
                MicronutrientType.VITAMIN_A to 156.0
            )
        ),
        "basil" to FoodItem(
            name = "Basil (dried)",
            calories = 233,
            protein = 14.4,
            carbs = 47.8,
            fat = 4.1,
            commonPortions = listOf(
                Portion("1 tsp", 0.7),
                Portion("1 tbsp", 2.1)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_K to 1714.5,
                MicronutrientType.IRON to 89.8,
                MicronutrientType.CALCIUM to 2240.0,
                MicronutrientType.MANGANESE to 9.8,
                MicronutrientType.VITAMIN_A to 744.0
            )
        ),
        "ginger" to FoodItem(
            name = "Ginger (ground)",
            calories = 335,
            protein = 9.0,
            carbs = 71.6,
            fat = 4.2,
            commonPortions = listOf(
                Portion("1 tsp", 2.0),
                Portion("1 tbsp", 6.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.MANGANESE to 33.3,
                MicronutrientType.IRON to 19.8,
                MicronutrientType.MAGNESIUM to 214.0,
                MicronutrientType.PHOSPHORUS to 168.0,
                MicronutrientType.POTASSIUM to 1320.0
            )
        ),
        "coriander" to FoodItem(
            name = "Coriander (ground)",
            calories = 298,
            protein = 12.4,
            carbs = 55.0,
            fat = 17.8,
            commonPortions = listOf(
                Portion("1 tsp", 1.8),
                Portion("1 tbsp", 5.4)
            ),
            micronutrients = mapOf(
                MicronutrientType.IRON to 16.3,
                MicronutrientType.MANGANESE to 1.9,
                MicronutrientType.CALCIUM to 709.0,
                MicronutrientType.MAGNESIUM to 330.0,
                MicronutrientType.PHOSPHORUS to 409.0
            )
        ),
        "cardamom" to FoodItem(
            name = "Cardamom (ground)",
            calories = 311,
            protein = 10.8,
            carbs = 68.5,
            fat = 6.7,
            commonPortions = listOf(
                Portion("1 tsp", 2.0),
                Portion("1 tbsp", 6.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.MANGANESE to 28.0,
                MicronutrientType.IRON to 13.9,
                MicronutrientType.MAGNESIUM to 229.0,
                MicronutrientType.CALCIUM to 383.0,
                MicronutrientType.PHOSPHORUS to 178.0
            )
        ),
        "nutmeg" to FoodItem(
            name = "Nutmeg (ground)",
            calories = 525,
            protein = 5.8,
            carbs = 49.3,
            fat = 36.3,
            commonPortions = listOf(
                Portion("1/4 tsp", 0.6),
                Portion("1 tsp", 2.2)
            ),
            micronutrients = mapOf(
                MicronutrientType.MANGANESE to 2.9,
                MicronutrientType.COPPER to 1.0,
                MicronutrientType.MAGNESIUM to 183.0,
                MicronutrientType.PHOSPHORUS to 213.0,
                MicronutrientType.VITAMIN_B1 to 0.3
            )
        ),
        "allspice" to FoodItem(
            name = "Allspice (ground)",
            calories = 263,
            protein = 6.1,
            carbs = 72.1,
            fat = 8.7,
            commonPortions = listOf(
                Portion("1 tsp", 2.1),
                Portion("1 tbsp", 6.3)
            ),
            micronutrients = mapOf(
                MicronutrientType.MANGANESE to 2.9,
                MicronutrientType.IRON to 7.1,
                MicronutrientType.CALCIUM to 661.0,
                MicronutrientType.MAGNESIUM to 135.0,
                MicronutrientType.VITAMIN_C to 39.2
            )
        ),
        "cloves" to FoodItem(
            name = "Cloves (ground)",
            calories = 274,
            protein = 6.0,
            carbs = 65.5,
            fat = 13.0,
            commonPortions = listOf(
                Portion("1 tsp", 2.1),
                Portion("1 tbsp", 6.3)
            ),
            micronutrients = mapOf(
                MicronutrientType.MANGANESE to 60.1,
                MicronutrientType.VITAMIN_K to 141.8,
                MicronutrientType.CALCIUM to 632.0,
                MicronutrientType.MAGNESIUM to 259.0
            )
        ),
        
        // ===== HOT PEPPERS =====
        "jalapeño" to FoodItem(
            name = "Jalapeño Pepper (raw)",
            calories = 29,
            protein = 0.9,
            carbs = 6.5,
            fat = 0.4,
            commonPortions = listOf(
                Portion("1 pepper (14g)", 14.0),
                Portion("1 cup sliced", 90.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 118.6,
                MicronutrientType.VITAMIN_A to 54.0,
                MicronutrientType.VITAMIN_K to 18.5,
                MicronutrientType.POTASSIUM to 248.0,
                MicronutrientType.VITAMIN_B6 to 0.4,
                MicronutrientType.VITAMIN_B9 to 27.0
            )
        ),
        "habanero" to FoodItem(
            name = "Habanero Pepper (raw)",
            calories = 40,
            protein = 1.9,
            carbs = 9.0,
            fat = 0.2,
            commonPortions = listOf(
                Portion("1 pepper (17g)", 17.0),
                Portion("1 tbsp chopped", 8.5)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 228.0,
                MicronutrientType.VITAMIN_A to 300.0,
                MicronutrientType.VITAMIN_B6 to 0.7,
                MicronutrientType.POTASSIUM to 293.0,
                MicronutrientType.VITAMIN_B9 to 23.0
            )
        ),
        "serrano pepper" to FoodItem(
            name = "Serrano Pepper (raw)",
            calories = 32,
            protein = 1.7,
            carbs = 7.0,
            fat = 0.4,
            commonPortions = listOf(
                Portion("1 pepper (5g)", 5.0),
                Portion("1 cup chopped", 105.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 44.9,
                MicronutrientType.VITAMIN_A to 47.0,
                MicronutrientType.VITAMIN_K to 11.8,
                MicronutrientType.POTASSIUM to 305.0,
                MicronutrientType.VITAMIN_B6 to 0.5
            )
        ),
        "cayenne pepper fresh" to FoodItem(
            name = "Cayenne Pepper (fresh)",
            calories = 40,
            protein = 1.5,
            carbs = 8.8,
            fat = 0.4,
            commonPortions = listOf(
                Portion("1 pepper (2g)", 2.0),
                Portion("1 tbsp chopped", 5.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 76.4,
                MicronutrientType.VITAMIN_A to 4161.0,
                MicronutrientType.VITAMIN_E to 3.0,
                MicronutrientType.VITAMIN_K to 14.3,
                MicronutrientType.POTASSIUM to 322.0
            )
        ),
        "thai chili" to FoodItem(
            name = "Thai Chili Pepper (raw)",
            calories = 40,
            protein = 2.0,
            carbs = 9.0,
            fat = 0.2,
            commonPortions = listOf(
                Portion("1 pepper (2g)", 2.0),
                Portion("1 tbsp chopped", 5.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 143.7,
                MicronutrientType.VITAMIN_A to 952.0,
                MicronutrientType.VITAMIN_B6 to 0.5,
                MicronutrientType.POTASSIUM to 322.0,
                MicronutrientType.VITAMIN_B9 to 23.0
            )
        ),
        "scotch bonnet" to FoodItem(
            name = "Scotch Bonnet Pepper (raw)",
            calories = 40,
            protein = 1.9,
            carbs = 9.0,
            fat = 0.2,
            commonPortions = listOf(
                Portion("1 pepper (9g)", 9.0),
                Portion("1 tbsp chopped", 5.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 144.0,
                MicronutrientType.VITAMIN_A to 300.0,
                MicronutrientType.VITAMIN_B6 to 0.5,
                MicronutrientType.POTASSIUM to 293.0
            )
        ),
        "chipotle pepper" to FoodItem(
            name = "Chipotle Pepper (dried)",
            calories = 281,
            protein = 12.0,
            carbs = 56.6,
            fat = 15.9,
            commonPortions = listOf(
                Portion("1 pepper (2g)", 2.0),
                Portion("1 tbsp ground", 5.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_A to 2081.0,
                MicronutrientType.VITAMIN_C to 76.4,
                MicronutrientType.VITAMIN_E to 29.8,
                MicronutrientType.VITAMIN_K to 80.3,
                MicronutrientType.POTASSIUM to 1870.0,
                MicronutrientType.IRON to 2.5
            )
        ),
        "poblano pepper" to FoodItem(
            name = "Poblano Pepper (raw)",
            calories = 20,
            protein = 1.0,
            carbs = 4.8,
            fat = 0.2,
            commonPortions = listOf(
                Portion("1 pepper (85g)", 85.0),
                Portion("1 cup chopped", 149.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 93.0,
                MicronutrientType.VITAMIN_A to 198.0,
                MicronutrientType.VITAMIN_K to 10.0,
                MicronutrientType.POTASSIUM to 175.0,
                MicronutrientType.VITAMIN_B6 to 0.3
            )
        ),
        "anaheim pepper" to FoodItem(
            name = "Anaheim Pepper (raw)",
            calories = 29,
            protein = 1.4,
            carbs = 7.0,
            fat = 0.2,
            commonPortions = listOf(
                Portion("1 pepper (45g)", 45.0),
                Portion("1 cup chopped", 144.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 82.0,
                MicronutrientType.VITAMIN_A to 50.0,
                MicronutrientType.POTASSIUM to 211.0,
                MicronutrientType.VITAMIN_B6 to 0.3
            )
        ),
        "guacamole" to FoodItem(
            name = "Guacamole",
            calories = 160,
            protein = 2.0,
            carbs = 9.0,
            fat = 15.0,
            commonPortions = listOf(
                Portion("2 tbsp", 30.0),
                Portion("1/4 cup", 60.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_E to 2.2,
                MicronutrientType.VITAMIN_B9 to 81.0,
                MicronutrientType.POTASSIUM to 485.0
            )
        ),
        "pesto" to FoodItem(
            name = "Pesto",
            calories = 458,
            protein = 5.0,
            carbs = 12.0,
            fat = 47.0,
            commonPortions = listOf(
                Portion("2 tbsp", 36.0),
                Portion("1/4 cup", 72.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_K to 260.0,
                MicronutrientType.VITAMIN_E to 7.1,
                MicronutrientType.MAGNESIUM to 52.0
            )
        ),
        "salsa" to FoodItem(
            name = "Salsa",
            calories = 36,
            protein = 1.5,
            carbs = 7.6,
            fat = 0.2,
            commonPortions = listOf(
                Portion("2 tbsp", 30.0),
                Portion("1/2 cup", 120.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 23.0,
                MicronutrientType.POTASSIUM to 330.0,
                MicronutrientType.VITAMIN_A to 40.0
            )
        ),
        "tzatziki" to FoodItem(
            name = "Tzatziki",
            calories = 60,
            protein = 5.0,
            carbs = 6.0,
            fat = 3.5,
            commonPortions = listOf(
                Portion("2 tbsp", 30.0),
                Portion("1/2 cup", 120.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.CALCIUM to 100.0,
                MicronutrientType.VITAMIN_A to 71.0,
                MicronutrientType.VITAMIN_C to 2.0
            )
        ),
        "coconut oil" to FoodItem(
            name = "Coconut Oil",
            calories = 892,
            protein = 0.0,
            carbs = 0.0,
            fat = 100.0,
            commonPortions = listOf(
                Portion("1 tsp", 4.5),
                Portion("1 tbsp", 14.0)
            ),
            micronutrients = emptyMap()
        ),
        "avocado oil" to FoodItem(
            name = "Avocado Oil",
            calories = 884,
            protein = 0.0,
            carbs = 0.0,
            fat = 100.0,
            commonPortions = listOf(
                Portion("1 tsp", 4.5),
                Portion("1 tbsp", 14.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_E to 13.5
            )
        ),

        // ===== SNACKS =====
        "granola bar" to FoodItem("Granola Bar", 471, 10.0, 64.0, 20.0, listOf(
            Portion("1 bar (24g)", 24.0), Portion("2 bars", 48.0)),
            sugar = 29.0,
            addedSugar = 25.0),
        "protein bar" to FoodItem("Protein Bar", 410, 30.0, 40.0, 15.0, listOf(
            Portion("1 bar (45g)", 45.0), Portion("2 bars", 90.0)),
            sugar = 15.0,
            addedSugar = 12.0),
        "trail mix" to FoodItem("Trail Mix", 462, 13.0, 44.0, 29.0, listOf(
            Portion("1/4 cup", 35.0), Portion("1/2 cup", 70.0))),
        "popcorn" to FoodItem("Air-Popped Popcorn", 387, 12.0, 78.0, 4.0, listOf(
            Portion("3 cups popped", 24.0), Portion("1 cup popped", 8.0))),
        "pretzels" to FoodItem("Pretzels", 380, 10.0, 80.0, 2.0, listOf(
            Portion("1 oz serving", 28.0), Portion("2 oz serving", 56.0))),
        "rice cakes" to FoodItem("Rice Cakes", 387, 8.0, 81.0, 1.0, listOf(
            Portion("1 cake", 9.0), Portion("2 cakes", 18.0))),
        "yogurt covered raisins" to FoodItem("Yogurt Covered Raisins", 436, 6.0, 74.0, 15.0, listOf(
            Portion("1/4 cup", 40.0), Portion("1/2 cup", 80.0))),
        "dark chocolate" to FoodItem("Dark Chocolate (70%)", 604, 7.8, 46.0, 43.0, listOf(
            Portion("1 oz square", 28.0), Portion("3 pieces", 30.0)),
            sugar = 24.0,
            addedSugar = 24.0)
        ,

        // ===== BREAKFAST FAVORITES =====
        "scrambled eggs" to FoodItem("Scrambled Eggs", 148, 9.9, 2.1, 10.5, listOf(
            Portion("1/2 cup", 120.0), Portion("2 eggs", 100.0))),
        "omelette" to FoodItem("Cheese Omelette", 154, 10.6, 1.3, 11.0, listOf(
            Portion("1 omelette", 120.0), Portion("1/2 omelette", 60.0))),
        "waffles" to FoodItem("Waffles", 291, 7.9, 34.0, 13.0, listOf(
            Portion("1 round waffle", 75.0), Portion("2 waffles", 150.0))),
        "pancakes" to FoodItem("Pancakes", 227, 6.0, 28.0, 10.0, listOf(
            Portion("1 pancake", 65.0), Portion("Stack of 3", 195.0))),
        "avocado toast" to FoodItem("Avocado Toast", 192, 5.0, 20.0, 10.0, listOf(
            Portion("1 slice", 90.0), Portion("2 slices", 180.0))),
        "yogurt parfait" to FoodItem("Yogurt Parfait", 146, 6.0, 20.0, 4.0, listOf(
            Portion("1 cup", 220.0), Portion("12 oz cup", 330.0))),
        "chia pudding" to FoodItem("Chia Pudding", 120, 5.0, 12.0, 6.0, listOf(
            Portion("1/2 cup", 120.0), Portion("1 cup", 240.0))),
        "protein oatmeal" to FoodItem("Protein Oatmeal", 105, 7.0, 15.0, 3.0, listOf(
            Portion("1 packet prepared", 220.0), Portion("1 cup prepared", 240.0))),

        // ===== LUNCH & SALAD OPTIONS =====
        "turkey bacon" to FoodItem("Turkey Bacon", 282, 29.0, 3.1, 18.0, listOf(
            Portion("2 slices", 28.0), Portion("4 slices", 56.0))),
        "grilled chicken salad" to FoodItem("Grilled Chicken Salad", 110, 12.0, 6.0, 4.0, listOf(
            Portion("1 bowl", 220.0), Portion("1/2 bowl", 110.0))),
        "caesar salad" to FoodItem("Caesar Salad", 190, 7.0, 8.0, 14.0, listOf(
            Portion("Side salad", 140.0), Portion("Entree salad", 280.0))),
        "greek salad" to FoodItem("Greek Salad", 120, 4.0, 6.0, 9.0, listOf(
            Portion("Side salad", 150.0), Portion("Entree salad", 300.0))),
        "cobb salad" to FoodItem("Cobb Salad", 150, 9.0, 8.0, 10.0, listOf(
            Portion("1 bowl", 250.0), Portion("1/2 bowl", 125.0))),
        "veggie burger" to FoodItem("Veggie Burger", 124, 12.0, 10.0, 4.0, listOf(
            Portion("1 patty", 113.0), Portion("1 burger", 170.0))),
        "beyond burger" to FoodItem("Beyond Burger", 190, 19.0, 8.0, 12.0, listOf(
            Portion("1 patty", 113.0))),

        // ===== GLOBAL FAVORITES =====
        "burrito" to FoodItem("Burrito", 206, 10.0, 22.0, 8.0, listOf(
            Portion("1 burrito", 320.0), Portion("Half burrito", 160.0))),
        "breakfast burrito" to FoodItem("Breakfast Burrito", 230, 12.0, 24.0, 9.0, listOf(
            Portion("1 burrito", 300.0))),
        "taco" to FoodItem("Taco", 226, 10.0, 23.0, 11.0, listOf(
            Portion("1 taco", 120.0), Portion("2 tacos", 240.0))),
        "tacos" to FoodItem("Taco", 226, 10.0, 23.0, 11.0, listOf(
            Portion("1 taco", 120.0), Portion("3 tacos", 360.0))),
        "quesadilla" to FoodItem("Cheese Quesadilla", 330, 14.0, 27.0, 18.0, listOf(
            Portion("Half quesadilla", 150.0), Portion("Full quesadilla", 300.0))),
        "nachos" to FoodItem("Chicken Nachos", 260, 16.0, 22.0, 12.0, listOf(
            Portion("1 plate", 240.0))),
        "sushi roll" to FoodItem("Sushi Roll", 142, 7.0, 28.0, 2.0, listOf(
            Portion("1 roll", 200.0), Portion("1/2 roll", 100.0))),
        "sushi" to FoodItem("Sushi Roll", 142, 7.0, 28.0, 2.0, listOf(
            Portion("1 roll", 200.0))),
        "nigiri" to FoodItem("Nigiri", 140, 12.0, 24.0, 2.0, listOf(
            Portion("2 pieces", 90.0), Portion("6 pieces", 270.0))),
        "poke bowl" to FoodItem("Poke Bowl", 155, 12.0, 18.0, 4.0, listOf(
            Portion("1 bowl", 300.0), Portion("Small bowl", 200.0))),
        "fried rice" to FoodItem("Chicken Fried Rice", 163, 4.0, 25.0, 5.0, listOf(
            Portion("1 cup", 200.0), Portion("2 cups", 400.0))),
        "pad thai" to FoodItem("Pad Thai", 240, 10.0, 32.0, 8.0, listOf(
            Portion("1 cup", 220.0), Portion("2 cups", 440.0))),
        "ramen" to FoodItem("Ramen", 150, 6.0, 26.0, 3.0, listOf(
            Portion("1 bowl", 350.0))),
        "pho" to FoodItem("Pho", 90, 6.0, 11.0, 2.0, listOf(
            Portion("1 bowl", 400.0))),
        "bibimbap" to FoodItem("Bibimbap", 165, 8.0, 22.0, 5.0, listOf(
            Portion("1 bowl", 350.0))),
        "biryani" to FoodItem("Chicken Biryani", 202, 12.0, 24.0, 6.0, listOf(
            Portion("1 cup", 250.0), Portion("2 cups", 500.0))),
        "butter chicken" to FoodItem("Butter Chicken", 222, 12.0, 11.0, 12.0, listOf(
            Portion("1 cup", 240.0), Portion("1/2 cup", 120.0))),
        "paneer tikka" to FoodItem("Paneer Tikka", 202, 16.0, 6.0, 12.0, listOf(
            Portion("4 cubes", 110.0), Portion("8 cubes", 220.0))),
        "falafel" to FoodItem("Falafel", 333, 13.0, 31.0, 17.0, listOf(
            Portion("3 falafel", 150.0), Portion("5 falafel", 250.0))),
        "hummus" to FoodItem("Hummus", 166, 8.0, 14.0, 9.0, listOf(
            Portion("2 tbsp", 30.0), Portion("1/2 cup", 120.0))),
        "curry" to FoodItem("Vegetable Curry", 180, 12.0, 14.0, 8.0, listOf(
            Portion("1 cup", 240.0), Portion("1/2 cup", 120.0))),

        // ===== BOWLS & HEALTHY OPTIONS =====
        "acai bowl" to FoodItem("Acai Bowl", 160, 3.0, 26.0, 5.0, listOf(
            Portion("12 oz bowl", 340.0), Portion("16 oz bowl", 450.0))),
        "smoothie bowl" to FoodItem("Smoothie Bowl", 180, 5.0, 30.0, 6.0, listOf(
            Portion("12 oz bowl", 350.0))),
        "green smoothie" to FoodItem("Green Smoothie", 95, 3.0, 20.0, 1.0, listOf(
            Portion("12 fl oz", 355.0), Portion("16 fl oz", 473.0))),
        "protein smoothie" to FoodItem("Protein Smoothie", 180, 20.0, 18.0, 4.0, listOf(
            Portion("12 fl oz", 355.0), Portion("16 fl oz", 473.0))),

        // ===== RESTAURANT & SEAFOOD =====
        "coconut shrimp" to FoodItem(
            name = "Coconut Shrimp (Restaurant)",
            calories = 320,
            protein = 18.0,
            carbs = 22.0,
            fat = 18.0,
            commonPortions = listOf(
                Portion("4 pieces (120g)", 120.0),
                Portion("6 pieces (180g)", 180.0),
                Portion("1 piece (30g)", 30.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 0.8,
                MicronutrientType.IODINE to 35.0,
                MicronutrientType.SELENIUM to 25.0,
                MicronutrientType.ZINC to 1.2,
                MicronutrientType.CALCIUM to 85.0,
                MicronutrientType.PHOSPHORUS to 180.0
            )
        ),

        "conch fritters" to FoodItem(
            name = "Conch Fritters (Restaurant)",
            calories = 285,
            protein = 12.0,
            carbs = 28.0,
            fat = 14.0,
            commonPortions = listOf(
                Portion("3 fritters (120g)", 120.0),
                Portion("6 fritters (240g)", 240.0),
                Portion("1 fritter (40g)", 40.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 1.2,
                MicronutrientType.IODINE to 45.0,
                MicronutrientType.SELENIUM to 22.0,
                MicronutrientType.ZINC to 1.8,
                MicronutrientType.CALCIUM to 95.0,
                MicronutrientType.PHOSPHORUS to 165.0
            )
        ),

        "fish tacos" to FoodItem(
            name = "Fish Tacos (Restaurant)",
            calories = 280,
            protein = 22.0,
            carbs = 26.0,
            fat = 12.0,
            commonPortions = listOf(
                Portion("2 tacos (200g)", 200.0),
                Portion("3 tacos (300g)", 300.0),
                Portion("1 taco (100g)", 100.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_D to 8.0,
                MicronutrientType.VITAMIN_B12 to 2.5,
                MicronutrientType.IODINE to 60.0,
                MicronutrientType.SELENIUM to 40.0,
                MicronutrientType.ZINC to 0.8,
                MicronutrientType.CALCIUM to 120.0
            )
        ),

        "lobster tail" to FoodItem(
            name = "Lobster Tail (Restaurant)",
            calories = 130,
            protein = 28.0,
            carbs = 1.0,
            fat = 1.5,
            commonPortions = listOf(
                Portion("1 tail (100g)", 100.0),
                Portion("6 oz tail", 170.0),
                Portion("8 oz tail", 227.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 2.0,
                MicronutrientType.IODINE to 100.0,
                MicronutrientType.SELENIUM to 45.0,
                MicronutrientType.ZINC to 2.2,
                MicronutrientType.COPPER to 1.5,
                MicronutrientType.PHOSPHORUS to 250.0
            )
        ),

        "crab cakes" to FoodItem(
            name = "Crab Cakes (Restaurant)",
            calories = 220,
            protein = 18.0,
            carbs = 15.0,
            fat = 12.0,
            commonPortions = listOf(
                Portion("1 cake (85g)", 85.0),
                Portion("2 cakes (170g)", 170.0),
                Portion("3 cakes (255g)", 255.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 3.2,
                MicronutrientType.IODINE to 55.0,
                MicronutrientType.SELENIUM to 35.0,
                MicronutrientType.ZINC to 2.8,
                MicronutrientType.COPPER to 0.8,
                MicronutrientType.PHOSPHORUS to 220.0
            )
        ),

        "scallops" to FoodItem(
            name = "Seared Scallops (Restaurant)",
            calories = 180,
            protein = 28.0,
            carbs = 8.0,
            fat = 6.0,
            commonPortions = listOf(
                Portion("4 large scallops (120g)", 120.0),
                Portion("6 large scallops (180g)", 180.0),
                Portion("1 scallop (30g)", 30.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 2.8,
                MicronutrientType.IODINE to 90.0,
                MicronutrientType.SELENIUM to 28.0,
                MicronutrientType.ZINC to 1.2,
                MicronutrientType.MAGNESIUM to 35.0,
                MicronutrientType.PHOSPHORUS to 280.0
            )
        ),

        "oysters" to FoodItem(
            name = "Oysters (Restaurant)",
            calories = 68,
            protein = 8.0,
            carbs = 4.0,
            fat = 2.5,
            commonPortions = listOf(
                Portion("6 oysters (120g)", 120.0),
                Portion("12 oysters (240g)", 240.0),
                Portion("1 oyster (20g)", 20.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 16.0,
                MicronutrientType.IODINE to 160.0,
                MicronutrientType.SELENIUM to 35.0,
                MicronutrientType.ZINC to 90.0,
                MicronutrientType.COPPER to 4.0,
                MicronutrientType.IRON to 6.0
            )
        ),

        "mussels" to FoodItem(
            name = "Steamed Mussels (Restaurant)",
            calories = 172,
            protein = 24.0,
            carbs = 7.0,
            fat = 4.5,
            commonPortions = listOf(
                Portion("1 lb mussels (454g)", 454.0),
                Portion("1/2 lb mussels (227g)", 227.0),
                Portion("6 mussels (120g)", 120.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 24.0,
                MicronutrientType.IODINE to 160.0,
                MicronutrientType.SELENIUM to 55.0,
                MicronutrientType.ZINC to 2.0,
                MicronutrientType.IRON to 6.8,
                MicronutrientType.MANGANESE to 3.2
            )
        ),

        "clams" to FoodItem(
            name = "Clams (Restaurant)",
            calories = 148,
            protein = 26.0,
            carbs = 5.0,
            fat = 2.0,
            commonPortions = listOf(
                Portion("6 clams (120g)", 120.0),
                Portion("12 clams (240g)", 240.0),
                Portion("1 clam (20g)", 20.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 22.0,
                MicronutrientType.IODINE to 140.0,
                MicronutrientType.SELENIUM to 45.0,
                MicronutrientType.ZINC to 1.8,
                MicronutrientType.IRON to 24.0,
                MicronutrientType.COPPER to 0.4
            )
        ),

        "grilled salmon" to FoodItem(
            name = "Grilled Salmon (Restaurant)",
            calories = 220,
            protein = 28.0,
            carbs = 0.0,
            fat = 12.0,
            commonPortions = listOf(
                Portion("6 oz fillet (170g)", 170.0),
                Portion("8 oz fillet (227g)", 227.0),
                Portion("4 oz portion (113g)", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_D to 15.0,
                MicronutrientType.VITAMIN_B12 to 3.5,
                MicronutrientType.IODINE to 50.0,
                MicronutrientType.SELENIUM to 40.0,
                MicronutrientType.ZINC to 0.8,
                MicronutrientType.SELENIUM to 40.0
            )
        ),

        "tuna steak" to FoodItem(
            name = "Seared Tuna Steak (Restaurant)",
            calories = 180,
            protein = 32.0,
            carbs = 0.0,
            fat = 6.0,
            commonPortions = listOf(
                Portion("6 oz steak (170g)", 170.0),
                Portion("8 oz steak (227g)", 227.0),
                Portion("4 oz portion (113g)", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_D to 8.0,
                MicronutrientType.VITAMIN_B12 to 2.8,
                MicronutrientType.IODINE to 15.0,
                MicronutrientType.SELENIUM to 60.0,
                MicronutrientType.ZINC to 0.6,
                MicronutrientType.SELENIUM to 55.0
            )
        ),

        "mahi mahi" to FoodItem(
            name = "Grilled Mahi Mahi (Restaurant)",
            calories = 140,
            protein = 28.0,
            carbs = 0.0,
            fat = 3.0,
            commonPortions = listOf(
                Portion("6 oz fillet (170g)", 170.0),
                Portion("8 oz fillet (227g)", 227.0),
                Portion("4 oz portion (113g)", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 2.2,
                MicronutrientType.IODINE to 25.0,
                MicronutrientType.SELENIUM to 45.0,
                MicronutrientType.ZINC to 0.8,
                MicronutrientType.PHOSPHORUS to 220.0,
                MicronutrientType.POTASSIUM to 480.0
            )
        ),

        "grouper" to FoodItem(
            name = "Blackened Grouper (Restaurant)",
            calories = 160,
            protein = 26.0,
            carbs = 2.0,
            fat = 6.0,
            commonPortions = listOf(
                Portion("6 oz fillet (170g)", 170.0),
                Portion("8 oz fillet (227g)", 227.0),
                Portion("4 oz portion (113g)", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 2.5,
                MicronutrientType.IODINE to 30.0,
                MicronutrientType.SELENIUM to 38.0,
                MicronutrientType.ZINC to 0.7,
                MicronutrientType.PHOSPHORUS to 240.0,
                MicronutrientType.POTASSIUM to 420.0
            )
        ),

        "snapper" to FoodItem(
            name = "Red Snapper (Restaurant)",
            calories = 140,
            protein = 28.0,
            carbs = 0.0,
            fat = 3.0,
            commonPortions = listOf(
                Portion("6 oz fillet (170g)", 170.0),
                Portion("8 oz fillet (227g)", 227.0),
                Portion("4 oz portion (113g)", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 3.0,
                MicronutrientType.IODINE to 20.0,
                MicronutrientType.SELENIUM to 42.0,
                MicronutrientType.ZINC to 0.8,
                MicronutrientType.PHOSPHORUS to 260.0,
                MicronutrientType.POTASSIUM to 380.0
            )
        ),

        "swordfish" to FoodItem(
            name = "Grilled Swordfish (Restaurant)",
            calories = 180,
            protein = 26.0,
            carbs = 0.0,
            fat = 8.0,
            commonPortions = listOf(
                Portion("6 oz steak (170g)", 170.0),
                Portion("8 oz steak (227g)", 227.0),
                Portion("4 oz portion (113g)", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_D to 12.0,
                MicronutrientType.VITAMIN_B12 to 2.8,
                MicronutrientType.IODINE to 45.0,
                MicronutrientType.SELENIUM to 55.0,
                MicronutrientType.ZINC to 0.9,
                MicronutrientType.SELENIUM to 35.0
            )
        ),

        "stone crab claws" to FoodItem(
            name = "Stone Crab Claws (Restaurant)",
            calories = 120,
            protein = 24.0,
            carbs = 1.0,
            fat = 2.0,
            commonPortions = listOf(
                Portion("1 lb claws (454g)", 454.0),
                Portion("1/2 lb claws (227g)", 227.0),
                Portion("3 claws (120g)", 120.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 3.5,
                MicronutrientType.IODINE to 75.0,
                MicronutrientType.SELENIUM to 48.0,
                MicronutrientType.ZINC to 2.5,
                MicronutrientType.COPPER to 0.6,
                MicronutrientType.PHOSPHORUS to 180.0
            )
        ),

        "king crab legs" to FoodItem(
            name = "King Crab Legs (Restaurant)",
            calories = 115,
            protein = 24.0,
            carbs = 0.0,
            fat = 1.5,
            commonPortions = listOf(
                Portion("1 lb legs (454g)", 454.0),
                Portion("1/2 lb legs (227g)", 227.0),
                Portion("3 legs (120g)", 120.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 4.0,
                MicronutrientType.IODINE to 85.0,
                MicronutrientType.SELENIUM to 50.0,
                MicronutrientType.ZINC to 1.8,
                MicronutrientType.COPPER to 0.8,
                MicronutrientType.PHOSPHORUS to 220.0
            )
        ),

        "dungeness crab" to FoodItem(
            name = "Dungeness Crab (Restaurant)",
            calories = 110,
            protein = 23.0,
            carbs = 1.0,
            fat = 1.0,
            commonPortions = listOf(
                Portion("1 lb crab (454g)", 454.0),
                Portion("1/2 lb crab (227g)", 227.0),
                Portion("1 cluster (120g)", 120.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 3.8,
                MicronutrientType.IODINE to 70.0,
                MicronutrientType.SELENIUM to 42.0,
                MicronutrientType.ZINC to 2.2,
                MicronutrientType.COPPER to 0.5,
                MicronutrientType.PHOSPHORUS to 200.0
            )
        ),

        "snow crab legs" to FoodItem(
            name = "Snow Crab Legs (Restaurant)",
            calories = 100,
            protein = 22.0,
            carbs = 0.0,
            fat = 1.0,
            commonPortions = listOf(
                Portion("1 lb legs (454g)", 454.0),
                Portion("1/2 lb legs (227g)", 227.0),
                Portion("4 legs (120g)", 120.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 3.2,
                MicronutrientType.IODINE to 60.0,
                MicronutrientType.SELENIUM to 38.0,
                MicronutrientType.ZINC to 1.5,
                MicronutrientType.COPPER to 0.4,
                MicronutrientType.PHOSPHORUS to 180.0
            )
        ),

        "langoustines" to FoodItem(
            name = "Langoustines (Restaurant)",
            calories = 95,
            protein = 20.0,
            carbs = 1.0,
            fat = 1.5,
            commonPortions = listOf(
                Portion("6 langoustines (120g)", 120.0),
                Portion("12 langoustines (240g)", 240.0),
                Portion("1 langoustine (20g)", 20.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 2.8,
                MicronutrientType.IODINE to 80.0,
                MicronutrientType.SELENIUM to 35.0,
                MicronutrientType.ZINC to 1.2,
                MicronutrientType.COPPER to 0.3,
                MicronutrientType.PHOSPHORUS to 160.0
            )
        ),

        "crawfish" to FoodItem(
            name = "Crawfish (Restaurant)",
            calories = 82,
            protein = 16.0,
            carbs = 0.0,
            fat = 1.0,
            commonPortions = listOf(
                Portion("1 lb crawfish (454g)", 454.0),
                Portion("1/2 lb crawfish (227g)", 227.0),
                Portion("10 crawfish (120g)", 120.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 1.8,
                MicronutrientType.IODINE to 25.0,
                MicronutrientType.SELENIUM to 28.0,
                MicronutrientType.ZINC to 1.0,
                MicronutrientType.COPPER to 0.3,
                MicronutrientType.PHOSPHORUS to 140.0
            )
        ),

        "ceviche" to FoodItem(
            name = "Seafood Ceviche (Restaurant)",
            calories = 120,
            protein = 18.0,
            carbs = 8.0,
            fat = 3.0,
            commonPortions = listOf(
                Portion("1 cup (180g)", 180.0),
                Portion("1/2 cup (90g)", 90.0),
                Portion("6 oz serving", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 15.0,
                MicronutrientType.VITAMIN_B12 to 2.2,
                MicronutrientType.IODINE to 40.0,
                MicronutrientType.SELENIUM to 32.0,
                MicronutrientType.ZINC to 0.8,
                MicronutrientType.POTASSIUM to 280.0
            )
        ),

        "seafood paella" to FoodItem(
            name = "Seafood Paella (Restaurant)",
            calories = 240,
            protein = 22.0,
            carbs = 28.0,
            fat = 8.0,
            commonPortions = listOf(
                Portion("1 cup (200g)", 200.0),
                Portion("1/2 cup (100g)", 100.0),
                Portion("8 oz serving", 227.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 2.8,
                MicronutrientType.IODINE to 35.0,
                MicronutrientType.SELENIUM to 38.0,
                MicronutrientType.ZINC to 1.2,
                MicronutrientType.IRON to 2.8,
                MicronutrientType.VITAMIN_B9 to 45.0
            )
        ),

        "cioppino" to FoodItem(
            name = "Cioppino (Restaurant)",
            calories = 180,
            protein = 24.0,
            carbs = 12.0,
            fat = 6.0,
            commonPortions = listOf(
                Portion("1 cup (240g)", 240.0),
                Portion("1/2 cup (120g)", 120.0),
                Portion("8 oz serving", 227.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 25.0,
                MicronutrientType.VITAMIN_B12 to 3.2,
                MicronutrientType.IODINE to 50.0,
                MicronutrientType.SELENIUM to 42.0,
                MicronutrientType.ZINC to 1.5,
                MicronutrientType.POTASSIUM to 380.0
            )
        ),

        "bouillabaisse" to FoodItem(
            name = "Bouillabaisse (Restaurant)",
            calories = 160,
            protein = 20.0,
            carbs = 8.0,
            fat = 7.0,
            commonPortions = listOf(
                Portion("1 cup (240g)", 240.0),
                Portion("1/2 cup (120g)", 120.0),
                Portion("8 oz serving", 227.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 18.0,
                MicronutrientType.VITAMIN_B12 to 2.8,
                MicronutrientType.IODINE to 45.0,
                MicronutrientType.SELENIUM to 35.0,
                MicronutrientType.ZINC to 1.2,
                MicronutrientType.IRON to 3.2
            )
        ),

        "surf and turf" to FoodItem(
            name = "Surf and Turf (Restaurant)",
            calories = 320,
            protein = 32.0,
            carbs = 2.0,
            fat = 22.0,
            commonPortions = listOf(
                Portion("8 oz steak + 4 shrimp (340g)", 340.0),
                Portion("6 oz steak + 3 shrimp (255g)", 255.0),
                Portion("4 oz steak + 2 shrimp (170g)", 170.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 4.2,
                MicronutrientType.IODINE to 25.0,
                MicronutrientType.SELENIUM to 38.0,
                MicronutrientType.ZINC to 6.8,
                MicronutrientType.IRON to 3.5,
                MicronutrientType.PHOSPHORUS to 280.0
            )
        ),

        "seafood linguine" to FoodItem(
            name = "Seafood Linguine (Restaurant)",
            calories = 280,
            protein = 22.0,
            carbs = 32.0,
            fat = 10.0,
            commonPortions = listOf(
                Portion("1 plate (300g)", 300.0),
                Portion("1/2 plate (150g)", 150.0),
                Portion("8 oz serving", 227.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 2.8,
                MicronutrientType.IODINE to 40.0,
                MicronutrientType.SELENIUM to 35.0,
                MicronutrientType.ZINC to 1.8,
                MicronutrientType.IRON to 2.2,
                MicronutrientType.VITAMIN_B9 to 35.0
            )
        ),

        "shrimp scampi" to FoodItem(
            name = "Shrimp Scampi (Restaurant)",
            calories = 240,
            protein = 24.0,
            carbs = 18.0,
            fat = 12.0,
            commonPortions = listOf(
                Portion("1 plate (250g)", 250.0),
                Portion("1/2 plate (125g)", 125.0),
                Portion("8 oz serving", 227.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 1.8,
                MicronutrientType.IODINE to 35.0,
                MicronutrientType.SELENIUM to 42.0,
                MicronutrientType.ZINC to 1.5,
                MicronutrientType.PHOSPHORUS to 220.0,
                MicronutrientType.POTASSIUM to 260.0
            )
        ),

        "crab linguine" to FoodItem(
            name = "Crab Linguine (Restaurant)",
            calories = 260,
            protein = 20.0,
            carbs = 30.0,
            fat = 9.0,
            commonPortions = listOf(
                Portion("1 plate (300g)", 300.0),
                Portion("1/2 plate (150g)", 150.0),
                Portion("8 oz serving", 227.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 3.5,
                MicronutrientType.IODINE to 60.0,
                MicronutrientType.SELENIUM to 48.0,
                MicronutrientType.ZINC to 2.2,
                MicronutrientType.COPPER to 0.6,
                MicronutrientType.PHOSPHORUS to 200.0
            )
        ),

        "lobster ravioli" to FoodItem(
            name = "Lobster Ravioli (Restaurant)",
            calories = 290,
            protein = 18.0,
            carbs = 35.0,
            fat = 11.0,
            commonPortions = listOf(
                Portion("1 plate (280g)", 280.0),
                Portion("1/2 plate (140g)", 140.0),
                Portion("6 pieces (200g)", 200.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 2.2,
                MicronutrientType.IODINE to 55.0,
                MicronutrientType.SELENIUM to 40.0,
                MicronutrientType.ZINC to 1.8,
                MicronutrientType.CALCIUM to 180.0,
                MicronutrientType.PHOSPHORUS to 220.0
            )
        ),

        "tuna tartare" to FoodItem(
            name = "Tuna Tartare (Restaurant)",
            calories = 180,
            protein = 26.0,
            carbs = 6.0,
            fat = 8.0,
            commonPortions = listOf(
                Portion("4 oz serving (113g)", 113.0),
                Portion("6 oz serving (170g)", 170.0),
                Portion("8 oz serving (227g)", 227.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_D to 8.0,
                MicronutrientType.VITAMIN_B12 to 2.8,
                MicronutrientType.IODINE to 15.0,
                MicronutrientType.SELENIUM to 55.0,
                MicronutrientType.ZINC to 0.6,
                MicronutrientType.SELENIUM to 55.0
            )
        ),

        "poke bowl" to FoodItem(
            name = "Poke Bowl (Restaurant)",
            calories = 320,
            protein = 24.0,
            carbs = 35.0,
            fat = 12.0,
            commonPortions = listOf(
                Portion("1 bowl (400g)", 400.0),
                Portion("1/2 bowl (200g)", 200.0),
                Portion("8 oz serving", 227.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_C to 45.0,
                MicronutrientType.VITAMIN_B12 to 2.5,
                MicronutrientType.IODINE to 20.0,
                MicronutrientType.SELENIUM to 38.0,
                MicronutrientType.ZINC to 1.2,
                MicronutrientType.SELENIUM to 55.0
            )
        ),

        "fish and chips" to FoodItem(
            name = "Fish and Chips (Restaurant)",
            calories = 480,
            protein = 22.0,
            carbs = 45.0,
            fat = 26.0,
            commonPortions = listOf(
                Portion("1 serving (350g)", 350.0),
                Portion("1/2 serving (175g)", 175.0),
                Portion("8 oz serving", 227.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 2.2,
                MicronutrientType.IODINE to 25.0,
                MicronutrientType.SELENIUM to 35.0,
                MicronutrientType.ZINC to 0.8,
                MicronutrientType.CALCIUM to 120.0,
                MicronutrientType.PHOSPHORUS to 280.0
            )
        ),

        "calamari" to FoodItem(
            name = "Fried Calamari (Restaurant)",
            calories = 220,
            protein = 18.0,
            carbs = 15.0,
            fat = 12.0,
            commonPortions = listOf(
                Portion("6 oz serving (170g)", 170.0),
                Portion("8 oz serving (227g)", 227.0),
                Portion("4 oz serving (113g)", 113.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 2.8,
                MicronutrientType.IODINE to 65.0,
                MicronutrientType.SELENIUM to 42.0,
                MicronutrientType.ZINC to 1.5,
                MicronutrientType.COPPER to 0.4,
                MicronutrientType.PHOSPHORUS to 240.0
            )
        ),

        "tempura shrimp" to FoodItem(
            name = "Tempura Shrimp (Restaurant)",
            calories = 280,
            protein = 16.0,
            carbs = 24.0,
            fat = 14.0,
            commonPortions = listOf(
                Portion("4 pieces (120g)", 120.0),
                Portion("6 pieces (180g)", 180.0),
                Portion("1 piece (30g)", 30.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 1.2,
                MicronutrientType.IODINE to 35.0,
                MicronutrientType.SELENIUM to 28.0,
                MicronutrientType.ZINC to 1.0,
                MicronutrientType.PHOSPHORUS to 180.0,
                MicronutrientType.POTASSIUM to 150.0
            )
        ),

        "soft shell crab" to FoodItem(
            name = "Soft Shell Crab (Restaurant)",
            calories = 140,
            protein = 16.0,
            carbs = 8.0,
            fat = 6.0,
            commonPortions = listOf(
                Portion("1 large crab (120g)", 120.0),
                Portion("2 crabs (240g)", 240.0),
                Portion("1/2 crab (60g)", 60.0)
            ),
            micronutrients = mapOf(
                MicronutrientType.VITAMIN_B12 to 2.5,
                MicronutrientType.IODINE to 40.0,
                MicronutrientType.SELENIUM to 32.0,
                MicronutrientType.ZINC to 1.8,
                MicronutrientType.COPPER to 0.5,
                MicronutrientType.PHOSPHORUS to 160.0
            )
        )
    )

    fun searchFood(query: String): List<FoodItem> {
        val lowerQuery = query.lowercase().trim()
        return foods.values.filter { food ->
            food.name.lowercase().contains(lowerQuery) ||
            lowerQuery.contains(food.name.lowercase()) ||
            // Check if query matches any portion names too
            food.commonPortions.any { portion ->
                portion.name.lowercase().contains(lowerQuery)
            }
        }.distinct()
    }

    fun getFoodByName(name: String): FoodItem? {
        return foods[name.lowercase().trim()]
    }

    fun getAllFoods(): List<FoodItem> = foods.values.toList()

    /**
     * Search foods via USDA API for expanded database
     */
    suspend fun searchUSDAFoods(query: String): List<FoodItem> {
        return try {
            val url = "$USDA_BASE_URL/foods/search?api_key=$USDA_API_KEY&query=${java.net.URLEncoder.encode(query, "UTF-8")}&dataType=Foundation,SR Legacy,Branded&pageSize=25"
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val jsonResponse = JSONObject(response)

            val foods = mutableListOf<FoodItem>()
            val foodsArray = jsonResponse.optJSONArray("foods")

            if (foodsArray != null) {
                for (i in 0 until foodsArray.length()) {
                    val foodJson = foodsArray.getJSONObject(i)
                    val foodItem = parseUSDAFood(foodJson)
                    if (foodItem != null) {
                        foods.add(foodItem)
                    }
                }
            }

            foods
        } catch (e: Exception) {
            android.util.Log.e("FoodDatabase", "Error searching USDA foods", e)
            emptyList()
        }
    }

    /**
     * Parse USDA food JSON into FoodItem
     */
    private fun parseUSDAFood(foodJson: JSONObject): FoodItem? {
        return try {
            val description = foodJson.getString("description")
            val fdcId = foodJson.getString("fdcId")

            // Extract nutritional data
            val nutrients = foodJson.optJSONArray("foodNutrients")
            var calories = 0
            var protein = 0.0
            var carbs = 0.0
            var fat = 0.0
            val micronutrients = mutableMapOf<MicronutrientType, Double>()

            if (nutrients != null) {
                for (i in 0 until nutrients.length()) {
                    val nutrient = nutrients.getJSONObject(i)
                    val nutrientId = nutrient.optInt("nutrientId", 0)
                    val value = nutrient.optDouble("value", 0.0)

                    when (nutrientId) {
                        1008 -> calories = value.toInt() // Energy (kcal)
                        1003 -> protein = value // Protein
                        1005 -> carbs = value // Carbohydrates
                        1004 -> fat = value // Fat
                        1087 -> micronutrients[MicronutrientType.CALCIUM] = value // Calcium
                        1093 -> micronutrients[MicronutrientType.SODIUM] = value // Sodium
                        1092 -> micronutrients[MicronutrientType.POTASSIUM] = value // Potassium
                        1089 -> micronutrients[MicronutrientType.IRON] = value // Iron
                        1104 -> micronutrients[MicronutrientType.VITAMIN_A] = value // Vitamin A
                        1106 -> micronutrients[MicronutrientType.VITAMIN_C] = value // Vitamin C
                        1109 -> micronutrients[MicronutrientType.VITAMIN_D] = value // Vitamin D
                        1114 -> micronutrients[MicronutrientType.VITAMIN_B12] = value // Vitamin B12
                    }
                }
            }

            FoodItem(
                name = description,
                calories = calories,
                protein = protein,
                carbs = carbs,
                fat = fat,
                commonPortions = listOf(Portion("100g", 100.0)),
                micronutrients = micronutrients
            )
        } catch (e: Exception) {
            android.util.Log.e("FoodDatabase", "Error parsing USDA food", e)
            null
        }
    }

    /**
     * Calculate nutritional info for a given portion
     */
    fun calculateNutrition(food: FoodItem, grams: Double): NutritionInfo {
        val factor = grams / 100.0
        return NutritionInfo(
            calories = (food.calories * factor).toInt(),
            protein = food.protein * factor,
            carbs = food.carbs * factor,
            fat = food.fat * factor,
            sugar = food.sugar * factor,
            addedSugar = food.addedSugar * factor,
            micronutrients = food.micronutrients.mapValues { (_, value) -> value * factor }
        )
    }
}

data class NutritionInfo(
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val sugar: Double = 0.0,
    val addedSugar: Double = 0.0,
    val micronutrients: Map<MicronutrientType, Double> = emptyMap()
) {
    fun formatProtein() = String.format("%.1f", protein)
    fun formatCarbs() = String.format("%.1f", carbs)
    fun formatFat() = String.format("%.1f", fat)
    fun formatSugar() = String.format("%.1f", sugar)
    fun formatAddedSugar() = String.format("%.1f", addedSugar)
}
