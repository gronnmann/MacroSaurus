package com.macrosaurus.goals.web

import com.macrosaurus.goals.application.CoachingService
import com.macrosaurus.goals.application.CoachingSetupDraft
import com.macrosaurus.shared.CurrentUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/me/coaching")
internal class CoachingController(
    private val users: CurrentUser,
    private val coaching: CoachingService,
) {
    @GetMapping("/status")
    fun status() = coaching.status(users.userId())

    @GetMapping("/setup-draft")
    fun draft() = coaching.draft(users.userId())

    @PutMapping("/setup-draft")
    fun saveDraft(
        @RequestBody draft: CoachingSetupDraft,
    ) = coaching.saveDraft(users.userId(), draft)

    @PostMapping("/setup-draft/preview")
    fun preview(
        @RequestBody draft: CoachingSetupDraft,
    ) = coaching.preview(users.userId(), draft)

    @PostMapping("/setup-draft/complete")
    fun complete(
        @RequestBody draft: CoachingSetupDraft,
    ) = coaching.complete(users.userId(), draft)

    @GetMapping("/check-ins/current")
    fun currentCheckIn() = coaching.currentCheckIn(users.userId())

    @PostMapping("/check-ins/{id}/refresh")
    fun refresh(
        @PathVariable id: UUID,
    ) = coaching.refresh(users.userId(), id)

    @PostMapping("/check-ins/{id}/accept")
    fun accept(
        @PathVariable id: UUID,
    ) = coaching.accept(users.userId(), id)

    @PostMapping("/check-ins/{id}/skip")
    fun skip(
        @PathVariable id: UUID,
    ) = coaching.skip(users.userId(), id)
}
