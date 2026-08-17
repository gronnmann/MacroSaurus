package com.macrosaurus.sharing

import com.macrosaurus.catalog.CatalogService
import com.macrosaurus.identity.UserContext
import com.macrosaurus.recipes.RecipeService
import com.macrosaurus.shared.ForbiddenException
import com.macrosaurus.shared.JsonCodec
import com.macrosaurus.shared.NotFoundException
import jakarta.validation.Valid
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

enum class ShareResourceType { FOOD, RECIPE }

data class CreateShareRequest(
    val resourceType: ShareResourceType,
    val resourceRevisionId: UUID,
    val expiresAt: OffsetDateTime? = null,
)

data class CreatedShareView(
    val id: UUID,
    val urlToken: String,
    val resourceType: ShareResourceType,
    val expiresAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)

data class SharedSnapshotView(
    val resourceType: ShareResourceType,
    val snapshot: JsonNode,
    val expiresAt: OffsetDateTime?,
)

@Service
class SharingService(
    private val db: DSLContext,
    private val json: JsonCodec,
    private val mapper: ObjectMapper,
    private val catalog: CatalogService,
    private val recipes: RecipeService,
) {
    @Transactional
    fun create(
        userId: String,
        request: CreateShareRequest,
    ): CreatedShareView {
        val snapshot: Any =
            when (request.resourceType) {
                ShareResourceType.FOOD -> catalog.byRevision(userId, request.resourceRevisionId)
                ShareResourceType.RECIPE -> recipes.getByRevision(userId, request.resourceRevisionId)
            }
        val tokenBytes = ByteArray(32).also(java.security.SecureRandom()::nextBytes)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        val id = UUID.randomUUID()
        val createdAt = OffsetDateTime.now()
        db.execute(
            """
            insert into share_links(id, owner_user_id, token_hash, resource_type, resource_revision_id,
                                    snapshot, expires_at, created_at)
            values (?, ?, ?, ?, ?, cast(? as jsonb), cast(? as timestamptz), cast(? as timestamptz))
            """.trimIndent(),
            id,
            userId,
            hash(rawToken),
            request.resourceType.name,
            request.resourceRevisionId,
            json.write(snapshot),
            request.expiresAt,
            createdAt,
        )
        return CreatedShareView(id, rawToken, request.resourceType, request.expiresAt, createdAt)
    }

    fun get(rawToken: String): SharedSnapshotView {
        val record =
            db.fetchOne(
                """
                select resource_type, snapshot::text as snapshot, expires_at from share_links
                 where token_hash = ? and revoked_at is null and (expires_at is null or expires_at > current_timestamp)
                """.trimIndent(),
                hash(rawToken),
            ) ?: throw NotFoundException("Share link is invalid, expired, or revoked")
        return SharedSnapshotView(
            ShareResourceType.valueOf(record.get("resource_type", String::class.java)!!),
            mapper.readTree(record.get("snapshot", String::class.java)!!),
            record.get("expires_at", OffsetDateTime::class.java),
        )
    }

    fun revoke(
        userId: String,
        id: UUID,
    ) {
        val changed =
            db.execute(
                "update share_links set revoked_at = current_timestamp where id = ? and owner_user_id = ? and revoked_at is null",
                id,
                userId,
            )
        if (changed == 0) throw NotFoundException("Active share link was not found")
    }

    private fun hash(token: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(token.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

@RestController
@RequestMapping("/api/v1")
class SharingController(
    private val users: UserContext,
    private val shares: SharingService,
) {
    @PostMapping("/share-links")
    fun create(
        @Valid @RequestBody request: CreateShareRequest,
    ) = shares.create(users.userId(), request)

    @DeleteMapping("/share-links/{id}")
    fun revoke(
        @PathVariable id: UUID,
    ) = shares.revoke(users.userId(), id)

    @GetMapping("/shared/{token}")
    fun shared(
        @PathVariable token: String,
    ) = shares.get(token)
}
