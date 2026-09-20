package com.macrosaurus.acquisition.integration

import com.macrosaurus.acquisition.application.aiStage
import com.macrosaurus.acquisition.config.OpenRouterProperties
import com.macrosaurus.acquisition.config.ReasoningEffort
import com.macrosaurus.shared.ExternalServiceException
import com.macrosaurus.shared.ServiceUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper
import java.net.SocketTimeoutException

internal enum class AiOperation(
    val schemaName: String,
) {
    MEAL_ESTIMATE("meal_estimate"),
    NUTRITION_LABEL("nutrition_label"),
}

@Service
internal class OpenRouterClient(
    private val properties: OpenRouterProperties,
    private val mapper: ObjectMapper,
) {
    private val client = restClient(properties.baseUrl, properties.connectTimeout, properties.readTimeout)
    private val logger = LoggerFactory.getLogger(javaClass)

    fun complete(
        operation: AiOperation,
        schema: Map<String, Any>,
        content: List<Map<String, Any>>,
    ): String =
        aiStage(operation.schemaName, "provider", properties.model) {
            val name = operation.schemaName
            val maxTokens =
                when (operation) {
                    AiOperation.MEAL_ESTIMATE -> properties.mealMaxTokens
                    AiOperation.NUTRITION_LABEL -> properties.labelMaxTokens
                }
            if (properties.apiKey.isBlank()) throw ServiceUnavailableException("AI is temporarily unavailable. Please try again later.")
            val body =
                mutableMapOf<String, Any>(
                    "model" to properties.model,
                    "messages" to listOf(mapOf("role" to "user", "content" to content)),
                    "provider" to mapOf("require_parameters" to true, "data_collection" to "deny"),
                    "response_format" to
                        mapOf(
                            "type" to "json_schema",
                            "json_schema" to mapOf("name" to name, "strict" to true, "schema" to schema),
                        ),
                    "temperature" to 0,
                    "max_tokens" to maxTokens,
                )
            if (properties.reasoningEffort != ReasoningEffort.DEFAULT) {
                body["reasoning"] = mapOf("effort" to properties.reasoningEffort.name.lowercase(), "exclude" to true)
            }
            logger.info(
                "ai_request operation={} image_count={} text_chars={} response_format=json_schema strict=true max_tokens={} reasoning_effort={}",
                name,
                content.count { it["type"] == "image_url" },
                content.sumOf { (it["text"] as? String)?.length ?: 0 },
                maxTokens,
                properties.reasoningEffort.name.lowercase(),
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
                        .toEntity(String::class.java)
                } catch (error: RestClientResponseException) {
                    // Never log the request, photos, API key, or provider response body.
                    logger.warn("ai_response operation={} provider_status={}", name, error.statusCode.value())
                    throw ExternalServiceException(providerError(error.statusCode.value()), failureCategory = "http_rejection")
                } catch (error: RestClientException) {
                    val timedOut = generateSequence<Throwable>(error) { it.cause }.take(8).any { it is SocketTimeoutException }
                    throw ExternalServiceException("AI could not be reached or took too long. Please try again.", failureCategory = if (timedOut) "timeout" else "connection_failure")
                }
            logger.info("ai_response operation={} provider_status={}", name, response.statusCode.value())
            aiStage(name, "response_validation") {
                parseContent(response.body ?: throw ExternalServiceException("AI returned no response. Please try again.", failureCategory = "empty_response"))
            }
        }

    internal fun parseContent(response: String): String =
        try {
            val root = mapper.readTree(response)
            val choice = root.path("choices").path(0)
            val usage = root.path("usage")
            // Log only allowlisted metadata, never model text or reasoning content.
            logger.info(
                "ai_usage generation_id={} model={} provider={} finish_reason={} prompt_tokens={} completion_tokens={} reasoning_tokens={} content_chars={}",
                metadata(root.path("id").asString("")),
                metadata(root.path("model").asString("")),
                metadata(root.path("provider").asString("")),
                choice.path("finish_reason").asString("").takeIf { it in setOf("stop", "length", "content_filter", "tool_calls", "error", "function_call") } ?: "unknown",
                usage.path("prompt_tokens").asLong(-1).takeIf { it >= 0 },
                usage.path("completion_tokens").asLong(-1).takeIf { it >= 0 },
                usage
                    .path("completion_tokens_details")
                    .path("reasoning_tokens")
                    .asLong(-1)
                    .takeIf { it >= 0 },
                choice
                    .path("message")
                    .path("content")
                    .asString("")
                    .length,
            )
            if (root.hasNonNull("error")) {
                val status = root.path("error").path("code").asInt(502)
                logger.warn("ai_provider_error provider_status={}", status)
                throw ExternalServiceException(providerError(status), failureCategory = "provider_error")
            }
            if (choice.path("finish_reason").asString("") == "length") {
                throw ExternalServiceException("AI returned an incomplete result within the response limit. Try a simpler description or clearer photo; if this persists, contact the administrator.", failureCategory = "incomplete_response")
            }
            val message = choice.path("message")
            if (message.hasNonNull("refusal")) throw ExternalServiceException("AI could not read this input. Please try another photo or description.", failureCategory = "refused_response")
            val content = message.path("content").asString("").trim()
            if (content.isBlank()) throw ExternalServiceException("AI returned no result. Please try another photo or description.", failureCategory = "empty_response")
            // Some providers wrap otherwise valid structured JSON in a Markdown fence.
            if (content.startsWith("```") && content.endsWith("```")) {
                content.substringAfter('\n').removeSuffix("```").trim()
            } else {
                content
            }
        } catch (error: ExternalServiceException) {
            throw error
        } catch (error: Exception) {
            throw ExternalServiceException("AI returned an unreadable response. Please try again.", failureCategory = "malformed_response")
        }

    private fun metadata(value: String) = value.takeIf { it.matches(Regex("[A-Za-z0-9_./: -]{1,160}")) } ?: "unknown"

    private fun providerError(status: Int) =
        when (status) {
            401, 403 -> "AI access is not configured correctly. Please contact the administrator."
            402 -> "AI credits are unavailable. Please contact the administrator."
            404 -> "The configured AI model is unavailable. Please contact the administrator."
            429 -> "AI is busy. Please wait a moment and try again."
            else -> "AI could not process this request. Please try again; if it persists, contact the administrator."
        }
}
