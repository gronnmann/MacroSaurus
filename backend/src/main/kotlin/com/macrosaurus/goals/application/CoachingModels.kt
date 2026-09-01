package com.macrosaurus.goals.application

import com.macrosaurus.expenditure.EnergyEstimate
import com.macrosaurus.goals.NutritionProgramSnapshot
import com.macrosaurus.goals.ProgramStyle
import com.macrosaurus.goals.WeightGoalSnapshot
import com.macrosaurus.goals.WeightGoalType
import com.macrosaurus.identity.FormulaSex
import com.macrosaurus.tracking.NutritionReviewCandidate
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

internal data class CoachingSetupDraft(
    val currentStep: Int = 1,
    val displayName: String? = null,
    val locale: String = "en",
    val timezone: String = "UTC",
    val birthDate: LocalDate? = null,
    val heightCm: BigDecimal? = null,
    val formulaSex: FormulaSex? = null,
    val activityMultiplier: BigDecimal = BigDecimal("1.2"),
    val weightKg: BigDecimal? = null,
    val goalType: WeightGoalType? = null,
    val targetWeightKg: BigDecimal? = null,
    val weeklyRatePercent: BigDecimal? = null,
    val programStyle: ProgramStyle? = null,
    val proteinGPerKg: BigDecimal = BigDecimal("1.6"),
    val fatEnergyPercent: BigDecimal = BigDecimal("25"),
    val manualEnergyKcal: BigDecimal? = null,
    val manualProteinG: BigDecimal? = null,
    val manualCarbohydrateG: BigDecimal? = null,
    val manualFatG: BigDecimal? = null,
)

internal data class SetupPreview(
    val expenditure: EnergyEstimate,
    val energyKcal: BigDecimal,
    val proteinG: BigDecimal,
    val carbohydrateG: BigDecimal,
    val fatG: BigDecimal,
    val estimatedCompletionDate: LocalDate?,
    val warnings: List<String>,
)

internal data class CoachingStatus(
    val setupComplete: Boolean,
    val goal: WeightGoalSnapshot?,
    val program: NutritionProgramSnapshot?,
    val nextCheckInDate: LocalDate?,
    val checkInDue: Boolean,
)

internal enum class CheckInStatus { DRAFT, ACCEPTED, SKIPPED }

internal data class CheckInProposal(
    val estimate: EnergyEstimate,
    val previousEnergyKcal: BigDecimal?,
    val proposedEnergyKcal: BigDecimal?,
    val proposedProteinG: BigDecimal?,
    val proposedCarbohydrateG: BigDecimal?,
    val proposedFatG: BigDecimal?,
    val targetUpdateAvailable: Boolean,
    val warnings: List<String>,
)

internal data class StoredCheckIn(
    val id: UUID,
    val weekStart: LocalDate,
    val status: CheckInStatus,
    val proposal: CheckInProposal?,
)

internal data class CheckInView(
    val due: Boolean,
    val id: UUID?,
    val weekStart: LocalDate?,
    val periodFrom: LocalDate?,
    val periodTo: LocalDate?,
    val status: CheckInStatus?,
    val candidates: List<NutritionReviewCandidate>,
    val needsWeight: Boolean,
    val proposal: CheckInProposal?,
)
