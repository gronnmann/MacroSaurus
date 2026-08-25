package com.macrosaurus.goals.web

import com.macrosaurus.goals.application.EnergyGoalMode
import com.macrosaurus.goals.application.GoalService
import com.macrosaurus.goals.application.GoalSettings
import com.macrosaurus.goals.application.GoalWeightBasis
import com.macrosaurus.goals.application.MacroGoalMode
import com.macrosaurus.goals.application.ResolvedGoal
import com.macrosaurus.goals.application.SaveGoalSettingsCommand
import com.macrosaurus.shared.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

internal data class GoalSettingsView(
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

internal data class SaveGoalSettingsRequest(
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

internal data class ResolvedGoalView(
    val date: LocalDate,
    val energyKcal: BigDecimal?,
    val proteinG: BigDecimal?,
    val carbohydrateG: BigDecimal?,
    val fatG: BigDecimal?,
    val expenditureKcal: BigDecimal?,
    val energyRule: EnergyGoalMode?,
    val warnings: List<String>,
)

private fun GoalSettings.toView() =
    GoalSettingsView(
        configured,
        energyMode,
        energyValue,
        macroMode,
        proteinGPerKg,
        fatEnergyPercent,
        weightBasis,
        manualWeightKg,
        proteinTargetG,
        carbohydrateTargetG,
        fatTargetG,
        proteinEnergyPercent,
        carbohydrateEnergyPercent,
    )

private fun ResolvedGoal.toView() = ResolvedGoalView(date, energyKcal, proteinG, carbohydrateG, fatG, expenditureKcal, energyRule, warnings)

private fun SaveGoalSettingsRequest.toCommand() =
    SaveGoalSettingsCommand(
        energyMode,
        energyValue,
        macroMode,
        proteinGPerKg,
        fatEnergyPercent,
        weightBasis,
        manualWeightKg,
        proteinTargetG,
        carbohydrateTargetG,
        fatTargetG,
        proteinEnergyPercent,
        carbohydrateEnergyPercent,
    )

@RestController
@RequestMapping("/api/v1/me/goals")
internal class GoalController(
    private val users: CurrentUser,
    private val goals: GoalService,
) {
    @GetMapping
    fun get() = goals.get(users.userId()).toView()

    @PutMapping
    fun save(
        @Valid @RequestBody request: SaveGoalSettingsRequest,
    ) = goals.save(users.userId(), request.toCommand()).toView()

    @GetMapping("/resolved")
    fun resolved(
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
    ) = goals.resolve(users.userId(), from, to).map { it.toView() }
}
