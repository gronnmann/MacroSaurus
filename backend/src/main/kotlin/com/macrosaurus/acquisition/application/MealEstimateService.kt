package com.macrosaurus.acquisition.application

import com.macrosaurus.identity.UserFeature
import com.macrosaurus.identity.UserFeatureReader
import com.macrosaurus.shared.ForbiddenException
import com.macrosaurus.shared.InvalidOperationException
import org.springframework.stereotype.Service
import java.math.BigDecimal

internal data class MealEstimateCommand(
    val text: String,
    val images: List<String>,
    val localeHint: String?,
)

internal data class MealEstimate(
    val name: String,
    val calories: BigDecimal,
    val proteinG: BigDecimal,
    val carbohydrateG: BigDecimal,
    val fatG: BigDecimal,
    val fiberG: BigDecimal?,
    val assumptions: List<String>,
)

internal fun interface MealEstimator {
    fun estimate(command: MealEstimateCommand): MealEstimate
}

@Service
internal class MealEstimateService(
    private val features: UserFeatureReader,
    private val estimator: MealEstimator,
) {
    fun estimate(
        userId: String,
        command: MealEstimateCommand,
    ): MealEstimate {
        aiStage("meal_estimate", "access") {
            if (!features.enabled(userId, UserFeature.AI_LABEL_SCAN)) throw ForbiddenException("AI meal estimation is not enabled for this user")
        }
        if (command.text.isBlank() && command.images.isEmpty()) throw InvalidOperationException("Add a photo or describe your meal.")
        return estimator.estimate(command.copy(text = command.text.trim()))
    }
}
