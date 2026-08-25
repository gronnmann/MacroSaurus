package com.macrosaurus.expenditure

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
)

interface ExpenditureEstimator {
    fun estimate(
        userId: String,
        date: LocalDate,
        persist: Boolean = false,
    ): EnergyEstimate
}
