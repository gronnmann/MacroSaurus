package com.macrosaurus.acquisition

import com.macrosaurus.acquisition.integration.parseOpenFoodFactsCandidate
import com.macrosaurus.catalog.SourceKind
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

    @Test
    fun `imports a named serving with a normalized weight`() {
        val candidate = product(""""serving_size": "2 biscuits (25 g)", "serving_quantity": "25", "serving_quantity_unit": "g"""")
        val portion = candidate!!.portions.single()
        assertThat(portion.name).isEqualTo("2 biscuits (25 g)")
        assertThat(portion.gramWeight).isEqualByComparingTo("25")
        assertThat(portion.quantity).isEqualByComparingTo("1")
        assertThat(portion.default).isTrue()
    }

    @Test
    fun `parses decimal comma and volume servings from labels`() {
        val candidate = product(""""serving_size": "1 glass (2,5 dl)"""")
        assertThat(candidate!!.basisType).isEqualTo(com.macrosaurus.catalog.BasisType.PER_100_ML)
        assertThat(candidate.portions.single().milliliterVolume).isEqualByComparingTo("250")
        assertThat(candidate.portions.single().gramWeight).isNull()
    }

    @Test
    fun `uses structured serving data even without a label`() {
        val candidate = product(""""serving_quantity": 30, "serving_quantity_unit": "g"""")
        assertThat(candidate!!.portions.single().gramWeight).isEqualByComparingTo("30")
    }

    @Test
    fun `ignores missing invalid and ambiguous serving sizes`() {
        listOf(
            """"serving_size": "1 slice"""",
            """"serving_size": "0 g"""",
            """"serving_quantity": -5, "serving_quantity_unit": "g"""",
            """"serving_quantity": "unknown", "serving_quantity_unit": "ml"""",
            """"serving_size": "20 g or 30 g"""",
            """"serving_size": "-5 g"""",
        ).forEach { fields -> assertThat(product(fields)!!.portions).isEmpty() }
    }

    private fun product(fields: String) =
        parseOpenFoodFactsCandidate(
            "3017620422003",
            """{"product":{"product_name":"Example",$fields,"nutriments":{"energy-kcal_100g":100}}}""",
            mapper,
        )
}
