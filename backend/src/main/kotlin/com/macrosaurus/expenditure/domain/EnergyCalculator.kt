package com.macrosaurus.expenditure.domain

import com.macrosaurus.expenditure.EnergyEstimate
import com.macrosaurus.identity.FormulaSex
import com.macrosaurus.identity.ProfileSnapshot
import com.macrosaurus.measurements.WeightMeasurement
import com.macrosaurus.tracking.DailyNutrition
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

internal object EnergyCalculator {
    fun calculate(
        date: LocalDate,
        profile: ProfileSnapshot?,
        weights: List<WeightMeasurement>,
        diary: List<DailyNutrition>,
    ): EnergyEstimate {
        val latest = weights.firstOrNull()
        val explanation = mutableListOf<String>()
        val age = profile?.birthDate?.let { Period.between(it, date).years }
        val baseline =
            if (profile?.heightCm != null && profile.formulaSex != null && age != null && age >= 18 && latest != null) {
                val sexAdjustment =
                    when (profile.formulaSex) {
                        FormulaSex.MALE -> BigDecimal("5")
                        FormulaSex.FEMALE -> BigDecimal("-161")
                    }
                BigDecimal("10")
                    .multiply(latest.weightKg)
                    .add(BigDecimal("6.25").multiply(profile.heightCm))
                    .subtract(BigDecimal("5").multiply(BigDecimal(age)))
                    .add(sexAdjustment)
                    .multiply(profile.activityMultiplier)
                    .setScale(2, RoundingMode.HALF_UP)
            } else {
                null
            }
        explanation +=
            if (baseline == null) {
                "Add adult birth date, height, formula sex, and a weigh-in to calculate the baseline."
            } else {
                "Baseline uses Mifflin-St Jeor and your selected activity multiplier."
            }

        val start = date.minusDays(20)
        val loggedDays = diary.filter { it.entryCount > 0 }
        val relevantWeights = weights.filter { !it.measuredAt.toLocalDate().isBefore(start) }.sortedBy { it.measuredAt }
        val spanDays =
            if (relevantWeights.size >= 2) {
                ChronoUnit.DAYS
                    .between(relevantWeights.first().measuredAt.toLocalDate(), relevantWeights.last().measuredAt.toLocalDate())
                    .toInt()
            } else {
                0
            }
        val eligible = baseline != null && loggedDays.size >= 14 && relevantWeights.size >= 4 && spanDays >= 14
        val adaptive =
            if (eligible) {
                val averageIntake =
                    loggedDays
                        .mapNotNull { it.totals["energy_kcal"] }
                        .fold(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal(loggedDays.size), 8, RoundingMode.HALF_UP)
                val weightDelta = relevantWeights.last().weightKg.subtract(relevantWeights.first().weightKg)
                val dailyStoredEnergy =
                    weightDelta.multiply(BigDecimal("7700")).divide(BigDecimal(spanDays), 8, RoundingMode.HALF_UP)
                averageIntake.subtract(dailyStoredEnergy).setScale(2, RoundingMode.HALF_UP)
            } else {
                null
            }
        val suggested = adaptive?.let { clamp(it, baseline!! * BigDecimal("0.90"), baseline * BigDecimal("1.10")) } ?: baseline
        explanation +=
            if (eligible) {
                "Adaptive estimate uses average intake on logged days and the measured weight trend; it is clamped to 10% around baseline."
            } else {
                "Adaptive estimates require 14 logged days and 4 weigh-ins spanning at least 14 days."
            }
        return EnergyEstimate(
            date,
            baseline,
            adaptive,
            suggested,
            when {
                eligible && loggedDays.size >= 18 -> "MEDIUM"
                baseline != null -> "LOW"
                else -> "INSUFFICIENT"
            },
            eligible,
            "energy-v1",
            explanation,
            mapOf("loggedDays" to loggedDays.size, "weighIns" to relevantWeights.size, "weightSpanDays" to spanDays),
        )
    }

    private fun clamp(
        value: BigDecimal,
        min: BigDecimal,
        max: BigDecimal,
    ): BigDecimal = value.max(min).min(max).setScale(2, RoundingMode.HALF_UP)
}
