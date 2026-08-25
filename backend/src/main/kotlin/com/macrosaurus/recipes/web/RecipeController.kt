package com.macrosaurus.recipes.web

import com.macrosaurus.recipes.RecipeIngredientSnapshot
import com.macrosaurus.recipes.RecipeSnapshot
import com.macrosaurus.recipes.application.RecipeIngredientCommand
import com.macrosaurus.recipes.application.RecipeService
import com.macrosaurus.recipes.application.SaveRecipeCommand
import com.macrosaurus.shared.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class RecipeIngredientInput(
    val foodRevisionId: UUID,
    @field:DecimalMin("0.000001") val quantity: BigDecimal,
    @field:NotBlank val unit: String,
    val portionId: UUID? = null,
)

data class SaveRecipeRequest(
    @field:NotBlank val name: String,
    @field:DecimalMin("0.000001") val servings: BigDecimal,
    @field:DecimalMin("0.000001") val finishedWeightG: BigDecimal? = null,
    @field:Size(min = 1) val ingredients: List<@Valid RecipeIngredientInput>,
)

data class RecipeIngredientView(
    val id: UUID,
    val foodRevisionId: UUID,
    val name: String,
    val quantity: BigDecimal,
    val unit: String,
    val portionId: UUID?,
    val resolvedGrams: BigDecimal?,
    val nutrients: Map<String, BigDecimal>,
)

data class RecipeView(
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
    val ingredients: List<RecipeIngredientView>,
    val createdAt: OffsetDateTime,
)

private fun RecipeIngredientSnapshot.toView() = RecipeIngredientView(id, foodRevisionId, name, quantity, unit, portionId, resolvedGrams, nutrients)

private fun RecipeSnapshot.toView() =
    RecipeView(
        id,
        revisionId,
        revision,
        name,
        servings,
        explicitYieldG,
        estimatedYieldG,
        totalNutrients,
        nutrientsPerServing,
        nutrientsPer100G,
        ingredients.map { it.toView() },
        createdAt,
    )

private fun SaveRecipeRequest.toCommand() =
    SaveRecipeCommand(
        name,
        servings,
        finishedWeightG,
        ingredients.map { RecipeIngredientCommand(it.foodRevisionId, it.quantity, it.unit, it.portionId) },
    )

@RestController
@RequestMapping("/api/v1/recipes")
internal class RecipeController(
    private val users: CurrentUser,
    private val recipes: RecipeService,
) {
    @GetMapping
    fun list() = recipes.list(users.userId()).map { it.toView() }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ) = recipes.get(users.userId(), id).toView()

    @GetMapping("/revisions/{id}")
    fun getRevision(
        @PathVariable id: UUID,
    ) = recipes.getByRevision(users.userId(), id).toView()

    @PostMapping
    fun create(
        @Valid @RequestBody request: SaveRecipeRequest,
    ) = recipes.create(users.userId(), request.toCommand()).toView()

    @PutMapping("/{id}")
    fun revise(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SaveRecipeRequest,
    ) = recipes.revise(users.userId(), id, request.toCommand()).toView()
}
