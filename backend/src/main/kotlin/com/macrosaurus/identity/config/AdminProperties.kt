package com.macrosaurus.identity.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("macrosaurus.admin")
data class AdminProperties(
    val userIds: Set<String> = emptySet(),
) {
    fun isAdmin(userId: String): Boolean = userIds.any { it.trim() == userId }
}
