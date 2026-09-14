package com.macrosaurus.acquisition

import com.macrosaurus.acquisition.application.MealEstimateCommand
import com.macrosaurus.acquisition.application.MealEstimateService
import com.macrosaurus.acquisition.application.MealEstimator
import com.macrosaurus.acquisition.application.StartLabelScanCommand
import com.macrosaurus.acquisition.config.OpenRouterProperties
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

    @AfterEach
    fun stop() = server.stop(0)

    private fun respond(
        content: String,
        status: Int = 200,
    ) {
        server.createContext("/chat/completions") { exchange ->
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
        assertThatThrownBy { client.complete("test", emptyMap(), emptyList()) }.isInstanceOf(ExternalServiceException::class.java).hasMessageContaining("credits").hasMessageNotContaining("sensitive")
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
        assertThatThrownBy { unconfigured.complete("test", emptyMap(), emptyList()) }.isInstanceOf(ServiceUnavailableException::class.java)
    }

    @Test
    fun `access and empty input are checked before paid inference`() {
        val estimator = MealEstimator { error("Must not call AI") }
        val command = MealEstimateCommand("", emptyList(), null)
        assertThatThrownBy { MealEstimateService(UserFeatureReader { _, _ -> false }, estimator).estimate("user", command) }.isInstanceOf(ForbiddenException::class.java)
        assertThatThrownBy { MealEstimateService(UserFeatureReader { _, _ -> true }, estimator).estimate("user", command) }.isInstanceOf(InvalidOperationException::class.java)
    }
}
