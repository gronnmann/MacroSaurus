package com.macrosaurus.catalog.application

import com.macrosaurus.catalog.CatalogImportResult
import com.macrosaurus.catalog.CatalogImporter
import com.macrosaurus.catalog.FoodAmount
import com.macrosaurus.catalog.FoodCatalog
import com.macrosaurus.catalog.FoodCreator
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodResolver
import com.macrosaurus.catalog.FoodSnapshot
import com.macrosaurus.catalog.ImportedFood
import com.macrosaurus.catalog.NutrientCatalog
import com.macrosaurus.catalog.NutrientDefinition
import com.macrosaurus.catalog.ResolvedFoodAmount
import com.macrosaurus.catalog.SourceKind
import com.macrosaurus.catalog.domain.FoodAmountResolver
import com.macrosaurus.catalog.persistence.JooqCatalogRepository
import com.macrosaurus.shared.InvalidOperationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
internal class CatalogService(
    private val repository: JooqCatalogRepository,
) : NutrientCatalog,
    FoodCatalog,
    FoodResolver,
    FoodCreator,
    CatalogImporter {
    override fun nutrients(): List<NutrientDefinition> = repository.nutrients()

    override fun search(
        userId: String,
        query: String,
        limit: Int,
    ): List<FoodSnapshot> = repository.search(userId, query, limit)

    override fun get(
        userId: String,
        foodId: UUID,
    ): FoodSnapshot = repository.get(userId, foodId)

    override fun byRevision(
        userId: String,
        revisionId: UUID,
    ): FoodSnapshot = repository.byRevision(userId, revisionId)

    override fun byRevisions(
        userId: String,
        revisionIds: Collection<UUID>,
    ): Map<UUID, FoodSnapshot> = repository.byRevisions(userId, revisionIds)

    override fun byBarcode(
        userId: String,
        barcode: String,
    ): List<FoodSnapshot> = repository.byBarcode(userId, barcode)

    @Transactional
    override fun create(
        userId: String,
        draft: FoodDraft,
        source: SourceKind,
        externalId: String?,
    ): FoodSnapshot = repository.create(userId, draft, source, externalId)

    @Transactional
    fun revise(
        userId: String,
        foodId: UUID,
        draft: FoodDraft,
    ): FoodSnapshot = repository.revise(userId, foodId, draft)

    override fun resolve(
        userId: String,
        revisionId: UUID,
        amount: FoodAmount,
    ): ResolvedFoodAmount = FoodAmountResolver.resolve(repository.byRevision(userId, revisionId), amount)

    @Transactional
    override fun importRelease(
        source: SourceKind,
        releaseKey: String,
        checksum: String,
        foods: List<ImportedFood>,
    ): CatalogImportResult {
        if (source !in setOf(SourceKind.MATVARETABELLEN, SourceKind.USDA_FOUNDATION, SourceKind.USDA_SR_LEGACY)) {
            throw InvalidOperationException("Only Matvaretabellen, USDA Foundation, and USDA SR Legacy releases can be bulk imported")
        }
        if (releaseKey.isBlank() || checksum.isBlank()) throw InvalidOperationException("Release key and checksum are required")
        if (foods.isEmpty()) throw InvalidOperationException("An import release must contain foods")
        if (foods.size > 100_000) throw InvalidOperationException("A single import is limited to 100,000 foods")
        if (foods.map { it.externalId }.any(String::isBlank) || foods.map { it.externalId }.toSet().size != foods.size) {
            throw InvalidOperationException("Imported foods need unique external IDs")
        }
        return repository.importRelease(source, releaseKey.trim(), checksum.trim(), foods)
    }
}
