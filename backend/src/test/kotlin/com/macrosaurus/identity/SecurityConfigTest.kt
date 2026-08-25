package com.macrosaurus.identity

import com.macrosaurus.identity.config.supabaseJwtValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.util.UUID

class SecurityConfigTest {
    private val issuer = "https://example.supabase.co/auth/v1"
    private val validator = supabaseJwtValidator(issuer, "authenticated")

    @Test
    fun `accepts an authenticated non-anonymous Supabase user`() {
        assertThat(validator.validate(token()).hasErrors()).isFalse()
    }

    @Test
    fun `rejects tokens for another audience`() {
        assertThat(validator.validate(token(audience = "anon")).hasErrors()).isTrue()
    }

    @Test
    fun `rejects tokens from another issuer`() {
        assertThat(validator.validate(token(issuer = "https://attacker.example/auth/v1")).hasErrors()).isTrue()
    }

    @Test
    fun `rejects expired tokens`() {
        assertThat(validator.validate(token(expired = true)).hasErrors()).isTrue()
    }

    @Test
    fun `rejects tokens without the authenticated role`() {
        assertThat(validator.validate(token(role = "service_role")).hasErrors()).isTrue()
    }

    @Test
    fun `rejects anonymous sessions`() {
        assertThat(validator.validate(token(isAnonymous = true)).hasErrors()).isTrue()
    }

    @Test
    fun `rejects a non-UUID subject`() {
        assertThat(validator.validate(token(subject = "legacy-user")).hasErrors()).isTrue()
    }

    private fun token(
        audience: String = "authenticated",
        role: String = "authenticated",
        isAnonymous: Boolean = false,
        subject: String = UUID.randomUUID().toString(),
        issuer: String = this.issuer,
        expired: Boolean = false,
    ): Jwt =
        Jwt
            .withTokenValue("token")
            .header("alg", "RS256")
            .issuer(issuer)
            .subject(subject)
            .audience(listOf(audience))
            .issuedAt(Instant.now().minusSeconds(if (expired) 600 else 0))
            .expiresAt(Instant.now().plusSeconds(if (expired) -300 else 300))
            .claim("role", role)
            .claim("is_anonymous", isAnonymous)
            .build()
}
