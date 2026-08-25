package com.macrosaurus.expenditure.application

import com.macrosaurus.expenditure.EnergyEstimate
import com.macrosaurus.expenditure.ExpenditureEstimator
import com.macrosaurus.expenditure.domain.EnergyCalculator
import com.macrosaurus.expenditure.persistence.JooqEnergyEstimateRepository
import com.macrosaurus.identity.ProfileReader
import com.macrosaurus.measurements.WeightHistory
import com.macrosaurus.tracking.NutritionHistory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate

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

    fun current(
        userId: String,
        persist: Boolean,
    ): EnergyEstimate = estimate(userId, LocalDate.now(clock), persist)
}
