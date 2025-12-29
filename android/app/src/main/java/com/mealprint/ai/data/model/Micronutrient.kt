package com.coachie.app.data.model

/**
 * Defines supported micronutrients (vitamins & minerals) and their gender-specific targets.
 */
enum class MicronutrientUnit(val displaySuffix: String) {
    MG("mg"),
    MCG("mcg"),
    IU("IU");

    override fun toString(): String = displaySuffix
}

data class MicronutrientTarget(
    val min: Double,
    val max: Double? = null
) {
    fun format(): String = when {
        max == null || max == min -> min.format()
        min == 0.0 -> "<= ${max.format()}"
        else -> "${min.format()} - ${max.format()}"
    }

    private fun Double.format(): String =
        if (this % 1.0 == 0.0) {
            String.format("%.0f", this)
        } else {
            String.format("%.1f", this)
        }
}

enum class MicronutrientType(
    val id: String,
    val displayName: String,
    val unit: MicronutrientUnit,
    val maleTarget: MicronutrientTarget,
    val femaleTarget: MicronutrientTarget,
    val topSources: List<String>
) {
    VITAMIN_A(
        id = "vitamin_a",
        displayName = "Vitamin A",
        unit = MicronutrientUnit.MCG,
        maleTarget = MicronutrientTarget(900.0),
        femaleTarget = MicronutrientTarget(700.0),
        topSources = listOf("Carrots", "Sweet potato", "Liver", "Spinach")
    ),
    VITAMIN_D(
        id = "vitamin_d",
        displayName = "Vitamin D",
        unit = MicronutrientUnit.IU,
        // RDA: 600 IU (18-70 years), 800 IU (70+ years). Using 800 IU as target.
        // Upper limit (UL): 4000 IU/day - not shown as range, just for internal safety checks
        maleTarget = MicronutrientTarget(800.0),
        femaleTarget = MicronutrientTarget(800.0),
        topSources = listOf("Sunlight", "Salmon", "Fortified milk", "Eggs")
    ),
    VITAMIN_E(
        id = "vitamin_e",
        displayName = "Vitamin E",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(15.0),
        femaleTarget = MicronutrientTarget(15.0),
        topSources = listOf("Almonds", "Sunflower seeds", "Avocado")
    ),
    VITAMIN_K(
        id = "vitamin_k",
        displayName = "Vitamin K",
        unit = MicronutrientUnit.MCG,
        maleTarget = MicronutrientTarget(120.0),
        femaleTarget = MicronutrientTarget(90.0),
        topSources = listOf("Kale", "Spinach", "Broccoli")
    ),
    VITAMIN_C(
        id = "vitamin_c",
        displayName = "Vitamin C",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(90.0),
        femaleTarget = MicronutrientTarget(75.0),
        topSources = listOf("Orange", "Bell pepper", "Kiwi", "Strawberries")
    ),
    VITAMIN_B1(
        id = "vitamin_b1",
        displayName = "Vitamin B1 (Thiamine)",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(1.2),
        femaleTarget = MicronutrientTarget(1.1),
        topSources = listOf("Pork", "Whole grains", "Beans")
    ),
    VITAMIN_B2(
        id = "vitamin_b2",
        displayName = "Vitamin B2 (Riboflavin)",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(1.3),
        femaleTarget = MicronutrientTarget(1.1),
        topSources = listOf("Milk", "Eggs", "Lean meat")
    ),
    VITAMIN_B3(
        id = "vitamin_b3",
        displayName = "Vitamin B3 (Niacin)",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(16.0),
        femaleTarget = MicronutrientTarget(14.0),
        topSources = listOf("Chicken", "Tuna", "Peanuts")
    ),
    VITAMIN_B5(
        id = "vitamin_b5",
        displayName = "Vitamin B5 (Pantothenic)",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(5.0),
        femaleTarget = MicronutrientTarget(5.0),
        topSources = listOf("Avocado", "Broccoli", "Chicken")
    ),
    VITAMIN_B6(
        id = "vitamin_b6",
        displayName = "Vitamin B6",
        unit = MicronutrientUnit.MG,
        // RDA: 1.3 mg (19-50 years), 1.7 mg (51+ years for men), 1.5 mg (51+ years for women)
        // Using 1.5 mg as average target for men, 1.4 mg for women
        maleTarget = MicronutrientTarget(1.5),
        femaleTarget = MicronutrientTarget(1.4),
        topSources = listOf("Chickpeas", "Salmon", "Potato")
    ),
    VITAMIN_B7(
        id = "vitamin_b7",
        displayName = "Vitamin B7 (Biotin)",
        unit = MicronutrientUnit.MCG,
        maleTarget = MicronutrientTarget(30.0),
        femaleTarget = MicronutrientTarget(30.0),
        topSources = listOf("Eggs", "Nuts", "Sweet potato")
    ),
    VITAMIN_B9(
        id = "vitamin_b9",
        displayName = "Vitamin B9 (Folate)",
        unit = MicronutrientUnit.MCG,
        maleTarget = MicronutrientTarget(400.0),
        femaleTarget = MicronutrientTarget(400.0),
        topSources = listOf("Lentils", "Spinach", "Fortified cereal")
    ),
    VITAMIN_B12(
        id = "vitamin_b12",
        displayName = "Vitamin B12",
        unit = MicronutrientUnit.MCG,
        maleTarget = MicronutrientTarget(2.4),
        femaleTarget = MicronutrientTarget(2.4),
        topSources = listOf("Meat", "Fish", "Eggs")
    ),
    CALCIUM(
        id = "calcium",
        displayName = "Calcium",
        unit = MicronutrientUnit.MG,
        // RDA: 1000 mg (19-50 years), 1200 mg (women 50+, men 70+). Using 1000 mg as target for adults.
        maleTarget = MicronutrientTarget(1000.0),
        femaleTarget = MicronutrientTarget(1000.0),
        topSources = listOf("Yogurt", "Cheese", "Kale", "Almond milk")
    ),
    MAGNESIUM(
        id = "magnesium",
        displayName = "Magnesium",
        unit = MicronutrientUnit.MG,
        // RDA: 400-420 mg (males 19+), 310-320 mg (females 19+). Using 420 mg for males, 320 mg for females as target.
        // Note: UL from supplements is 350 mg, but from food there's no established UL.
        maleTarget = MicronutrientTarget(420.0),
        femaleTarget = MicronutrientTarget(320.0),
        topSources = listOf("Pumpkin seeds", "Almonds", "Spinach")
    ),
    POTASSIUM(
        id = "potassium",
        displayName = "Potassium",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(3400.0),
        femaleTarget = MicronutrientTarget(2600.0),
        topSources = listOf("Banana", "Potato", "Beans", "Avocado")
    ),
    SODIUM(
        id = "sodium",
        displayName = "Sodium",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(0.0, 2300.0),
        femaleTarget = MicronutrientTarget(0.0, 2300.0),
        topSources = listOf("Limit processed foods")
    ),
    IRON(
        id = "iron",
        displayName = "Iron",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(8.0),
        femaleTarget = MicronutrientTarget(18.0),
        topSources = listOf("Red meat", "Lentils", "Spinach", "Vitamin C pairing")
    ),
    ZINC(
        id = "zinc",
        displayName = "Zinc",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(11.0),
        femaleTarget = MicronutrientTarget(8.0),
        topSources = listOf("Oysters", "Beef", "Chickpeas", "Nuts")
    ),
    IODINE(
        id = "iodine",
        displayName = "Iodine",
        unit = MicronutrientUnit.MCG,
        maleTarget = MicronutrientTarget(150.0),
        femaleTarget = MicronutrientTarget(150.0),
        topSources = listOf("Iodized salt", "Seafood", "Dairy")
    ),
    SELENIUM(
        id = "selenium",
        displayName = "Selenium",
        unit = MicronutrientUnit.MCG,
        maleTarget = MicronutrientTarget(55.0),
        femaleTarget = MicronutrientTarget(55.0),
        topSources = listOf("Brazil nuts", "Tuna", "Eggs")
    ),
    PHOSPHORUS(
        id = "phosphorus",
        displayName = "Phosphorus",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(700.0),
        femaleTarget = MicronutrientTarget(700.0),
        topSources = listOf("Cheese", "Chicken", "Milk", "Nuts")
    ),
    MANGANESE(
        id = "manganese",
        displayName = "Manganese",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(2.3),
        femaleTarget = MicronutrientTarget(1.8),
        topSources = listOf("Nuts", "Grains", "Vegetables")
    ),
    COPPER(
        id = "copper",
        displayName = "Copper",
        unit = MicronutrientUnit.MCG,
        maleTarget = MicronutrientTarget(900.0),
        femaleTarget = MicronutrientTarget(900.0),
        topSources = listOf("Organ meats", "Nuts", "Seeds", "Seafood")
    ),
    CHOLINE(
        id = "choline",
        displayName = "Choline",
        unit = MicronutrientUnit.MG,
        maleTarget = MicronutrientTarget(550.0),
        femaleTarget = MicronutrientTarget(425.0),
        topSources = listOf("Eggs", "Liver", "Fish", "Beef")
    ),
    CHROMIUM(
        id = "chromium",
        displayName = "Chromium",
        unit = MicronutrientUnit.MCG,
        maleTarget = MicronutrientTarget(35.0),
        femaleTarget = MicronutrientTarget(25.0),
        topSources = listOf("Broccoli", "Whole grains", "Meat", "Nuts")
    ),
    MOLYBDENUM(
        id = "molybdenum",
        displayName = "Molybdenum",
        unit = MicronutrientUnit.MCG,
        maleTarget = MicronutrientTarget(45.0),
        femaleTarget = MicronutrientTarget(45.0),
        topSources = listOf("Legumes", "Grains", "Nuts", "Leafy vegetables")
    );

    fun targetForGender(gender: String?): MicronutrientTarget = when (gender?.lowercase()) {
        "male", "man", "m" -> maleTarget
        "female", "woman", "f" -> femaleTarget
        else -> femaleTarget
    }

    companion object {
        fun fromId(id: String): MicronutrientType? = values().firstOrNull { it.id == id }

        val ordered: List<MicronutrientType> = values().toList()
    }
}

fun Map<MicronutrientType, Double>.toPersistedMicronutrientMap(): Map<String, Double> =
    entries.associate { (type, value) -> type.id to value }

fun Map<String, Double>.toMicronutrientTypeMap(): Map<MicronutrientType, Double> =
    entries.mapNotNull { (key, value) ->
        MicronutrientType.fromId(key)?.let { it to value }
    }.toMap()

fun Map<MicronutrientType, Boolean>.toPersistedMicronutrientChecklist(): Map<String, Boolean> =
    entries.associate { (type, value) -> type.id to value }

fun Map<String, Boolean>.toMicronutrientChecklist(): Map<MicronutrientType, Boolean> =
    entries.mapNotNull { (key, value) ->
        MicronutrientType.fromId(key)?.let { it to value }
    }.toMap()

fun Double.formatMicronutrientAmount(): String =
    if (this % 1.0 == 0.0) {
        toInt().toString()
    } else {
        String.format("%.1f", this)
    }

fun MicronutrientTarget.isSatisfiedBy(amount: Double): Boolean = when {
    min == 0.0 && max != null -> amount <= max
    max != null -> amount >= min && amount <= max
    else -> amount >= min
}

