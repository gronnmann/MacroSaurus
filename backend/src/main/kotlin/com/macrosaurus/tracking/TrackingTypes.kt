package com.macrosaurus.tracking

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

enum class Meal { BREAKFAST, LUNCH, DINNER, SNACK, OTHER }

enum class DiaryEntryType { FOOD, RECIPE, QUICK }

data class DiaryEntrySnapshot(
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

data class DailyNutrition(
    val date: LocalDate,
    val entryCount: Int,
    val totals: Map<String, BigDecimal>,
)

fun interface NutritionHistory {
    fun dailyNutrition(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<DailyNutrition>
}
