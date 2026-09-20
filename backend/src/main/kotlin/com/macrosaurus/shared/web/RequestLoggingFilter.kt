package com.macrosaurus.shared.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
internal class RequestLoggingFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest) = !request.requestURI.startsWith("${request.contextPath}/api/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val supplied = request.getHeader("X-Request-ID")
        val requestId =
            supplied?.takeIf { it.matches(Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) }
                ?: UUID.randomUUID().toString()
        val previous = MDC.get("requestId")
        val started = System.nanoTime()
        val method = request.method.takeIf { it.matches(Regex("[A-Z]{1,16}")) } ?: "UNKNOWN"
        var failed = false
        MDC.put("requestId", requestId)
        response.setHeader("X-Request-ID", requestId)
        try {
            // Route matching happens downstream. Never log the raw URI (it may contain a share token).
            log.info("request_start method={}", method)
            filterChain.doFilter(request, response)
        } catch (error: Exception) {
            failed = true
            log.error("request_failed exception={}", safeStackTrace(error))
            throw error
        } finally {
            val route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) ?: "unmatched"
            val status = if (failed) 500 else response.status
            val duration = (System.nanoTime() - started) / 1_000_000
            if (status >= 500) {
                log.error("request_complete method={} route={} status={} duration_ms={}", method, route, status, duration)
            } else if (status >= 400) {
                log.warn("request_complete method={} route={} status={} duration_ms={}", method, route, status, duration)
            } else {
                log.info("request_complete method={} route={} status={} duration_ms={}", method, route, status, duration)
            }
            if (previous == null) MDC.remove("requestId") else MDC.put("requestId", previous)
        }
    }
}

// Do not pass Throwables to the logger: their messages and causes can contain SQL values or payloads.
internal fun safeStackTrace(error: Throwable): String =
    generateSequence(error) { it.cause }.take(8).joinToString("\nCaused by: ") { cause ->
        cause.javaClass.name + cause.stackTrace.joinToString("\n    at ", prefix = "\n    at ")
    }
