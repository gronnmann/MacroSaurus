package com.macrosaurus.acquisition.integration

import com.macrosaurus.acquisition.application.LabelDraft
import com.macrosaurus.acquisition.application.LabelExtractor
import com.macrosaurus.acquisition.application.StartLabelScanCommand
import com.macrosaurus.acquisition.config.OpenRouterProperties
import com.macrosaurus.shared.ExternalServiceException
import com.macrosaurus.shared.InvalidOperationException
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
internal class OpenRouterLabelExtractor(
    private val properties: OpenRouterProperties,
    private val mapper: ObjectMapper,
) : LabelExtractor {
    private val client = restClient(properties.baseUrl, properties.connectTimeout, properties.readTimeout)

    override fun extract(command: StartLabelScanCommand): LabelDraft {
        if (properties.apiKey.isBlank()) throw InvalidOperationException("OPENROUTER_API_KEY is not configured")
        val content =
            mutableListOf<Map<String, Any>>(
                mapOf(
                    "type" to "text",
                    "text" to
                        "Extract the product identity and nutrition label. Preserve printed values and units. " +
                        "Do not guess missing values. Locale hint: ${command.localeHint ?: "unknown"}. " +
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
                        "basisType" to mapOf("type" to listOf("string", "null"), "enum" to listOf("PER_100_G", "PER_100_ML", "PER_SERVING", null)),
                        "basisAmount" to nullableNumber(),
                        "basisUnit" to nullableString(),
                        "servingName" to nullableString(),
                        "servingMassG" to nullableNumber(),
                        "servingVolumeMl" to nullableNumber(),
                        "nutrients" to
                            mapOf(
                                "type" to "array",
                                "items" to
                                    mapOf(
                                        "type" to "object",
                                        "additionalProperties" to false,
                                        "properties" to
                                            mapOf(
                                                "code" to mapOf("type" to "string"),
                                                "amount" to mapOf("type" to "number"),
                                                "unit" to mapOf("type" to "string"),
                                                "confidence" to mapOf("type" to "number"),
                                            ),
                                        "required" to listOf("code", "amount", "unit", "confidence"),
                                    ),
                            ),
                        "ingredients" to nullableString(),
                        "allergens" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                        "warnings" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                    ),
                "required" to
                    listOf(
                        "name",
                        "brand",
                        "barcode",
                        "basisType",
                        "basisAmount",
                        "basisUnit",
                        "servingName",
                        "servingMassG",
                        "servingVolumeMl",
                        "nutrients",
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
            mapper.readValue(contentJson, LabelDraft::class.java)
        } catch (error: ExternalServiceException) {
            throw error
        } catch (error: Exception) {
            throw ExternalServiceException("Label extraction returned malformed structured content", error)
        }
    }

    private fun nullableString() = mapOf("type" to listOf("string", "null"))

    private fun nullableNumber() = mapOf("type" to listOf("number", "null"))
}
