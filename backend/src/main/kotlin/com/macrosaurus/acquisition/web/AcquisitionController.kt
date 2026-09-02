package com.macrosaurus.acquisition.web

import com.macrosaurus.acquisition.application.BarcodeCandidate
import com.macrosaurus.acquisition.application.BarcodeService
import com.macrosaurus.acquisition.application.ExtractedNutrient
import com.macrosaurus.acquisition.application.LabelDraft
import com.macrosaurus.acquisition.application.ScanJob
import com.macrosaurus.acquisition.application.ScanService
import com.macrosaurus.acquisition.application.StartLabelScanCommand
import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.PortionDraft
import com.macrosaurus.catalog.SourceKind
import com.macrosaurus.shared.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

internal data class BarcodeCandidateView(
    val barcode: String,
    val name: String,
    val brand: String?,
    val source: SourceKind,
    val basisType: BasisType,
    val nutrients: Map<String, BigDecimal>,
    val externalId: String,
)

internal data class StartLabelScanRequest(
    @field:Pattern(regexp = "^data:image/(jpeg|png|webp);base64,.+")
    @field:Size(max = 12_000_000)
    val image: String,
    val barcode: String? = null,
    val localeHint: String? = null,
)

internal data class ExtractedNutrientView(
    val code: String,
    val amount: BigDecimal,
    val unit: String,
    val confidence: BigDecimal,
)

internal data class LabelDraftView(
    val name: String?,
    val brand: String?,
    val barcode: String?,
    val basisType: BasisType?,
    val basisAmount: BigDecimal?,
    val basisUnit: String?,
    val servingName: String?,
    val servingMassG: BigDecimal?,
    val servingVolumeMl: BigDecimal?,
    val nutrients: List<ExtractedNutrientView>,
    val ingredients: String?,
    val allergens: List<String>,
    val warnings: List<String>,
    val per100Nutrients: List<ExtractedNutrientView>,
    val perServingNutrients: List<ExtractedNutrientView>,
)

internal data class ScanJobView(
    val id: UUID,
    val status: String,
    val draft: LabelDraftView?,
    val errorMessage: String?,
    val expiresAt: OffsetDateTime,
)

internal data class ConfirmPortionInput(
    @field:NotBlank val name: String,
    @field:DecimalMin("0.000001") val quantity: BigDecimal = BigDecimal.ONE,
    @field:DecimalMin("0.000001") val gramWeight: BigDecimal? = null,
    @field:DecimalMin("0.000001") val milliliterVolume: BigDecimal? = null,
    val default: Boolean = false,
)

internal data class ConfirmFoodRequest(
    @field:NotBlank val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val basisType: BasisType = BasisType.PER_100_G,
    @field:DecimalMin("0.000001") val basisAmount: BigDecimal = BigDecimal("100"),
    @field:NotBlank val basisUnit: String = "g",
    @field:DecimalMin("0.000001") val densityGPerMl: BigDecimal? = null,
    val nutrients: Map<
        String,
        @DecimalMin("0")
        BigDecimal,
    >,
    val portions: List<@Valid ConfirmPortionInput> = emptyList(),
)

private fun BarcodeCandidate.toView() = BarcodeCandidateView(barcode, name, brand, source, basisType, nutrients, externalId)

private fun ExtractedNutrient.toView() = ExtractedNutrientView(code, amount, unit, confidence)

private fun LabelDraft.toView() =
    LabelDraftView(
        name,
        brand,
        barcode,
        basisType,
        basisAmount,
        basisUnit,
        servingName,
        servingMassG,
        servingVolumeMl,
        nutrients.map { it.toView() },
        ingredients,
        allergens,
        warnings,
        per100Nutrients.map { it.toView() },
        perServingNutrients.map { it.toView() },
    )

private fun ScanJob.toView() = ScanJobView(id, status, draft?.toView(), errorMessage, expiresAt)

private fun ConfirmFoodRequest.toDraft() =
    FoodDraft(
        name,
        brand,
        barcode,
        basisType,
        basisAmount,
        basisUnit,
        densityGPerMl,
        nutrients,
        portions.map { PortionDraft(it.name, it.quantity, it.gramWeight, it.milliliterVolume, it.default) },
    )

@RestController
@RequestMapping("/api/v1")
internal class AcquisitionController(
    private val users: CurrentUser,
    private val barcodes: BarcodeService,
    private val scans: ScanService,
) {
    @GetMapping("/barcodes/{code}")
    fun barcode(
        @PathVariable code: String,
    ) = barcodes.find(users.userId(), code).map { it.toView() }

    @PostMapping("/barcodes/{code}/import")
    fun importBarcode(
        @PathVariable code: String,
    ) = barcodes.import(users.userId(), code)

    @PostMapping("/food-scans")
    fun scan(
        @Valid @RequestBody request: StartLabelScanRequest,
    ) = scans.start(users.userId(), StartLabelScanCommand(request.image, request.barcode, request.localeHint)).toView()

    @GetMapping("/food-scans/{id}")
    fun scan(
        @PathVariable id: UUID,
    ) = scans.get(users.userId(), id).toView()

    @PostMapping("/food-scans/{id}/confirm")
    fun confirm(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ConfirmFoodRequest,
    ) = scans.confirm(users.userId(), id, request.toDraft())
}
