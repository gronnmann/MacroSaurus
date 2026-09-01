package com.macrosaurus.catalog.web

import com.macrosaurus.shared.NutrientValues
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.util.UUID
import com.macrosaurus.catalog.ResolvedFoodAmount as ResolvedFoodAmountContract

class CatalogHttpModelsTest {
    @Test
    fun `resolved nutrients are serialized as a flat map`() {
        val view =
            ResolvedFoodAmountContract(
                UUID.randomUUID(),
                "Chocolate",
                BigDecimal("30"),
                "g",
                BigDecimal("30"),
                NutrientValues(
                    mapOf(
                        "energy_kcal" to BigDecimal("160"),
                        "protein_g" to BigDecimal("2.1"),
                    ),
                ),
            ).toView()

        val json = ObjectMapper().writeValueAsString(view)

        assertThat(view.nutrients["energy_kcal"]).isEqualByComparingTo("160")
        assertThat(json).contains("\"nutrients\":{\"energy_kcal\":160")
        assertThat(json).doesNotContain("\"values\"")
    }
}
