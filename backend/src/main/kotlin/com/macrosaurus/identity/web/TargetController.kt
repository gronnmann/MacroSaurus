package com.macrosaurus.identity.web

import com.macrosaurus.identity.application.NutrientTarget
import com.macrosaurus.identity.application.SetNutrientTargetCommand
import com.macrosaurus.identity.application.TargetService
import com.macrosaurus.shared.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

data class NutrientTargetView(
    val nutrientCode: String,
    val displayName: String,
    val unit: String,
    val targetAmount: BigDecimal?,
    val minimumAmount: BigDecimal?,
    val maximumAmount: BigDecimal?,
)

data class SetNutrientTargetRequest(
    @field:DecimalMin("0") val targetAmount: BigDecimal? = null,
    @field:DecimalMin("0") val minimumAmount: BigDecimal? = null,
    @field:DecimalMin("0") val maximumAmount: BigDecimal? = null,
)

private fun NutrientTarget.toView() = NutrientTargetView(nutrientCode, displayName, unit, targetAmount, minimumAmount, maximumAmount)

@RestController
@RequestMapping("/api/v1/me/targets")
internal class TargetController(
    private val users: CurrentUser,
    private val targets: TargetService,
) {
    @GetMapping
    fun list() = targets.list(users.userId()).map { it.toView() }

    @PutMapping("/{nutrientCode}")
    fun set(
        @PathVariable nutrientCode: String,
        @Valid @RequestBody request: SetNutrientTargetRequest,
    ) = targets
        .set(
            users.userId(),
            nutrientCode,
            SetNutrientTargetCommand(request.targetAmount, request.minimumAmount, request.maximumAmount),
        ).toView()

    @DeleteMapping("/{nutrientCode}")
    fun clear(
        @PathVariable nutrientCode: String,
    ) = targets.clear(users.userId(), nutrientCode)
}
