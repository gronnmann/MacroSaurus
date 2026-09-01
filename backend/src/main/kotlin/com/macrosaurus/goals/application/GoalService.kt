package com.macrosaurus.goals.application

import com.macrosaurus.expenditure.ExpenditureEstimator
import com.macrosaurus.goals.persistence.JooqGoalRepository
import com.macrosaurus.goals.persistence.JooqStrategyRepository
import com.macrosaurus.measurements.WeightHistory
import com.macrosaurus.shared.InvalidOperationException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

internal enum class EnergyGoalMode { FIXED, MAINTENANCE, KCAL_DELTA, PERCENT_DELTA }

internal enum class MacroGoalMode { GUIDED, CUSTOM_GRAMS, PERCENT_SPLIT }

internal enum class GoalWeightBasis { LATEST_WEIGHT, MANUAL_WEIGHT }

internal data class GoalSettings(
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

internal data class SaveGoalSettingsCommand(
    val energyMode: EnergyGoalMode,
    val energyValue: BigDecimal? = null,
    val macroMode: MacroGoalMode,
    val proteinGPerKg: BigDecimal? = null,
    val fatEnergyPercent: BigDecimal? = null,
    val weightBasis: GoalWeightBasis? = null,
    val manualWeightKg: BigDecimal? = null,
    val proteinTargetG: BigDecimal? = null,
    val carbohydrateTargetG: BigDecimal? = null,
    val fatTargetG: BigDecimal? = null,
    val proteinEnergyPercent: BigDecimal? = null,
    val carbohydrateEnergyPercent: BigDecimal? = null,
)

internal data class ResolvedGoal(
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
internal class GoalService(
    private val repository: JooqGoalRepository,
    private val strategy: JooqStrategyRepository,
    private val expenditure: ExpenditureEstimator,
    private val measurements: WeightHistory,
) {
    fun get(userId: String): GoalSettings =
        repository.get(userId)
            ?: strategy.activeProgram(userId)?.let { program ->
                GoalSettings(
                    configured = program.energyKcal != null,
                    energyMode = EnergyGoalMode.FIXED,
                    energyValue = program.energyKcal,
                    macroMode = MacroGoalMode.CUSTOM_GRAMS,
                    proteinGPerKg = program.proteinGPerKg,
                    fatEnergyPercent = program.fatEnergyPercent,
                    weightBasis = GoalWeightBasis.LATEST_WEIGHT,
                    manualWeightKg = null,
                    proteinTargetG = program.proteinG,
                    carbohydrateTargetG = program.carbohydrateG,
                    fatTargetG = program.fatG,
                    proteinEnergyPercent = null,
                    carbohydrateEnergyPercent = null,
                )
            }
            ?: GoalSettings(false, null, null, null, null, null, null, null, null, null, null, null, null)

    fun save(
        userId: String,
        request: SaveGoalSettingsCommand,
    ): GoalSettings {
        validate(request)
        repository.save(userId, request)
        return get(userId)
    }

    fun resolve(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<ResolvedGoal> {
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
        settings: GoalSettings,
    ): ResolvedGoal {
        val program = strategy.programForDate(userId, date)
        if (program?.energyKcal != null) {
            return ResolvedGoal(
                date,
                program.energyKcal,
                program.proteinG,
                program.carbohydrateG,
                program.fatG,
                program.expenditureKcal,
                EnergyGoalMode.FIXED,
                emptyList(),
            )
        }
        if (!settings.configured) return ResolvedGoal(date, null, null, null, null, null, null, emptyList())
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
        return ResolvedGoal(
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

    private fun validate(request: SaveGoalSettingsCommand) {
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
