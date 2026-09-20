package com.macrosaurus.acquisition

import com.macrosaurus.acquisition.application.MealEstimateCommand
import com.macrosaurus.acquisition.application.MealEstimateService
import com.macrosaurus.acquisition.application.MealEstimator
import com.macrosaurus.acquisition.application.StartLabelScanCommand
import com.macrosaurus.acquisition.config.OpenRouterProperties
import com.macrosaurus.acquisition.config.ReasoningEffort
import com.macrosaurus.acquisition.integration.AiOperation
import com.macrosaurus.acquisition.integration.OpenRouterClient
import com.macrosaurus.acquisition.integration.OpenRouterLabelExtractor
import com.macrosaurus.acquisition.integration.OpenRouterMealEstimator
import com.macrosaurus.identity.UserFeatureReader
import com.macrosaurus.shared.ExternalServiceException
import com.macrosaurus.shared.ForbiddenException
import com.macrosaurus.shared.InvalidOperationException
import com.macrosaurus.shared.ServiceUnavailableException
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.net.InetSocketAddress

class AiAcquisitionTest {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val client = OpenRouterClient(OpenRouterProperties("http://127.0.0.1:${server.address.port}", "test-key", "test-model"), mapper)
    private var request = ""
    private var requestCount = 0

    @AfterEach
    fun stop() = server.stop(0)

    private fun respond(
        content: String,
        status: Int = 200,
    ) {
        server.createContext("/chat/completions") { exchange ->
            requestCount++
            request = exchange.requestBody.bufferedReader().readText()
            val bytes = content.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    private fun completion(json: String) = mapper.writeValueAsString(mapOf("choices" to listOf(mapOf("finish_reason" to "stop", "message" to mapOf("content" to json)))))

    @Test
    fun `label request sends image and parses complete provider response`() {
        respond(completion("""{"name":"Milk","brand":null,"barcode":null,"per100BasisUnit":"ml","per100Nutrients":[{"code":"energy_kcal","amount":43,"unit":"kcal","confidence":0.9}],"perServingNutrients":[],"servingName":null,"servingMassG":null,"servingVolumeMl":null,"ingredients":null,"allergens":[],"warnings":[]}"""))
        val draft = OpenRouterLabelExtractor(client, mapper).extract(StartLabelScanCommand("data:image/jpeg;base64,YQ==", null, "en"))
        assertThat(draft.name).isEqualTo("Milk")
        assertThat(draft.nutrients.single().amount).isEqualByComparingTo("43")
        val body = mapper.readTree(request)
        assertThat(body.path("max_tokens").asInt()).isEqualTo(4096)
        assertThat(body.path("reasoning").path("effort").asString()).isEqualTo("none")
        assertThat(body.path("reasoning").path("exclude").asBoolean()).isTrue()
        assertThat(
            body
                .path("messages")
                .path(0)
                .path("content")
                .path(1)
                .path("image_url")
                .path("url")
                .asString(),
        ).isEqualTo("data:image/jpeg;base64,YQ==")
        assertThat(
            body
                .path("response_format")
                .path("json_schema")
                .path("strict")
                .asBoolean(),
        ).isTrue()
        assertThat(body.path("provider").path("data_collection").asString()).isEqualTo("deny")
    }

    @Test
    fun `meal estimate combines all photos and description and returns totals`() {
        respond(completion("""{"name":"Chicken and rice","calories":650,"proteinG":45,"carbohydrateG":70,"fatG":20,"fiberG":3,"assumptions":["Includes one tablespoon of oil"]}"""))
        val result = OpenRouterMealEstimator(client, mapper).estimate(MealEstimateCommand("200 g chicken, rice, 1 tbsp oil", listOf("data:image/jpeg;base64,YQ==", "data:image/png;base64,Yg=="), "en"))
        assertThat(result.calories).isEqualByComparingTo("650")
        val body = mapper.readTree(request)
        assertThat(body.path("max_tokens").asInt()).isEqualTo(1024)
        assertThat(body.path("reasoning").path("effort").asString()).isEqualTo("none")
        val schema = body.path("response_format").path("json_schema")
        assertThat(schema.path("strict").asBoolean()).isTrue()
        assertThat(
            schema
                .path("schema")
                .path("properties")
                .path("assumptions")
                .path("maxItems")
                .asInt(),
        ).isEqualTo(4)
        assertThat(body.path("provider").path("require_parameters").asBoolean()).isTrue()
        assertThat(result.assumptions).containsExactly("Includes one tablespoon of oil")
        val content =
            mapper
                .readTree(request)
                .path("messages")
                .path(0)
                .path("content")
        assertThat(content.size()).isEqualTo(3)
        assertThat(content.path(0).path("text").asString()).contains("200 g chicken, rice, 1 tbsp oil")
        assertThat(
            content
                .path(2)
                .path("image_url")
                .path("url")
                .asString(),
        ).isEqualTo("data:image/png;base64,Yg==")
    }

    @Test
    fun `HTTP provider errors explain configuration issues without exposing upstream body`() {
        respond("""{"error":{"message":"sensitive upstream details"}}""", 402)
        assertThatThrownBy { client.complete(AiOperation.MEAL_ESTIMATE, emptyMap(), emptyList()) }.isInstanceOf(ExternalServiceException::class.java).hasMessageContaining("credits").hasMessageNotContaining("sensitive")
    }

    @Test
    fun `handles error envelopes and fenced JSON and rejects incomplete results`() {
        assertThat(client.parseContent(completion("```json\n{\"name\":\"Meal\"}\n```"))).isEqualTo("""{"name":"Meal"}""")
        assertThatThrownBy { client.parseContent("""{"error":{"code":429}}""") }.hasMessageContaining("busy")
        assertThatThrownBy { client.parseContent("""{"choices":[{"finish_reason":"length","message":{"content":"{}"}}]}""") }.hasMessageContaining("incomplete")
        assertThatThrownBy { client.parseContent("""{"choices":[{"message":{"content":null}}]}""") }.hasMessageContaining("no result")
    }

    @Test
    fun `missing API key returns service unavailable`() {
        val unconfigured = OpenRouterClient(OpenRouterProperties("https://example.invalid", "", "test"), mapper)
        assertThatThrownBy { unconfigured.complete(AiOperation.MEAL_ESTIMATE, emptyMap(), emptyList()) }.isInstanceOf(ServiceUnavailableException::class.java)
    }

    @Test
    fun `budgets and reasoning are configurable independently of model identity`() {
        respond(completion("{}"))
        for (effort in ReasoningEffort.entries) {
            val properties = OpenRouterProperties("http://127.0.0.1:${server.address.port}", "test-key", "arbitrary/provider-model", mealMaxTokens = 768, labelMaxTokens = 3072, reasoningEffort = effort)
            val configured = OpenRouterClient(properties, mapper)
            for ((operation, expectedBudget) in listOf(AiOperation.MEAL_ESTIMATE to 768, AiOperation.NUTRITION_LABEL to 3072)) {
                configured.complete(operation, emptyMap(), emptyList())
                val body = mapper.readTree(request)
                assertThat(body.path("model").asString()).isEqualTo("arbitrary/provider-model")
                assertThat(body.path("max_tokens").asInt()).isEqualTo(expectedBudget)
                if (effort == ReasoningEffort.DEFAULT) {
                    assertThat(body.has("reasoning")).isFalse()
                } else {
                    assertThat(body.path("reasoning").path("effort").asString()).isEqualTo(effort.name.lowercase())
                    assertThat(body.path("reasoning").path("exclude").asBoolean()).isTrue()
                }
            }
        }
    }

    @Test
    fun `token-limited responses are rejected without another paid request even with valid JSON`() {
        respond("""{"choices":[{"finish_reason":"length","message":{"content":"{}"}}],"usage":{"completion_tokens":1024}}""")
        assertThatThrownBy { client.complete(AiOperation.MEAL_ESTIMATE, emptyMap(), emptyList()) }
            .isInstanceOf(ExternalServiceException::class.java)
            .hasMessageContaining("response limit")
        assertThat(requestCount).isEqualTo(1)
    }

    @Test
    fun `provider rejection does not trigger an unrestricted fallback`() {
        respond("""{"error":{"message":"unsupported reasoning setting"}}""", 400)
        assertThatThrownBy { client.complete(AiOperation.MEAL_ESTIMATE, emptyMap(), emptyList()) }.isInstanceOf(ExternalServiceException::class.java)
        assertThat(requestCount).isEqualTo(1)
        assertThat(mapper.readTree(request).path("max_tokens").asInt()).isEqualTo(1024)
    }

    @Test
    fun `overlong assumptions are rejected even if a provider ignores schema bounds`() {
        respond(completion("""{"name":"Meal","calories":100,"proteinG":1,"carbohydrateG":1,"fatG":1,"fiberG":null,"assumptions":["1","2","3","4","5"]}"""))
        assertThatThrownBy { OpenRouterMealEstimator(client, mapper).estimate(MealEstimateCommand("Meal", emptyList(), null)) }.hasMessageContaining("overly long")
    }

    @Test
    fun `invalid token budgets fail before a provider request`() {
        assertThatThrownBy { OpenRouterProperties("https://example.invalid", "", "any", mealMaxTokens = 0) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { OpenRouterProperties("https://example.invalid", "", "any", labelMaxTokens = 100000) }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(requestCount).isZero()
    }

    @Test
    fun `access and empty input are checked before paid inference`() {
        val estimator = MealEstimator { error("Must not call AI") }
        val command = MealEstimateCommand("", emptyList(), null)
        assertThatThrownBy { MealEstimateService(UserFeatureReader { _, _ -> false }, estimator).estimate("user", command) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { MealEstimateService(UserFeatureReader { _, _ -> true }, estimator).estimate("user", command) }.isInstanceOf(InvalidOperationException::class.java)
    }
}
