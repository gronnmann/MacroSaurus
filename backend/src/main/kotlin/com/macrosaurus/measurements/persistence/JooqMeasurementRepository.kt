package com.macrosaurus.measurements.persistence

import com.macrosaurus.measurements.WeightMeasurement
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Repository
internal class JooqMeasurementRepository(
    private val db: DSLContext,
) {
    fun list(
        userId: String,
        limit: Int,
    ): List<WeightMeasurement> =
        db
            .fetch(
                "select id, weight_kg, measured_at, note from weight_measurements where user_id = ? order by measured_at desc limit ?",
                userId,
                limit.coerceIn(1, 500),
            ).map(::toMeasurement)

    fun insert(
        userId: String,
        measurement: WeightMeasurement,
    ) {
        db.execute(
            "insert into weight_measurements(id, user_id, measured_at, weight_kg, note) values (?, ?, cast(? as timestamptz), ?, ?)",
            measurement.id,
            userId,
            measurement.measuredAt,
            measurement.weightKg,
            measurement.note,
        )
    }

    fun delete(
        userId: String,
        id: UUID,
    ): Int = db.execute("delete from weight_measurements where id = ? and user_id = ?", id, userId)

    fun get(
        userId: String,
        id: UUID,
    ): WeightMeasurement? =
        db
            .fetchOne(
                "select id, weight_kg, measured_at, note from weight_measurements where id = ? and user_id = ?",
                id,
                userId,
            )?.let(::toMeasurement)

    private fun toMeasurement(record: org.jooq.Record) =
        WeightMeasurement(
            record.get("id", UUID::class.java)!!,
            record.get("weight_kg", BigDecimal::class.java)!!,
            record.get("measured_at", OffsetDateTime::class.java)!!,
            record.get("note", String::class.java),
        )
}
