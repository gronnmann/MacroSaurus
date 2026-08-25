package com.macrosaurus.recipes

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class RecipeIngredientSnapshot(
    val id: UUID,
    val foodRevisionId: UUID,
    val name: String,
    val quantity: BigDecimal,
    val unit: String,
    val portionId: UUID?,
    val resolvedGrams: BigDecimal?,
    val nutrients: Map<String, BigDecimal>,
)

data class RecipeSnapshot(
    val id: UUID,
    val revisionId: UUID,
    val revision: Int,
    val name: String,
    val servings: BigDecimal,
    val explicitYieldG: BigDecimal?,
    val estimatedYieldG: BigDecimal?,
    val totalNutrients: Map<String, BigDecimal>,
    val nutrientsPerServing: Map<String, BigDecimal>,
    val nutrientsPer100G: Map<String, BigDecimal>?,
    val ingredients: List<RecipeIngredientSnapshot>,
    val createdAt: OffsetDateTime,
)

interface RecipeReader {
    fun list(userId: String): List<RecipeSnapshot>

    fun get(
        userId: String,
        recipeId: UUID,
    ): RecipeSnapshot

    fun getByRevision(
        userId: String,
        revisionId: UUID,
    ): RecipeSnapshot
}
