package com.macrosaurus.tracking.application

import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.FoodAmount
import com.macrosaurus.catalog.FoodCatalog
import com.macrosaurus.catalog.FoodCreator
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodResolver
import com.macrosaurus.identity.ProfileReader
import com.macrosaurus.recipes.RecipeReader
import com.macrosaurus.shared.InvalidOperationException
import com.macrosaurus.shared.NotFoundException
import com.macrosaurus.shared.NutrientMath
import com.macrosaurus.shared.NutrientValues
import com.macrosaurus.tracking.DailyNutrition
import com.macrosaurus.tracking.DiaryEntrySnapshot
import com.macrosaurus.tracking.DiaryEntryType
import com.macrosaurus.tracking.Meal
import com.macrosaurus.tracking.NutritionHistory
import com.macrosaurus.tracking.persistence.JooqDiaryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

internal enum class TrackableType { ALL, FOOD, RECIPE }

internal data class DiaryDay(
    val date: LocalDate,
    val entries: List<DiaryEntrySnapshot>,
    val totals: Map<String, BigDecimal>,
)

internal data class Trackable(
    val type: String,
    val id: UUID,
    val revisionId: UUID,
    val name: String,
    val brand: String?,
    val servingLabel: String,
    val nutrients: Map<String, BigDecimal>,
)

internal data class AddFoodEntryCommand(
    val foodRevisionId: UUID,
    val quantity: BigDecimal,
    val unit: String,
    val portionId: UUID? = null,
    val localDate: LocalDate,
    val consumedAt: OffsetDateTime? = null,
    val meal: Meal = Meal.OTHER,
)

internal data class QuickTrackCommand(
    val name: String,
    val localDate: LocalDate,
    val consumedAt: OffsetDateTime? = null,
    val meal: Meal = Meal.OTHER,
    val calories: BigDecimal? = null,
    val proteinG: BigDecimal = BigDecimal.ZERO,
    val carbohydrateG: BigDecimal = BigDecimal.ZERO,
    val fatG: BigDecimal = BigDecimal.ZERO,
    val fiberG: BigDecimal? = null,
    val saveAsFood: Boolean = false,
)

internal data class AddRecipeEntryCommand(
    val recipeRevisionId: UUID,
    val servings: BigDecimal,
    val localDate: LocalDate,
    val consumedAt: OffsetDateTime? = null,
    val meal: Meal = Meal.OTHER,
)

internal data class UpdateDiaryEntryCommand(
    val localDate: LocalDate,
    val consumedAt: OffsetDateTime,
    val meal: Meal,
    val quantity: BigDecimal? = null,
    val unit: String? = null,
    val portionId: UUID? = null,
    val name: String? = null,
    val calories: BigDecimal? = null,
    val proteinG: BigDecimal? = null,
    val carbohydrateG: BigDecimal? = null,
    val fatG: BigDecimal? = null,
    val fiberG: BigDecimal? = null,
)

internal data class CopyDiaryEntryCommand(
    val destinationDate: LocalDate,
    val destinationTime: LocalTime? = null,
)

internal data class QuickTrackResult(
    val entry: DiaryEntrySnapshot,
    val calculatedCalories: BigDecimal,
    val calorieDiscrepancy: BigDecimal?,
    val savedFoodId: UUID?,
)

@Service
internal class TrackingService(
    private val repository: JooqDiaryRepository,
    private val catalog: FoodCatalog,
    private val foodResolver: FoodResolver,
    private val foodCreator: FoodCreator,
    private val recipes: RecipeReader,
    private val profiles: ProfileReader,
    private val clock: Clock,
) : NutritionHistory {
    fun day(
        userId: String,
        date: LocalDate,
    ): DiaryDay = dayView(date, entries(userId, date, date))

    @Transactional
    fun addFood(
        userId: String,
        request: AddFoodEntryCommand,
    ): DiaryEntrySnapshot {
        val resolved =
            foodResolver.resolve(
                userId,
                request.foodRevisionId,
                FoodAmount(request.quantity, request.unit, request.portionId),
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
        request: QuickTrackCommand,
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
                foodCreator
                    .create(
                        userId,
                        FoodDraft(
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
        request: AddRecipeEntryCommand,
    ): DiaryEntrySnapshot {
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
        request: UpdateDiaryEntryCommand,
    ): DiaryEntrySnapshot {
        val current = entry(userId, entryId)
        val resolved =
            when (current.entryType) {
                DiaryEntryType.FOOD -> {
                    val quantity = request.quantity ?: throw InvalidOperationException("Food quantity is required")
                    val unit = request.unit?.takeIf { it.isNotBlank() } ?: throw InvalidOperationException("Food unit is required")
                    val revisionId = current.sourceRevisionId ?: throw InvalidOperationException("Food revision is missing")
                    val food = foodResolver.resolve(userId, revisionId, FoodAmount(quantity, unit, request.portionId))
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
        repository.update(
            userId,
            entryId,
            request.localDate,
            request.consumedAt,
            request.meal,
            resolved.name,
            resolved.quantity,
            resolved.unit,
            resolved.portionId,
            resolved.nutrients,
        )
        return entry(userId, entryId)
    }

    @Transactional
    fun copy(
        userId: String,
        entryId: UUID,
        request: CopyDiaryEntryCommand,
    ): DiaryEntrySnapshot {
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
        val changed = repository.delete(userId, entryId)
        if (changed == 0) throw NotFoundException("Diary entry was not found")
    }

    fun summary(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<DiaryDay> {
        if (to.isBefore(from) || to.isAfter(from.plusDays(92))) {
            throw InvalidOperationException("Summary range must be between 1 and 93 days")
        }
        val grouped = entries(userId, from, to).groupBy(DiaryEntrySnapshot::localDate)
        return generateSequence(from) { current -> current.plusDays(1).takeUnless { it.isAfter(to) } }
            .map { date -> dayView(date, grouped[date].orEmpty()) }
            .toList()
    }

    override fun dailyNutrition(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<DailyNutrition> = summary(userId, from, to).map { DailyNutrition(it.date, it.entries.size, it.totals) }

    fun trackables(
        userId: String,
        query: String,
        type: TrackableType,
        limit: Int,
    ): List<Trackable> {
        val foods =
            if (type in setOf(TrackableType.ALL, TrackableType.FOOD)) {
                catalog.search(userId, query, limit).map { food ->
                    Trackable(
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
                        Trackable("RECIPE", recipe.id, recipe.revisionId, recipe.name, null, "1 serving", recipe.nutrientsPerServing)
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
        repository.insert(
            id,
            userId,
            localDate,
            consumedAt,
            meal,
            displayName,
            type,
            revisionId,
            quantity,
            unit,
            portionId,
            nutrients,
        )
    }

    private fun entry(
        userId: String,
        entryId: UUID,
    ): DiaryEntrySnapshot = repository.find(userId, entryId) ?: throw NotFoundException("Diary entry was not found")

    private fun entries(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<DiaryEntrySnapshot> = repository.findBetween(userId, from, to)

    private fun dayView(
        date: LocalDate,
        entries: List<DiaryEntrySnapshot>,
    ): DiaryDay {
        val totals = entries.fold(NutrientValues.EMPTY) { total, entry -> total.plus(NutrientValues(entry.nutrients)) }
        return DiaryDay(date, entries, totals.values)
    }

    private data class UpdatedEntry(
        val name: String,
        val quantity: BigDecimal,
        val unit: String,
        val portionId: UUID?,
        val nutrients: NutrientValues,
    )
}
