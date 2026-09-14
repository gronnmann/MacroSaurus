package com.macrosaurus.acquisition.integration

import com.macrosaurus.acquisition.application.BarcodeCandidate
import com.macrosaurus.acquisition.application.FoodFactsLookup
import com.macrosaurus.acquisition.config.OpenFoodFactsProperties
import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.PortionDraft
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
                    .uri("/api/v3/product/{barcode}.json?fields=code,product_name,brands,nutriments,serving_size,serving_quantity,serving_quantity_unit", barcode)
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
    val portion = parseServing(product)
    return BarcodeCandidate(
        barcode,
        name,
        product.path("brands").asString("").ifBlank { null },
        SourceKind.OPEN_FOOD_FACTS,
        if (portion?.milliliterVolume != null) BasisType.PER_100_ML else BasisType.PER_100_G,
        nutrients,
        barcode,
        listOfNotNull(portion),
    )
}

private fun parseServing(product: JsonNode): PortionDraft? {
    val label = product.path("serving_size").asString("").trim()
    // Only use an explicit mass or volume; a label such as "1 slice" is not a weight.
    val measures =
        Regex("""(?<![\d.,+-])(\d+(?:[.,]\d+)?)\s*(kg|mg|g|ml|cl|dl|l)\b""", RegexOption.IGNORE_CASE)
            .findAll(label)
            .toList()
    val normalized =
        product
            .path("serving_quantity")
            .asString("")
            .replace(',', '.')
            .toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO }
    val normalizedUnit = product.path("serving_quantity_unit").asString("").lowercase()
    val measure = measures.singleOrNull()
    val structured = normalized != null && normalizedUnit in listOf("g", "ml")
    val unit = if (structured) normalizedUnit else measure?.groupValues?.get(2)?.lowercase() ?: return null
    val quantity =
        if (structured) {
            requireNotNull(normalized)
        } else {
            measure
                ?.groupValues
                ?.get(1)
                ?.replace(',', '.')
                ?.toBigDecimalOrNull() ?: return null
        }
    val equivalent =
        quantity
            .multiply(
                when (unit) {
                    "kg", "l" -> BigDecimal("1000")
                    "mg" -> BigDecimal("0.001")
                    "cl" -> BigDecimal("10")
                    "dl" -> BigDecimal("100")
                    else -> BigDecimal.ONE
                },
            ).takeIf { it > BigDecimal.ZERO } ?: return null
    val volume = unit in listOf("ml", "cl", "dl", "l")
    return PortionDraft(
        name = label.ifBlank { "Serving ($equivalent $unit)" },
        gramWeight = equivalent.takeUnless { volume },
        milliliterVolume = equivalent.takeIf { volume },
        default = true,
    )
}
