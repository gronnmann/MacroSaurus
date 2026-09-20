package com.macrosaurus.acquisition.application

import com.macrosaurus.shared.ExternalServiceException
import com.macrosaurus.shared.ForbiddenException
import com.macrosaurus.shared.InvalidOperationException
import com.macrosaurus.shared.ServiceUnavailableException
import org.slf4j.LoggerFactory

private val aiLogger = LoggerFactory.getLogger("com.macrosaurus.acquisition.AI")

internal fun <T> aiStage(
    operation: String,
    stage: String,
    model: String? = null,
    action: () -> T,
): T {
    val started = System.nanoTime()
    aiLogger.info("ai_start operation={} stage={} model={}", operation, stage, model ?: "-")
    try {
        val result = action()
        aiLogger.info("ai_complete operation={} stage={} duration_ms={}", operation, stage, (System.nanoTime() - started) / 1_000_000)
        return result
    } catch (error: Exception) {
        val category =
            when (error) {
                is ExternalServiceException -> error.failureCategory
                is ServiceUnavailableException -> "missing_configuration"
                is ForbiddenException -> "access_denied"
                is InvalidOperationException -> "invalid_input"
                else -> "unexpected"
            }
        // Exceptions can contain prompts, credentials, or provider responses. Log only metadata.
        aiLogger.warn("ai_failed operation={} stage={} category={} exception={} duration_ms={}", operation, stage, category, error.javaClass.simpleName, (System.nanoTime() - started) / 1_000_000)
        throw error
    }
}
