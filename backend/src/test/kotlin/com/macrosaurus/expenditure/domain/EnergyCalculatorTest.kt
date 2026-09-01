package com.macrosaurus.expenditure.domain

import com.macrosaurus.identity.FormulaSex
import com.macrosaurus.identity.ProfileSnapshot
import com.macrosaurus.identity.UnitSystem
import com.macrosaurus.measurements.WeightMeasurement
import com.macrosaurus.tracking.DailyNutrition
import com.macrosaurus.tracking.NutritionDayStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class EnergyCalculatorTest {
    @Test
    fun `reviewed intake and robust weight trend unlock adaptive expenditure`() {
        val end = LocalDate.of(2026, 8, 31)
        val start = end.minusDays(20)
        val weights =
            (0L..20L).map { offset ->
                val date = start.plusDays(offset)
                WeightMeasurement(
                    UUID.randomUUID(),
                    BigDecimal.valueOf(80.4 - 0.02 * offset),
                    date.atTime(7, 0).atOffset(ZoneOffset.UTC),
                    null,
                )
            }
        val diary =
            (0L..20L).map { offset ->
                DailyNutrition(
                    start.plusDays(offset),
                    3,
                    mapOf("energy_kcal" to BigDecimal("2300")),
                    NutritionDayStatus.CONFIRMED_COMPLETE,
                    BigDecimal("2300"),
                    BigDecimal.ONE,
                )
            }

        val result = EnergyCalculator.calculate(end, profile(), weights, diary)

        assertThat(result.adaptiveEligible).isTrue()
        assertThat(result.modelState).isEqualTo("UPDATING")
        assertThat(result.adaptiveKcal).isBetween(BigDecimal("2445"), BigDecimal("2465"))
        assertThat(result.suggestedKcal).isBetween(result.lowerKcal, result.upperKcal)
        assertThat(result.trendWeightKg).isBetween(BigDecimal("79.9"), BigDecimal("80.1"))
        assertThat(result.requirements).containsEntry("effectiveDays", 21)
    }

    @Test
    fun `partial estimates contribute less than confirmed days`() {
        val end = LocalDate.of(2026, 8, 31)
        val diary =
            listOf(
                DailyNutrition(
                    end,
                    1,
                    emptyMap(),
                    NutritionDayStatus.ESTIMATED_TOTAL,
                    BigDecimal("2200"),
                    BigDecimal("0.5"),
                ),
            )

        val result = EnergyCalculator.calculate(end, profile(), emptyList(), diary)

        assertThat(result.adaptiveEligible).isFalse()
        assertThat(result.requirements).containsEntry("estimatedDays", 1).containsEntry("effectiveDays", 0)
    }

    private fun profile() =
        ProfileSnapshot(
            "energy-test",
            "Energy test",
            "en",
            "UTC",
            UnitSystem.METRIC,
            LocalDate.of(1992, 1, 1),
            BigDecimal("178"),
            FormulaSex.MALE,
            BigDecimal("1.4"),
        )
}
