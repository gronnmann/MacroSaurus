package com.macrosaurus.acquisition.application

import com.macrosaurus.acquisition.domain.Barcode
import com.macrosaurus.acquisition.persistence.JooqScanJobRepository
import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.FoodCatalog
import com.macrosaurus.catalog.FoodCreator
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodSnapshot
import com.macrosaurus.identity.UserFeature
import com.macrosaurus.identity.UserFeatureReader
import com.macrosaurus.shared.ForbiddenException
import com.macrosaurus.shared.InvalidOperationException
import com.macrosaurus.shared.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

@Service
internal class BarcodeService(
    private val catalog: FoodCatalog,
    private val foodCreator: FoodCreator,
    private val foodFacts: FoodFactsLookup,
) {
    fun find(
        userId: String,
        rawBarcode: String,
    ): List<BarcodeCandidate> {
        val barcode = Barcode.normalizeAndValidate(rawBarcode)
        val local =
            catalog.byBarcode(userId, barcode).map {
                BarcodeCandidate(barcode, it.name, it.brand, it.source, it.basisType, it.nutrients, it.id.toString())
            }
        return if (local.isNotEmpty()) local else listOfNotNull(foodFacts.find(barcode))
    }

    fun import(
        userId: String,
        rawBarcode: String,
    ): FoodSnapshot {
        val barcode = Barcode.normalizeAndValidate(rawBarcode)
        catalog.byBarcode(userId, barcode).firstOrNull()?.let { return it }
        val candidate = foodFacts.find(barcode) ?: throw NotFoundException("Barcode was not found")
        return foodCreator.create(
            userId,
            FoodDraft(
                name = candidate.name,
                brand = candidate.brand,
                barcode = barcode,
                basisType = candidate.basisType,
                basisAmount = BigDecimal("100"),
                basisUnit = if (candidate.basisType == BasisType.PER_100_ML) "ml" else "g",
                nutrients = candidate.nutrients,
            ),
            candidate.source,
            candidate.externalId,
        )
    }
}

@Service
internal class ScanService(
    private val repository: JooqScanJobRepository,
    private val extractor: LabelExtractor,
    private val foodCreator: FoodCreator,
    private val features: UserFeatureReader,
    private val clock: Clock,
) {
    fun start(
        userId: String,
        command: StartLabelScanCommand,
    ): ScanJob {
        requireAccess(userId)
        val id = UUID.randomUUID()
        val expires = OffsetDateTime.now(clock).plusHours(24)
        repository.insertProcessing(id, userId, expires)
        return try {
            val draft = extractor.extract(command)
            repository.markReview(id, draft)
            ScanJob(id, "REVIEW", draft, null, expires)
        } catch (error: RuntimeException) {
            repository.markFailed(id, error.message?.take(500))
            throw error
        }
    }

    fun get(
        userId: String,
        id: UUID,
    ): ScanJob {
        requireAccess(userId)
        return repository.find(userId, id) ?: throw NotFoundException("Scan was not found")
    }

    @Transactional
    fun confirm(
        userId: String,
        id: UUID,
        draft: FoodDraft,
    ): FoodSnapshot {
        requireAccess(userId)
        val job = get(userId, id)
        if (job.status != "REVIEW") throw InvalidOperationException("Only scans awaiting review can be confirmed")
        val food = foodCreator.create(userId, draft)
        repository.markConfirmed(userId, id)
        return food
    }

    private fun requireAccess(userId: String) {
        if (!features.enabled(userId, UserFeature.AI_LABEL_SCAN)) {
            throw ForbiddenException("AI label scanning is not enabled for this user")
        }
    }
}
