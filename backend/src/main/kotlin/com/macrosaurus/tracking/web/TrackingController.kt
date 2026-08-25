package com.macrosaurus.tracking.web

import com.macrosaurus.shared.CurrentUser
import com.macrosaurus.tracking.DiaryEntrySnapshot
import com.macrosaurus.tracking.DiaryEntryType
import com.macrosaurus.tracking.Meal
import com.macrosaurus.tracking.application.AddFoodEntryCommand
import com.macrosaurus.tracking.application.AddRecipeEntryCommand
import com.macrosaurus.tracking.application.CopyDiaryEntryCommand
import com.macrosaurus.tracking.application.DiaryDay
import com.macrosaurus.tracking.application.QuickTrackCommand
import com.macrosaurus.tracking.application.Trackable
import com.macrosaurus.tracking.application.TrackableType
import com.macrosaurus.tracking.application.TrackingService
import com.macrosaurus.tracking.application.UpdateDiaryEntryCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.UUID
import com.macrosaurus.tracking.application.QuickTrackResult as QuickTrackResultContract

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

private fun DiaryEntrySnapshot.toView() = DiaryEntryView(id, localDate, consumedAt, meal, displayName, entryType, sourceRevisionId, quantity, unit, portionId, nutrients)

private fun DiaryDay.toView() = DiaryDayView(date, entries.map { it.toView() }, totals)

private fun Trackable.toView() = TrackableView(type, id, revisionId, name, brand, servingLabel, nutrients)

private fun QuickTrackResultContract.toView() = QuickTrackResult(entry.toView(), calculatedCalories, calorieDiscrepancy, savedFoodId)

@RestController
@RequestMapping("/api/v1")
internal class TrackingController(
    private val users: CurrentUser,
    private val tracking: TrackingService,
) {
    @GetMapping("/diary-days/{date}")
    fun day(
        @PathVariable date: LocalDate,
    ) = tracking.day(users.userId(), date).toView()

    @GetMapping("/diary-days")
    fun summary(
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
    ) = tracking.summary(users.userId(), from, to).map { it.toView() }

    @GetMapping("/trackables")
    fun trackables(
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(defaultValue = "ALL") type: TrackableType,
        @RequestParam(defaultValue = "30") limit: Int,
    ) = tracking.trackables(users.userId(), query, type, limit).map { it.toView() }

    @PostMapping("/diary-entries/food")
    fun addFood(
        @Valid @RequestBody request: AddFoodEntryRequest,
    ) = tracking
        .addFood(
            users.userId(),
            AddFoodEntryCommand(request.foodRevisionId, request.quantity, request.unit, request.portionId, request.localDate, request.consumedAt, request.meal),
        ).toView()

    @PostMapping("/quick-entries")
    fun quick(
        @Valid @RequestBody request: QuickTrackRequest,
    ) = tracking
        .quickTrack(
            users.userId(),
            QuickTrackCommand(
                request.name,
                request.localDate,
                request.consumedAt,
                request.meal,
                request.calories,
                request.proteinG,
                request.carbohydrateG,
                request.fatG,
                request.fiberG,
                request.saveAsFood,
            ),
        ).toView()

    @PostMapping("/diary-entries/recipe")
    fun addRecipe(
        @Valid @RequestBody request: AddRecipeEntryRequest,
    ) = tracking
        .addRecipe(
            users.userId(),
            AddRecipeEntryCommand(request.recipeRevisionId, request.servings, request.localDate, request.consumedAt, request.meal),
        ).toView()

    @PutMapping("/diary-entries/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateDiaryEntryRequest,
    ) = tracking
        .update(
            users.userId(),
            id,
            UpdateDiaryEntryCommand(
                request.localDate,
                request.consumedAt,
                request.meal,
                request.quantity,
                request.unit,
                request.portionId,
                request.name,
                request.calories,
                request.proteinG,
                request.carbohydrateG,
                request.fatG,
                request.fiberG,
            ),
        ).toView()

    @PostMapping("/diary-entries/{id}/copies")
    fun copy(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CopyDiaryEntryRequest,
    ) = tracking.copy(users.userId(), id, CopyDiaryEntryCommand(request.destinationDate, request.destinationTime)).toView()

    @DeleteMapping("/diary-entries/{id}")
    fun delete(
        @PathVariable id: UUID,
    ) = tracking.delete(users.userId(), id)
}
