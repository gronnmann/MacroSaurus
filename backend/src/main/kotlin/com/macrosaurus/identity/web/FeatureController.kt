package com.macrosaurus.identity.web

import com.macrosaurus.identity.UserFeature
import com.macrosaurus.identity.application.FeatureGrantService
import com.macrosaurus.shared.CurrentUser
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class AiLabelScanFeatureView(
    val granted: Boolean,
    val available: Boolean,
)

data class MyFeaturesView(
    val isAdmin: Boolean,
    val aiLabelScan: AiLabelScanFeatureView,
)

data class AdminUserView(
    val userId: String,
    val displayName: String,
    val aiLabelScanEnabled: Boolean,
)

data class SetFeatureRequest(
    val enabled: Boolean,
)

@RestController
@RequestMapping("/api/v1")
internal class FeatureController(
    private val users: CurrentUser,
    private val features: FeatureGrantService,
    @param:Value("\${macrosaurus.open-router.api-key:}") private val openRouterApiKey: String,
) {
    @GetMapping("/me/features")
    fun mine(): MyFeaturesView {
        val userId = users.userId()
        return MyFeaturesView(
            features.isAdmin(userId),
            AiLabelScanFeatureView(features.enabled(userId, UserFeature.AI_LABEL_SCAN), openRouterApiKey.isNotBlank()),
        )
    }

    @GetMapping("/admin/users")
    fun adminUsers(
        @RequestParam(defaultValue = "") query: String,
    ) = features.users(users.userId(), query).map { AdminUserView(it.profile.userId, it.profile.displayName, it.aiLabelScanEnabled) }

    @PutMapping("/admin/users/{userId}/features/ai-label-scan")
    fun setAiLabelScan(
        @PathVariable userId: String,
        @Valid @RequestBody request: SetFeatureRequest,
    ): AdminUserView {
        features.set(users.userId(), userId, UserFeature.AI_LABEL_SCAN, request.enabled)
        val profile = features.users(users.userId(), userId).first { it.profile.userId == userId }.profile
        return AdminUserView(profile.userId, profile.displayName, request.enabled)
    }
}
