package com.macrosaurus.acquisition

import com.macrosaurus.shared.SourceKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class OpenFoodFactsClientTest {
    private val mapper = ObjectMapper()

    @Test
    fun `parses a successful v3 product response without a legacy status field`() {
        val candidate =
            parseOpenFoodFactsCandidate(
                "3017620422003",
                """
                {
                  "code": "3017620422003",
                  "errors": [],
                  "product": {
                    "product_name": "Nutella",
                    "brands": "Ferrero",
                    "nutriments": {
                      "energy-kcal_100g": 539,
                      "proteins_100g": 6.3,
                      "sodium_100g": 0.0428
                    }
                  }
                }
                """.trimIndent(),
                mapper,
            )

        assertThat(candidate).isNotNull
        assertThat(candidate!!.name).isEqualTo("Nutella")
        assertThat(candidate.brand).isEqualTo("Ferrero")
        assertThat(candidate.source).isEqualTo(SourceKind.OPEN_FOOD_FACTS)
        assertThat(candidate.nutrients)
            .containsEntry("energy_kcal", "539".toBigDecimal())
            .containsEntry("protein_g", "6.3".toBigDecimal())
            .containsEntry("sodium_mg", "42.8000".toBigDecimal())
    }

    @Test
    fun `returns no candidate when v3 response has no product`() {
        val candidate =
            parseOpenFoodFactsCandidate(
                "0000000000000",
                """{"code":"0000000000000","errors":[{"message":"Product not found"}]}""",
                mapper,
            )

        assertThat(candidate).isNull()
    }
}
