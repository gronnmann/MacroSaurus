package com.macrosaurus.acquisition

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.macrosaurus.acquisition.application.MealEstimateCommand
import com.macrosaurus.acquisition.config.OpenRouterProperties
import com.macrosaurus.acquisition.integration.OpenRouterClient
import com.macrosaurus.acquisition.integration.OpenRouterMealEstimator
import com.macrosaurus.shared.ExternalServiceException
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AiLoggingTest {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = this@AiLoggingTest.executor
            start()
        }
    private val logger = LoggerFactory.getLogger("com.macrosaurus.acquisition") as Logger
    private val logs =
        object : ListAppender<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                event.prepareForDeferredProcessing()
                super.append(event)
            }
        }.apply {
            start()
            logger.addAppender(this)
        }

    private fun client(
        timeout: Duration = Duration.ofSeconds(2),
        url: String = "http://127.0.0.1:${server.address.port}",
        key: String = "secret-key",
    ): OpenRouterClient = OpenRouterClient(OpenRouterProperties(url, key, "test-model", readTimeout = timeout), mapper)

    @AfterEach
    fun cleanup() {
        server.stop(0)
        executor.shutdownNow()
        logger.detachAppender(logs)
        MDC.clear()
    }

    @Test
    fun `read timeout before headers is logged without retrying`() = verifyDeadline(false)

    @Test
    fun `read timeout in response body is logged without retrying`() = verifyDeadline(true)

    private fun verifyDeadline(sendHeaders: Boolean) {
        val calls = AtomicInteger()
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.createContext("/chat/completions") { exchange ->
            calls.incrementAndGet()
            exchange.requestBody.readAllBytes()
            if (sendHeaders) {
                exchange.sendResponseHeaders(200, 1000)
                exchange.responseBody.write('{'.code)
                exchange.responseBody.flush()
            }
            arrived.countDown()
            release.await(5, TimeUnit.SECONDS)
            exchange.close()
        }
        val client = client(Duration.ofMillis(500))
        val started = System.nanoTime()
        try {
            assertThatThrownBy { client.complete("meal_estimate", emptyMap(), emptyList()) }
                .isInstanceOf(ExternalServiceException::class.java)
                .hasMessageContaining("too long")
            assertThat(arrived.count).isZero()
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3))
            assertThat(calls.get()).isEqualTo(1)
            assertThat(logs.list.map { it.formattedMessage }).anyMatch { it.contains("category=timeout") }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `connection errors are classified and missing configuration makes no request`() {
        val port = ServerSocket(0).use { it.localPort }
        assertThatThrownBy { client(url = "http://127.0.0.1:$port").complete("meal_estimate", emptyMap(), emptyList()) }
            .hasMessageContaining("could not be reached")
        assertThatThrownBy { client(key = "").complete("meal_estimate", emptyMap(), emptyList()) }
            .hasMessageContaining("unavailable")
        assertThat(logs.list.map { it.formattedMessage })
            .anyMatch { it.contains("category=connection_failure") }
            .anyMatch { it.contains("category=missing_configuration") }
    }

    @Test
    fun `provider errors and invalid results have correlated metadata without payloads`() {
        MDC.put("requestId", "test-request")
        var response = """{"error":{"code":402,"message":"sensitive-provider-body"}}"""
        var status = 200
        server.createContext("/chat/completions") { exchange ->
            exchange.requestBody.readAllBytes()
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        val client = client()
        val estimator = OpenRouterMealEstimator(client, mapper)
        val command = MealEstimateCommand("private meal description", listOf("private image"), null)
        assertThatThrownBy { estimator.estimate(command) }.hasMessageContaining("credits")
        status = 429
        assertThatThrownBy { estimator.estimate(command) }.hasMessageContaining("busy")
        status = 200
        response = "sensitive-provider-body"
        assertThatThrownBy { estimator.estimate(command) }.hasMessageContaining("unreadable response")
        response = completion("sensitive-provider-body")
        assertThatThrownBy { estimator.estimate(command) }.hasMessageContaining("unreadable estimate")
        response = completion("""{"name":"Meal","calories":-1,"proteinG":0,"carbohydrateG":0,"fatG":0,"fiberG":null,"assumptions":[]}""")
        assertThatThrownBy { estimator.estimate(command) }.hasMessageContaining("invalid nutrition")
        response = completion("""{"name":"Meal","calories":100,"proteinG":0,"carbohydrateG":0,"fatG":0,"fiberG":null,"assumptions":[]}""")
        assertThat(estimator.estimate(command).calories).isEqualByComparingTo("100")
        val messages = logs.list.joinToString("\n") { it.formattedMessage }
        assertThat(messages)
            .contains("category=provider_error", "provider_status=402", "provider_status=429", "category=http_rejection", "category=malformed_response", "category=malformed_result", "category=invalid_nutrition", "ai_complete", "stage=result_validation", "generation_id=gen-test-123", "model=qwen/test-model", "completion_tokens=3000", "reasoning_tokens=2800", "finish_reason=stop")
            .doesNotContain("sensitive-provider-body", "private meal description", "private image", "secret-key")
        assertThat(logs.list).allMatch { it.mdcPropertyMap["requestId"] == "test-request" && it.throwableProxy == null }
    }

    private fun completion(content: String) = mapper.writeValueAsString(mapOf("id" to "gen-test-123", "model" to "qwen/test-model", "provider" to "Test Provider", "usage" to mapOf("prompt_tokens" to 100, "completion_tokens" to 3000, "completion_tokens_details" to mapOf("reasoning_tokens" to 2800)), "choices" to listOf(mapOf("finish_reason" to "stop", "message" to mapOf("content" to content)))))
}
