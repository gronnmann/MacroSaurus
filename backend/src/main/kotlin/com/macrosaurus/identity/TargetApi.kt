package com.macrosaurus.identity

import com.macrosaurus.shared.InvalidOperationException
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import org.jooq.DSLContext
import org.springframework.stereotype.Service
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

@Service
class TargetService(
    private val db: DSLContext,
) {
    fun list(userId: String): List<NutrientTargetView> =
        db
            .fetch(
                """
                select n.code, n.display_name, n.unit, t.target_amount, t.minimum_amount, t.maximum_amount
                  from nutrient_definitions n
                  left join user_nutrient_targets t on t.nutrient_code = n.code and t.user_id = ?
                 order by n.sort_order, n.code
                """.trimIndent(),
                userId,
            ).map {
                NutrientTargetView(
                    it.get("code", String::class.java)!!,
                    it.get("display_name", String::class.java)!!,
                    it.get("unit", String::class.java)!!,
                    it.get("target_amount", BigDecimal::class.java),
                    it.get("minimum_amount", BigDecimal::class.java),
                    it.get("maximum_amount", BigDecimal::class.java),
                )
            }

    fun set(
        userId: String,
        nutrientCode: String,
        request: SetNutrientTargetRequest,
    ): NutrientTargetView {
        if (request.targetAmount == null && request.minimumAmount == null && request.maximumAmount == null) {
            throw InvalidOperationException("At least one target value is required")
        }
        if (request.minimumAmount != null && request.maximumAmount != null && request.minimumAmount > request.maximumAmount) {
            throw InvalidOperationException("Minimum target cannot exceed maximum target")
        }
        val exists = db.fetchExists(db.selectOne().from("nutrient_definitions").where("code = ?", nutrientCode))
        if (!exists) throw InvalidOperationException("Unknown nutrient code")
        db.execute(
            """
            insert into user_nutrient_targets(user_id, nutrient_code, target_amount, minimum_amount, maximum_amount)
            values (?, ?, ?, ?, ?)
            on conflict(user_id, nutrient_code) do update set
              target_amount = excluded.target_amount,
              minimum_amount = excluded.minimum_amount,
              maximum_amount = excluded.maximum_amount,
              updated_at = current_timestamp
            """.trimIndent(),
            userId,
            nutrientCode,
            request.targetAmount,
            request.minimumAmount,
            request.maximumAmount,
        )
        return list(userId).first { it.nutrientCode == nutrientCode }
    }

    fun clear(
        userId: String,
        nutrientCode: String,
    ) {
        db.execute("delete from user_nutrient_targets where user_id = ? and nutrient_code = ?", userId, nutrientCode)
    }
}

@RestController
@RequestMapping("/api/v1/me/targets")
class TargetController(
    private val users: UserContext,
    private val targets: TargetService,
) {
    @GetMapping
    fun list() = targets.list(users.userId())

    @PutMapping("/{nutrientCode}")
    fun set(
        @PathVariable nutrientCode: String,
        @Valid @RequestBody request: SetNutrientTargetRequest,
    ) = targets.set(users.userId(), nutrientCode, request)

    @DeleteMapping("/{nutrientCode}")
    fun clear(
        @PathVariable nutrientCode: String,
    ) = targets.clear(users.userId(), nutrientCode)
}
