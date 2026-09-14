package com.macrosaurus.acquisition.web

import com.macrosaurus.acquisition.application.MealEstimateCommand
import com.macrosaurus.acquisition.application.MealEstimateService
import com.macrosaurus.shared.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

internal data class MealEstimateRequest(
    @field:Size(max = 4000) val text: String = "",
    @field:Size(max = 3)
    val images: List<String> = emptyList(),
    @field:Size(max = 50) val localeHint: String? = null,
) {
    @AssertTrue(message = "Each photo must be a JPEG, PNG, or WebP data URL smaller than 4 MB")
    fun isImagesValid(): Boolean =
        images.all {
            it.length <= 4_000_000 && it.matches(Regex("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/]+={0,2}$"))
        }
}

@RestController
@RequestMapping("/api/v1/meal-estimates")
internal class MealEstimateController(
    private val users: CurrentUser,
    private val estimates: MealEstimateService,
) {
    @PostMapping
    fun estimate(
        @Valid @RequestBody request: MealEstimateRequest,
    ) = estimates.estimate(users.userId(), MealEstimateCommand(request.text, request.images, request.localeHint))
}
