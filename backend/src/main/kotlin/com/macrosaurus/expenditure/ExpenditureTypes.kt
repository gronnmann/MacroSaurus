package com.macrosaurus.expenditure

import com.macrosaurus.identity.ProfileSnapshot
import java.math.BigDecimal
import java.time.LocalDate

data class EnergyEstimate(
    val date: LocalDate,
    val baselineKcal: BigDecimal?,
    val adaptiveKcal: BigDecimal?,
    val suggestedKcal: BigDecimal?,
    val confidence: String,
    val adaptiveEligible: Boolean,
    val algorithmVersion: String,
    val explanation: List<String>,
    val requirements: Map<String, Int>,
    val lowerKcal: BigDecimal? = null,
    val upperKcal: BigDecimal? = null,
    val trendWeightKg: BigDecimal? = null,
    val trendWeightLowerKg: BigDecimal? = null,
    val trendWeightUpperKg: BigDecimal? = null,
    val modelState: String = "BASELINE",
)

data class ProgressSeriesPoint(
    val date: LocalDate,
    val measuredWeightKg: BigDecimal?,
    val estimate: EnergyEstimate,
)

interface ExpenditureEstimator {
    fun estimate(
        userId: String,
        date: LocalDate,
        persist: Boolean = false,
    ): EnergyEstimate

    fun preview(
        profile: ProfileSnapshot,
        weightKg: BigDecimal,
        date: LocalDate,
    ): EnergyEstimate

    fun series(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<ProgressSeriesPoint>
}
