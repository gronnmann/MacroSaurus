package com.macrosaurus.catalog

import com.macrosaurus.identity.UserContext
import com.macrosaurus.shared.BasisType
import com.macrosaurus.shared.ForbiddenException
import com.macrosaurus.shared.InvalidOperationException
import com.macrosaurus.shared.JsonCodec
import com.macrosaurus.shared.NotFoundException
import com.macrosaurus.shared.NutrientValues
import com.macrosaurus.shared.SourceKind
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.util.UUID

data class NutrientDefinitionView(
    val code: String,
    val displayName: String,
    val category: String,
    val unit: String,
    val sortOrder: Int,
)

data class PortionView(
    val id: UUID,
    val name: String,
    val quantity: BigDecimal,
    val gramWeight: BigDecimal?,
    val milliliterVolume: BigDecimal?,
    val default: Boolean,
)

data class FoodView(
    val id: UUID,
    val revisionId: UUID,
    val revision: Int,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val source: SourceKind,
    val basisType: BasisType,
    val basisAmount: BigDecimal,
    val basisUnit: String,
    val densityGPerMl: BigDecimal?,
    val nutrients: Map<String, BigDecimal>,
    val portions: List<PortionView>,
    val createdAt: OffsetDateTime,
)

data class PortionInput(
    @field:NotBlank val name: String,
    @field:DecimalMin("0.000001") val quantity: BigDecimal = BigDecimal.ONE,
    @field:DecimalMin("0.000001") val gramWeight: BigDecimal? = null,
    @field:DecimalMin("0.000001") val milliliterVolume: BigDecimal? = null,
    val default: Boolean = false,
)

data class CreateFoodRequest(
    @field:NotBlank val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val basisType: BasisType = BasisType.PER_100_G,
    @field:DecimalMin("0.000001") val basisAmount: BigDecimal = BigDecimal("100"),
    @field:NotBlank val basisUnit: String = "g",
    @field:DecimalMin("0.000001") val densityGPerMl: BigDecimal? = null,
    val nutrients: Map<
        String,
        @DecimalMin("0")
        BigDecimal,
    >,
    val portions: List<@Valid PortionInput> = emptyList(),
)

data class FoodAmountRequest(
    @field:DecimalMin("0.000001") val quantity: BigDecimal,
    val unit: String,
    val portionId: UUID? = null,
)

data class ResolvedFoodAmount(
    val foodRevisionId: UUID,
    val displayName: String,
    val quantity: BigDecimal,
    val unit: String,
    val resolvedGrams: BigDecimal?,
    val nutrients: NutrientValues,
)

@Service
class CatalogService(
    private val db: DSLContext,
    private val json: JsonCodec,
) {
    fun nutrients(): List<NutrientDefinitionView> =
        db
            .fetch(
                "select code, display_name, category, unit, sort_order from nutrient_definitions order by sort_order, code",
            ).map {
                NutrientDefinitionView(
                    it.get("code", String::class.java)!!,
                    it.get("display_name", String::class.java)!!,
                    it.get("category", String::class.java)!!,
                    it.get("unit", String::class.java)!!,
                    it.get("sort_order", Int::class.java)!!,
                )
            }

    fun search(
        userId: String,
        query: String,
        limit: Int = 25,
    ): List<FoodView> {
        val term = "%${query.trim()}%"
        return db
            .fetch(
                """
                select distinct on (f.id) f.id, fr.id as revision_id, fr.revision, fr.name, fr.brand,
                       f.barcode, f.source_kind, fr.basis_type, fr.basis_amount, fr.basis_unit,
                       fr.density_g_per_ml, fr.created_at
                  from foods f join food_revisions fr on fr.food_id = f.id
                 where (f.source_kind <> 'USER' or f.owner_user_id = ?)
                   and (fr.name ilike ? or coalesce(fr.brand, '') ilike ? or coalesce(f.barcode, '') = ?)
                 order by f.id, fr.revision desc
                 limit ?
                """.trimIndent(),
                userId,
                term,
                term,
                query.trim(),
                limit.coerceIn(1, 100),
            ).map { foodFromRecord(it) }
    }

    fun get(
        userId: String,
        foodId: UUID,
    ): FoodView {
        val record =
            db.fetchOne(
                """
                select f.id, fr.id as revision_id, fr.revision, fr.name, fr.brand, f.barcode,
                       f.source_kind, fr.basis_type, fr.basis_amount, fr.basis_unit,
                       fr.density_g_per_ml, fr.created_at
                  from foods f join food_revisions fr on fr.food_id = f.id
                 where f.id = ? and (f.source_kind <> 'USER' or f.owner_user_id = ?)
                 order by fr.revision desc limit 1
                """.trimIndent(),
                foodId,
                userId,
            ) ?: throw NotFoundException("Food was not found")
        return foodFromRecord(record)
    }

    fun byRevision(
        userId: String,
        revisionId: UUID,
    ): FoodView {
        val record =
            db.fetchOne(
                """
                select f.id, fr.id as revision_id, fr.revision, fr.name, fr.brand, f.barcode,
                       f.source_kind, fr.basis_type, fr.basis_amount, fr.basis_unit,
                       fr.density_g_per_ml, fr.created_at
                  from foods f join food_revisions fr on fr.food_id = f.id
                 where fr.id = ? and (f.source_kind <> 'USER' or f.owner_user_id = ?)
                """.trimIndent(),
                revisionId,
                userId,
            ) ?: throw NotFoundException("Food revision was not found")
        return foodFromRecord(record)
    }

    @Transactional
    fun create(
        userId: String,
        request: CreateFoodRequest,
        source: SourceKind = SourceKind.USER,
        externalId: String? = null,
    ): FoodView {
        validateBasis(request)
        val foodId = UUID.randomUUID()
        val revisionId = UUID.randomUUID()
        db.execute(
            "insert into foods(id, owner_user_id, source_kind, external_id, barcode) values (?, ?, ?, ?, ?)",
            foodId,
            if (source == SourceKind.USER) userId else null,
            source.name,
            externalId,
            normalizeBarcode(request.barcode),
        )
        insertRevision(foodId, revisionId, 1, request)
        return get(userId, foodId)
    }

    @Transactional
    fun revise(
        userId: String,
        foodId: UUID,
        request: CreateFoodRequest,
    ): FoodView {
        val existing = get(userId, foodId)
        if (existing.source != SourceKind.USER) throw ForbiddenException("External source foods cannot be edited")
        validateBasis(request)
        val revisionId = UUID.randomUUID()
        insertRevision(foodId, revisionId, existing.revision + 1, request)
        db.execute("update foods set barcode = ? where id = ?", normalizeBarcode(request.barcode), foodId)
        return get(userId, foodId)
    }

    fun resolve(
        userId: String,
        revisionId: UUID,
        request: FoodAmountRequest,
    ): ResolvedFoodAmount {
        val food = byRevision(userId, revisionId)
        val portion =
            request.portionId?.let { id ->
                food.portions.firstOrNull { it.id == id } ?: throw InvalidOperationException("Portion does not belong to this food revision")
            }
        val (factor, grams) =
            when {
                portion != null && portion.gramWeight != null -> {
                    val totalG = portion.gramWeight.multiply(request.quantity).divide(portion.quantity, 12, RoundingMode.HALF_UP)
                    factorForMass(food, totalG) to totalG
                }

                portion != null && portion.milliliterVolume != null -> {
                    val ml = portion.milliliterVolume.multiply(request.quantity).divide(portion.quantity, 12, RoundingMode.HALF_UP)
                    factorForVolume(food, ml) to food.densityGPerMl?.multiply(ml)
                }

                request.unit.lowercase() in setOf("g", "gram", "grams") -> {
                    factorForMass(food, request.quantity) to request.quantity
                }

                request.unit.lowercase() in setOf("ml", "milliliter", "milliliters") -> {
                    factorForVolume(food, request.quantity) to food.densityGPerMl?.multiply(request.quantity)
                }

                request.unit.lowercase() in setOf("serving", "servings") && food.basisType == BasisType.PER_SERVING -> {
                    request.quantity.divide(food.basisAmount, 12, RoundingMode.HALF_UP) to null
                }

                else -> {
                    throw InvalidOperationException("The selected unit cannot be converted for this food")
                }
            }
        return ResolvedFoodAmount(
            food.revisionId,
            food.name,
            request.quantity,
            request.unit,
            grams,
            NutrientValues(food.nutrients).scaled(factor),
        )
    }

    private fun factorForMass(
        food: FoodView,
        grams: BigDecimal,
    ): BigDecimal =
        when (food.basisType) {
            BasisType.PER_100_G -> {
                grams.divide(food.basisAmount, 12, RoundingMode.HALF_UP)
            }

            BasisType.PER_100_ML -> {
                food.densityGPerMl?.let { density ->
                    grams.divide(density, 12, RoundingMode.HALF_UP).divide(food.basisAmount, 12, RoundingMode.HALF_UP)
                } ?: throw InvalidOperationException("Density is required to convert this volume-based food to grams")
            }

            BasisType.PER_SERVING -> {
                throw InvalidOperationException("This serving-only food has no gram conversion")
            }
        }

    private fun factorForVolume(
        food: FoodView,
        ml: BigDecimal,
    ): BigDecimal =
        when (food.basisType) {
            BasisType.PER_100_ML -> {
                ml.divide(food.basisAmount, 12, RoundingMode.HALF_UP)
            }

            BasisType.PER_100_G -> {
                food.densityGPerMl?.multiply(ml)?.divide(food.basisAmount, 12, RoundingMode.HALF_UP)
                    ?: throw InvalidOperationException("Density is required to convert this mass-based food to milliliters")
            }

            BasisType.PER_SERVING -> {
                throw InvalidOperationException("This serving-only food has no volume conversion")
            }
        }

    private fun insertRevision(
        foodId: UUID,
        revisionId: UUID,
        revision: Int,
        request: CreateFoodRequest,
    ) {
        db.execute(
            """
            insert into food_revisions(id, food_id, revision, name, brand, basis_type, basis_amount, basis_unit, density_g_per_ml)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            revisionId,
            foodId,
            revision,
            request.name.trim(),
            request.brand?.trim(),
            request.basisType.name,
            request.basisAmount,
            request.basisUnit,
            request.densityGPerMl,
        )
        request.nutrients.forEach { (code, amount) ->
            db.execute(
                "insert into food_nutrients(food_revision_id, nutrient_code, amount, value_kind) values (?, ?, ?, 'REPORTED')",
                revisionId,
                code,
                amount,
            )
        }
        request.portions.forEach { portion ->
            if (portion.gramWeight == null && portion.milliliterVolume == null) {
                throw InvalidOperationException("Named portions need a gram weight or milliliter volume")
            }
            db.execute(
                """
                insert into portions(
                    id, food_revision_id, name, quantity, gram_weight, milliliter_volume, is_default
                ) values (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                revisionId,
                portion.name.trim(),
                portion.quantity,
                portion.gramWeight,
                portion.milliliterVolume,
                portion.default,
            )
        }
    }

    private fun validateBasis(request: CreateFoodRequest) {
        when (request.basisType) {
            BasisType.PER_100_G -> {
                if (request.basisUnit.lowercase() !in setOf("g", "gram", "grams")) {
                    throw InvalidOperationException("PER_100_G foods must use grams")
                }
            }

            BasisType.PER_100_ML -> {
                if (request.basisUnit.lowercase() !in setOf("ml", "milliliter", "milliliters")) {
                    throw InvalidOperationException("PER_100_ML foods must use milliliters")
                }
            }

            BasisType.PER_SERVING -> {
                Unit
            }
        }
    }

    private fun foodFromRecord(record: org.jooq.Record): FoodView {
        val revisionId = record.get("revision_id", UUID::class.java)!!
        val nutrients =
            db
                .fetch(
                    "select nutrient_code, amount from food_nutrients where food_revision_id = ?",
                    revisionId,
                ).associate { it.get("nutrient_code", String::class.java)!! to it.get("amount", BigDecimal::class.java)!! }
        val portions =
            db
                .fetch(
                    """
                    select id, name, quantity, gram_weight, milliliter_volume, is_default
                      from portions
                     where food_revision_id = ?
                     order by is_default desc, name
                    """.trimIndent(),
                    revisionId,
                ).map {
                    PortionView(
                        it.get("id", UUID::class.java)!!,
                        it.get("name", String::class.java)!!,
                        it.get("quantity", BigDecimal::class.java)!!,
                        it.get("gram_weight", BigDecimal::class.java),
                        it.get("milliliter_volume", BigDecimal::class.java),
                        it.get("is_default", Boolean::class.java) ?: false,
                    )
                }
        return FoodView(
            record.get("id", UUID::class.java)!!,
            revisionId,
            record.get("revision", Int::class.java)!!,
            record.get("name", String::class.java)!!,
            record.get("brand", String::class.java),
            record.get("barcode", String::class.java),
            SourceKind.valueOf(record.get("source_kind", String::class.java)!!),
            BasisType.valueOf(record.get("basis_type", String::class.java)!!),
            record.get("basis_amount", BigDecimal::class.java)!!,
            record.get("basis_unit", String::class.java)!!,
            record.get("density_g_per_ml", BigDecimal::class.java),
            nutrients,
            portions,
            record.get("created_at", OffsetDateTime::class.java)!!,
        )
    }

    private fun normalizeBarcode(barcode: String?): String? = barcode?.filter(Char::isDigit)?.takeIf { it.isNotBlank() }
}

@RestController
@RequestMapping("/api/v1")
class CatalogController(
    private val users: UserContext,
    private val catalog: CatalogService,
) {
    @GetMapping("/nutrients")
    fun nutrients() = catalog.nutrients()

    @GetMapping("/foods")
    fun search(
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(defaultValue = "25") limit: Int,
    ) = catalog.search(users.userId(), query, limit)

    @GetMapping("/foods/{id}")
    fun get(
        @PathVariable id: UUID,
    ) = catalog.get(users.userId(), id)

    @GetMapping("/food-revisions/{id}")
    fun getRevision(
        @PathVariable id: UUID,
    ) = catalog.byRevision(users.userId(), id)

    @PostMapping("/foods")
    fun create(
        @Valid @RequestBody request: CreateFoodRequest,
    ) = catalog.create(users.userId(), request)

    @PutMapping("/foods/{id}")
    fun revise(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateFoodRequest,
    ) = catalog.revise(users.userId(), id, request)

    @PostMapping("/food-revisions/{id}/resolve")
    fun resolve(
        @PathVariable id: UUID,
        @Valid @RequestBody request: FoodAmountRequest,
    ) = catalog.resolve(users.userId(), id, request)
}
