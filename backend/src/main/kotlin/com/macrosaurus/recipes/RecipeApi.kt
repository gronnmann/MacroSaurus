package com.macrosaurus.recipes

import com.macrosaurus.catalog.CatalogService
import com.macrosaurus.catalog.FoodAmountRequest
import com.macrosaurus.identity.UserContext
import com.macrosaurus.shared.JsonCodec
import com.macrosaurus.shared.NotFoundException
import com.macrosaurus.shared.NutrientValues
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
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

@Service
class RecipeService(
    private val db: DSLContext,
    private val json: JsonCodec,
    private val catalog: CatalogService,
) {
    fun list(userId: String): List<RecipeView> =
        db
            .fetch(
                """
                select distinct on (r.id) r.id, rr.id as revision_id
                  from recipes r join recipe_revisions rr on rr.recipe_id = r.id
                 where r.owner_user_id = ? order by r.id, rr.revision desc
                """.trimIndent(),
                userId,
            ).map { getByRevision(userId, it.get("revision_id", UUID::class.java)!!) }

    fun get(
        userId: String,
        recipeId: UUID,
    ): RecipeView {
        val revisionId =
            db.fetchValue(
                """
                select rr.id from recipes r join recipe_revisions rr on rr.recipe_id = r.id
                 where r.id = ? and r.owner_user_id = ? order by rr.revision desc limit 1
                """.trimIndent(),
                recipeId,
                userId,
            ) as UUID? ?: throw NotFoundException("Recipe was not found")
        return getByRevision(userId, revisionId)
    }

    fun getByRevision(
        userId: String,
        revisionId: UUID,
    ): RecipeView {
        val record =
            db.fetchOne(
                """
                select r.id, rr.id as revision_id, rr.revision, rr.name, rr.servings,
                       rr.explicit_yield_g, rr.estimated_yield_g, rr.nutrients::text as nutrients, rr.created_at
                  from recipes r join recipe_revisions rr on rr.recipe_id = r.id
                 where rr.id = ? and r.owner_user_id = ?
                """.trimIndent(),
                revisionId,
                userId,
            ) ?: throw NotFoundException("Recipe revision was not found")
        val total = json.nutrients(record.get("nutrients", String::class.java)!!)
        val servings = record.get("servings", BigDecimal::class.java)!!
        val yield =
            record.get("explicit_yield_g", BigDecimal::class.java)
                ?: record.get("estimated_yield_g", BigDecimal::class.java)
        val ingredients =
            db
                .fetch(
                    """
                    select id, food_revision_id, quantity, unit, portion_id, resolved_grams, nutrients::text as nutrients
                      from recipe_ingredients where recipe_revision_id = ? order by id
                    """.trimIndent(),
                    revisionId,
                ).map {
                    val foodRevisionId = it.get("food_revision_id", UUID::class.java)!!
                    RecipeIngredientView(
                        it.get("id", UUID::class.java)!!,
                        foodRevisionId,
                        catalog.byRevision(userId, foodRevisionId).name,
                        it.get("quantity", BigDecimal::class.java)!!,
                        it.get("unit", String::class.java)!!,
                        it.get("portion_id", UUID::class.java),
                        it.get("resolved_grams", BigDecimal::class.java),
                        json.nutrients(it.get("nutrients", String::class.java)!!).values,
                    )
                }
        return RecipeView(
            record.get("id", UUID::class.java)!!,
            revisionId,
            record.get("revision", Int::class.java)!!,
            record.get("name", String::class.java)!!,
            servings,
            record.get("explicit_yield_g", BigDecimal::class.java),
            record.get("estimated_yield_g", BigDecimal::class.java),
            total.values,
            total.scaled(BigDecimal.ONE.divide(servings, 12, RoundingMode.HALF_UP)).values,
            yield?.let { total.scaled(BigDecimal("100").divide(it, 12, RoundingMode.HALF_UP)).values },
            ingredients,
            record.get("created_at", OffsetDateTime::class.java)!!,
        )
    }

    @Transactional
    fun create(
        userId: String,
        request: SaveRecipeRequest,
    ): RecipeView {
        val recipeId = UUID.randomUUID()
        db.execute("insert into recipes(id, owner_user_id) values (?, ?)", recipeId, userId)
        return insertRevision(userId, recipeId, 1, request)
    }

    @Transactional
    fun revise(
        userId: String,
        recipeId: UUID,
        request: SaveRecipeRequest,
    ): RecipeView {
        val current = get(userId, recipeId)
        return insertRevision(userId, recipeId, current.revision + 1, request)
    }

    private fun insertRevision(
        userId: String,
        recipeId: UUID,
        revision: Int,
        request: SaveRecipeRequest,
    ): RecipeView {
        val resolved =
            request.ingredients.map { input ->
                input to catalog.resolve(userId, input.foodRevisionId, FoodAmountRequest(input.quantity, input.unit, input.portionId))
            }
        val total = resolved.fold(NutrientValues.EMPTY) { sum, (_, ingredient) -> sum.plus(ingredient.nutrients) }
        val allHaveMass = resolved.all { (_, ingredient) -> ingredient.resolvedGrams != null }
        val estimatedYield =
            if (allHaveMass) {
                resolved.fold(BigDecimal.ZERO) { sum, (_, ingredient) ->
                    sum.add(requireNotNull(ingredient.resolvedGrams))
                }
            } else {
                null
            }
        val revisionId = UUID.randomUUID()
        db.execute(
            """
            insert into recipe_revisions(id, recipe_id, revision, name, servings, explicit_yield_g, estimated_yield_g, nutrients)
            values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
            """.trimIndent(),
            revisionId,
            recipeId,
            revision,
            request.name.trim(),
            request.servings,
            request.finishedWeightG,
            estimatedYield,
            json.writeNutrients(total),
        )
        resolved.forEach { (input, ingredient) ->
            db.execute(
                """
                insert into recipe_ingredients(id, recipe_revision_id, food_revision_id, quantity, unit,
                                               portion_id, resolved_grams, nutrients)
                values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                """.trimIndent(),
                UUID.randomUUID(),
                revisionId,
                input.foodRevisionId,
                input.quantity,
                input.unit,
                input.portionId,
                ingredient.resolvedGrams,
                json.writeNutrients(ingredient.nutrients),
            )
        }
        return getByRevision(userId, revisionId)
    }
}

@RestController
@RequestMapping("/api/v1/recipes")
class RecipeController(
    private val users: UserContext,
    private val recipes: RecipeService,
) {
    @GetMapping
    fun list() = recipes.list(users.userId())

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: UUID,
    ) = recipes.get(users.userId(), id)

    @GetMapping("/revisions/{id}")
    fun getRevision(
        @PathVariable id: UUID,
    ) = recipes.getByRevision(users.userId(), id)

    @PostMapping
    fun create(
        @Valid @RequestBody request: SaveRecipeRequest,
    ) = recipes.create(users.userId(), request)

    @PutMapping("/{id}")
    fun revise(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SaveRecipeRequest,
    ) = recipes.revise(users.userId(), id, request)
}
