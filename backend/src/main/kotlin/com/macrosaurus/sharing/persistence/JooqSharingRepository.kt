package com.macrosaurus.sharing.persistence

import com.macrosaurus.shared.JsonCodec
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.OffsetDateTime
import java.util.UUID

internal data class StoredSharedSnapshot(
    val resourceType: String,
    val snapshot: JsonNode,
    val expiresAt: OffsetDateTime?,
)

@Repository
internal class JooqSharingRepository(
    private val db: DSLContext,
    private val json: JsonCodec,
    private val mapper: ObjectMapper,
) {
    fun insert(
        id: UUID,
        userId: String,
        tokenHash: String,
        resourceType: String,
        resourceRevisionId: UUID,
        snapshot: Any,
        expiresAt: OffsetDateTime?,
        createdAt: OffsetDateTime,
    ) {
        db.execute(
            """
            insert into share_links(id, owner_user_id, token_hash, resource_type, resource_revision_id,
                                    snapshot, expires_at, created_at)
            values (?, ?, ?, ?, ?, cast(? as jsonb), cast(? as timestamptz), cast(? as timestamptz))
            """.trimIndent(),
            id,
            userId,
            tokenHash,
            resourceType,
            resourceRevisionId,
            json.write(snapshot),
            expiresAt,
            createdAt,
        )
    }

    fun findActive(tokenHash: String): StoredSharedSnapshot? {
        val record =
            db.fetchOne(
                """
                select resource_type, snapshot::text as snapshot, expires_at from share_links
                 where token_hash = ? and revoked_at is null and (expires_at is null or expires_at > current_timestamp)
                """.trimIndent(),
                tokenHash,
            ) ?: return null
        return StoredSharedSnapshot(
            record.get("resource_type", String::class.java)!!,
            mapper.readTree(record.get("snapshot", String::class.java)!!),
            record.get("expires_at", OffsetDateTime::class.java),
        )
    }

    fun revoke(
        userId: String,
        id: UUID,
    ): Int =
        db.execute(
            "update share_links set revoked_at = current_timestamp where id = ? and owner_user_id = ? and revoked_at is null",
            id,
            userId,
        )
}
