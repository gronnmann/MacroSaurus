package com.macrosaurus.shared

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class NutrientMathTest {
    @Test
    fun `calories are calculated from available energy-bearing macros`() {
        val calories =
            NutrientMath.calculatedCalories(
                mapOf(
                    "protein_g" to BigDecimal("20"),
                    "carbohydrate_g" to BigDecimal("30"),
                    "fat_g" to BigDecimal("10"),
                    "alcohol_g" to BigDecimal("2"),
                ),
            )

        assertThat(calories).isEqualByComparingTo("304")
    }

    @Test
    fun `scaling retains unknown nutrients instead of manufacturing zero fields`() {
        val scaled =
            NutrientValues(mapOf("protein_g" to BigDecimal("12.5")))
                .scaled(BigDecimal("2"))

        assertThat(scaled.values).containsExactlyEntriesOf(mapOf("protein_g" to BigDecimal("25")))
        assertThat(scaled["vitamin_c_mg"]).isNull()
    }

    @Test
    fun `combining snapshots sums present nutrient values`() {
        val total =
            NutrientValues(mapOf("protein_g" to BigDecimal("10")))
                .plus(NutrientValues(mapOf("protein_g" to BigDecimal("5"), "fiber_g" to BigDecimal("3"))))

        assertThat(total["protein_g"]).isEqualByComparingTo("15")
        assertThat(total["fiber_g"]).isEqualByComparingTo("3")
    }
}
