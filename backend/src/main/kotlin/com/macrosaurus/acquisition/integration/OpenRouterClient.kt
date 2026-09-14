package com.macrosaurus.acquisition.integration

import com.macrosaurus.acquisition.config.OpenRouterProperties
import com.macrosaurus.shared.ExternalServiceException
import com.macrosaurus.shared.ServiceUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper

@Service
internal class OpenRouterClient(
    private val properties: OpenRouterProperties,
    private val mapper: ObjectMapper,
) {
    private val client = restClient(properties.baseUrl, properties.connectTimeout, properties.readTimeout)
    private val logger = LoggerFactory.getLogger(javaClass)

    fun complete(
        name: String,
        schema: Map<String, Any>,
        content: List<Map<String, Any>>,
    ): String {
        if (properties.apiKey.isBlank()) throw ServiceUnavailableException("AI is temporarily unavailable. Please try again later.")
        val body =
            mapOf(
                "model" to properties.model,
                "messages" to listOf(mapOf("role" to "user", "content" to content)),
                "provider" to mapOf("require_parameters" to true, "data_collection" to "deny"),
                "response_format" to
                    mapOf(
                        "type" to "json_schema",
                        "json_schema" to mapOf("name" to name, "strict" to true, "schema" to schema),
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
                    .body(mapper.writeValueAsString(body))
                    .retrieve()
                    .body(String::class.java)
            } catch (error: RestClientResponseException) {
                // Never log the request, photos, API key, or provider response body.
                logger.warn("AI request {} failed for model {} with HTTP {}", name, properties.model, error.statusCode.value())
                throw ExternalServiceException(providerError(error.statusCode.value()))
            } catch (error: ResourceAccessException) {
                throw ExternalServiceException("AI could not be reached or took too long. Please try again.")
            }
        return parseContent(response ?: throw ExternalServiceException("AI returned no response. Please try again."))
    }

    internal fun parseContent(response: String): String =
        try {
            val root = mapper.readTree(response)
            if (root.hasNonNull("error")) {
                throw ExternalServiceException(providerError(root.path("error").path("code").asInt(502)))
            }
            val choice = root.path("choices").path(0)
            if (choice.path("finish_reason").asString("") == "length") {
                throw ExternalServiceException("AI returned an incomplete result. Please try again with a clearer photo or shorter description.")
            }
            val message = choice.path("message")
            if (message.hasNonNull("refusal")) throw ExternalServiceException("AI could not read this input. Please try another photo or description.")
            val content = message.path("content").asString("").trim()
            if (content.isBlank()) throw ExternalServiceException("AI returned no result. Please try another photo or description.")
            // Some providers wrap otherwise valid structured JSON in a Markdown fence.
            if (content.startsWith("```") && content.endsWith("```")) {
                content.substringAfter('\n').removeSuffix("```").trim()
            } else {
                content
            }
        } catch (error: ExternalServiceException) {
            throw error
        } catch (error: Exception) {
            throw ExternalServiceException("AI returned an unreadable response. Please try again.")
        }

    private fun providerError(status: Int) =
        when (status) {
            401, 403 -> "AI access is not configured correctly. Please contact the administrator."
            402 -> "AI credits are unavailable. Please contact the administrator."
            404 -> "The configured AI model is unavailable. Please contact the administrator."
            429 -> "AI is busy. Please wait a moment and try again."
            else -> "AI could not process this request. Please try again; if it persists, contact the administrator."
        }
}
