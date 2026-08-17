package com.macrosaurus.measurements

import com.macrosaurus.identity.UserContext
import com.macrosaurus.shared.NotFoundException
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import org.jooq.DSLContext
import org.springframework.stereotype.Service
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
    val measuredAt: OffsetDateTime = OffsetDateTime.now(),
    val note: String? = null,
)

data class WeightView(
    val id: UUID,
    val weightKg: BigDecimal,
    val measuredAt: OffsetDateTime,
    val note: String?,
)

@Service
class MeasurementService(
    private val db: DSLContext,
) {
    fun list(
        userId: String,
        limit: Int = 100,
    ): List<WeightView> =
        db
            .fetch(
                "select id, weight_kg, measured_at, note from weight_measurements where user_id = ? order by measured_at desc limit ?",
                userId,
                limit.coerceIn(1, 500),
            ).map {
                WeightView(
                    it.get("id", UUID::class.java)!!,
                    it.get("weight_kg", BigDecimal::class.java)!!,
                    it.get("measured_at", OffsetDateTime::class.java)!!,
                    it.get("note", String::class.java),
                )
            }

    fun add(
        userId: String,
        request: AddWeightRequest,
    ): WeightView {
        val id = UUID.randomUUID()
        db.execute(
            "insert into weight_measurements(id, user_id, measured_at, weight_kg, note) values (?, ?, cast(? as timestamptz), ?, ?)",
            id,
            userId,
            request.measuredAt,
            request.weightKg,
            request.note?.take(500),
        )
        return list(userId).first { it.id == id }
    }

    fun delete(
        userId: String,
        id: UUID,
    ) {
        if (db.execute("delete from weight_measurements where id = ? and user_id = ?", id, userId) == 0) {
            throw NotFoundException("Weight measurement was not found")
        }
    }
}

@RestController
@RequestMapping("/api/v1/weight-measurements")
class MeasurementController(
    private val users: UserContext,
    private val measurements: MeasurementService,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "100") limit: Int,
    ) = measurements.list(users.userId(), limit)

    @PostMapping
    fun add(
        @Valid @RequestBody request: AddWeightRequest,
    ) = measurements.add(users.userId(), request)

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: UUID,
    ) = measurements.delete(users.userId(), id)
}
