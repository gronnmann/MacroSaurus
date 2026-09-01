package com.macrosaurus.tracking.persistence

import com.macrosaurus.tracking.NutritionDayReview
import com.macrosaurus.tracking.NutritionDayStatus
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
internal class JooqNutritionDayReviewRepository(
    private val db: DSLContext,
) {
    fun save(
        userId: String,
        review: NutritionDayReview,
    ) {
        db.execute(
            """
            insert into nutrition_day_reviews(user_id, local_date, status, estimated_total_kcal)
            values (?, ?, ?, ?)
            on conflict(user_id, local_date) do update set
                status = excluded.status,
                estimated_total_kcal = excluded.estimated_total_kcal,
                updated_at = current_timestamp
            """.trimIndent(),
            userId,
            review.date,
            review.status.name,
            review.estimatedTotalKcal,
        )
    }

    fun findBetween(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, NutritionDayReview> =
        db
            .fetch(
                """
                select local_date, status, estimated_total_kcal
                  from nutrition_day_reviews
                 where user_id = ? and local_date between ? and ?
                """.trimIndent(),
                userId,
                from,
                to,
            ).associate { record ->
                val date = record.get("local_date", LocalDate::class.java)!!
                date to
                    NutritionDayReview(
                        date,
                        NutritionDayStatus.valueOf(record.get("status", String::class.java)!!),
                        record.get("estimated_total_kcal", BigDecimal::class.java),
                    )
            }
}
