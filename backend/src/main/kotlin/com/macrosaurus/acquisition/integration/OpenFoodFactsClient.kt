package com.macrosaurus.acquisition.integration

import com.macrosaurus.acquisition.application.BarcodeCandidate
import com.macrosaurus.acquisition.application.FoodFactsLookup
import com.macrosaurus.acquisition.config.OpenFoodFactsProperties
import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.SourceKind
import com.macrosaurus.shared.ExternalServiceException
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal

@Service
internal class OpenFoodFactsClient(
    properties: OpenFoodFactsProperties,
    private val mapper: ObjectMapper,
) : FoodFactsLookup {
    private val userAgent = properties.userAgent
    private val client = restClient(properties.baseUrl, properties.connectTimeout, properties.readTimeout)

    override fun find(barcode: String): BarcodeCandidate? =
        try {
            val payload =
                client
                    .get()
                    .uri("/api/v3/product/{barcode}.json?fields=code,product_name,brands,nutriments", barcode)
                    .header("User-Agent", userAgent)
                    .retrieve()
                    .body(String::class.java) ?: return null
            parseOpenFoodFactsCandidate(barcode, payload, mapper)
        } catch (error: Exception) {
            throw ExternalServiceException("Open Food Facts lookup failed", error)
        }
}

internal fun parseOpenFoodFactsCandidate(
    barcode: String,
    payload: String,
    mapper: ObjectMapper,
): BarcodeCandidate? {
    val root = mapper.readTree(payload)
    val product = root.path("product").takeIf(JsonNode::isObject) ?: return null
    val name = product.path("product_name").asString("").ifBlank { return null }
    val nutriments = product.path("nutriments")

    fun decimal(key: String): BigDecimal? =
        nutriments
            .path(key)
            .takeUnless(JsonNode::isMissingNode)
            ?.takeUnless(JsonNode::isNull)
            ?.decimalValue()
    val nutrients =
        linkedMapOf<String, BigDecimal>().apply {
            decimal("energy-kcal_100g")?.let { put("energy_kcal", it) }
            decimal("proteins_100g")?.let { put("protein_g", it) }
            decimal("carbohydrates_100g")?.let { put("carbohydrate_g", it) }
            decimal("fat_100g")?.let { put("fat_g", it) }
            decimal("fiber_100g")?.let { put("fiber_g", it) }
            decimal("sugars_100g")?.let { put("sugars_g", it) }
            decimal("saturated-fat_100g")?.let { put("saturated_fat_g", it) }
            decimal("sodium_100g")?.multiply(BigDecimal("1000"))?.let { put("sodium_mg", it) }
        }
    return BarcodeCandidate(
        barcode,
        name,
        product.path("brands").asString("").ifBlank { null },
        SourceKind.OPEN_FOOD_FACTS,
        BasisType.PER_100_G,
        nutrients,
        barcode,
    )
}
