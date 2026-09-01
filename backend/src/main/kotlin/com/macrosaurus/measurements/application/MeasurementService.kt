package com.macrosaurus.measurements.application

import com.macrosaurus.measurements.NewWeightMeasurement
import com.macrosaurus.measurements.WeightHistory
import com.macrosaurus.measurements.WeightMeasurement
import com.macrosaurus.measurements.WeightRecorder
import com.macrosaurus.measurements.persistence.JooqMeasurementRepository
import com.macrosaurus.shared.NotFoundException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID

internal data class AddWeightCommand(
    val weightKg: BigDecimal,
    val measuredAt: OffsetDateTime?,
    val note: String?,
)

@Service
internal class MeasurementService(
    private val repository: JooqMeasurementRepository,
    private val clock: Clock,
) : WeightHistory,
    WeightRecorder {
    override fun list(
        userId: String,
        limit: Int,
    ): List<WeightMeasurement> = repository.list(userId, limit)

    fun add(
        userId: String,
        command: AddWeightCommand,
    ): WeightMeasurement {
        val measurement =
            WeightMeasurement(
                UUID.randomUUID(),
                command.weightKg,
                command.measuredAt ?: OffsetDateTime.now(clock),
                command.note?.take(500),
            )
        repository.insert(userId, measurement)
        return repository.get(userId, measurement.id) ?: throw NotFoundException("Weight measurement was not found")
    }

    override fun record(
        userId: String,
        measurement: NewWeightMeasurement,
    ): WeightMeasurement = add(userId, AddWeightCommand(measurement.weightKg, measurement.measuredAt, measurement.note))

    fun delete(
        userId: String,
        id: UUID,
    ) {
        if (repository.delete(userId, id) == 0) throw NotFoundException("Weight measurement was not found")
    }
}
