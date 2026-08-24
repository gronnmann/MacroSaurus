package com.macrosaurus.tracking

import com.macrosaurus.catalog.CatalogService
import com.macrosaurus.catalog.CreateFoodRequest
import com.macrosaurus.catalog.FoodAmountRequest
import com.macrosaurus.identity.ProfileService
import com.macrosaurus.identity.UserContext
import com.macrosaurus.recipes.RecipeService
import com.macrosaurus.shared.BasisType
import com.macrosaurus.shared.InvalidOperationException
import com.macrosaurus.shared.JsonCodec
import com.macrosaurus.shared.NotFoundException
import com.macrosaurus.shared.NutrientMath
import com.macrosaurus.shared.NutrientValues
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

enum class Meal { BREAKFAST, LUNCH, DINNER, SNACK, OTHER }

enum class DiaryEntryType { FOOD, RECIPE, QUICK }

enum class TrackableType { ALL, FOOD, RECIPE }

data class DiaryEntryView(
    val id: UUID,
    val localDate: LocalDate,
    val consumedAt: OffsetDateTime,
    val meal: Meal,
    val displayName: String,
    val entryType: DiaryEntryType,
    val sourceRevisionId: UUID?,
    val quantity: BigDecimal?,
    val unit: String?,
    val portionId: UUID?,
    val nutrients: Map<String, BigDecimal>,
)

data class DiaryDayView(
    val date: LocalDate,
    val entries: List<DiaryEntryView>,
    val totals: Map<String, BigDecimal>,
)

data class TrackableView(
    val type: String,
    val id: UUID,
    val revisionId: UUID,
    val name: String,
    val brand: String?,
    val servingLabel: String,
    val nutrients: Map<String, BigDecimal>,
)

data class AddFoodEntryRequest(
    val foodRevisionId: UUID,
    @field:DecimalMin("0.000001") val quantity: BigDecimal,
    @field:NotBlank val unit: String,
    val portionId: UUID? = null,
    val localDate: LocalDate,
    val consumedAt: OffsetDateTime? = null,
    val meal: Meal = Meal.OTHER,
)

data class QuickTrackRequest(
    @field:NotBlank val name: String,
    val localDate: LocalDate,
    val consumedAt: OffsetDateTime? = null,
    val meal: Meal = Meal.OTHER,
    @field:DecimalMin("0") val calories: BigDecimal? = null,
    @field:DecimalMin("0") val proteinG: BigDecimal = BigDecimal.ZERO,
    @field:DecimalMin("0") val carbohydrateG: BigDecimal = BigDecimal.ZERO,
    @field:DecimalMin("0") val fatG: BigDecimal = BigDecimal.ZERO,
    @field:DecimalMin("0") val fiberG: BigDecimal? = null,
    val saveAsFood: Boolean = false,
)

data class AddRecipeEntryRequest(
    val recipeRevisionId: UUID,
    @field:DecimalMin("0.000001") val servings: BigDecimal,
    val localDate: LocalDate,
    val consumedAt: OffsetDateTime? = null,
    val meal: Meal = Meal.OTHER,
)

data class UpdateDiaryEntryRequest(
    val localDate: LocalDate,
    val consumedAt: OffsetDateTime,
    val meal: Meal,
    @field:DecimalMin("0.000001") val quantity: BigDecimal? = null,
    val unit: String? = null,
    val portionId: UUID? = null,
    val name: String? = null,
    @field:DecimalMin("0") val calories: BigDecimal? = null,
    @field:DecimalMin("0") val proteinG: BigDecimal? = null,
    @field:DecimalMin("0") val carbohydrateG: BigDecimal? = null,
    @field:DecimalMin("0") val fatG: BigDecimal? = null,
    @field:DecimalMin("0") val fiberG: BigDecimal? = null,
)

data class CopyDiaryEntryRequest(
    val destinationDate: LocalDate,
    val destinationTime: LocalTime? = null,
)

data class QuickTrackResult(
    val entry: DiaryEntryView,
    val calculatedCalories: BigDecimal,
    val calorieDiscrepancy: BigDecimal?,
    val savedFoodId: UUID?,
)

@Service
class TrackingService(
    private val db: DSLContext,
    private val json: JsonCodec,
    private val catalog: CatalogService,
    private val recipes: RecipeService,
    private val profiles: ProfileService,
    private val clock: Clock,
) {
    fun day(
        userId: String,
        date: LocalDate,
    ): DiaryDayView = dayView(date, entries(userId, date, date))

    @Transactional
    fun addFood(
        userId: String,
        request: AddFoodEntryRequest,
    ): DiaryEntryView {
        val resolved =
            catalog.resolve(
                userId,
                request.foodRevisionId,
                FoodAmountRequest(request.quantity, request.unit, request.portionId),
            )
        val id = UUID.randomUUID()
        insert(
            id,
            userId,
            request.localDate,
            request.consumedAt ?: OffsetDateTime.now(clock),
            request.meal,
            resolved.displayName,
            DiaryEntryType.FOOD,
            request.foodRevisionId,
            request.quantity,
            request.unit,
            request.portionId,
            resolved.nutrients,
        )
        return entry(userId, id)
    }

    @Transactional
    fun quickTrack(
        userId: String,
        request: QuickTrackRequest,
    ): QuickTrackResult {
        val macroValues =
            linkedMapOf(
                "protein_g" to request.proteinG,
                "carbohydrate_g" to request.carbohydrateG,
                "fat_g" to request.fatG,
            ).apply { request.fiberG?.let { put("fiber_g", it) } }
        val calculated = NutrientMath.calculatedCalories(macroValues)
        val usedCalories = request.calories ?: calculated
        val nutrients = NutrientValues(macroValues + ("energy_kcal" to usedCalories))
        val id = UUID.randomUUID()
        insert(
            id,
            userId,
            request.localDate,
            request.consumedAt ?: OffsetDateTime.now(clock),
            request.meal,
            request.name.trim(),
            DiaryEntryType.QUICK,
            null,
            BigDecimal.ONE,
            "serving",
            null,
            nutrients,
        )
        val saved =
            if (request.saveAsFood) {
                catalog
                    .create(
                        userId,
                        CreateFoodRequest(
                            name = request.name.trim(),
                            basisType = BasisType.PER_SERVING,
                            basisAmount = BigDecimal.ONE,
                            basisUnit = "serving",
                            nutrients = nutrients.values,
                        ),
                    ).id
            } else {
                null
            }
        return QuickTrackResult(
            entry(userId, id),
            calculated,
            request.calories?.subtract(calculated),
            saved,
        )
    }

    @Transactional
    fun addRecipe(
        userId: String,
        request: AddRecipeEntryRequest,
    ): DiaryEntryView {
        val recipe = recipes.getByRevision(userId, request.recipeRevisionId)
        val nutrients = NutrientValues(recipe.nutrientsPerServing).scaled(request.servings)
        val id = UUID.randomUUID()
        insert(
            id,
            userId,
            request.localDate,
            request.consumedAt ?: OffsetDateTime.now(clock),
            request.meal,
            recipe.name,
            DiaryEntryType.RECIPE,
            request.recipeRevisionId,
            request.servings,
            "serving",
            null,
            nutrients,
        )
        return entry(userId, id)
    }

    @Transactional
    fun update(
        userId: String,
        entryId: UUID,
        request: UpdateDiaryEntryRequest,
    ): DiaryEntryView {
        val current = entry(userId, entryId)
        val resolved =
            when (current.entryType) {
                DiaryEntryType.FOOD -> {
                    val quantity = request.quantity ?: throw InvalidOperationException("Food quantity is required")
                    val unit = request.unit?.takeIf { it.isNotBlank() } ?: throw InvalidOperationException("Food unit is required")
                    val revisionId = current.sourceRevisionId ?: throw InvalidOperationException("Food revision is missing")
                    val food = catalog.resolve(userId, revisionId, FoodAmountRequest(quantity, unit, request.portionId))
                    UpdatedEntry(food.displayName, quantity, unit, request.portionId, food.nutrients)
                }

                DiaryEntryType.RECIPE -> {
                    val servings = request.quantity ?: throw InvalidOperationException("Recipe servings are required")
                    val revisionId = current.sourceRevisionId ?: throw InvalidOperationException("Recipe revision is missing")
                    val recipe = recipes.getByRevision(userId, revisionId)
                    UpdatedEntry(recipe.name, servings, "serving", null, NutrientValues(recipe.nutrientsPerServing).scaled(servings))
                }

                DiaryEntryType.QUICK -> {
                    val name =
                        request.name?.trim()?.takeIf { it.isNotBlank() }
                            ?: throw InvalidOperationException("Quick entry name is required")
                    val protein = request.proteinG ?: throw InvalidOperationException("Quick entry protein is required")
                    val carbohydrate = request.carbohydrateG ?: throw InvalidOperationException("Quick entry carbohydrate is required")
                    val fat = request.fatG ?: throw InvalidOperationException("Quick entry fat is required")
                    val macros =
                        linkedMapOf(
                            "protein_g" to protein,
                            "carbohydrate_g" to carbohydrate,
                            "fat_g" to fat,
                        ).apply { request.fiberG?.let { put("fiber_g", it) } }
                    val calories = request.calories ?: NutrientMath.calculatedCalories(macros)
                    UpdatedEntry(name, BigDecimal.ONE, "serving", null, NutrientValues(macros + ("energy_kcal" to calories)))
                }
            }
        db.execute(
            """
            update diary_entries set local_date = ?, consumed_at = cast(? as timestamptz), meal = ?, display_name = ?,
                                     quantity = ?, unit = ?, portion_id = ?, nutrients = cast(? as jsonb)
             where id = ? and user_id = ?
            """.trimIndent(),
            request.localDate,
            request.consumedAt,
            request.meal.name,
            resolved.name,
            resolved.quantity,
            resolved.unit,
            resolved.portionId,
            json.writeNutrients(resolved.nutrients),
            entryId,
            userId,
        )
        return entry(userId, entryId)
    }

    @Transactional
    fun copy(
        userId: String,
        entryId: UUID,
        request: CopyDiaryEntryRequest,
    ): DiaryEntryView {
        val current = entry(userId, entryId)
        val timezone = profiles.get(userId)?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("UTC")
        val originalTime = current.consumedAt.atZoneSameInstant(timezone).toLocalTime()
        val consumedAt =
            request.destinationDate
                .atTime(request.destinationTime ?: originalTime)
                .atZone(timezone)
                .toOffsetDateTime()
        val id = UUID.randomUUID()
        insert(
            id,
            userId,
            request.destinationDate,
            consumedAt,
            current.meal,
            current.displayName,
            current.entryType,
            current.sourceRevisionId,
            current.quantity,
            current.unit,
            current.portionId,
            NutrientValues(current.nutrients),
        )
        return entry(userId, id)
    }

    fun delete(
        userId: String,
        entryId: UUID,
    ) {
        val changed = db.execute("delete from diary_entries where id = ? and user_id = ?", entryId, userId)
        if (changed == 0) throw NotFoundException("Diary entry was not found")
    }

    fun summary(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<DiaryDayView> {
        if (to.isBefore(from) || to.isAfter(from.plusDays(92))) {
            throw InvalidOperationException("Summary range must be between 1 and 93 days")
        }
        val grouped = entries(userId, from, to).groupBy(DiaryEntryView::localDate)
        return generateSequence(from) { current -> current.plusDays(1).takeUnless { it.isAfter(to) } }
            .map { date -> dayView(date, grouped[date].orEmpty()) }
            .toList()
    }

    fun trackables(
        userId: String,
        query: String,
        type: TrackableType,
        limit: Int,
    ): List<TrackableView> {
        val foods =
            if (type in setOf(TrackableType.ALL, TrackableType.FOOD)) {
                catalog.search(userId, query, limit).map { food ->
                    TrackableView(
                        "FOOD",
                        food.id,
                        food.revisionId,
                        food.name,
                        food.brand,
                        food.portions.firstOrNull { it.default }?.name
                            ?: "${food.basisAmount.stripTrailingZeros().toPlainString()} ${food.basisUnit}",
                        food.nutrients,
                    )
                }
            } else {
                emptyList()
            }
        val recipes =
            if (type in setOf(TrackableType.ALL, TrackableType.RECIPE)) {
                recipes
                    .list(userId)
                    .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                    .map { recipe ->
                        TrackableView("RECIPE", recipe.id, recipe.revisionId, recipe.name, null, "1 serving", recipe.nutrientsPerServing)
                    }
            } else {
                emptyList()
            }
        return (foods + recipes).sortedBy { it.name.lowercase() }.take(limit.coerceIn(1, 100))
    }

    private fun insert(
        id: UUID,
        userId: String,
        localDate: LocalDate,
        consumedAt: OffsetDateTime,
        meal: Meal,
        displayName: String,
        type: DiaryEntryType,
        revisionId: UUID?,
        quantity: BigDecimal?,
        unit: String?,
        portionId: UUID?,
        nutrients: NutrientValues,
    ) {
        db.execute(
            """
            insert into diary_entries(id, user_id, local_date, consumed_at, meal, display_name,
                                      entry_type, source_revision_id, quantity, unit, portion_id, nutrients)
            values (?, ?, ?, cast(? as timestamptz), ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
            """.trimIndent(),
            id,
            userId,
            localDate,
            consumedAt,
            meal.name,
            displayName,
            type.name,
            revisionId,
            quantity,
            unit,
            portionId,
            json.writeNutrients(nutrients),
        )
    }

    private fun entry(
        userId: String,
        entryId: UUID,
    ): DiaryEntryView {
        val record =
            db.fetchOne(
                """
                select id, local_date, consumed_at, meal, display_name, entry_type, source_revision_id,
                       quantity, unit, portion_id, nutrients::text as nutrients
                  from diary_entries where id = ? and user_id = ?
                """.trimIndent(),
                entryId,
                userId,
            ) ?: throw NotFoundException("Diary entry was not found")
        return entryFromRecord(record)
    }

    private fun entries(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<DiaryEntryView> =
        db
            .fetch(
                """
                select id, local_date, consumed_at, meal, display_name, entry_type, source_revision_id,
                       quantity, unit, portion_id, nutrients::text as nutrients
                  from diary_entries
                 where user_id = ? and local_date between ? and ?
                 order by local_date, consumed_at, created_at
                """.trimIndent(),
                userId,
                from,
                to,
            ).map(::entryFromRecord)

    private fun entryFromRecord(record: org.jooq.Record): DiaryEntryView =
        DiaryEntryView(
            record.get("id", UUID::class.java)!!,
            record.get("local_date", LocalDate::class.java)!!,
            record.get("consumed_at", OffsetDateTime::class.java)!!,
            Meal.valueOf(record.get("meal", String::class.java)!!),
            record.get("display_name", String::class.java)!!,
            DiaryEntryType.valueOf(record.get("entry_type", String::class.java)!!),
            record.get("source_revision_id", UUID::class.java),
            record.get("quantity", BigDecimal::class.java),
            record.get("unit", String::class.java),
            record.get("portion_id", UUID::class.java),
            json.nutrients(record.get("nutrients", String::class.java)!!).values,
        )

    private fun dayView(
        date: LocalDate,
        entries: List<DiaryEntryView>,
    ): DiaryDayView {
        val totals = entries.fold(NutrientValues.EMPTY) { total, entry -> total.plus(NutrientValues(entry.nutrients)) }
        return DiaryDayView(date, entries, totals.values)
    }

    private data class UpdatedEntry(
        val name: String,
        val quantity: BigDecimal,
        val unit: String,
        val portionId: UUID?,
        val nutrients: NutrientValues,
    )
}

@RestController
@RequestMapping("/api/v1")
class TrackingController(
    private val users: UserContext,
    private val tracking: TrackingService,
) {
    @GetMapping("/diary-days/{date}")
    fun day(
        @PathVariable date: LocalDate,
    ) = tracking.day(users.userId(), date)

    @GetMapping("/diary-days")
    fun summary(
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
    ) = tracking.summary(users.userId(), from, to)

    @GetMapping("/trackables")
    fun trackables(
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(defaultValue = "ALL") type: TrackableType,
        @RequestParam(defaultValue = "30") limit: Int,
    ) = tracking.trackables(users.userId(), query, type, limit)

    @PostMapping("/diary-entries/food")
    fun addFood(
        @Valid @RequestBody request: AddFoodEntryRequest,
    ) = tracking.addFood(users.userId(), request)

    @PostMapping("/quick-entries")
    fun quick(
        @Valid @RequestBody request: QuickTrackRequest,
    ) = tracking.quickTrack(users.userId(), request)

    @PostMapping("/diary-entries/recipe")
    fun addRecipe(
        @Valid @RequestBody request: AddRecipeEntryRequest,
    ) = tracking.addRecipe(users.userId(), request)

    @PutMapping("/diary-entries/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateDiaryEntryRequest,
    ) = tracking.update(users.userId(), id, request)

    @PostMapping("/diary-entries/{id}/copies")
    fun copy(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CopyDiaryEntryRequest,
    ) = tracking.copy(users.userId(), id, request)

    @DeleteMapping("/diary-entries/{id}")
    fun delete(
        @PathVariable id: UUID,
    ) = tracking.delete(users.userId(), id)
}
