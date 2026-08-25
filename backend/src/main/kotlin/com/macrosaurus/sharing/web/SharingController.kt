package com.macrosaurus.sharing.web

import com.macrosaurus.shared.CurrentUser
import com.macrosaurus.sharing.application.CreateShareCommand
import com.macrosaurus.sharing.application.CreatedShare
import com.macrosaurus.sharing.application.ShareResourceType
import com.macrosaurus.sharing.application.SharedSnapshot
import com.macrosaurus.sharing.application.SharingService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.time.OffsetDateTime
import java.util.UUID

internal data class CreateShareRequest(
    val resourceType: ShareResourceType,
    val resourceRevisionId: UUID,
    val expiresAt: OffsetDateTime? = null,
)

internal data class CreatedShareView(
    val id: UUID,
    val urlToken: String,
    val resourceType: ShareResourceType,
    val expiresAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
)

internal data class SharedSnapshotView(
    val resourceType: ShareResourceType,
    val snapshot: JsonNode,
    val expiresAt: OffsetDateTime?,
)

private fun CreatedShare.toView() = CreatedShareView(id, urlToken, resourceType, expiresAt, createdAt)

private fun SharedSnapshot.toView() = SharedSnapshotView(resourceType, snapshot, expiresAt)

@RestController
@RequestMapping("/api/v1")
internal class SharingController(
    private val users: CurrentUser,
    private val shares: SharingService,
) {
    @PostMapping("/share-links")
    fun create(
        @Valid @RequestBody request: CreateShareRequest,
    ) = shares
        .create(
            users.userId(),
            CreateShareCommand(request.resourceType, request.resourceRevisionId, request.expiresAt),
        ).toView()

    @DeleteMapping("/share-links/{id}")
    fun revoke(
        @PathVariable id: UUID,
    ) = shares.revoke(users.userId(), id)

    @GetMapping("/shared/{token}")
    fun shared(
        @PathVariable token: String,
    ) = shares.get(token).toView()
}
