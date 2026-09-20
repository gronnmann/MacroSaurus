package com.macrosaurus.acquisition.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("macrosaurus.open-food-facts")
internal data class OpenFoodFactsProperties(
    val baseUrl: String,
    val userAgent: String,
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(15),
)

@ConfigurationProperties("macrosaurus.open-router")
internal data class OpenRouterProperties(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val connectTimeout: Duration = Duration.ofSeconds(5),
    val readTimeout: Duration = Duration.ofSeconds(90),
    val mealMaxTokens: Int = 1024,
    val labelMaxTokens: Int = 4096,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.NONE,
) {
    init {
        require(mealMaxTokens in 256..8192) { "AI meal token limit must be between 256 and 8192" }
        require(labelMaxTokens in 1024..16384) { "AI label token limit must be between 1024 and 16384" }
    }
}

// DEFAULT omits the setting for models without reasoning controls. It is an explicit opt-in.
internal enum class ReasoningEffort { NONE, MINIMAL, LOW, DEFAULT }
