package com.macrosaurus.recipes.application

import com.macrosaurus.catalog.FoodAmount
import com.macrosaurus.catalog.FoodCatalog
import com.macrosaurus.catalog.FoodResolver
import com.macrosaurus.recipes.RecipeIngredientSnapshot
import com.macrosaurus.recipes.RecipeReader
import com.macrosaurus.recipes.RecipeSnapshot
import com.macrosaurus.recipes.persistence.JooqRecipeRepository
import com.macrosaurus.recipes.persistence.StoredRecipeIngredient
import com.macrosaurus.shared.NotFoundException
import com.macrosaurus.shared.NutrientValues
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

internal data class RecipeIngredientCommand(
    val foodRevisionId: UUID,
    val quantity: BigDecimal,
    val unit: String,
    val portionId: UUID?,
)

internal data class SaveRecipeCommand(
    val name: String,
    val servings: BigDecimal,
    val finishedWeightG: BigDecimal?,
    val ingredients: List<RecipeIngredientCommand>,
)

@Service
internal class RecipeService(
    private val repository: JooqRecipeRepository,
    private val catalog: FoodCatalog,
    private val foodResolver: FoodResolver,
) : RecipeReader {
    override fun list(userId: String): List<RecipeSnapshot> = repository.latestRevisionIds(userId).map { getByRevision(userId, it) }

    override fun get(
        userId: String,
        recipeId: UUID,
    ): RecipeSnapshot {
        val revisionId = repository.latestRevisionId(userId, recipeId) ?: throw NotFoundException("Recipe was not found")
        return getByRevision(userId, revisionId)
    }

    override fun getByRevision(
        userId: String,
        revisionId: UUID,
    ): RecipeSnapshot {
        val stored = repository.findRevision(userId, revisionId) ?: throw NotFoundException("Recipe revision was not found")
        val foods = catalog.byRevisions(userId, stored.ingredients.map { it.foodRevisionId })
        val yield = stored.explicitYieldG ?: stored.estimatedYieldG
        return RecipeSnapshot(
            stored.recipeId,
            stored.revisionId,
            stored.revision,
            stored.name,
            stored.servings,
            stored.explicitYieldG,
            stored.estimatedYieldG,
            stored.nutrients.values,
            stored.nutrients.scaled(BigDecimal.ONE.divide(stored.servings, 12, RoundingMode.HALF_UP)).values,
            yield?.let { stored.nutrients.scaled(BigDecimal("100").divide(it, 12, RoundingMode.HALF_UP)).values },
            stored.ingredients.map { ingredient ->
                RecipeIngredientSnapshot(
                    ingredient.id,
                    ingredient.foodRevisionId,
                    foods.getValue(ingredient.foodRevisionId).name,
                    ingredient.quantity,
                    ingredient.unit,
                    ingredient.portionId,
                    ingredient.resolvedGrams,
                    ingredient.nutrients.values,
                )
            },
            stored.createdAt,
        )
    }

    @Transactional
    fun create(
        userId: String,
        command: SaveRecipeCommand,
    ): RecipeSnapshot {
        val recipeId = UUID.randomUUID()
        repository.insertRecipe(recipeId, userId)
        return insertRevision(userId, recipeId, 1, command)
    }

    @Transactional
    fun revise(
        userId: String,
        recipeId: UUID,
        command: SaveRecipeCommand,
    ): RecipeSnapshot {
        if (!repository.lockOwnedRecipe(recipeId, userId)) throw NotFoundException("Recipe was not found")
        return insertRevision(userId, recipeId, get(userId, recipeId).revision + 1, command)
    }

    private fun insertRevision(
        userId: String,
        recipeId: UUID,
        revision: Int,
        command: SaveRecipeCommand,
    ): RecipeSnapshot {
        val resolved =
            command.ingredients.map { input ->
                input to foodResolver.resolve(userId, input.foodRevisionId, FoodAmount(input.quantity, input.unit, input.portionId))
            }
        val total = resolved.fold(NutrientValues.EMPTY) { sum, (_, ingredient) -> sum.plus(ingredient.nutrients) }
        val estimatedYield =
            resolved
                .map { it.second.resolvedGrams }
                .takeIf { values -> values.all { it != null } }
                ?.fold(BigDecimal.ZERO) { sum, value -> sum.add(requireNotNull(value)) }
        val revisionId = UUID.randomUUID()
        repository.insertRevision(
            recipeId,
            revisionId,
            revision,
            command.name.trim(),
            command.servings,
            command.finishedWeightG,
            estimatedYield,
            total,
            resolved.map { (input, ingredient) ->
                StoredRecipeIngredient(
                    UUID.randomUUID(),
                    input.foodRevisionId,
                    input.quantity,
                    input.unit,
                    input.portionId,
                    ingredient.resolvedGrams,
                    ingredient.nutrients,
                )
            },
        )
        return getByRevision(userId, revisionId)
    }
}
