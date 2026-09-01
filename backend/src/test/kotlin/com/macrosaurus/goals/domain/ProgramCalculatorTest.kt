package com.macrosaurus.goals.domain

import com.macrosaurus.expenditure.EnergyEstimate
import com.macrosaurus.goals.ProgramStyle
import com.macrosaurus.goals.WeightGoalType
import com.macrosaurus.shared.InvalidOperationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class ProgramCalculatorTest {
    @Test
    fun `weekly coached changes are capped without changing the requested goal`() {
        val result =
            ProgramCalculator.calculate(
                LocalDate.of(2026, 8, 31),
                coachedInputs(),
                estimate(),
                BigDecimal("1.4"),
                previousEnergyKcal = BigDecimal("2400"),
                recurringCheckIn = true,
            )

        assertThat(result.energyKcal).isEqualByComparingTo("2300")
        assertThat(result.proteinG).isEqualByComparingTo("144")
        assertThat(result.estimatedCompletionDate).isEqualTo(LocalDate.of(2026, 11, 27))
        assertThat(result.warnings).anyMatch { it.contains("100 kcal") }
    }

    @Test
    fun `loss goals reject targets above current weight`() {
        assertThatThrownBy {
            ProgramCalculator.calculate(
                LocalDate.of(2026, 8, 31),
                coachedInputs().copy(targetWeightKg = BigDecimal("85")),
                estimate(),
                BigDecimal("1.4"),
            )
        }.isInstanceOf(InvalidOperationException::class.java)
            .hasMessageContaining("below")
    }

    private fun coachedInputs() =
        ProgramInputs(
            ProgramStyle.COACHED,
            WeightGoalType.LOSS,
            BigDecimal("80"),
            BigDecimal("70"),
            BigDecimal("1.0"),
            BigDecimal("1.8"),
            BigDecimal("25"),
            null,
            null,
            null,
            null,
        )

    private fun estimate() =
        EnergyEstimate(
            LocalDate.of(2026, 8, 31),
            BigDecimal("2500"),
            BigDecimal("2700"),
            BigDecimal("2700"),
            "HIGH",
            true,
            "energy-v2",
            emptyList(),
            emptyMap(),
        )
}
