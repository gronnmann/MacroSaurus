package com.macrosaurus.goals

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

enum class WeightGoalType { LOSS, MAINTAIN, GAIN }

enum class GoalStatus { ACTIVE, COMPLETED, ARCHIVED }

enum class ProgramStyle { COACHED, MANUAL }

enum class ProgramSource { ONBOARDING, CHECK_IN, PROFILE_RERUN, LEGACY, MANUAL_API }

data class WeightGoalSnapshot(
    val id: UUID,
    val type: WeightGoalType,
    val startingWeightKg: BigDecimal,
    val targetWeightKg: BigDecimal?,
    val weeklyRatePercent: BigDecimal,
    val status: GoalStatus,
    val startedOn: LocalDate,
    val endedOn: LocalDate?,
)

data class NutritionProgramSnapshot(
    val id: UUID,
    val goalId: UUID?,
    val style: ProgramStyle,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate?,
    val energyKcal: BigDecimal?,
    val proteinG: BigDecimal?,
    val carbohydrateG: BigDecimal?,
    val fatG: BigDecimal?,
    val proteinGPerKg: BigDecimal?,
    val fatEnergyPercent: BigDecimal?,
    val expenditureKcal: BigDecimal?,
    val expenditureLowerKcal: BigDecimal?,
    val expenditureUpperKcal: BigDecimal?,
    val algorithmVersion: String?,
    val source: ProgramSource,
)
