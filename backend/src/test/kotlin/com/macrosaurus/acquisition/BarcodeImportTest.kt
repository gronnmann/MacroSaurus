package com.macrosaurus.acquisition

import com.macrosaurus.acquisition.application.BarcodeService
import com.macrosaurus.acquisition.application.FoodFactsLookup
import com.macrosaurus.acquisition.integration.parseOpenFoodFactsCandidate
import com.macrosaurus.catalog.FoodCatalog
import com.macrosaurus.catalog.FoodCreator
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodSnapshot
import com.macrosaurus.catalog.PortionSnapshot
import com.macrosaurus.catalog.SourceKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import tools.jackson.databind.ObjectMapper
import java.time.OffsetDateTime
import java.util.UUID

class BarcodeImportTest {
    @Test
    fun `barcode import carries a serving into the created food`() {
        val candidate =
            parseOpenFoodFactsCandidate(
                "3017620422003",
                """{"product":{"product_name":"Milk","serving_size":"1 glass (250 ml)","serving_quantity":250,"serving_quantity_unit":"ml","nutriments":{"energy-kcal_100g":43}}}""",
                ObjectMapper(),
            )!!
        val creator =
            object : FoodCreator {
                override fun create(
                    userId: String,
                    draft: FoodDraft,
                    source: SourceKind,
                    externalId: String?,
                ): FoodSnapshot =
                    FoodSnapshot(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        draft.name,
                        draft.brand,
                        draft.barcode,
                        source,
                        draft.basisType,
                        draft.basisAmount,
                        draft.basisUnit,
                        draft.densityGPerMl,
                        draft.nutrients,
                        draft.portions.map { PortionSnapshot(UUID.randomUUID(), it.name, it.quantity, it.gramWeight, it.milliliterVolume, it.default) },
                        OffsetDateTime.now(),
                        externalId,
                    )
            }
        val result = BarcodeService(mock(FoodCatalog::class.java), creator, FoodFactsLookup { candidate }).import("user", candidate.barcode)
        assertThat(result.basisUnit).isEqualTo("ml")
        assertThat(result.portions.single().milliliterVolume).isEqualByComparingTo("250")
        assertThat(result.portions.single().name).isEqualTo("1 glass (250 ml)")
        assertThat(result.portions.single().default).isTrue()
    }
}
