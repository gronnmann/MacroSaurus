package com.macrosaurus.identity

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
    @Value("\${macrosaurus.security.issuer-uri:}") private val issuerUri: String,
    @Value("\${macrosaurus.security.audience}") private val audience: String,
    @Value("\${macrosaurus.web.allowed-origins}") private val allowedOrigins: List<String>,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(
                        "/actuator/health/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/api-docs/**",
                        "/api/v1/shared/**",
                    ).permitAll()
                if (issuerUri.isBlank()) it.anyRequest().permitAll() else it.anyRequest().authenticated()
            }
        if (issuerUri.isNotBlank()) {
            http.oauth2ResourceServer { resource -> resource.jwt { jwt -> jwt.decoder(buildJwtDecoder()) } }
        }
        return http.build()
    }

    private fun buildJwtDecoder(): NimbusJwtDecoder {
        val decoder = JwtDecoders.fromIssuerLocation<NimbusJwtDecoder>(issuerUri)
        val issuer = JwtValidators.createDefaultWithIssuer(issuerUri)
        val audienceValidator =
            OAuth2TokenValidator<Jwt> { jwt ->
                if (jwt.audience?.contains(audience) == true) {
                    org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
                        .success()
                } else {
                    org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                        org.springframework.security.oauth2.core
                            .OAuth2Error("invalid_token", "Required audience is missing", null),
                    )
                }
            }
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(issuer, audienceValidator))
        return decoder
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().also { source ->
            source.registerCorsConfiguration(
                "/**",
                CorsConfiguration().apply {
                    allowedOrigins = this@SecurityConfig.allowedOrigins
                    allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    allowedHeaders = listOf("Authorization", "Content-Type", "Idempotency-Key", "X-User-Id")
                    exposedHeaders = listOf("ETag", "Location")
                },
            )
        }
}
