package com.macrosaurus.identity.web

import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.CatalogImporter
import com.macrosaurus.catalog.ImportedFood
import com.macrosaurus.catalog.PortionDraft
import com.macrosaurus.catalog.SourceKind
import com.macrosaurus.identity.UserFeature
import com.macrosaurus.identity.application.FeatureGrantService
import com.macrosaurus.shared.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

data class AiLabelScanFeatureView(
    val granted: Boolean,
    val available: Boolean,
)

data class MyFeaturesView(
    val isAdmin: Boolean,
    val aiLabelScan: AiLabelScanFeatureView,
)

data class AdminUserView(
    val userId: String,
    val displayName: String,
    val aiLabelScanEnabled: Boolean,
)

data class SetFeatureRequest(
    val enabled: Boolean,
)

data class ImportedPortionRequest(
    @field:NotBlank val name: String,
    @field:DecimalMin("0.000001") val gramWeight: BigDecimal? = null,
    @field:DecimalMin("0.000001") val milliliterVolume: BigDecimal? = null,
    val default: Boolean = false,
)

data class ImportedFoodRequest(
    @field:NotBlank val externalId: String,
    @field:NotBlank val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val locale: String? = null,
    val aliases: Map<String, String> = emptyMap(),
    val basisType: BasisType = BasisType.PER_100_G,
    @field:DecimalMin("0.000001") val basisAmount: BigDecimal = BigDecimal("100"),
    @field:NotBlank val basisUnit: String = "g",
    @field:DecimalMin("0.000001") val densityGPerMl: BigDecimal? = null,
    val nutrients: Map<String, BigDecimal>,
    val portions: List<@Valid ImportedPortionRequest> = emptyList(),
)

data class CatalogImportRequest(
    val source: SourceKind,
    @field:NotBlank val releaseKey: String,
    @field:NotBlank val checksum: String,
    val foods: List<@Valid ImportedFoodRequest>,
)

@RestController
@RequestMapping("/api/v1")
internal class FeatureController(
    private val users: CurrentUser,
    private val features: FeatureGrantService,
    private val catalogImporter: CatalogImporter,
    @param:Value("\${macrosaurus.open-router.api-key:}") private val openRouterApiKey: String,
) {
    @GetMapping("/me/features")
    fun mine(): MyFeaturesView {
        val userId = users.userId()
        return MyFeaturesView(
            features.isAdmin(userId),
            AiLabelScanFeatureView(features.enabled(userId, UserFeature.AI_LABEL_SCAN), openRouterApiKey.isNotBlank()),
        )
    }

    @GetMapping("/admin/users")
    fun adminUsers(
        @RequestParam(defaultValue = "") query: String,
    ) = features.users(users.userId(), query).map { AdminUserView(it.profile.userId, it.profile.displayName, it.aiLabelScanEnabled) }

    @PutMapping("/admin/users/{userId}/features/ai-label-scan")
    fun setAiLabelScan(
        @PathVariable userId: String,
        @Valid @RequestBody request: SetFeatureRequest,
    ): AdminUserView {
        features.set(users.userId(), userId, UserFeature.AI_LABEL_SCAN, request.enabled)
        val profile = features.users(users.userId(), userId).first { it.profile.userId == userId }.profile
        return AdminUserView(profile.userId, profile.displayName, request.enabled)
    }

    @PostMapping("/admin/catalog-imports")
    fun importCatalog(
        @Valid @RequestBody request: CatalogImportRequest,
    ) = features.assertAdmin(users.userId()).let {
        catalogImporter.importRelease(
            request.source,
            request.releaseKey,
            request.checksum,
            request.foods.map { food ->
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
                    food.portions.map { PortionDraft(it.name, BigDecimal.ONE, it.gramWeight, it.milliliterVolume, it.default) },
                )
            },
        )
    }
}
