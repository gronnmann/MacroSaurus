package com.macrosaurus.identity

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
class UserContext(
    private val request: HttpServletRequest,
) {
    fun userId(): String {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication is JwtAuthenticationToken && authentication.isAuthenticated) {
            return authentication.token.subject?.takeIf { it.isNotBlank() } ?: authentication.name
        }
        return request.getHeader("X-User-Id")?.takeIf { it.isNotBlank() } ?: "dev-user"
    }
}
