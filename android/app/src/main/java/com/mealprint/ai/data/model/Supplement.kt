/**
 * Represents a saved supplement definition with micronutrient information.
 */
package com.coachie.app.data.model

import com.google.firebase.firestore.PropertyName

data class Supplement(
    @PropertyName("id")
    val id: String = "",

    @PropertyName("name")
    val name: String = "",

    @PropertyName("micronutrients")
    val micronutrients: Map<String, Double> = emptyMap(),

    @PropertyName("labelImagePath")
    val labelImagePath: String? = null,

    @PropertyName("labelText")
    val labelText: String? = null,

    @PropertyName("isDaily")
    val isDaily: Boolean = false,

    @PropertyName("createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @PropertyName("updatedAt")
    val updatedAt: Long = System.currentTimeMillis()
) {
    // Note: This is not serialized to Firestore, only used at runtime
    val micronutrientsTyped: Map<MicronutrientType, Double>
        get() = micronutrients.toMicronutrientTypeMap()
}
