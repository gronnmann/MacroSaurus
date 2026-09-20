package com.macrosaurus.catalog.application

import com.macrosaurus.catalog.FoodAmount
import com.macrosaurus.catalog.FoodCatalog
import com.macrosaurus.catalog.FoodCreator
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodResolver
import com.macrosaurus.catalog.FoodSnapshot
import com.macrosaurus.catalog.NutrientCatalog
import com.macrosaurus.catalog.NutrientDefinition
import com.macrosaurus.catalog.ResolvedFoodAmount
import com.macrosaurus.catalog.SourceKind
import com.macrosaurus.catalog.domain.FoodAmountResolver
import com.macrosaurus.catalog.persistence.JooqCatalogRepository
import com.macrosaurus.shared.SearchHit
import com.macrosaurus.shared.SearchStage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
internal class CatalogService(
    private val repository: JooqCatalogRepository,
) : NutrientCatalog,
    FoodCatalog,
    FoodResolver,
    FoodCreator {
    override fun nutrients(): List<NutrientDefinition> = repository.nutrients()

    override fun search(
        userId: String,
        query: String,
        limit: Int,
    ): List<FoodSnapshot> {
        if (query.isBlank()) return repository.browse(userId, limit)
        for (stage in SearchStage.entries) {
            val hits = searchHits(userId, query, stage, limit)
            if (hits.isNotEmpty()) {
                val foods = byRevisions(userId, hits.map { it.revisionId })
                return hits.map { foods.getValue(it.revisionId) }
            }
        }
        return emptyList()
    }

    override fun searchHits(
        userId: String,
        query: String,
        stage: SearchStage,
        limit: Int,
        includeIds: Collection<UUID>,
    ): List<SearchHit> = repository.searchHits(userId, query, stage, limit, includeIds)

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
}
