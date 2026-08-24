package com.macrosaurus.goals

import com.macrosaurus.expenditure.ExpenditureService
import com.macrosaurus.identity.UserContext
import com.macrosaurus.measurements.MeasurementService
import com.macrosaurus.shared.InvalidOperationException
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import org.jooq.DSLContext
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class EnergyGoalMode { FIXED, MAINTENANCE, KCAL_DELTA, PERCENT_DELTA }

enum class MacroGoalMode { GUIDED, CUSTOM_GRAMS, PERCENT_SPLIT }

enum class GoalWeightBasis { LATEST_WEIGHT, MANUAL_WEIGHT }

data class GoalSettingsView(
    val configured: Boolean,
    val energyMode: EnergyGoalMode?,
    val energyValue: BigDecimal?,
    val macroMode: MacroGoalMode?,
    val proteinGPerKg: BigDecimal?,
    val fatEnergyPercent: BigDecimal?,
    val weightBasis: GoalWeightBasis?,
    val manualWeightKg: BigDecimal?,
    val proteinTargetG: BigDecimal?,
    val carbohydrateTargetG: BigDecimal?,
    val fatTargetG: BigDecimal?,
    val proteinEnergyPercent: BigDecimal?,
    val carbohydrateEnergyPercent: BigDecimal?,
)

data class SaveGoalSettingsRequest(
    val energyMode: EnergyGoalMode,
    val energyValue: BigDecimal? = null,
    val macroMode: MacroGoalMode,
    @field:DecimalMin("0.1") val proteinGPerKg: BigDecimal? = null,
    @field:DecimalMin("0") val fatEnergyPercent: BigDecimal? = null,
    val weightBasis: GoalWeightBasis? = null,
    @field:DecimalMin("10") val manualWeightKg: BigDecimal? = null,
    @field:DecimalMin("0") val proteinTargetG: BigDecimal? = null,
    @field:DecimalMin("0") val carbohydrateTargetG: BigDecimal? = null,
    @field:DecimalMin("0") val fatTargetG: BigDecimal? = null,
    @field:DecimalMin("0") val proteinEnergyPercent: BigDecimal? = null,
    @field:DecimalMin("0") val carbohydrateEnergyPercent: BigDecimal? = null,
)

data class ResolvedGoalView(
    val date: LocalDate,
    val energyKcal: BigDecimal?,
    val proteinG: BigDecimal?,
    val carbohydrateG: BigDecimal?,
    val fatG: BigDecimal?,
    val expenditureKcal: BigDecimal?,
    val energyRule: EnergyGoalMode?,
    val warnings: List<String>,
)

@Service
class GoalService(
    private val db: DSLContext,
    private val expenditure: ExpenditureService,
    private val measurements: MeasurementService,
) {
    fun get(userId: String): GoalSettingsView {
        val record =
            db.fetchOne("select * from user_goal_settings where user_id = ?", userId)
                ?: return GoalSettingsView(false, null, null, null, null, null, null, null, null, null, null, null, null)
        return GoalSettingsView(
            true,
            record.get("energy_mode", String::class.java)?.let(EnergyGoalMode::valueOf),
            record.get("energy_value", BigDecimal::class.java),
            record.get("macro_mode", String::class.java)?.let(MacroGoalMode::valueOf),
            record.get("protein_g_per_kg", BigDecimal::class.java),
            record.get("fat_energy_percent", BigDecimal::class.java),
            record.get("weight_basis", String::class.java)?.let(GoalWeightBasis::valueOf),
            record.get("manual_weight_kg", BigDecimal::class.java),
            record.get("protein_target_g", BigDecimal::class.java),
            record.get("carbohydrate_target_g", BigDecimal::class.java),
            record.get("fat_target_g", BigDecimal::class.java),
            record.get("protein_energy_percent", BigDecimal::class.java),
            record.get("carbohydrate_energy_percent", BigDecimal::class.java),
        )
    }

    fun save(
        userId: String,
        request: SaveGoalSettingsRequest,
    ): GoalSettingsView {
        validate(request)
        db.execute(
            """
            insert into user_goal_settings(
                user_id, energy_mode, energy_value, macro_mode, protein_g_per_kg, fat_energy_percent,
                weight_basis, manual_weight_kg, protein_target_g, carbohydrate_target_g, fat_target_g,
                protein_energy_percent, carbohydrate_energy_percent
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict(user_id) do update set
                energy_mode = excluded.energy_mode, energy_value = excluded.energy_value,
                macro_mode = excluded.macro_mode, protein_g_per_kg = excluded.protein_g_per_kg,
                fat_energy_percent = excluded.fat_energy_percent, weight_basis = excluded.weight_basis,
                manual_weight_kg = excluded.manual_weight_kg, protein_target_g = excluded.protein_target_g,
                carbohydrate_target_g = excluded.carbohydrate_target_g, fat_target_g = excluded.fat_target_g,
                protein_energy_percent = excluded.protein_energy_percent,
                carbohydrate_energy_percent = excluded.carbohydrate_energy_percent,
                updated_at = current_timestamp
            """.trimIndent(),
            userId,
            request.energyMode.name,
            request.energyValue,
            request.macroMode.name,
            request.proteinGPerKg,
            request.fatEnergyPercent,
            request.weightBasis?.name,
            request.manualWeightKg,
            request.proteinTargetG,
            request.carbohydrateTargetG,
            request.fatTargetG,
            request.proteinEnergyPercent,
            request.carbohydrateEnergyPercent,
        )
        return get(userId)
    }

    fun resolve(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<ResolvedGoalView> {
        if (to.isBefore(from) || to.isAfter(from.plusDays(30))) {
            throw InvalidOperationException("Goal range must be between 1 and 31 days")
        }
        val settings = get(userId)
        return generateSequence(from) { it.plusDays(1).takeUnless { next -> next.isAfter(to) } }
            .map { resolveDay(userId, it, settings) }
            .toList()
    }

    private fun resolveDay(
        userId: String,
        date: LocalDate,
        settings: GoalSettingsView,
    ): ResolvedGoalView {
        if (!settings.configured) return ResolvedGoalView(date, null, null, null, null, null, null, emptyList())
        val warnings = mutableListOf<String>()
        val estimate = expenditure.estimate(userId, date)
        val maintenance = estimate.suggestedKcal
        val targetEnergy =
            when (settings.energyMode) {
                EnergyGoalMode.FIXED -> {
                    settings.energyValue
                }

                EnergyGoalMode.MAINTENANCE -> {
                    maintenance
                }

                EnergyGoalMode.KCAL_DELTA -> {
                    maintenance?.add(settings.energyValue ?: BigDecimal.ZERO)
                }

                EnergyGoalMode.PERCENT_DELTA -> {
                    maintenance?.multiply(
                        BigDecimal.ONE.add(
                            (settings.energyValue ?: BigDecimal.ZERO)
                                .divide(BigDecimal("100"), 8, RoundingMode.HALF_UP),
                        ),
                    )
                }

                else -> {
                    null
                }
            }?.takeIf { it > BigDecimal.ZERO }
        if (settings.energyMode != EnergyGoalMode.FIXED &&
            maintenance == null
        ) {
            warnings += "Complete your profile and add a weigh-in to resolve a relative calorie goal."
        }
        if (targetEnergy == null) warnings += "A positive calorie target is required before macros can be calculated."

        var protein: BigDecimal? = null
        var carbohydrate: BigDecimal? = null
        var fat: BigDecimal? = null
        when (settings.macroMode) {
            MacroGoalMode.CUSTOM_GRAMS -> {
                protein = settings.proteinTargetG
                carbohydrate = settings.carbohydrateTargetG
                fat = settings.fatTargetG
            }

            MacroGoalMode.PERCENT_SPLIT -> {
                targetEnergy?.let { energy ->
                    protein = gramsFromPercent(energy, settings.proteinEnergyPercent, BigDecimal("4"))
                    carbohydrate = gramsFromPercent(energy, settings.carbohydrateEnergyPercent, BigDecimal("4"))
                    val fatPercent =
                        BigDecimal("100")
                            .subtract(settings.proteinEnergyPercent ?: BigDecimal.ZERO)
                            .subtract(settings.carbohydrateEnergyPercent ?: BigDecimal.ZERO)
                    fat = gramsFromPercent(energy, fatPercent, BigDecimal("9"))
                }
            }

            MacroGoalMode.GUIDED -> {
                targetEnergy?.let { energy ->
                    val weight =
                        if (settings.weightBasis == GoalWeightBasis.MANUAL_WEIGHT) {
                            settings.manualWeightKg
                        } else {
                            measurements.list(userId, 200).firstOrNull { !it.measuredAt.toLocalDate().isAfter(date) }?.weightKg
                        }
                    if (weight == null) {
                        warnings += "Add a weigh-in or manual reference weight to resolve protein."
                    } else {
                        protein = weight.multiply(settings.proteinGPerKg ?: BigDecimal("1.6"))
                    }
                    fat = gramsFromPercent(energy, settings.fatEnergyPercent, BigDecimal("9"))
                    val resolvedProtein = protein
                    val resolvedFat = fat
                    if (resolvedProtein != null && resolvedFat != null) {
                        val remaining =
                            energy
                                .subtract(resolvedProtein.multiply(BigDecimal("4")))
                                .subtract(resolvedFat.multiply(BigDecimal("9")))
                        if (remaining < BigDecimal.ZERO) {
                            warnings += "Protein and fat exceed the calorie target; adjust the goal sliders."
                        } else {
                            carbohydrate = remaining.divide(BigDecimal("4"), 4, RoundingMode.HALF_UP)
                        }
                    }
                }
            }

            null -> {}
        }
        return ResolvedGoalView(
            date,
            targetEnergy?.setScale(0, RoundingMode.HALF_UP),
            protein?.setScale(1, RoundingMode.HALF_UP),
            carbohydrate?.setScale(1, RoundingMode.HALF_UP),
            fat?.setScale(1, RoundingMode.HALF_UP),
            maintenance?.setScale(0, RoundingMode.HALF_UP),
            settings.energyMode,
            warnings.distinct(),
        )
    }

    private fun gramsFromPercent(
        energy: BigDecimal,
        percent: BigDecimal?,
        kcalPerGram: BigDecimal,
    ): BigDecimal? = percent?.let { energy.multiply(it).divide(BigDecimal("100"), 8, RoundingMode.HALF_UP).divide(kcalPerGram, 4, RoundingMode.HALF_UP) }

    private fun validate(request: SaveGoalSettingsRequest) {
        if (request.energyMode != EnergyGoalMode.MAINTENANCE && request.energyValue == null) {
            throw InvalidOperationException("This energy goal needs a value")
        }
        if (request.energyMode == EnergyGoalMode.FIXED &&
            request.energyValue!! <= BigDecimal.ZERO
        ) {
            throw InvalidOperationException("Fixed calorie goal must be positive")
        }
        when (request.macroMode) {
            MacroGoalMode.GUIDED -> {
                if (request.proteinGPerKg == null ||
                    request.fatEnergyPercent == null
                ) {
                    throw InvalidOperationException("Guided goals need protein and fat settings")
                }
                if (request.weightBasis == null) {
                    throw InvalidOperationException("Choose a protein weight basis")
                }
                if (request.weightBasis == GoalWeightBasis.MANUAL_WEIGHT &&
                    request.manualWeightKg == null
                ) {
                    throw InvalidOperationException("Manual reference weight is required")
                }
            }

            MacroGoalMode.CUSTOM_GRAMS -> {
                if (listOf(request.proteinTargetG, request.carbohydrateTargetG, request.fatTargetG).any { it == null }) {
                    throw InvalidOperationException("Custom gram goals need all three macros")
                }
            }

            MacroGoalMode.PERCENT_SPLIT -> {
                if (request.proteinEnergyPercent == null ||
                    request.carbohydrateEnergyPercent == null
                ) {
                    throw InvalidOperationException("Percentage goals need protein and carbohydrate percentages")
                }
                val fat = BigDecimal("100").subtract(request.proteinEnergyPercent).subtract(request.carbohydrateEnergyPercent)
                if (fat < BigDecimal.ZERO) throw InvalidOperationException("Macro percentages cannot exceed 100%")
            }
        }
    }
}

@RestController
@RequestMapping("/api/v1/me/goals")
class GoalController(
    private val users: UserContext,
    private val goals: GoalService,
) {
    @GetMapping
    fun get() = goals.get(users.userId())

    @PutMapping
    fun save(
        @Valid @RequestBody request: SaveGoalSettingsRequest,
    ) = goals.save(users.userId(), request)

    @GetMapping("/resolved")
    fun resolved(
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
    ) = goals.resolve(users.userId(), from, to)
}
