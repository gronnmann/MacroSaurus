package com.macrosaurus.shared.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
internal class ApiDocumentationConfig {
    @Bean
    fun macrosaurusOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Macrosaurus API")
                    .version("v1")
                    .description("Nutrition catalog, diary, recipe, measurement, sharing, and expenditure APIs."),
            ).components(
                Components().addSecuritySchemes(
                    "oauth2Bearer",
                    SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"),
                ),
            )
}
