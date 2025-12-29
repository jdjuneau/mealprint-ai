package com.coachie.app.data.ai

import com.mealprint.ai.data.model.MicronutrientType
import com.mealprint.ai.data.model.MicronutrientUnit

/**
 * Maps raw nutrient label strings to [MicronutrientType] and normalizes amounts.
 */
object MicronutrientNameMapper {

    private val aliasMap: Map<String, MicronutrientType> = buildMap {
        fun add(type: MicronutrientType, vararg aliases: String) {
            aliases.forEach { alias ->
                put(normalize(alias), type)
            }
        }

        add(MicronutrientType.VITAMIN_A, "vitamin a", "vit a", "retinol", "retinyl")
        add(MicronutrientType.VITAMIN_C, "vitamin c", "vit c", "ascorbic")
        add(MicronutrientType.VITAMIN_D, "vitamin d", "vit d", "cholecalciferol", "ergocalciferol")
        add(MicronutrientType.VITAMIN_E, "vitamin e", "vit e", "tocopherol")
        add(MicronutrientType.VITAMIN_K, "vitamin k", "vit k", "phytonadione", "menaquinone")
        add(MicronutrientType.VITAMIN_B1, "vitamin b1", "vit b1", "thiamine", "thiamin")
        add(MicronutrientType.VITAMIN_B2, "vitamin b2", "vit b2", "riboflavin")
        add(MicronutrientType.VITAMIN_B3, "vitamin b3", "vit b3", "niacin", "niacinamide")
        add(MicronutrientType.VITAMIN_B5, "vitamin b5", "vit b5", "pantothenic")
        add(MicronutrientType.VITAMIN_B6, "vitamin b6", "vit b6", "pyridoxine", "pyridoxal")
        add(MicronutrientType.VITAMIN_B7, "vitamin b7", "vit b7", "biotin")
        add(MicronutrientType.VITAMIN_B9, "vitamin b9", "vit b9", "folate", "folic")
        add(MicronutrientType.VITAMIN_B12, "vitamin b12", "vit b12", "cobalamin", "methylcobalamin")

        add(MicronutrientType.CALCIUM, "calcium")
        add(MicronutrientType.MAGNESIUM, "magnesium")
        add(MicronutrientType.POTASSIUM, "potassium")
        add(MicronutrientType.SODIUM, "sodium")
        add(MicronutrientType.IRON, "iron", "ferrous", "ferric")
        add(MicronutrientType.ZINC, "zinc")
        add(MicronutrientType.IODINE, "iodine")
        add(MicronutrientType.SELENIUM, "selenium")
        add(MicronutrientType.PHOSPHORUS, "phosphorus", "phosphate")
        add(MicronutrientType.MANGANESE, "manganese")
        add(MicronutrientType.COPPER, "copper")
    }

    private val unitNormalization: Map<String, MicronutrientUnit> = mapOf(
        "mg" to MicronutrientUnit.MG,
        "mcg" to MicronutrientUnit.MCG,
        "µg" to MicronutrientUnit.MCG,
        "ug" to MicronutrientUnit.MCG,
        "g" to MicronutrientUnit.MG, // convert to mg
        "iu" to MicronutrientUnit.IU
    )

    private fun normalize(input: String): String =
        input.lowercase().replace("[^a-z0-9]".toRegex(), "")

    /**
     * Attempt to map a raw nutrient label to a [MicronutrientType].
     */
    fun identify(raw: String): MicronutrientType? {
        val normalized = normalize(raw)
        return aliasMap.entries.firstOrNull { (alias, _) ->
            normalized.contains(alias)
        }?.value ?: fuzzyIdentify(normalized)
    }

    private fun fuzzyIdentify(normalized: String): MicronutrientType? {
        if (normalized.isBlank()) return null

        var bestMatch: MicronutrientType? = null
        var bestScore = Int.MAX_VALUE

        aliasMap.forEach { (alias, type) ->
            val distance = levenshtein(normalized, alias)
            if (distance < bestScore) {
                bestScore = distance
                bestMatch = type
            }
        }

        val maxAllowed = when {
            normalized.length >= 10 -> 3
            normalized.length >= 6 -> 2
            else -> 1
        }

        return if (bestScore <= maxAllowed) bestMatch else null
    }

    /**
     * Parse a unit string into [MicronutrientUnit], handling µ and uppercase variants.
     */
    fun parseUnit(rawUnit: String): MicronutrientUnit? =
        unitNormalization[rawUnit.lowercase()]

    /**
     * Convert the supplied [amount] and source [unit] into the canonical unit used by [type].
     * Returns null if the conversion is unsupported.
     */
    fun convert(amount: Double, unit: MicronutrientUnit, type: MicronutrientType): Double? =
        when (type.unit) {
            unit -> amount
            MicronutrientUnit.MCG -> when (unit) {
                MicronutrientUnit.MG -> amount * 1000.0
                MicronutrientUnit.IU -> when (type) {
                    MicronutrientType.VITAMIN_A -> amount * 0.3
                    else -> null
                }
                else -> null
            }

            MicronutrientUnit.MG -> when (unit) {
                MicronutrientUnit.MCG -> amount / 1000.0
                MicronutrientUnit.MG -> amount
                MicronutrientUnit.IU -> null
                else -> null
            }

            MicronutrientUnit.IU -> when (unit) {
                MicronutrientUnit.MCG -> when (type) {
                    MicronutrientType.VITAMIN_D -> amount * 40.0
                    else -> null
                }

                MicronutrientUnit.MG -> when (type) {
                    MicronutrientType.VITAMIN_D -> amount * 1000.0 * 40.0
                    else -> null
                }

                MicronutrientUnit.IU -> amount
            }
        }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val column = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var previous = column[0]
            column[0] = i
            for (j in 1..b.length) {
                val temp = column[j]
                val insertion = column[j] + 1
                val deletion = column[j - 1] + 1
                val substitution = previous + if (a[i - 1] == b[j - 1]) 0 else 1
                column[j] = minOf(insertion, deletion, substitution)
                previous = temp
            }
        }
        return column[b.length]
    }
}

