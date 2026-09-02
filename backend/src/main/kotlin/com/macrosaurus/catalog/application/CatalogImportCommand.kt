package com.macrosaurus.catalog.application

import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.CatalogImportCommand
import com.macrosaurus.catalog.CatalogImporter
import com.macrosaurus.catalog.ImportedFood
import com.macrosaurus.catalog.PortionDraft
import com.macrosaurus.catalog.SourceKind
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal

internal data class CatalogReleaseInput(
    val source: SourceKind,
    val releaseKey: String,
    val checksum: String,
    val foods: List<CatalogFoodInput>,
)

internal data class CatalogFoodInput(
    val externalId: String,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val locale: String? = null,
    val aliases: Map<String, String> = emptyMap(),
    val basisType: BasisType = BasisType.PER_100_G,
    val basisAmount: BigDecimal = BigDecimal("100"),
    val basisUnit: String = "g",
    val densityGPerMl: BigDecimal? = null,
    val nutrients: Map<String, BigDecimal>,
    val portions: List<CatalogPortionInput> = emptyList(),
)

internal data class CatalogPortionInput(
    val name: String,
    val gramWeight: BigDecimal? = null,
    val milliliterVolume: BigDecimal? = null,
    val default: Boolean = false,
)

@Component
internal class CatalogImportCommandHandler(
    private val mapper: ObjectMapper,
    private val importer: CatalogImporter,
) : CatalogImportCommand {
    override fun execute(
        input: InputStream,
        output: OutputStream,
    ) {
        val release = mapper.readValue(input, CatalogReleaseInput::class.java)
        val result =
            importer.importRelease(
                release.source,
                release.releaseKey,
                release.checksum,
                release.foods.map { food ->
                    ImportedFood(
                        food.externalId,
                        food.name,
                        food.brand,
                        food.barcode,
                        food.locale,
                        food.aliases,
                        food.basisType,
                        food.basisAmount,
                        food.basisUnit,
                        food.densityGPerMl,
                        food.nutrients,
                        food.portions.map {
                            PortionDraft(
                                it.name,
                                BigDecimal.ONE,
                                it.gramWeight,
                                it.milliliterVolume,
                                it.default,
                            )
                        },
                    )
                },
            )
        output.write(mapper.writeValueAsBytes(result))
        output.write('\n'.code)
        output.flush()
    }
}
