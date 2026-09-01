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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

internal object EnergyCalculator {
    private const val WINDOW_DAYS = 21L
    private const val ENERGY_DENSITY_KCAL_PER_KG = 7700.0

    fun calculate(
        date: LocalDate,
        profile: ProfileSnapshot?,
        weights: List<WeightMeasurement>,
        diary: List<DailyNutrition>,
    ): EnergyEstimate {
        val start = date.minusDays(WINDOW_DAYS - 1)
        val dailyWeights =
            weights
                .filter { !it.measuredAt.toLocalDate().isBefore(start) && !it.measuredAt.toLocalDate().isAfter(date) }
                .groupBy { it.measuredAt.toLocalDate() }
                .mapValues { (_, entries) -> median(entries.map { it.weightKg.toDouble() }) }
                .toSortedMap()
        val latestWeight = weights.filter { !it.measuredAt.toLocalDate().isAfter(date) }.maxByOrNull { it.measuredAt }?.weightKg
        val age = profile?.birthDate?.let { Period.between(it, date).years }
        val resting =
            if (profile?.heightCm != null && profile.formulaSex != null && age != null && age >= 18 && latestWeight != null) {
                10.0 * latestWeight.toDouble() + 6.25 * profile.heightCm.toDouble() - 5.0 * age +
                    if (profile.formulaSex == FormulaSex.MALE) 5.0 else -161.0
            } else {
                null
            }
        val baseline = resting?.times(profile!!.activityMultiplier.toDouble())
        val weightedIntakes =
            diary
                .filter { !it.date.isBefore(start) && !it.date.isAfter(date) }
                .mapNotNull { day ->
                    val energy = day.analysisEnergyKcal?.toDouble() ?: return@mapNotNull null
                    val weight = day.analysisWeight.toDouble()
                    if (weight <= 0.0) null else WeightedValue(energy, weight)
                }
        val effectiveDays = weightedIntakes.sumOf { it.weight }
        val spanDays = if (dailyWeights.size >= 2) ChronoUnit.DAYS.between(dailyWeights.firstKey(), dailyWeights.lastKey()).toInt() else 0
        val recentWeight = dailyWeights.keys.any { !it.isBefore(date.minusDays(6)) }
        val regression = robustRegression(dailyWeights, start)
        val eligible = baseline != null && effectiveDays >= 14.0 && dailyWeights.size >= 4 && spanDays >= 14 && recentWeight && regression != null
        val intake = weightedMean(weightedIntakes)
        val adaptive =
            if (regression != null && intake != null && effectiveDays >= 4.0) {
                intake.mean - ENERGY_DENSITY_KCAL_PER_KG * regression.slope
            } else {
                null
            }

        val estimate: Double?
        val standardError: Double?
        if (eligible && adaptive != null && intake != null) {
            val adaptiveSe =
                max(
                    100.0,
                    sqrt(intake.standardError * intake.standardError + square(ENERGY_DENSITY_KCAL_PER_KG * regression.slopeStandardError)),
                )
            val baselineVariance = square(max(100.0, baseline * 0.20))
            val adaptiveVariance = square(adaptiveSe)
            estimate = (baseline / baselineVariance + adaptive / adaptiveVariance) / (1.0 / baselineVariance + 1.0 / adaptiveVariance)
            standardError = sqrt(1.0 / (1.0 / baselineVariance + 1.0 / adaptiveVariance))
        } else {
            estimate = baseline
            standardError = baseline?.let { max(100.0, it * 0.20) }
        }
        val halfWidth = standardError?.times(1.96)
        val trend = regression?.predict(ChronoUnit.DAYS.between(start, date).toDouble())
        val trendHalfWidth = regression?.predictionStandardError?.let { max(0.10, it * 1.96) }
        val modelState =
            when {
                eligible -> "UPDATING"
                baseline == null -> "INSUFFICIENT"
                (effectiveDays >= 14.0 || dailyWeights.size >= 4) && !recentWeight -> "HOLDING"
                else -> "BASELINE"
            }
        val confidence =
            when {
                estimate == null -> "INSUFFICIENT"
                !eligible -> "LOW"
                effectiveDays >= 18.0 && dailyWeights.size >= 7 && halfWidth != null && halfWidth <= estimate * 0.10 -> "HIGH"
                else -> "MEDIUM"
            }
        val explanation =
            buildList {
                if (baseline == null) {
                    add("Add adult birth date, height, formula sex, and a weigh-in to calculate the starting estimate.")
                } else {
                    add("The starting estimate uses Mifflin-St Jeor and your selected activity level.")
                }
                if (eligible) {
                    add("The adaptive estimate combines reviewed intake with a robust 21-day weight trend.")
                } else if (modelState == "HOLDING") {
                    add("The adaptive estimate is holding until there is a recent weigh-in and enough reviewed nutrition data.")
                } else {
                    add("Adaptive estimates need 14 reviewed nutrition days and four weigh-in days spanning at least 14 days.")
                }
                add("The shaded range describes model uncertainty, not a guaranteed calorie requirement.")
            }
        return EnergyEstimate(
            date = date,
            baselineKcal = decimal(baseline),
            adaptiveKcal = decimal(adaptive),
            suggestedKcal = decimal(estimate),
            confidence = confidence,
            adaptiveEligible = eligible,
            algorithmVersion = "energy-v2",
            explanation = explanation,
            requirements =
                mapOf(
                    "loggedDays" to weightedIntakes.count { it.weight == 1.0 },
                    "estimatedDays" to weightedIntakes.count { it.weight < 1.0 },
                    "effectiveDays" to effectiveDays.toInt(),
                    "weighIns" to dailyWeights.size,
                    "weightSpanDays" to spanDays,
                ),
            lowerKcal = decimal(estimate?.let { value -> halfWidth?.let { value - it } }),
            upperKcal = decimal(estimate?.let { value -> halfWidth?.let { value + it } }),
            trendWeightKg = decimal(trend, 3),
            trendWeightLowerKg = decimal(trend?.let { value -> trendHalfWidth?.let { value - it } }, 3),
            trendWeightUpperKg = decimal(trend?.let { value -> trendHalfWidth?.let { value + it } }, 3),
            modelState = modelState,
        )
    }

    private fun robustRegression(
        values: Map<LocalDate, Double>,
        start: LocalDate,
    ): Regression? {
        if (values.size < 2) return null
        val points = values.map { (date, value) -> Point(ChronoUnit.DAYS.between(start, date).toDouble(), value) }
        var weights = DoubleArray(points.size) { 1.0 }
        var fit = fit(points, weights) ?: return null
        repeat(3) {
            val residuals = points.map { point -> abs(point.y - fit.predict(point.x)) }
            val scale = max(0.001, 1.4826 * median(residuals))
            val threshold = 1.345 * scale
            weights = DoubleArray(points.size) { index -> if (residuals[index] <= threshold) 1.0 else threshold / residuals[index] }
            fit = fit(points, weights) ?: fit
        }
        return fit
    }

    private fun fit(
        points: List<Point>,
        weights: DoubleArray,
    ): Regression? {
        val sumW = weights.sum()
        val meanX = points.indices.sumOf { weights[it] * points[it].x } / sumW
        val meanY = points.indices.sumOf { weights[it] * points[it].y } / sumW
        val denominator = points.indices.sumOf { weights[it] * square(points[it].x - meanX) }
        if (denominator == 0.0) return null
        val slope = points.indices.sumOf { weights[it] * (points[it].x - meanX) * (points[it].y - meanY) } / denominator
        val intercept = meanY - slope * meanX
        val residualVariance =
            points.indices.sumOf { weights[it] * square(points[it].y - (intercept + slope * points[it].x)) } /
                max(1.0, sumW - 2.0)
        return Regression(intercept, slope, sqrt(residualVariance / denominator), sqrt(residualVariance))
    }

    private fun weightedMean(values: List<WeightedValue>): Mean? {
        if (values.isEmpty()) return null
        val totalWeight = values.sumOf { it.weight }
        val mean = values.sumOf { it.value * it.weight } / totalWeight
        val variance = values.sumOf { it.weight * square(it.value - mean) } / max(1.0, totalWeight - 1.0)
        return Mean(mean, sqrt(variance / totalWeight))
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun square(value: Double) = value * value

    private fun decimal(
        value: Double?,
        scale: Int = 2,
    ): BigDecimal? = value?.takeIf { it.isFinite() }?.let { BigDecimal.valueOf(it).setScale(scale, RoundingMode.HALF_UP) }

    private data class WeightedValue(
        val value: Double,
        val weight: Double,
    )

    private data class Mean(
        val mean: Double,
        val standardError: Double,
    )

    private data class Point(
        val x: Double,
        val y: Double,
    )

    private data class Regression(
        val intercept: Double,
        val slope: Double,
        val slopeStandardError: Double,
        val predictionStandardError: Double,
    ) {
        fun predict(x: Double) = intercept + slope * x
    }
}
