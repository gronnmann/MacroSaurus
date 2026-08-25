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
)
