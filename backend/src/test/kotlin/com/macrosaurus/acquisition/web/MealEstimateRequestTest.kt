package com.macrosaurus.acquisition.web

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MealEstimateRequestTest {
    @Test
    fun `request validation rejects external URLs oversized photos and too many photos`() {
        Validation.buildDefaultValidatorFactory().use { factory ->
            val validator = factory.validator
            assertThat(validator.validate(MealEstimateRequest(images = listOf("https://example.com/image.jpg")))).isNotEmpty()
            assertThat(validator.validate(MealEstimateRequest(images = listOf("data:image/jpeg;base64," + "a".repeat(4_000_000))))).isNotEmpty()
            assertThat(validator.validate(MealEstimateRequest(images = List(4) { "data:image/jpeg;base64,YQ==" }))).isNotEmpty()
            assertThat(validator.validate(MealEstimateRequest(text = "Rice", images = listOf("data:image/jpeg;base64,YQ==")))).isEmpty()
        }
    }
}
