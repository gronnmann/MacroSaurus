package com.macrosaurus.shared.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.macrosaurus.shared.InvalidOperationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID

class RequestLoggingTest {
    private val logger = LoggerFactory.getLogger("com.macrosaurus.shared.web") as Logger
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

    @AfterEach
    fun cleanup() {
        logger.detachAppender(logs)
        MDC.clear()
    }

    @RestController
    class FailureController {
        @GetMapping("/api/test/failure")
        fun failure(): String = throw IllegalStateException("private request content")

        @GetMapping("/api/test/invalid")
        fun invalid(): String = throw InvalidOperationException("Invalid operation")
    }

    @Test
    fun `MVC errors preserve standard status and headers and log handled failures`() {
        val mvc =
            MockMvcBuilders
                .standaloneSetup(FailureController())
                .setControllerAdvice(ApiExceptionHandler())
                .addFilters<StandaloneMockMvcBuilder>(RequestLoggingFilter())
                .build()
        val failed = mvc.perform(get("/api/test/failure")).andReturn().response
        assertThat(failed.status).isEqualTo(500)
        assertThat(failed.contentAsString).doesNotContain("private request content")
        assertThat(failed.getHeader("X-Request-ID")).isNotBlank()
        val invalid = mvc.perform(get("/api/test/invalid")).andReturn().response
        assertThat(invalid.status).isEqualTo(422)
        val method = mvc.perform(post("/api/test/invalid")).andReturn().response
        assertThat(method.status).isEqualTo(405)
        assertThat(method.getHeader("Allow")).contains("GET")
        assertThat(logs.list.joinToString { it.formattedMessage })
            .contains("status=500", "status=422", "status=405")
            .doesNotContain("private request content")
    }

    @Test
    fun `requests include correlation status template and duration without private URI or query`() {
        val id = UUID.randomUUID().toString()
        val request =
            MockHttpServletRequest("GET", "/api/v1/shared/private-token").apply {
                queryString = "query=private-meal"
                addHeader("X-Request-ID", id)
            }
        val response = MockHttpServletResponse()
        RequestLoggingFilter().doFilter(request, response) { _, _ ->
            assertThat(MDC.get("requestId")).isEqualTo(id)
            request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/shared/{token}")
        }
        assertThat(response.getHeader("X-Request-ID")).isEqualTo(id)
        assertThat(MDC.get("requestId")).isNull()
        assertThat(logs.list).hasSize(2).allMatch { it.mdcPropertyMap["requestId"] == id }
        assertThat(logs.list.last().formattedMessage).contains("route=/api/v1/shared/{token}", "status=200", "duration_ms=")
        assertThat(logs.list.joinToString { it.formattedMessage }).doesNotContain("private-token", "private-meal")
    }

    @Test
    fun `early authentication rejection replaces invalid IDs and restores existing MDC`() {
        MDC.put("requestId", "previous")
        val request = MockHttpServletRequest("POST", "/api/v1/meal-estimates").apply { addHeader("X-Request-ID", "bad\nsecret") }
        val response = MockHttpServletResponse()
        RequestLoggingFilter().doFilter(request, response) { _, _ -> response.sendError(401) }
        assertThat(UUID.fromString(response.getHeader("X-Request-ID"))).isNotNull()
        assertThat(logs.list.last().formattedMessage).contains("route=unmatched", "status=401")
        assertThat(logs.list.joinToString { it.formattedMessage }).doesNotContain("secret")
        assertThat(MDC.get("requestId")).isEqualTo("previous")
    }

    @Test
    fun `unexpected exceptions log stack frames without exception or cause messages`() {
        val handler = ApiExceptionHandler()
        val response = MockHttpServletResponse()
        RequestLoggingFilter().doFilter(MockHttpServletRequest("POST", "/api/v1/meal-estimates"), response) { _, _ ->
            val problem = handler.unexpected(IllegalStateException("private SQL", IllegalArgumentException("secret-key")))
            response.status = problem.statusCode.value()
            assertThat(problem.body!!.detail).doesNotContain("private SQL", "secret-key")
        }
        assertThat(response.status).isEqualTo(500)
        assertThat(logs.list.joinToString { it.formattedMessage })
            .contains("IllegalStateException", "IllegalArgumentException", "RequestLoggingTest", "status=500")
            .doesNotContain("private SQL", "secret-key")
        assertThat(MDC.get("requestId")).isNull()
    }

    @Test
    fun `standard Spring errors retain their HTTP status and validation failures are logged`() {
        val handler = ApiExceptionHandler()
        val response = MockHttpServletResponse()
        RequestLoggingFilter().doFilter(MockHttpServletRequest("POST", "/api/v1/meal-estimates"), response) { _, _ ->
            response.status = handler.malformedRequest(IllegalArgumentException()).status
        }
        assertThat(logs.list.last().formattedMessage).contains("status=400")
        assertThat(handler.unexpected(ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED)).statusCode.value()).isEqualTo(405)
    }
}
