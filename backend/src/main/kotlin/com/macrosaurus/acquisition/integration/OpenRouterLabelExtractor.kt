package com.macrosaurus.acquisition.integration

import com.macrosaurus.acquisition.application.ExtractedNutrient
import com.macrosaurus.acquisition.application.LabelDraft
import com.macrosaurus.acquisition.application.LabelExtractor
import com.macrosaurus.acquisition.application.StartLabelScanCommand
import com.macrosaurus.acquisition.config.OpenRouterProperties
import com.macrosaurus.catalog.BasisType
import com.macrosaurus.shared.ExternalServiceException
import com.macrosaurus.shared.ServiceUnavailableException
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.math.RoundingMode

internal data class RawLabelExtraction(
    val name: String?,
    val brand: String?,
    val barcode: String?,
    val per100BasisUnit: String?,
    val per100Nutrients: List<ExtractedNutrient>,
    val perServingNutrients: List<ExtractedNutrient>,
    val servingName: String?,
    val servingMassG: BigDecimal?,
    val servingVolumeMl: BigDecimal?,
    val ingredients: String?,
    val allergens: List<String>,
    val warnings: List<String>,
)

@Service
internal class OpenRouterLabelExtractor(
    private val properties: OpenRouterProperties,
    private val mapper: ObjectMapper,
) : LabelExtractor {
    private val client = restClient(properties.baseUrl, properties.connectTimeout, properties.readTimeout)

    override fun extract(command: StartLabelScanCommand): LabelDraft {
        if (properties.apiKey.isBlank()) throw ServiceUnavailableException("AI label scanning is temporarily unavailable")
        val content =
            mutableListOf<Map<String, Any>>(
                mapOf(
                    "type" to "text",
                    "text" to
                        "Extract product identity and every printed nutrition value separately for per-100 and per-serving columns. " +
                        "Use only the allowed nutrient codes and units. Convert neither values nor units and do not guess missing values. " +
                        "per100BasisUnit must be g or ml only when a per-100 column exists. Locale hint: ${command.localeHint ?: "unknown"}. " +
                        "Barcode hint: ${command.barcode ?: "none"}.",
                ),
            )
        content += mapOf("type" to "image_url", "image_url" to mapOf("url" to command.image))
        val schema =
            mapOf(
                "type" to "object",
                "additionalProperties" to false,
                "properties" to
                    mapOf(
                        "name" to nullableString(),
                        "brand" to nullableString(),
                        "barcode" to nullableString(),
                        "per100BasisUnit" to mapOf("type" to listOf("string", "null"), "enum" to listOf("g", "ml", null)),
                        "servingName" to nullableString(),
                        "servingMassG" to nullableNumber(),
                        "servingVolumeMl" to nullableNumber(),
                        "per100Nutrients" to nutrientArraySchema(),
                        "perServingNutrients" to nutrientArraySchema(),
                        "ingredients" to nullableString(),
                        "allergens" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "warnings" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                    ),
                "required" to
                    listOf(
                        "name",
                        "brand",
                        "barcode",
                        "per100BasisUnit",
                        "servingName",
                        "servingMassG",
                        "servingVolumeMl",
                        "per100Nutrients",
                        "perServingNutrients",
                        "ingredients",
                        "allergens",
                        "warnings",
                    ),
            )
        val body =
            mapOf(
                "model" to properties.model,
                "messages" to listOf(mapOf("role" to "user", "content" to content)),
                "provider" to mapOf("require_parameters" to true, "data_collection" to "deny"),
                "response_format" to
                    mapOf(
                        "type" to "json_schema",
                        "json_schema" to mapOf("name" to "nutrition_label", "strict" to true, "schema" to schema),
                    ),
                "temperature" to 0,
            )
        val response =
            try {
                client
                    .post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer ${properties.apiKey}")
                    .header("HTTP-Referer", "https://macrosaurus.app")
                    .header("X-OpenRouter-Title", "Macrosaurus")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String::class.java)
            } catch (error: Exception) {
                throw ExternalServiceException("Label extraction failed", error)
            } ?: throw ExternalServiceException("Label extraction returned no response")
        return try {
            val contentJson =
                mapper
                    .readTree(response)
                    .path("choices")
                    .path(0)
                    .path("message")
                    .path("content")
                    .asString()
            if (contentJson.isBlank()) throw ExternalServiceException("Label extraction returned no structured content")
            normalize(mapper.readValue(contentJson, RawLabelExtraction::class.java), command.barcode)
        } catch (error: ExternalServiceException) {
            throw error
        } catch (error: Exception) {
            throw ExternalServiceException("Label extraction returned malformed structured content", error)
        }
    }

    private fun nullableString() = mapOf("type" to listOf("string", "null"))

    private fun nullableNumber() = mapOf("type" to listOf("number", "null"))

    private fun nutrientArraySchema() =
        mapOf(
            "type" to "array",
            "items" to
                mapOf(
                    "type" to "object",
                    "additionalProperties" to false,
                    "properties" to
                        mapOf(
                            "code" to mapOf("type" to "string", "enum" to CANONICAL_UNITS.keys.toList()),
                            "amount" to mapOf("type" to "number", "minimum" to 0),
                            "unit" to mapOf("type" to "string", "enum" to listOf("kcal", "kJ", "g", "mg", "ug")),
                            "confidence" to mapOf("type" to "number", "minimum" to 0, "maximum" to 1),
                        ),
                    "required" to listOf("code", "amount", "unit", "confidence"),
                ),
        )

    internal fun normalize(
        raw: RawLabelExtraction,
        barcodeHint: String?,
    ): LabelDraft {
        val warnings = raw.warnings.toMutableList()
        val per100 = normalizeNutrients(raw.per100Nutrients, warnings)
        val perServing = normalizeNutrients(raw.perServingNutrients, warnings)
        val basis =
            when {
                per100.isNotEmpty() && raw.per100BasisUnit == "ml" -> {
                    Triple(BasisType.PER_100_ML, BigDecimal("100"), "ml")
                }

                per100.isNotEmpty() -> {
                    Triple(BasisType.PER_100_G, BigDecimal("100"), "g")
                }

                perServing.isNotEmpty() && raw.servingMassG != null && raw.servingMassG > BigDecimal.ZERO -> {
                    Triple(BasisType.PER_100_G, BigDecimal("100"), "g")
                }

                perServing.isNotEmpty() && raw.servingVolumeMl != null && raw.servingVolumeMl > BigDecimal.ZERO -> {
                    Triple(BasisType.PER_100_ML, BigDecimal("100"), "ml")
                }

                else -> {
                    Triple(BasisType.PER_SERVING, BigDecimal.ONE, "serving")
                }
            }
        val selected =
            when {
                per100.isNotEmpty() -> per100
                basis.first == BasisType.PER_100_G -> scaleTo100(perServing, raw.servingMassG!!)
                basis.first == BasisType.PER_100_ML -> scaleTo100(perServing, raw.servingVolumeMl!!)
                else -> perServing
            }
        if (per100.isEmpty() && basis.first != BasisType.PER_SERVING) {
            warnings += "Per-100 values were calculated from the printed serving size."
        }
        if (selected.isEmpty()) warnings += "No supported nutrition values were found."
        return LabelDraft(
            raw.name,
            raw.brand,
            raw.barcode?.filter(Char::isDigit)?.takeIf(String::isNotBlank)
                ?: barcodeHint?.filter(Char::isDigit)?.takeIf(String::isNotBlank),
            basis.first,
            basis.second,
            basis.third,
            raw.servingName?.trim()?.takeIf(String::isNotBlank)
                ?: if (raw.servingMassG != null || raw.servingVolumeMl != null) "1 serving" else null,
            raw.servingMassG,
            raw.servingVolumeMl,
            selected,
            raw.ingredients,
            raw.allergens,
            warnings.distinct(),
            per100,
            perServing,
        )
    }

    private fun normalizeNutrients(
        nutrients: List<ExtractedNutrient>,
        warnings: MutableList<String>,
    ): List<ExtractedNutrient> =
        nutrients
            .mapNotNull { nutrient ->
                if (nutrient.code == "salt_g" && nutrient.unit == "g") {
                    warnings += "Sodium was calculated from the printed salt value."
                    return@mapNotNull nutrient.copy(
                        code = "sodium_mg",
                        amount = nutrient.amount.multiply(BigDecimal("400")),
                        unit = "mg",
                        confidence = nutrient.confidence.coerceIn(BigDecimal.ZERO, BigDecimal.ONE),
                    )
                }
                val expected = CANONICAL_UNITS[nutrient.code]
                if (expected == null) {
                    warnings += "Unsupported nutrient ${nutrient.code} was ignored."
                    null
                } else {
                    val amount = convert(nutrient.amount, nutrient.unit, expected)
                    if (amount == null) {
                        warnings += "${nutrient.code} used an unsupported unit and was ignored."
                        null
                    } else {
                        nutrient.copy(amount = amount, unit = expected, confidence = nutrient.confidence.coerceIn(BigDecimal.ZERO, BigDecimal.ONE))
                    }
                }
            }.distinctBy { it.code }

    private fun convert(
        amount: BigDecimal,
        from: String,
        to: String,
    ): BigDecimal? =
        when {
            from == to -> amount
            from == "kJ" && to == "kcal" -> amount.divide(BigDecimal("4.184"), 8, RoundingMode.HALF_UP)
            from == "g" && to == "mg" -> amount.multiply(BigDecimal("1000"))
            from == "g" && to == "ug" -> amount.multiply(BigDecimal("1000000"))
            from == "mg" && to == "g" -> amount.divide(BigDecimal("1000"), 8, RoundingMode.HALF_UP)
            from == "mg" && to == "ug" -> amount.multiply(BigDecimal("1000"))
            from == "ug" && to == "mg" -> amount.divide(BigDecimal("1000"), 8, RoundingMode.HALF_UP)
            from == "ug" && to == "g" -> amount.divide(BigDecimal("1000000"), 8, RoundingMode.HALF_UP)
            else -> null
        }

    private fun scaleTo100(
        nutrients: List<ExtractedNutrient>,
        servingSize: BigDecimal,
    ): List<ExtractedNutrient> = nutrients.map { it.copy(amount = it.amount.multiply(BigDecimal("100")).divide(servingSize, 8, RoundingMode.HALF_UP)) }

    companion object {
        private val CANONICAL_UNITS =
            linkedMapOf(
                "energy_kcal" to "kcal",
                "protein_g" to "g",
                "carbohydrate_g" to "g",
                "fat_g" to "g",
                "fiber_g" to "g",
                "sugars_g" to "g",
                "saturated_fat_g" to "g",
                "trans_fat_g" to "g",
                "salt_g" to "g",
                "sodium_mg" to "mg",
                "cholesterol_mg" to "mg",
                "calcium_mg" to "mg",
                "iron_mg" to "mg",
                "potassium_mg" to "mg",
                "vitamin_c_mg" to "mg",
                "vitamin_d_ug" to "ug",
            )
    }
}
