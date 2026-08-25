package com.macrosaurus.recipes.persistence

import com.macrosaurus.shared.JsonCodec
import com.macrosaurus.shared.NutrientValues
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

internal data class StoredRecipeRevision(
    val recipeId: UUID,
    val revisionId: UUID,
    val revision: Int,
    val name: String,
    val servings: BigDecimal,
    val explicitYieldG: BigDecimal?,
    val estimatedYieldG: BigDecimal?,
    val nutrients: NutrientValues,
    val createdAt: OffsetDateTime,
    val ingredients: List<StoredRecipeIngredient>,
)

internal data class StoredRecipeIngredient(
    val id: UUID,
    val foodRevisionId: UUID,
    val quantity: BigDecimal,
    val unit: String,
    val portionId: UUID?,
    val resolvedGrams: BigDecimal?,
    val nutrients: NutrientValues,
)

@Repository
internal class JooqRecipeRepository(
    private val db: DSLContext,
    private val json: JsonCodec,
) {
    fun latestRevisionIds(userId: String): List<UUID> =
        db
            .fetch(
                """
                select distinct on (r.id) rr.id as revision_id
                  from recipes r join recipe_revisions rr on rr.recipe_id = r.id
                 where r.owner_user_id = ? order by r.id, rr.revision desc
                """.trimIndent(),
                userId,
            ).map { it.get("revision_id", UUID::class.java)!! }

    fun latestRevisionId(
        userId: String,
        recipeId: UUID,
    ): UUID? =
        db.fetchValue(
            """
            select rr.id from recipes r join recipe_revisions rr on rr.recipe_id = r.id
             where r.id = ? and r.owner_user_id = ? order by rr.revision desc limit 1
            """.trimIndent(),
            recipeId,
            userId,
        ) as UUID?

    fun findRevision(
        userId: String,
        revisionId: UUID,
    ): StoredRecipeRevision? {
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
            ) ?: return null
        val ingredients =
            db
                .fetch(
                    """
                    select id, food_revision_id, quantity, unit, portion_id, resolved_grams, nutrients::text as nutrients
                      from recipe_ingredients where recipe_revision_id = ? order by id
                    """.trimIndent(),
                    revisionId,
                ).map {
                    StoredRecipeIngredient(
                        it.get("id", UUID::class.java)!!,
                        it.get("food_revision_id", UUID::class.java)!!,
                        it.get("quantity", BigDecimal::class.java)!!,
                        it.get("unit", String::class.java)!!,
                        it.get("portion_id", UUID::class.java),
                        it.get("resolved_grams", BigDecimal::class.java),
                        json.nutrients(it.get("nutrients", String::class.java)!!),
                    )
                }
        return StoredRecipeRevision(
            record.get("id", UUID::class.java)!!,
            revisionId,
            record.get("revision", Int::class.java)!!,
            record.get("name", String::class.java)!!,
            record.get("servings", BigDecimal::class.java)!!,
            record.get("explicit_yield_g", BigDecimal::class.java),
            record.get("estimated_yield_g", BigDecimal::class.java),
            json.nutrients(record.get("nutrients", String::class.java)!!),
            record.get("created_at", OffsetDateTime::class.java)!!,
            ingredients,
        )
    }

    fun insertRecipe(
        recipeId: UUID,
        userId: String,
    ) {
        db.execute("insert into recipes(id, owner_user_id) values (?, ?)", recipeId, userId)
    }

    fun lockOwnedRecipe(
        recipeId: UUID,
        userId: String,
    ): Boolean = db.fetchOne("select id from recipes where id = ? and owner_user_id = ? for update", recipeId, userId) != null

    fun insertRevision(
        recipeId: UUID,
        revisionId: UUID,
        revision: Int,
        name: String,
        servings: BigDecimal,
        explicitYieldG: BigDecimal?,
        estimatedYieldG: BigDecimal?,
        nutrients: NutrientValues,
        ingredients: List<StoredRecipeIngredient>,
    ) {
        db.execute(
            """
            insert into recipe_revisions(id, recipe_id, revision, name, servings, explicit_yield_g, estimated_yield_g, nutrients)
            values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
            """.trimIndent(),
            revisionId,
            recipeId,
            revision,
            name,
            servings,
            explicitYieldG,
            estimatedYieldG,
            json.writeNutrients(nutrients),
        )
        ingredients.forEach { ingredient ->
            db.execute(
                """
                insert into recipe_ingredients(id, recipe_revision_id, food_revision_id, quantity, unit,
                                               portion_id, resolved_grams, nutrients)
                values (?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                """.trimIndent(),
                ingredient.id,
                revisionId,
                ingredient.foodRevisionId,
                ingredient.quantity,
                ingredient.unit,
                ingredient.portionId,
                ingredient.resolvedGrams,
                json.writeNutrients(ingredient.nutrients),
            )
        }
    }
}
