package com.macrosaurus

import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.FoodAmount
import com.macrosaurus.catalog.FoodCatalog
import com.macrosaurus.catalog.FoodCreator
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodResolver
import com.macrosaurus.catalog.PortionDraft
import com.macrosaurus.catalog.SourceKind
import com.macrosaurus.catalog.application.CatalogService
import com.macrosaurus.goals.ProgramStyle
import com.macrosaurus.goals.WeightGoalType
import com.macrosaurus.goals.application.CoachingService
import com.macrosaurus.goals.application.CoachingSetupDraft
import com.macrosaurus.identity.FormulaSex
import com.macrosaurus.measurements.application.AddWeightCommand
import com.macrosaurus.measurements.application.MeasurementService
import com.macrosaurus.recipes.application.RecipeIngredientCommand
import com.macrosaurus.recipes.application.RecipeService
import com.macrosaurus.recipes.application.SaveRecipeCommand
import com.macrosaurus.shared.NotFoundException
import com.macrosaurus.sharing.application.CreateShareCommand
import com.macrosaurus.sharing.application.ShareResourceType
import com.macrosaurus.sharing.application.SharingService
import com.macrosaurus.tracking.Meal
import com.macrosaurus.tracking.NutritionDayReview
import com.macrosaurus.tracking.NutritionDayStatus
import com.macrosaurus.tracking.application.AddFoodEntryCommand
import com.macrosaurus.tracking.application.TrackableType
import com.macrosaurus.tracking.application.TrackingService
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
    @Autowired private lateinit var catalog: FoodCatalog

    @Autowired private lateinit var foodCreator: FoodCreator

    @Autowired private lateinit var foodResolver: FoodResolver

    @Autowired private lateinit var catalogService: CatalogService

    @Autowired private lateinit var recipes: RecipeService

    @Autowired private lateinit var tracking: TrackingService

    @Autowired private lateinit var measurements: MeasurementService

    @Autowired private lateinit var sharing: SharingService

    @Autowired private lateinit var coaching: CoachingService

    @Test
    fun `food recipe diary measurement and sharing flows retain snapshots and ownership`() {
        val userId = "integration-user"
        val barcode = "3017620422003"
        foodCreator.create(
            userId,
            FoodDraft(
                name = "Shared barcode food",
                barcode = barcode,
                nutrients = mapOf("energy_kcal" to BigDecimal("100")),
            ),
            SourceKind.USDA,
            "integration-shared-food",
        )
        val food =
            foodCreator.create(
                userId,
                FoodDraft(
                    name = "Integration oats",
                    barcode = barcode,
                    basisType = BasisType.PER_100_G,
                    basisAmount = BigDecimal("100"),
                    basisUnit = "g",
                    nutrients = mapOf("energy_kcal" to BigDecimal("380"), "protein_g" to BigDecimal("13")),
                ),
            )
        foodCreator.create(
            "another-user",
            FoodDraft(
                name = "Another user's barcode food",
                barcode = barcode,
                nutrients = mapOf("energy_kcal" to BigDecimal("200")),
            ),
        )
        val foundFood = catalog.search(userId, "Integration oats").single()
        assertThat(foundFood.nutrients["protein_g"]).isEqualByComparingTo("13")
        assertThat(catalog.byBarcode(userId, barcode).first().id).isEqualTo(food.id)
        assertThat(catalog.byBarcode(userId, barcode).map { it.name })
            .doesNotContain("Another user's barcode food")
        assertThatThrownBy { catalog.get("another-user", food.id) }.isInstanceOf(NotFoundException::class.java)

        val recipe =
            recipes.create(
                userId,
                SaveRecipeCommand(
                    name = "Integration porridge",
                    servings = BigDecimal("2"),
                    finishedWeightG = null,
                    ingredients = listOf(RecipeIngredientCommand(food.revisionId, BigDecimal("200"), "g", null)),
                ),
            )
        assertThat(recipe.nutrientsPerServing["protein_g"]).isEqualByComparingTo("13")

        val date = LocalDate.of(2026, 8, 22)
        tracking.addFood(
            userId,
            AddFoodEntryCommand(
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

        val weight = measurements.add(userId, AddWeightCommand(BigDecimal("82.4"), null, null))
        assertThat(measurements.list(userId, 100).map { it.id }).contains(weight.id)

        val share = sharing.create(userId, CreateShareCommand(ShareResourceType.FOOD, food.revisionId, null))
        assertThat(sharing.get(share.urlToken).resourceType).isEqualTo(ShareResourceType.FOOD)
        val expiredShare =
            sharing.create(
                userId,
                CreateShareCommand(
                    ShareResourceType.FOOD,
                    food.revisionId,
                    OffsetDateTime.parse("2020-01-01T00:00:00Z"),
                ),
            )
        assertThatThrownBy { sharing.get(expiredShare.urlToken) }.isInstanceOf(NotFoundException::class.java)

        val resolved = foodResolver.resolve(userId, food.revisionId, FoodAmount(BigDecimal("25"), "g"))
        assertThat(resolved.nutrients["protein_g"]).isEqualByComparingTo("3.25")
    }

    @Test
    fun `tracking defaults and time suggestions follow history across food revisions`() {
        val userId = "tracking-history-user"
        val original =
            foodCreator.create(
                userId,
                FoodDraft(
                    name = "Habit chocolate",
                    nutrients = mapOf("energy_kcal" to BigDecimal("520")),
                    portions = listOf(PortionDraft("bar", gramWeight = BigDecimal("30"), default = true)),
                ),
            )
        val now = OffsetDateTime.now().withSecond(0).withNano(0)
        val firstUse = now.minusDays(3)
        tracking.addFood(
            userId,
            AddFoodEntryCommand(
                original.revisionId,
                BigDecimal.ONE,
                "portion",
                original.portions.single().id,
                firstUse.toLocalDate(),
                firstUse,
            ),
        )

        val revised =
            catalogService.revise(
                userId,
                original.id,
                FoodDraft(
                    name = "Habit chocolate",
                    nutrients = mapOf("energy_kcal" to BigDecimal("510")),
                    portions = listOf(PortionDraft("Bar", gramWeight = BigDecimal("32"), default = true)),
                ),
            )
        val remembered = tracking.lastTrackedAmount(userId, TrackableType.FOOD, revised.revisionId)
        assertThat(remembered?.quantity).isEqualByComparingTo("1")
        assertThat(remembered?.unit).isEqualTo("portion")
        assertThat(remembered?.portionId).isEqualTo(revised.portions.single().id)

        val secondUse = now.minusDays(1)
        tracking.addFood(
            userId,
            AddFoodEntryCommand(
                revised.revisionId,
                BigDecimal("2"),
                "portion",
                revised.portions.single().id,
                secondUse.toLocalDate(),
                secondUse,
            ),
        )

        val suggestions = tracking.timeOfDaySuggestions(userId, TrackableType.FOOD, 5)
        assertThat(suggestions.items.map { it.id }).containsExactly(revised.id)
        assertThat(tracking.trackables(userId, "", TrackableType.FOOD, 30).first().id).isEqualTo(revised.id)
        assertThat(tracking.lastTrackedAmount(userId, TrackableType.FOOD, revised.revisionId)?.quantity)
            .isEqualByComparingTo("2")
    }

    @Test
    fun `guided setup persists a versioned program and reviewed nutrition state`() {
        val userId = "coaching-integration-user"
        val status =
            coaching.complete(
                userId,
                CoachingSetupDraft(
                    currentStep = 5,
                    displayName = "Integration athlete",
                    locale = "en-NO",
                    timezone = "Europe/Oslo",
                    birthDate = LocalDate.of(1990, 5, 10),
                    heightCm = BigDecimal("178"),
                    formulaSex = FormulaSex.MALE,
                    activityMultiplier = BigDecimal("1.55"),
                    weightKg = BigDecimal("80"),
                    goalType = WeightGoalType.LOSS,
                    targetWeightKg = BigDecimal("75"),
                    weeklyRatePercent = BigDecimal("0.5"),
                    programStyle = ProgramStyle.COACHED,
                    proteinGPerKg = BigDecimal("1.8"),
                    fatEnergyPercent = BigDecimal("25"),
                ),
            )

        assertThat(status.setupComplete).isTrue()
        assertThat(status.goal?.type).isEqualTo(WeightGoalType.LOSS)
        assertThat(status.program?.style).isEqualTo(ProgramStyle.COACHED)
        assertThat(status.program?.energyKcal).isPositive()

        val reviewDate = LocalDate.now().minusDays(1)
        tracking.saveReview(
            userId,
            NutritionDayReview(reviewDate, NutritionDayStatus.ESTIMATED_TOTAL, BigDecimal("2100")),
        )
        val analyzed = tracking.dailyNutrition(userId, reviewDate, reviewDate).single()
        assertThat(analyzed.analysisEnergyKcal).isEqualByComparingTo("2100")
        assertThat(analyzed.analysisWeight).isEqualByComparingTo("0.5")
    }
}
