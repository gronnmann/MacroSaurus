package com.macrosaurus.tracking.application

import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.FoodAmount
import com.macrosaurus.catalog.FoodCatalog
import com.macrosaurus.catalog.FoodCreator
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodResolver
import com.macrosaurus.catalog.FoodSnapshot
import com.macrosaurus.identity.ProfileReader
import com.macrosaurus.recipes.RecipeReader
import com.macrosaurus.recipes.RecipeSnapshot
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
import com.macrosaurus.tracking.persistence.TrackableUse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

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

internal data class LastTrackedAmount(
    val quantity: BigDecimal,
    val unit: String,
    val portionId: UUID?,
)

internal data class TimeOfDaySuggestions(
    val anchorHour: Int,
    val items: List<Trackable>,
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
        val safeLimit = limit.coerceIn(1, 100)
        val foods =
            if (type in setOf(TrackableType.ALL, TrackableType.FOOD)) {
                catalog.search(userId, query, safeLimit).map(::foodTrackable)
            } else {
                emptyList()
            }
        val recipeSnapshots =
            if (type in setOf(TrackableType.ALL, TrackableType.RECIPE)) recipes.list(userId) else emptyList()
        val recipes =
            recipeSnapshots
                .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                .map(::recipeTrackable)

        val recentUses =
            repository
                .findRecentTrackableUses(userId, 100)
                .filter { type == TrackableType.ALL || it.entryType.name == type.name }
        val recentTrackables = materializeTrackables(userId, recentUses, recipeSnapshots)
        val matchingRecent =
            recentTrackables.values.filter { item ->
                query.isBlank() ||
                    item.name.contains(query.trim(), ignoreCase = true) ||
                    item.brand?.contains(query.trim(), ignoreCase = true) == true
            }
        val recency = recentUses.associate { TrackableKey(it.entryType.name, it.entityId) to it.createdAt }
        val merged = linkedMapOf<TrackableKey, Trackable>()
        (matchingRecent + foods + recipes).forEach { item -> merged.putIfAbsent(TrackableKey(item.type, item.id), item) }
        return merged.values
            .sortedWith { left, right ->
                val leftRecent = recency[TrackableKey(left.type, left.id)]
                val rightRecent = recency[TrackableKey(right.type, right.id)]
                when {
                    leftRecent != null && rightRecent == null -> -1
                    leftRecent == null && rightRecent != null -> 1
                    leftRecent != null && rightRecent != null -> rightRecent.compareTo(leftRecent)
                    else -> left.name.compareTo(right.name, ignoreCase = true)
                }
            }.take(safeLimit)
    }

    fun lastTrackedAmount(
        userId: String,
        type: TrackableType,
        revisionId: UUID,
    ): LastTrackedAmount? =
        when (type) {
            TrackableType.FOOD -> {
                lastFoodAmount(userId, revisionId)
            }

            TrackableType.RECIPE -> {
                recipes.getByRevision(userId, revisionId)
                repository
                    .findLatestTrackedAmount(userId, DiaryEntryType.RECIPE, revisionId)
                    ?.let { LastTrackedAmount(it.quantity, "serving", null) }
            }

            TrackableType.ALL -> {
                throw InvalidOperationException("A concrete trackable type is required")
            }
        }

    fun timeOfDaySuggestions(
        userId: String,
        type: TrackableType,
        limit: Int,
    ): TimeOfDaySuggestions {
        val zone = userZone(userId)
        val now = OffsetDateTime.now(clock)
        val localNow = now.atZoneSameInstant(zone)
        val anchorHour = (localNow.hour + if (localNow.minute >= 30) 1 else 0) % 24
        val uses =
            repository
                .findTrackableUsesSince(userId, now.minusDays(28))
                .filter { type == TrackableType.ALL || it.entryType.name == type.name }
                .filter { use ->
                    val localUse = use.consumedAt.atZoneSameInstant(zone)
                    circularMinuteDistance(localNow.hour * 60 + localNow.minute, localUse.hour * 60 + localUse.minute) <= 120
                }
        val habits =
            uses
                .groupBy { TrackableKey(it.entryType.name, it.entityId) }
                .mapValues { (_, matches) ->
                    val matchingDays = matches.map { it.consumedAt.atZoneSameInstant(zone).toLocalDate() }.toSet().size
                    HabitScore(matchingDays, matches.maxOf { it.consumedAt })
                }.filterValues { it.matchingDays >= 2 }
        if (habits.isEmpty()) return TimeOfDaySuggestions(anchorHour, emptyList())

        val habitualUses = uses.filter { TrackableKey(it.entryType.name, it.entityId) in habits }
        val recipeSnapshots =
            if (type in setOf(TrackableType.ALL, TrackableType.RECIPE)) recipes.list(userId) else emptyList()
        val items = materializeTrackables(userId, habitualUses, recipeSnapshots)
        val ranked =
            items
                .filterKeys { it in habits }
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<TrackableKey, Trackable>> { habits.getValue(it.key).matchingDays }
                        .thenByDescending { habits.getValue(it.key).latestUse }
                        .thenBy { it.value.name.lowercase() },
                ).map { it.value }
                .take(limit.coerceIn(1, 10))
        return TimeOfDaySuggestions(anchorHour, ranked)
    }

    private fun lastFoodAmount(
        userId: String,
        revisionId: UUID,
    ): LastTrackedAmount? {
        val target = catalog.byRevision(userId, revisionId)
        val latest = repository.findLatestTrackedAmount(userId, DiaryEntryType.FOOD, revisionId) ?: return null
        val unit = latest.unit.lowercase()
        val portionId =
            if (unit == "portion") {
                target.portions.firstOrNull { it.id == latest.portionId }?.id
                    ?: latest.portionName?.let { previousName ->
                        target.portions.firstOrNull { it.name.trim().equals(previousName.trim(), ignoreCase = true) }?.id
                    }
                    ?: return null
            } else {
                null
            }
        return try {
            foodResolver.resolve(userId, target.revisionId, FoodAmount(latest.quantity, unit, portionId))
            LastTrackedAmount(latest.quantity, unit, portionId)
        } catch (_: InvalidOperationException) {
            null
        }
    }

    private fun materializeTrackables(
        userId: String,
        uses: List<TrackableUse>,
        loadedRecipes: List<RecipeSnapshot>,
    ): Map<TrackableKey, Trackable> {
        val foodRevisionIds = uses.filter { it.entryType == DiaryEntryType.FOOD }.map { it.currentRevisionId }.distinct()
        val foods = catalog.byRevisions(userId, foodRevisionIds)
        val recipesByRevision =
            (
                loadedRecipes.ifEmpty {
                    if (uses.any { it.entryType == DiaryEntryType.RECIPE }) recipes.list(userId) else emptyList()
                }
            ).associateBy(RecipeSnapshot::revisionId)
        return uses
            .distinctBy { TrackableKey(it.entryType.name, it.entityId) }
            .mapNotNull { use ->
                val item =
                    when (use.entryType) {
                        DiaryEntryType.FOOD -> foods[use.currentRevisionId]?.let(::foodTrackable)
                        DiaryEntryType.RECIPE -> recipesByRevision[use.currentRevisionId]?.let(::recipeTrackable)
                        DiaryEntryType.QUICK -> null
                    }
                item?.let { TrackableKey(it.type, it.id) to it }
            }.toMap()
    }

    private fun foodTrackable(food: FoodSnapshot) =
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

    private fun recipeTrackable(recipe: RecipeSnapshot) = Trackable("RECIPE", recipe.id, recipe.revisionId, recipe.name, null, "1 serving", recipe.nutrientsPerServing)

    private fun userZone(userId: String): ZoneId = profiles.get(userId)?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("UTC")

    private fun circularMinuteDistance(
        left: Int,
        right: Int,
    ): Int {
        val direct = abs(left - right)
        return minOf(direct, 24 * 60 - direct)
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

    private data class TrackableKey(
        val type: String,
        val id: UUID,
    )

    private data class HabitScore(
        val matchingDays: Int,
        val latestUse: OffsetDateTime,
    )
}
