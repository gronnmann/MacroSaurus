package com.macrosaurus.tracking

import com.macrosaurus.catalog.FoodCatalog
import com.macrosaurus.catalog.FoodCreator
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodResolver
import com.macrosaurus.catalog.FoodSnapshot
import com.macrosaurus.catalog.NutrientCatalog
import com.macrosaurus.catalog.NutrientDefinition
import com.macrosaurus.catalog.SourceKind
import com.macrosaurus.identity.ProfileReader
import com.macrosaurus.recipes.RecipeReader
import com.macrosaurus.shared.InvalidOperationException
import com.macrosaurus.shared.NutrientValues
import com.macrosaurus.tracking.application.QuickTrackCommand
import com.macrosaurus.tracking.application.TrackingService
import com.macrosaurus.tracking.application.UpdateDiaryEntryCommand
import com.macrosaurus.tracking.persistence.JooqDiaryRepository
import com.macrosaurus.tracking.persistence.JooqNutritionDayReviewRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

class QuickTrackingTest {
    private var stored: DiaryEntrySnapshot? = null
    private var savedFood: FoodDraft? = null
    private val extra = mapOf("saturated_fat_g" to BigDecimal("2.5"), "vitamin_c_mg" to BigDecimal("35"), "sodium_mg" to BigDecimal("400"))
    private val repository =
        mock(JooqDiaryRepository::class.java) { call ->
            when (call.method.name) {
                "insert" -> {
                    stored =
                        DiaryEntrySnapshot(
                            call.getArgument(0),
                            call.getArgument(2),
                            call.getArgument(3),
                            call.getArgument(4),
                            call.getArgument(5),
                            null,
                            call.getArgument(7),
                            call.getArgument(8),
                            null,
                            call.getArgument<NutrientValues>(10).values,
                        )
                    null
                }

                "find" -> {
                    stored
                }

                "update" -> {
                    stored = stored!!.copy(nutrients = call.getArgument<NutrientValues>(8).values)
                    null
                }

                else -> {
                    null
                }
            }
        }
    private val creator =
        object : FoodCreator {
            override fun create(
                userId: String,
                draft: FoodDraft,
                source: SourceKind,
                externalId: String?,
            ): FoodSnapshot {
                savedFood = draft
                return FoodSnapshot(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    1,
                    draft.name,
                    null,
                    null,
                    source,
                    draft.basisType,
                    draft.basisAmount,
                    draft.basisUnit,
                    null,
                    draft.nutrients,
                    emptyList(),
                    OffsetDateTime.now(),
                )
            }
        }
    private val nutrientCatalog =
        mock(NutrientCatalog::class.java).also {
            `when`(it.nutrients()).thenReturn(extra.keys.map { code -> NutrientDefinition(code, code, "MICRO", "g", 0) })
        }
    private val service =
        TrackingService(
            repository,
            mock(JooqNutritionDayReviewRepository::class.java),
            mock(FoodCatalog::class.java),
            mock(FoodResolver::class.java),
            creator,
            mock(RecipeReader::class.java),
            mock(ProfileReader::class.java),
            Clock.systemUTC(),
            nutrientCatalog,
        )
    private val command =
        QuickTrackCommand(
            "Lunch",
            LocalDate.of(2026, 9, 14),
            proteinG = BigDecimal.TEN,
            carbohydrateG = BigDecimal("20"),
            fatG = BigDecimal("5"),
            fiberG = BigDecimal("3"),
            saveAsFood = true,
            additionalNutrients = extra,
        )

    @Test
    fun `extra nutrients are logged and included when saving as a food`() {
        val result = service.quickTrack("user", command)
        assertThat(result.entry.nutrients).containsAllEntriesOf(extra)
        assertThat(result.entry.nutrients["fiber_g"]).isEqualByComparingTo("3")
        assertThat(result.calculatedCalories).isEqualByComparingTo("165")
        assertThat(savedFood!!.nutrients).isEqualTo(result.entry.nutrients)
    }

    @Test
    fun `legacy edits preserve extras while explicit edits can change and clear them`() {
        val result = service.quickTrack("user", command)
        val edit =
            UpdateDiaryEntryCommand(
                command.localDate,
                result.entry.consumedAt,
                name = "Lunch",
                proteinG = BigDecimal.TEN,
                carbohydrateG = BigDecimal("20"),
                fatG = BigDecimal("5"),
                fiberG = BigDecimal("3"),
            )
        assertThat(service.update("user", result.entry.id, edit).nutrients).containsAllEntriesOf(extra)
        val edited = service.update("user", result.entry.id, edit.copy(additionalNutrients = mapOf("saturated_fat_g" to BigDecimal.ZERO)))
        assertThat(edited.nutrients["saturated_fat_g"]).isEqualByComparingTo("0")
        assertThat(edited.nutrients).doesNotContainKeys("vitamin_c_mg", "sodium_mg")
        val cleared = service.update("user", result.entry.id, edit.copy(additionalNutrients = emptyMap()))
        assertThat(cleared.nutrients).doesNotContainKey("saturated_fat_g")
    }

    @Test
    fun `invalid extra nutrients are rejected before saving`() {
        listOf(mapOf("saturated_fat_g" to BigDecimal("-1")), mapOf("invented" to BigDecimal.ONE), mapOf("fat_g" to BigDecimal.ONE)).forEach { invalid ->
            assertThatThrownBy { service.quickTrack("user", command.copy(additionalNutrients = invalid)) }.isInstanceOf(InvalidOperationException::class.java)
        }
        assertThat(stored).isNull()
        assertThat(savedFood).isNull()
    }
}
