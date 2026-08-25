package com.macrosaurus.catalog.domain

import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.FoodAmount
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodSnapshot
import com.macrosaurus.catalog.SourceKind
import com.macrosaurus.shared.InvalidOperationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

class FoodRulesTest {
    @Test
    fun `mass resolution scales a per 100 gram snapshot`() {
        val resolved = FoodAmountResolver.resolve(food(), FoodAmount(BigDecimal("25"), "g"))

        assertThat(resolved.resolvedGrams).isEqualByComparingTo("25")
        assertThat(resolved.nutrients["protein_g"]).isEqualByComparingTo("3.25")
    }

    @Test
    fun `volume conversion requires density for mass based food`() {
        assertThatThrownBy { FoodAmountResolver.resolve(food(), FoodAmount(BigDecimal("100"), "ml")) }
            .isInstanceOf(InvalidOperationException::class.java)
            .hasMessageContaining("Density")
    }

    @Test
    fun `draft validation rejects unknown nutrients`() {
        val draft =
            FoodDraft(
                name = "Test",
                nutrients = mapOf("mystery" to BigDecimal.ONE),
            )

        assertThatThrownBy { FoodDraftValidator.validate(draft, emptySet()) }
            .isInstanceOf(InvalidOperationException::class.java)
            .hasMessageContaining("mystery")
    }

    private fun food() =
        FoodSnapshot(
            UUID.randomUUID(),
            UUID.randomUUID(),
            1,
            "Oats",
            null,
            null,
            SourceKind.USER,
            BasisType.PER_100_G,
            BigDecimal("100"),
            "g",
            null,
            mapOf("protein_g" to BigDecimal("13")),
            emptyList(),
            OffsetDateTime.parse("2026-08-25T00:00:00Z"),
        )
}
