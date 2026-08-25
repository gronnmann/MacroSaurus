package com.macrosaurus.measurements.web

import com.macrosaurus.measurements.WeightMeasurement
import com.macrosaurus.measurements.application.AddWeightCommand
import com.macrosaurus.measurements.application.MeasurementService
import com.macrosaurus.shared.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class AddWeightRequest(
    @field:DecimalMin("10") @field:DecimalMax("700") val weightKg: BigDecimal,
    val measuredAt: OffsetDateTime? = null,
    val note: String? = null,
)

data class WeightView(
    val id: UUID,
    val weightKg: BigDecimal,
    val measuredAt: OffsetDateTime,
    val note: String?,
)

private fun WeightMeasurement.toView() = WeightView(id, weightKg, measuredAt, note)

@RestController
@RequestMapping("/api/v1/weight-measurements")
internal class MeasurementController(
    private val users: CurrentUser,
    private val measurements: MeasurementService,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "100") limit: Int,
    ) = measurements.list(users.userId(), limit).map { it.toView() }

    @PostMapping
    fun add(
        @Valid @RequestBody request: AddWeightRequest,
    ) = measurements.add(users.userId(), AddWeightCommand(request.weightKg, request.measuredAt, request.note)).toView()

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
    ) = measurements.delete(users.userId(), id)
}
