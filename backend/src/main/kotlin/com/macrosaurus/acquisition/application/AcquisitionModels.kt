package com.macrosaurus.acquisition.application

import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.SourceKind
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

internal data class BarcodeCandidate(
    val barcode: String,
    val name: String,
    val brand: String?,
    val source: SourceKind,
    val basisType: BasisType,
    val nutrients: Map<String, BigDecimal>,
    val externalId: String,
)

internal data class StartLabelScanCommand(
    val image: String,
    val barcode: String?,
    val localeHint: String?,
)

internal data class ExtractedNutrient(
    val code: String,
    val amount: BigDecimal,
    val unit: String,
    val confidence: BigDecimal,
)

internal data class LabelDraft(
    val name: String?,
    val brand: String?,
    val barcode: String?,
    val basisType: BasisType?,
    val basisAmount: BigDecimal?,
    val basisUnit: String?,
    val servingName: String?,
    val servingMassG: BigDecimal?,
    val servingVolumeMl: BigDecimal?,
    val nutrients: List<ExtractedNutrient>,
    val ingredients: String?,
    val allergens: List<String>,
    val warnings: List<String>,
    val per100Nutrients: List<ExtractedNutrient> = emptyList(),
    val perServingNutrients: List<ExtractedNutrient> = emptyList(),
)

internal data class ScanJob(
    val id: UUID,
    val status: String,
    val draft: LabelDraft?,
    val errorMessage: String?,
    val expiresAt: OffsetDateTime,
)

internal fun interface FoodFactsLookup {
    fun find(barcode: String): BarcodeCandidate?
}

internal fun interface LabelExtractor {
    fun extract(command: StartLabelScanCommand): LabelDraft
}
