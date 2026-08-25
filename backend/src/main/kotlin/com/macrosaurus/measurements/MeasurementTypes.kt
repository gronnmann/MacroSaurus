package com.macrosaurus.measurements

import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class WeightMeasurement(
    val id: UUID,
    val weightKg: BigDecimal,
    val measuredAt: OffsetDateTime,
    val note: String?,
)

fun interface WeightHistory {
    fun list(
        userId: String,
        limit: Int,
    ): List<WeightMeasurement>
}
