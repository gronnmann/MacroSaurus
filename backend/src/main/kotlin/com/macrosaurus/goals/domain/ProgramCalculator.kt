package com.macrosaurus.goals.domain

import com.macrosaurus.expenditure.EnergyEstimate
import com.macrosaurus.goals.ProgramStyle
import com.macrosaurus.goals.WeightGoalType
import com.macrosaurus.shared.InvalidOperationException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.ceil

internal data class ProgramCalculation(
    val energyKcal: BigDecimal,
    val proteinG: BigDecimal,
    val carbohydrateG: BigDecimal,
    val fatG: BigDecimal,
    val estimatedCompletionDate: LocalDate?,
    val warnings: List<String>,
)

internal data class ProgramInputs(
    val style: ProgramStyle,
    val goalType: WeightGoalType,
    val weightKg: BigDecimal,
    val targetWeightKg: BigDecimal?,
    val weeklyRatePercent: BigDecimal,
    val proteinGPerKg: BigDecimal,
    val fatEnergyPercent: BigDecimal,
    val manualEnergyKcal: BigDecimal?,
    val manualProteinG: BigDecimal?,
    val manualCarbohydrateG: BigDecimal?,
    val manualFatG: BigDecimal?,
)

internal object ProgramCalculator {
    private val energyDensity = BigDecimal("7700")

    fun calculate(
        date: LocalDate,
        inputs: ProgramInputs,
        estimate: EnergyEstimate,
        activityMultiplier: BigDecimal,
        previousEnergyKcal: BigDecimal? = null,
        recurringCheckIn: Boolean = false,
    ): ProgramCalculation {
        if (inputs.style == ProgramStyle.MANUAL) return manual(inputs)
        validateGoal(inputs)
        val expenditure = estimate.suggestedKcal ?: throw InvalidOperationException("A starting expenditure estimate is required")
        val direction =
            when (inputs.goalType) {
                WeightGoalType.LOSS -> BigDecimal("-1")
                WeightGoalType.MAINTAIN -> BigDecimal.ZERO
                WeightGoalType.GAIN -> BigDecimal.ONE
            }
        val dailyOffset =
            inputs.weightKg
                .multiply(inputs.weeklyRatePercent)
                .divide(BigDecimal("100"), 8, RoundingMode.HALF_UP)
                .multiply(energyDensity)
                .divide(BigDecimal("7"), 8, RoundingMode.HALF_UP)
                .multiply(direction)
        val raw = expenditure.add(dailyOffset)
        val resting = estimate.baselineKcal?.divide(activityMultiplier, 8, RoundingMode.HALF_UP) ?: BigDecimal.ZERO
        val lower = expenditure.multiply(BigDecimal("0.70")).max(resting)
        val upper = expenditure.multiply(BigDecimal("1.20"))
        val warnings = mutableListOf<String>()
        var energy = raw.max(lower).min(upper)
        if (energy.compareTo(raw) != 0) warnings += "The requested pace was reduced to stay within the coached energy guardrails."
        if (recurringCheckIn && previousEnergyKcal != null) {
            val change = energy.subtract(previousEnergyKcal)
            val capped = change.max(BigDecimal("-100")).min(BigDecimal("100"))
            if (capped.compareTo(change) != 0) warnings += "The weekly adjustment was limited to 100 kcal per day to avoid over-correcting."
            energy = previousEnergyKcal.add(capped)
        }
        val protein = inputs.weightKg.multiply(inputs.proteinGPerKg)
        val preferredFat = energy.multiply(inputs.fatEnergyPercent).divide(BigDecimal("900"), 8, RoundingMode.HALF_UP)
        val fat = preferredFat.max(inputs.weightKg.multiply(BigDecimal("0.6")))
        val remaining = energy.subtract(protein.multiply(BigDecimal("4"))).subtract(fat.multiply(BigDecimal("9")))
        if (remaining < BigDecimal.ZERO) throw InvalidOperationException("The calorie target cannot support the selected protein and fat preferences")
        val completion =
            if (inputs.goalType == WeightGoalType.MAINTAIN || inputs.targetWeightKg == null) {
                null
            } else {
                val weeklyKg = inputs.weightKg.multiply(inputs.weeklyRatePercent).divide(BigDecimal("100"), 8, RoundingMode.HALF_UP)
                val weeks =
                    inputs.targetWeightKg
                        .subtract(inputs.weightKg)
                        .abs()
                        .divide(weeklyKg, 8, RoundingMode.HALF_UP)
                date.plusDays(ceil(weeks.toDouble() * 7.0).toLong())
            }
        return ProgramCalculation(
            energy.setScale(0, RoundingMode.HALF_UP),
            protein.setScale(1, RoundingMode.HALF_UP),
            remaining.divide(BigDecimal("4"), 1, RoundingMode.HALF_UP),
            fat.setScale(1, RoundingMode.HALF_UP),
            completion,
            warnings,
        )
    }

    private fun manual(inputs: ProgramInputs): ProgramCalculation {
        val values = listOf(inputs.manualEnergyKcal, inputs.manualProteinG, inputs.manualCarbohydrateG, inputs.manualFatG)
        if (values.any { it == null || it < BigDecimal.ZERO } || inputs.manualEnergyKcal == BigDecimal.ZERO) {
            throw InvalidOperationException("Manual programs need positive calories and non-negative macro targets")
        }
        return ProgramCalculation(
            inputs.manualEnergyKcal!!.setScale(0, RoundingMode.HALF_UP),
            inputs.manualProteinG!!.setScale(1, RoundingMode.HALF_UP),
            inputs.manualCarbohydrateG!!.setScale(1, RoundingMode.HALF_UP),
            inputs.manualFatG!!.setScale(1, RoundingMode.HALF_UP),
            null,
            emptyList(),
        )
    }

    private fun validateGoal(inputs: ProgramInputs) {
        val range =
            when (inputs.goalType) {
                WeightGoalType.LOSS -> BigDecimal("0.25")..BigDecimal("1.0")
                WeightGoalType.MAINTAIN -> BigDecimal.ZERO..BigDecimal.ZERO
                WeightGoalType.GAIN -> BigDecimal("0.10")..BigDecimal("0.50")
            }
        if (inputs.weeklyRatePercent !in range) throw InvalidOperationException("Choose a weekly goal rate within the coached range")
        if (inputs.goalType == WeightGoalType.LOSS && (inputs.targetWeightKg == null || inputs.targetWeightKg >= inputs.weightKg)) {
            throw InvalidOperationException("A loss target must be below the current weight")
        }
        if (inputs.goalType == WeightGoalType.GAIN && (inputs.targetWeightKg == null || inputs.targetWeightKg <= inputs.weightKg)) {
            throw InvalidOperationException("A gain target must be above the current weight")
        }
        if (inputs.proteinGPerKg !in BigDecimal("1.2")..BigDecimal("2.2")) throw InvalidOperationException("Protein must be between 1.2 and 2.2 g/kg")
        if (inputs.fatEnergyPercent !in BigDecimal("20")..BigDecimal("40")) throw InvalidOperationException("Fat must be between 20% and 40%")
    }
}
