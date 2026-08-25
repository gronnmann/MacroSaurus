package com.macrosaurus.identity.persistence

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal

internal data class TargetAmounts(
    val target: BigDecimal?,
    val minimum: BigDecimal?,
    val maximum: BigDecimal?,
)

@Repository
internal class JooqTargetRepository(
    private val db: DSLContext,
) {
    fun findAll(userId: String): Map<String, TargetAmounts> =
        db
            .fetch(
                "select nutrient_code, target_amount, minimum_amount, maximum_amount from user_nutrient_targets where user_id = ?",
                userId,
            ).associate {
                it.get("nutrient_code", String::class.java)!! to
                    TargetAmounts(
                        it.get("target_amount", BigDecimal::class.java),
                        it.get("minimum_amount", BigDecimal::class.java),
                        it.get("maximum_amount", BigDecimal::class.java),
                    )
            }

    fun save(
        userId: String,
        nutrientCode: String,
        amounts: TargetAmounts,
    ) {
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
            amounts.target,
            amounts.minimum,
            amounts.maximum,
        )
    }

    fun delete(
        userId: String,
        nutrientCode: String,
    ) {
        db.execute("delete from user_nutrient_targets where user_id = ? and nutrient_code = ?", userId, nutrientCode)
    }
}
