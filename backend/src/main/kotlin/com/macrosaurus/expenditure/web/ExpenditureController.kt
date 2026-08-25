package com.macrosaurus.expenditure.web

import com.macrosaurus.expenditure.EnergyEstimate
import com.macrosaurus.expenditure.application.ExpenditureService
import com.macrosaurus.shared.CurrentUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

data class EnergyEstimateView(
    val date: LocalDate,
    val baselineKcal: BigDecimal?,
    val adaptiveKcal: BigDecimal?,
    val suggestedKcal: BigDecimal?,
    val confidence: String,
    val adaptiveEligible: Boolean,
    val algorithmVersion: String,
    val explanation: List<String>,
    val requirements: Map<String, Int>,
)

private fun EnergyEstimate.toView() = EnergyEstimateView(date, baselineKcal, adaptiveKcal, suggestedKcal, confidence, adaptiveEligible, algorithmVersion, explanation, requirements)

@RestController
@RequestMapping("/api/v1/expenditure-estimates")
internal class ExpenditureController(
    private val users: CurrentUser,
    private val expenditure: ExpenditureService,
) {
    @GetMapping("/current")
    fun current(
        @RequestParam(required = false) date: LocalDate?,
        @RequestParam(defaultValue = "false") persist: Boolean,
    ) = (
        date?.let { expenditure.estimate(users.userId(), it, persist) }
            ?: expenditure.current(users.userId(), persist)
    ).toView()
}
