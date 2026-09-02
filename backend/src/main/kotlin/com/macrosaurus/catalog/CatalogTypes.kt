package com.macrosaurus.catalog

import com.macrosaurus.shared.NutrientValues
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

enum class BasisType { PER_100_G, PER_100_ML, PER_SERVING }

enum class SourceKind { USDA, USDA_FOUNDATION, USDA_SR_LEGACY, MATVARETABELLEN, OPEN_FOOD_FACTS, USER }

data class NutrientDefinition(
    val code: String,
    val displayName: String,
    val category: String,
    val unit: String,
    val sortOrder: Int,
)

data class PortionSnapshot(
    val id: UUID,
    val name: String,
    val quantity: BigDecimal,
    val gramWeight: BigDecimal?,
    val milliliterVolume: BigDecimal?,
    val default: Boolean,
)

data class FoodSnapshot(
    val id: UUID,
    val revisionId: UUID,
    val revision: Int,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val source: SourceKind,
    val basisType: BasisType,
    val basisAmount: BigDecimal,
    val basisUnit: String,
    val densityGPerMl: BigDecimal?,
    val nutrients: Map<String, BigDecimal>,
    val portions: List<PortionSnapshot>,
    val createdAt: OffsetDateTime,
    val externalId: String? = null,
    val sourceRelease: String? = null,
)

data class PortionDraft(
    val name: String,
    val quantity: BigDecimal = BigDecimal.ONE,
    val gramWeight: BigDecimal? = null,
    val milliliterVolume: BigDecimal? = null,
    val default: Boolean = false,
)

data class FoodDraft(
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val basisType: BasisType = BasisType.PER_100_G,
    val basisAmount: BigDecimal = BigDecimal("100"),
    val basisUnit: String = "g",
    val densityGPerMl: BigDecimal? = null,
    val nutrients: Map<String, BigDecimal>,
    val portions: List<PortionDraft> = emptyList(),
)

data class FoodAmount(
    val quantity: BigDecimal,
    val unit: String,
    val portionId: UUID? = null,
)

data class ResolvedFoodAmount(
    val foodRevisionId: UUID,
    val displayName: String,
    val quantity: BigDecimal,
    val unit: String,
    val resolvedGrams: BigDecimal?,
    val nutrients: NutrientValues,
)

interface NutrientCatalog {
    fun nutrients(): List<NutrientDefinition>
}

interface FoodCatalog {
    fun search(
        userId: String,
        query: String,
        limit: Int = 25,
    ): List<FoodSnapshot>

    fun get(
        userId: String,
        foodId: UUID,
    ): FoodSnapshot

    fun byRevision(
        userId: String,
        revisionId: UUID,
    ): FoodSnapshot

    fun byRevisions(
        userId: String,
        revisionIds: Collection<UUID>,
    ): Map<UUID, FoodSnapshot>

    fun byBarcode(
        userId: String,
        barcode: String,
    ): List<FoodSnapshot>
}

interface FoodResolver {
    fun resolve(
        userId: String,
        revisionId: UUID,
        amount: FoodAmount,
    ): ResolvedFoodAmount
}

interface FoodCreator {
    fun create(
        userId: String,
        draft: FoodDraft,
        source: SourceKind = SourceKind.USER,
        externalId: String? = null,
    ): FoodSnapshot
}

data class ImportedFood(
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
    val portions: List<PortionDraft> = emptyList(),
)

data class CatalogImportResult(
    val source: SourceKind,
    val releaseKey: String,
    val importedCount: Int,
    val alreadyImported: Boolean,
)

interface CatalogImporter {
    fun importRelease(
        source: SourceKind,
        releaseKey: String,
        checksum: String,
        foods: List<ImportedFood>,
    ): CatalogImportResult
}
