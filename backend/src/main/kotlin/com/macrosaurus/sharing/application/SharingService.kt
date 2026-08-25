package com.macrosaurus.sharing.application

import com.macrosaurus.catalog.FoodCatalog
import com.macrosaurus.recipes.RecipeReader
import com.macrosaurus.shared.NotFoundException
import com.macrosaurus.sharing.persistence.JooqSharingRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.security.MessageDigest
import java.time.Clock
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

internal enum class ShareResourceType { FOOD, RECIPE }

internal data class CreateShareCommand(
    val resourceType: ShareResourceType,
    val resourceRevisionId: UUID,
    val expiresAt: OffsetDateTime?,
)

internal data class CreatedShare(
    val id: UUID,
    val urlToken: String,
    val resourceType: ShareResourceType,
    val expiresAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)

internal data class SharedSnapshot(
    val resourceType: ShareResourceType,
    val snapshot: JsonNode,
    val expiresAt: OffsetDateTime?,
)

@Service
internal class SharingService(
    private val repository: JooqSharingRepository,
    private val catalog: FoodCatalog,
    private val recipes: RecipeReader,
    private val clock: Clock,
) {
    @Transactional
    fun create(
        userId: String,
        command: CreateShareCommand,
    ): CreatedShare {
        val snapshot: Any =
            when (command.resourceType) {
                ShareResourceType.FOOD -> catalog.byRevision(userId, command.resourceRevisionId)
                ShareResourceType.RECIPE -> recipes.getByRevision(userId, command.resourceRevisionId)
            }
        val tokenBytes = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        val id = UUID.randomUUID()
        val createdAt = OffsetDateTime.now(clock)
        repository.insert(
            id,
            userId,
            hash(rawToken),
            command.resourceType.name,
            command.resourceRevisionId,
            snapshot,
            command.expiresAt,
            createdAt,
        )
        return CreatedShare(id, rawToken, command.resourceType, command.expiresAt, createdAt)
    }

    fun get(rawToken: String): SharedSnapshot {
        val stored = repository.findActive(hash(rawToken)) ?: throw NotFoundException("Share link is invalid, expired, or revoked")
        return SharedSnapshot(ShareResourceType.valueOf(stored.resourceType), stored.snapshot, stored.expiresAt)
    }

    fun revoke(
        userId: String,
        id: UUID,
    ) {
        if (repository.revoke(userId, id) == 0) throw NotFoundException("Active share link was not found")
    }

    private fun hash(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}
