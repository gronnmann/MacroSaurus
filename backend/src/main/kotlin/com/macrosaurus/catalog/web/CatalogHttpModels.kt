package com.macrosaurus.catalog.web

import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.FoodAmount
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodSnapshot
import com.macrosaurus.catalog.NutrientDefinition
import com.macrosaurus.catalog.PortionDraft
import com.macrosaurus.catalog.PortionSnapshot
import com.macrosaurus.catalog.SourceKind
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import com.macrosaurus.catalog.ResolvedFoodAmount as ResolvedFoodAmountContract

data class NutrientDefinitionView(
    val code: String,
    val displayName: String,
    val category: String,
    val unit: String,
    val sortOrder: Int,
)

data class PortionView(
    val id: UUID,
    val name: String,
    val quantity: BigDecimal,
    val gramWeight: BigDecimal?,
    val milliliterVolume: BigDecimal?,
    val default: Boolean,
)

data class FoodView(
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
    val portions: List<PortionView>,
    val createdAt: OffsetDateTime,
    val externalId: String?,
    val sourceRelease: String?,
)

data class PortionInput(
    @field:NotBlank val name: String,
    @field:DecimalMin("0.000001") val quantity: BigDecimal = BigDecimal.ONE,
    @field:DecimalMin("0.000001") val gramWeight: BigDecimal? = null,
    @field:DecimalMin("0.000001") val milliliterVolume: BigDecimal? = null,
    val default: Boolean = false,
)

data class CreateFoodRequest(
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
    val portions: List<@Valid PortionInput> = emptyList(),
)

data class FoodAmountRequest(
    @field:DecimalMin("0.000001") val quantity: BigDecimal,
    val unit: String,
    val portionId: UUID? = null,
)

data class ResolvedFoodAmount(
    val foodRevisionId: UUID,
    val displayName: String,
    val quantity: BigDecimal,
    val unit: String,
    val resolvedGrams: BigDecimal?,
    val nutrients: Map<String, BigDecimal>,
)

internal fun NutrientDefinition.toView() = NutrientDefinitionView(code, displayName, category, unit, sortOrder)

internal fun PortionSnapshot.toView() = PortionView(id, name, quantity, gramWeight, milliliterVolume, default)

internal fun FoodSnapshot.toView() =
    FoodView(
        id,
        revisionId,
        revision,
        name,
        brand,
        barcode,
        source,
        basisType,
        basisAmount,
        basisUnit,
        densityGPerMl,
        nutrients,
        portions.map(PortionSnapshot::toView),
        createdAt,
        externalId,
        sourceRelease,
    )

internal fun CreateFoodRequest.toDraft() =
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

internal fun FoodAmountRequest.toAmount() = FoodAmount(quantity, unit, portionId)

internal fun ResolvedFoodAmountContract.toView() = ResolvedFoodAmount(foodRevisionId, displayName, quantity, unit, resolvedGrams, nutrients.values)
