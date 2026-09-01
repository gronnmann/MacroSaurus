package com.macrosaurus.expenditure.application

import com.macrosaurus.expenditure.EnergyEstimate
import com.macrosaurus.expenditure.ExpenditureEstimator
import com.macrosaurus.expenditure.ProgressSeriesPoint
import com.macrosaurus.expenditure.domain.EnergyCalculator
import com.macrosaurus.expenditure.persistence.JooqEnergyEstimateRepository
import com.macrosaurus.identity.ProfileReader
import com.macrosaurus.identity.ProfileSnapshot
import com.macrosaurus.measurements.WeightHistory
import com.macrosaurus.measurements.WeightMeasurement
import com.macrosaurus.shared.InvalidOperationException
import com.macrosaurus.tracking.NutritionHistory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Service
internal class ExpenditureService(
    private val profiles: ProfileReader,
    private val measurements: WeightHistory,
    private val tracking: NutritionHistory,
    private val repository: JooqEnergyEstimateRepository,
    private val clock: Clock,
) : ExpenditureEstimator {
    override fun estimate(
        userId: String,
        date: LocalDate,
        persist: Boolean,
    ): EnergyEstimate {
        val result =
            EnergyCalculator.calculate(
                date,
                profiles.get(userId),
                measurements.list(userId, 200).filter { !it.measuredAt.toLocalDate().isAfter(date) },
                tracking.dailyNutrition(userId, date.minusDays(20), date),
            )
        if (persist) repository.insert(userId, result)
        return result
    }

    override fun preview(
        profile: ProfileSnapshot,
        weightKg: BigDecimal,
        date: LocalDate,
    ): EnergyEstimate {
        val zone = runCatching { ZoneId.of(profile.timezone) }.getOrDefault(ZoneId.of("UTC"))
        val weight = WeightMeasurement(UUID.randomUUID(), weightKg, date.atStartOfDay(zone).toOffsetDateTime(), "Setup weight")
        return EnergyCalculator.calculate(date, profile, listOf(weight), emptyList())
    }

    override fun series(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<ProgressSeriesPoint> {
        if (to.isBefore(from) || to.isAfter(from.plusDays(365))) {
            throw InvalidOperationException("Progress range must be between 1 and 366 days")
        }
        val contextFrom = from.minusDays(20)
        val profile = profiles.get(userId)
        val weights = measurements.list(userId, 500).filter { !it.measuredAt.toLocalDate().isAfter(to) }
        val diary = tracking.dailyNutrition(userId, contextFrom, to)
        return generateSequence(from) { current -> current.plusDays(1).takeUnless { it.isAfter(to) } }
            .map { date ->
                val estimate =
                    EnergyCalculator.calculate(
                        date,
                        profile,
                        weights.filter { !it.measuredAt.toLocalDate().isAfter(date) },
                        diary.filter { !it.date.isAfter(date) },
                    )
                val measured = weights.filter { it.measuredAt.toLocalDate() == date }.map { it.weightKg }.sorted()
                val median =
                    when {
                        measured.isEmpty() -> null
                        measured.size % 2 == 1 -> measured[measured.size / 2]
                        else -> measured[measured.size / 2 - 1].add(measured[measured.size / 2]).divide(BigDecimal("2"))
                    }
                ProgressSeriesPoint(date, median, estimate)
            }.toList()
    }

    fun current(
        userId: String,
        persist: Boolean,
    ): EnergyEstimate {
        val zone = profiles.get(userId)?.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("UTC")
        return estimate(userId, LocalDate.now(clock.withZone(zone)), persist)
    }
}
