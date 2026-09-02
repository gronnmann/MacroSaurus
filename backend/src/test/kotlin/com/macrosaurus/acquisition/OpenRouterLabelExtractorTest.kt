package com.macrosaurus.acquisition

import com.macrosaurus.acquisition.application.ExtractedNutrient
import com.macrosaurus.acquisition.config.OpenRouterProperties
import com.macrosaurus.acquisition.integration.OpenRouterLabelExtractor
import com.macrosaurus.acquisition.integration.RawLabelExtraction
import com.macrosaurus.catalog.BasisType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

class OpenRouterLabelExtractorTest {
    private val extractor =
        OpenRouterLabelExtractor(
            OpenRouterProperties("https://example.invalid", "test", "test-model"),
            ObjectMapper(),
        )

    @Test
    fun `keeps printed per-100 and serving columns separate while selecting per-100 basis`() {
        val result =
            extractor.normalize(
                RawLabelExtraction(
                    name = "Yoghurt",
                    brand = "Example",
                    barcode = null,
                    per100BasisUnit = "g",
                    per100Nutrients = listOf(nutrient("protein_g", "4.2", "g")),
                    perServingNutrients = listOf(nutrient("protein_g", "6.3", "g")),
                    servingName = "1 pot",
                    servingMassG = BigDecimal("150"),
                    servingVolumeMl = null,
                    ingredients = null,
                    allergens = emptyList(),
                    warnings = emptyList(),
                ),
                "12345670",
            )

        assertThat(result.basisType).isEqualTo(BasisType.PER_100_G)
        assertThat(result.nutrients.single().amount).isEqualByComparingTo("4.2")
        assertThat(result.per100Nutrients.single().amount).isEqualByComparingTo("4.2")
        assertThat(result.perServingNutrients.single().amount).isEqualByComparingTo("6.3")
        assertThat(result.servingName).isEqualTo("1 pot")
        assertThat(result.servingMassG).isEqualByComparingTo("150")
        assertThat(result.barcode).isEqualTo("12345670")
    }

    @Test
    fun `derives per-100 values from a printed serving and converts salt to sodium`() {
        val result =
            extractor.normalize(
                RawLabelExtraction(
                    name = null,
                    brand = null,
                    barcode = null,
                    per100BasisUnit = null,
                    per100Nutrients = emptyList(),
                    perServingNutrients =
                        listOf(
                            nutrient("energy_kcal", "120", "kcal"),
                            nutrient("salt_g", "0.5", "g"),
                        ),
                    servingName = null,
                    servingMassG = BigDecimal("40"),
                    servingVolumeMl = null,
                    ingredients = null,
                    allergens = emptyList(),
                    warnings = emptyList(),
                ),
                null,
            )

        assertThat(result.basisType).isEqualTo(BasisType.PER_100_G)
        assertThat(result.servingName).isEqualTo("1 serving")
        assertThat(result.nutrients.associate { it.code to it.amount })
            .containsEntry("energy_kcal", BigDecimal("300.00000000"))
            .containsEntry("sodium_mg", BigDecimal("500.00000000"))
        assertThat(result.warnings)
            .contains("Sodium was calculated from the printed salt value.")
            .contains("Per-100 values were calculated from the printed serving size.")
    }

    private fun nutrient(
        code: String,
        amount: String,
        unit: String,
    ) = ExtractedNutrient(code, BigDecimal(amount), unit, BigDecimal("0.9"))
}
