package com.macrosaurus.identity.application

import com.macrosaurus.identity.UserFeature
import com.macrosaurus.identity.UserFeatureReader
import com.macrosaurus.identity.config.AdminProperties
import com.macrosaurus.identity.persistence.AdminUserRecord
import com.macrosaurus.identity.persistence.JooqFeatureGrantRepository
import com.macrosaurus.shared.ForbiddenException
import com.macrosaurus.shared.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
internal class FeatureGrantService(
    private val repository: JooqFeatureGrantRepository,
    private val profiles: ProfileService,
    private val admins: AdminProperties,
) : UserFeatureReader {
    override fun enabled(
        userId: String,
        feature: UserFeature,
    ): Boolean = repository.enabled(userId, feature)

    fun isAdmin(userId: String): Boolean = admins.isAdmin(userId)

    fun assertAdmin(userId: String) = requireAdmin(userId)

    fun users(
        actorUserId: String,
        query: String,
    ): List<AdminUserRecord> {
        requireAdmin(actorUserId)
        return repository.users(query)
    }

    @Transactional
    fun set(
        actorUserId: String,
        userId: String,
        feature: UserFeature,
        enabled: Boolean,
    ) {
        requireAdmin(actorUserId)
        if (profiles.get(userId) == null) throw NotFoundException("User profile was not found")
        repository.set(userId, feature, enabled, actorUserId)
    }

    private fun requireAdmin(userId: String) {
        if (!admins.isAdmin(userId)) throw ForbiddenException("Administrator access is required")
    }
}
