package com.macrosaurus

import com.macrosaurus.catalog.CatalogService
import com.macrosaurus.catalog.CreateFoodRequest
import com.macrosaurus.catalog.FoodAmountRequest
import com.macrosaurus.measurements.AddWeightRequest
import com.macrosaurus.measurements.MeasurementService
import com.macrosaurus.recipes.RecipeIngredientInput
import com.macrosaurus.recipes.RecipeService
import com.macrosaurus.recipes.SaveRecipeRequest
import com.macrosaurus.shared.BasisType
import com.macrosaurus.shared.NotFoundException
import com.macrosaurus.sharing.CreateShareRequest
import com.macrosaurus.sharing.ShareResourceType
import com.macrosaurus.sharing.SharingService
import com.macrosaurus.tracking.AddFoodEntryRequest
import com.macrosaurus.tracking.Meal
import com.macrosaurus.tracking.TrackingService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///macrosaurus",
        "spring.datasource.username=macrosaurus",
        "spring.datasource.password=macrosaurus",
    ],
)
class BackendPersistenceIT {
    @Autowired private lateinit var catalog: CatalogService

    @Autowired private lateinit var recipes: RecipeService

    @Autowired private lateinit var tracking: TrackingService

    @Autowired private lateinit var measurements: MeasurementService

    @Autowired private lateinit var sharing: SharingService

    @Test
    fun `food recipe diary measurement and sharing flows retain snapshots and ownership`() {
        val userId = "integration-user"
        val food =
            catalog.create(
                userId,
                CreateFoodRequest(
                    name = "Integration oats",
                    basisType = BasisType.PER_100_G,
                    basisAmount = BigDecimal("100"),
                    basisUnit = "g",
                    nutrients = mapOf("energy_kcal" to BigDecimal("380"), "protein_g" to BigDecimal("13")),
                ),
            )
        val foundFood = catalog.search(userId, "Integration oats").single()
        assertThat(foundFood.nutrients["protein_g"]).isEqualByComparingTo("13")
        assertThatThrownBy { catalog.get("another-user", food.id) }.isInstanceOf(NotFoundException::class.java)

        val recipe =
            recipes.create(
                userId,
                SaveRecipeRequest(
                    name = "Integration porridge",
                    servings = BigDecimal("2"),
                    ingredients = listOf(RecipeIngredientInput(food.revisionId, BigDecimal("200"), "g")),
                ),
            )
        assertThat(recipe.nutrientsPerServing["protein_g"]).isEqualByComparingTo("13")

        val date = LocalDate.of(2026, 8, 22)
        tracking.addFood(
            userId,
            AddFoodEntryRequest(
                foodRevisionId = food.revisionId,
                quantity = BigDecimal("50"),
                unit = "g",
                localDate = date,
                consumedAt = OffsetDateTime.parse("2026-08-22T08:00:00+02:00"),
                meal = Meal.BREAKFAST,
            ),
        )
        val summary = tracking.summary(userId, date.minusDays(1), date.plusDays(1))
        assertThat(summary).hasSize(3)
        assertThat(summary[1].totals["protein_g"]).isEqualByComparingTo("6.5")

        val weight = measurements.add(userId, AddWeightRequest(BigDecimal("82.4")))
        assertThat(measurements.list(userId).map { it.id }).contains(weight.id)

        val share = sharing.create(userId, CreateShareRequest(ShareResourceType.FOOD, food.revisionId))
        assertThat(sharing.get(share.urlToken).resourceType).isEqualTo(ShareResourceType.FOOD)
        val expiredShare =
            sharing.create(
                userId,
                CreateShareRequest(
                    ShareResourceType.FOOD,
                    food.revisionId,
                    OffsetDateTime.parse("2020-01-01T00:00:00Z"),
                ),
            )
        assertThatThrownBy { sharing.get(expiredShare.urlToken) }.isInstanceOf(NotFoundException::class.java)

        val resolved = catalog.resolve(userId, food.revisionId, FoodAmountRequest(BigDecimal("25"), "g"))
        assertThat(resolved.nutrients["protein_g"]).isEqualByComparingTo("3.25")
    }
}
