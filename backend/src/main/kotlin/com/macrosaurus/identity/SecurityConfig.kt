package com.macrosaurus.identity

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.net.URI
import java.util.UUID

@Configuration
class SecurityConfig(
    private val security: SecurityProperties,
    private val web: WebProperties,
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
                if (security.devMode) it.anyRequest().permitAll() else it.anyRequest().authenticated()
            }
        if (!security.devMode) {
            http.oauth2ResourceServer { resource -> resource.jwt { jwt -> jwt.decoder(buildJwtDecoder()) } }
        }
        return http.build()
    }

    private fun buildJwtDecoder(): NimbusJwtDecoder {
        val supabaseUrl = security.supabaseUrl.trimEnd('/')
        require(supabaseUrl.isNotBlank()) { "SUPABASE_URL is required when development authentication is disabled" }
        val uri = URI.create(supabaseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "SUPABASE_URL must be an absolute HTTPS URL" }
        val issuerUri = "$supabaseUrl/auth/v1"
        val decoder = NimbusJwtDecoder.withJwkSetUri("$issuerUri/.well-known/jwks.json").build()
        decoder.setJwtValidator(supabaseJwtValidator(issuerUri, security.audience))
        return decoder
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().also { source ->
            source.registerCorsConfiguration(
                "/**",
                CorsConfiguration().apply {
                    allowedOrigins = web.allowedOrigins
                    allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    allowedHeaders = listOf("Authorization", "Content-Type", "Idempotency-Key", "X-User-Id")
                    exposedHeaders = listOf("ETag", "Location")
                },
            )
        }
}

@ConfigurationProperties("macrosaurus.security")
data class SecurityProperties(
    val supabaseUrl: String = "",
    val audience: String = "authenticated",
    val devMode: Boolean = true,
)

@ConfigurationProperties("macrosaurus.web")
data class WebProperties(
    val allowedOrigins: List<String>,
)

internal fun supabaseJwtValidator(
    issuerUri: String,
    audience: String,
): OAuth2TokenValidator<Jwt> =
    DelegatingOAuth2TokenValidator(
        JwtValidators.createDefaultWithIssuer(issuerUri),
        OAuth2TokenValidator { jwt ->
            val validSubject = runCatching { UUID.fromString(jwt.subject) }.isSuccess
            when {
                jwt.audience?.contains(audience) != true -> {
                    invalidToken("Required audience is missing")
                }

                jwt.claims["role"] != "authenticated" -> {
                    invalidToken("Required role is missing")
                }

                jwt.claims["is_anonymous"] != false -> {
                    invalidToken("Anonymous sessions are not accepted")
                }

                !validSubject -> {
                    invalidToken("Subject must be a UUID")
                }

                else -> {
                    org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
                        .success()
                }
            }
        },
    )

private fun invalidToken(description: String) =
    org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
        org.springframework.security.oauth2.core
            .OAuth2Error("invalid_token", description, null),
    )
