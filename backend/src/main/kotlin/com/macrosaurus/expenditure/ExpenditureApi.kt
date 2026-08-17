package com.macrosaurus.expenditure

import com.macrosaurus.identity.ProfileService
import com.macrosaurus.identity.UserContext
import com.macrosaurus.measurements.MeasurementService
import com.macrosaurus.shared.JsonCodec
import com.macrosaurus.tracking.TrackingService
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.UUID

data class EnergyEstimateView(
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

@Service
class ExpenditureService(
    private val db: DSLContext,
    private val json: JsonCodec,
    private val profiles: ProfileService,
    private val measurements: MeasurementService,
    private val tracking: TrackingService,
) {
    fun estimate(
        userId: String,
        date: LocalDate = LocalDate.now(),
        persist: Boolean = false,
    ): EnergyEstimateView {
        val profile = profiles.get(userId)
        val weights = measurements.list(userId, 200).filter { !it.measuredAt.toLocalDate().isAfter(date) }
        val latest = weights.firstOrNull()
        val explanation = mutableListOf<String>()
        val age = profile?.birthDate?.let { Period.between(it, date).years }
        val baseline =
            if (profile?.heightCm != null && profile.formulaSex != null && age != null && age >= 18 && latest != null) {
                val sexAdjustment =
                    when (profile.formulaSex.uppercase()) {
                        "MALE" -> BigDecimal("5")
                        "FEMALE" -> BigDecimal("-161")
                        else -> null
                    }
                sexAdjustment?.let {
                    BigDecimal("10")
                        .multiply(latest.weightKg)
                        .add(BigDecimal("6.25").multiply(profile.heightCm))
                        .subtract(BigDecimal("5").multiply(BigDecimal(age)))
                        .add(it)
                        .multiply(profile.activityMultiplier)
                        .setScale(2, RoundingMode.HALF_UP)
                }
            } else {
                null
            }
        if (baseline == null) {
            explanation += "Add adult birth date, height, formula sex, and a weigh-in to calculate the baseline."
        } else {
            explanation += "Baseline uses Mifflin-St Jeor and your selected activity multiplier."
        }

        val start = date.minusDays(20)
        val diary = tracking.summary(userId, start, date)
        val loggedDays = diary.filter { it.entries.isNotEmpty() }
        val relevantWeights = weights.filter { !it.measuredAt.toLocalDate().isBefore(start) }.sortedBy { it.measuredAt }
        val spanDays =
            if (relevantWeights.size >= 2) {
                ChronoUnit.DAYS
                    .between(
                        relevantWeights.first().measuredAt.toLocalDate(),
                        relevantWeights.last().measuredAt.toLocalDate(),
                    ).toInt()
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
                    weightDelta
                        .multiply(BigDecimal("7700"))
                        .divide(BigDecimal(spanDays), 8, RoundingMode.HALF_UP)
                averageIntake.subtract(dailyStoredEnergy).setScale(2, RoundingMode.HALF_UP)
            } else {
                null
            }
        val suggested = adaptive?.let { clamp(it, baseline!! * BigDecimal("0.90"), baseline * BigDecimal("1.10")) } ?: baseline
        if (eligible) {
            explanation +=
                "Adaptive estimate uses average intake on logged days and the measured weight trend; it is clamped to 10% around baseline."
        } else {
            explanation += "Adaptive estimates require 14 logged days and 4 weigh-ins spanning at least 14 days."
        }

        val result =
            EnergyEstimateView(
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
        if (persist) {
            db.execute(
                """
                insert into energy_estimates(id, user_id, estimate_date, baseline_kcal, adaptive_kcal,
                                             suggested_kcal, confidence, algorithm_version, explanation)
                values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
                """.trimIndent(),
                UUID.randomUUID(),
                userId,
                date,
                baseline,
                adaptive,
                suggested,
                result.confidence,
                result.algorithmVersion,
                json.write(result.explanation),
            )
        }
        return result
    }

    private fun clamp(
        value: BigDecimal,
        min: BigDecimal,
        max: BigDecimal,
    ): BigDecimal = value.max(min).min(max).setScale(2, RoundingMode.HALF_UP)
}

@RestController
@RequestMapping("/api/v1/expenditure-estimates")
class ExpenditureController(
    private val users: UserContext,
    private val expenditure: ExpenditureService,
) {
    @GetMapping("/current")
    fun current(
        @RequestParam(required = false) date: LocalDate?,
        @RequestParam(defaultValue = "false") persist: Boolean,
    ) = expenditure.estimate(users.userId(), date ?: LocalDate.now(), persist)
}
