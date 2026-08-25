package com.macrosaurus.catalog.web

import com.macrosaurus.catalog.SourceKind
import com.macrosaurus.catalog.application.CatalogService
import com.macrosaurus.shared.CurrentUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
internal class CatalogController(
    private val users: CurrentUser,
    private val catalog: CatalogService,
) {
    @GetMapping("/nutrients")
    fun nutrients() = catalog.nutrients().map { it.toView() }

    @GetMapping("/foods")
    fun search(
        @RequestParam(defaultValue = "") query: String,
        @RequestParam(defaultValue = "25") limit: Int,
    ) = catalog.search(users.userId(), query, limit).map { it.toView() }

    @GetMapping("/foods/{id}")
    fun get(
        @PathVariable id: UUID,
    ) = catalog.get(users.userId(), id).toView()

    @GetMapping("/food-revisions/{id}")
    fun getRevision(
        @PathVariable id: UUID,
    ) = catalog.byRevision(users.userId(), id).toView()

    @PostMapping("/foods")
    fun create(
        @Valid @RequestBody request: CreateFoodRequest,
    ) = catalog.create(users.userId(), request.toDraft(), SourceKind.USER, null).toView()

    @PutMapping("/foods/{id}")
    fun revise(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateFoodRequest,
    ) = catalog.revise(users.userId(), id, request.toDraft()).toView()

    @PostMapping("/food-revisions/{id}/resolve")
    fun resolve(
        @PathVariable id: UUID,
        @Valid @RequestBody request: FoodAmountRequest,
    ) = catalog.resolve(users.userId(), id, request.toAmount()).toView()
}
