package com.macrosaurus.tracking.application

import com.macrosaurus.shared.InvalidOperationException
import java.math.BigDecimal

internal val quickCoreNutrients = setOf("energy_kcal", "protein_g", "carbohydrate_g", "fat_g", "fiber_g")

internal fun quickNutrients(
    protein: BigDecimal,
    carbohydrate: BigDecimal,
    fat: BigDecimal,
    fiber: BigDecimal?,
    additional: Map<String, BigDecimal>,
    knownNutrients: Set<String>,
): Map<String, BigDecimal> {
    if (additional.values.any { it < BigDecimal.ZERO }) throw InvalidOperationException("Nutrient values cannot be negative")
    val unknown = additional.keys - knownNutrients
    if (unknown.isNotEmpty()) throw InvalidOperationException("Unknown nutrient codes: ${unknown.sorted().joinToString()}")
    if (additional.keys.any { it in quickCoreNutrients }) throw InvalidOperationException("Use the main fields for calories, macros and fiber")
    return additional +
        linkedMapOf(
            "protein_g" to protein,
            "carbohydrate_g" to carbohydrate,
            "fat_g" to fat,
        ).apply { fiber?.let { put("fiber_g", it) } }
}
