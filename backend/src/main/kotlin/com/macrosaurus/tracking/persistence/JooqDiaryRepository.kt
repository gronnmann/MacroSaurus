package com.macrosaurus.tracking.persistence

import com.macrosaurus.shared.JsonCodec
import com.macrosaurus.shared.NutrientValues
import com.macrosaurus.tracking.DiaryEntrySnapshot
import com.macrosaurus.tracking.DiaryEntryType
import com.macrosaurus.tracking.Meal
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Repository
internal class JooqDiaryRepository(
    private val db: DSLContext,
    private val json: JsonCodec,
) {
    fun insert(
        id: UUID,
        userId: String,
        localDate: LocalDate,
        consumedAt: OffsetDateTime,
        meal: Meal,
        displayName: String,
        type: DiaryEntryType,
        revisionId: UUID?,
        quantity: BigDecimal?,
        unit: String?,
        portionId: UUID?,
        nutrients: NutrientValues,
    ) {
        db.execute(
            """
            insert into diary_entries(id, user_id, local_date, consumed_at, meal, display_name,
                                      entry_type, source_revision_id, quantity, unit, portion_id, nutrients)
            values (?, ?, ?, cast(? as timestamptz), ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb))
            """.trimIndent(),
            id,
            userId,
            localDate,
            consumedAt,
            meal.name,
            displayName,
            type.name,
            revisionId,
            quantity,
            unit,
            portionId,
            json.writeNutrients(nutrients),
        )
    }

    fun update(
        userId: String,
        id: UUID,
        localDate: LocalDate,
        consumedAt: OffsetDateTime,
        meal: Meal,
        displayName: String,
        quantity: BigDecimal,
        unit: String,
        portionId: UUID?,
        nutrients: NutrientValues,
    ) {
        db.execute(
            """
            update diary_entries set local_date = ?, consumed_at = cast(? as timestamptz), meal = ?, display_name = ?,
                                     quantity = ?, unit = ?, portion_id = ?, nutrients = cast(? as jsonb)
             where id = ? and user_id = ?
            """.trimIndent(),
            localDate,
            consumedAt,
            meal.name,
            displayName,
            quantity,
            unit,
            portionId,
            json.writeNutrients(nutrients),
            id,
            userId,
        )
    }

    fun delete(
        userId: String,
        id: UUID,
    ): Int = db.execute("delete from diary_entries where id = ? and user_id = ?", id, userId)

    fun find(
        userId: String,
        id: UUID,
    ): DiaryEntrySnapshot? =
        db
            .fetchOne(
                """
                select id, local_date, consumed_at, meal, display_name, entry_type, source_revision_id,
                       quantity, unit, portion_id, nutrients::text as nutrients
                  from diary_entries where id = ? and user_id = ?
                """.trimIndent(),
                id,
                userId,
            )?.let(::toEntry)

    fun findBetween(
        userId: String,
        from: LocalDate,
        to: LocalDate,
    ): List<DiaryEntrySnapshot> =
        db
            .fetch(
                """
                select id, local_date, consumed_at, meal, display_name, entry_type, source_revision_id,
                       quantity, unit, portion_id, nutrients::text as nutrients
                  from diary_entries
                 where user_id = ? and local_date between ? and ?
                 order by local_date, consumed_at, created_at
                """.trimIndent(),
                userId,
                from,
                to,
            ).map(::toEntry)

    private fun toEntry(record: org.jooq.Record) =
        DiaryEntrySnapshot(
            record.get("id", UUID::class.java)!!,
            record.get("local_date", LocalDate::class.java)!!,
            record.get("consumed_at", OffsetDateTime::class.java)!!,
            Meal.valueOf(record.get("meal", String::class.java)!!),
            record.get("display_name", String::class.java)!!,
            DiaryEntryType.valueOf(record.get("entry_type", String::class.java)!!),
            record.get("source_revision_id", UUID::class.java),
            record.get("quantity", BigDecimal::class.java),
            record.get("unit", String::class.java),
            record.get("portion_id", UUID::class.java),
            json.nutrients(record.get("nutrients", String::class.java)!!).values,
        )
}
