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

internal data class StoredTrackedAmount(
    val sourceRevisionId: UUID,
    val quantity: BigDecimal,
    val unit: String,
    val portionId: UUID?,
    val portionName: String?,
)

internal data class TrackableUse(
    val entryType: DiaryEntryType,
    val entityId: UUID,
    val currentRevisionId: UUID,
    val consumedAt: OffsetDateTime,
    val createdAt: OffsetDateTime,
)

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

    fun findLatestTrackedAmount(
        userId: String,
        type: DiaryEntryType,
        targetRevisionId: UUID,
    ): StoredTrackedAmount? {
        val identityJoin =
            when (type) {
                DiaryEntryType.FOOD -> {
                    """
                    join food_revisions used_revision on used_revision.id = diary.source_revision_id
                    join food_revisions target_revision
                      on target_revision.id = ? and target_revision.food_id = used_revision.food_id
                    """.trimIndent()
                }

                DiaryEntryType.RECIPE -> {
                    """
                    join recipe_revisions used_revision on used_revision.id = diary.source_revision_id
                    join recipe_revisions target_revision
                      on target_revision.id = ? and target_revision.recipe_id = used_revision.recipe_id
                    """.trimIndent()
                }

                DiaryEntryType.QUICK -> {
                    return null
                }
            }
        return db
            .fetchOne(
                """
                select diary.source_revision_id, diary.quantity, diary.unit, diary.portion_id,
                       portion.name as portion_name
                  from diary_entries diary
                  $identityJoin
                  left join portions portion on portion.id = diary.portion_id
                 where diary.user_id = ? and diary.entry_type = ?
                   and diary.quantity is not null and diary.unit is not null
                 order by diary.created_at desc
                 limit 1
                """.trimIndent(),
                targetRevisionId,
                userId,
                type.name,
            )?.let {
                StoredTrackedAmount(
                    it.get("source_revision_id", UUID::class.java)!!,
                    it.get("quantity", BigDecimal::class.java)!!,
                    it.get("unit", String::class.java)!!,
                    it.get("portion_id", UUID::class.java),
                    it.get("portion_name", String::class.java),
                )
            }
    }

    fun findRecentTrackableUses(
        userId: String,
        limit: Int,
    ): List<TrackableUse> =
        db
            .fetch(
                """
                select entry_type, entity_id, current_revision_id, consumed_at, created_at
                  from (
                    select distinct on (entry_type, entity_id)
                           entry_type, entity_id, current_revision_id, consumed_at, created_at
                      from (
                        ${sourceUseUnion("diary.user_id = ?")}
                      ) source_uses
                     order by entry_type, entity_id, created_at desc
                  ) latest_uses
                 order by created_at desc
                 limit ?
                """.trimIndent(),
                userId,
                userId,
                limit.coerceIn(1, 500),
            ).map(::toTrackableUse)

    fun findTrackableUsesSince(
        userId: String,
        since: OffsetDateTime,
    ): List<TrackableUse> =
        db
            .fetch(
                """
                select entry_type, entity_id, current_revision_id, consumed_at, created_at
                  from (
                    ${sourceUseUnion("diary.user_id = ? and diary.consumed_at >= cast(? as timestamptz)")}
                  ) source_uses
                 order by consumed_at desc
                """.trimIndent(),
                userId,
                since,
                userId,
                since,
            ).map(::toTrackableUse)

    private fun sourceUseUnion(predicate: String) =
        """
        select diary.entry_type, used_revision.food_id as entity_id,
               current_revision.id as current_revision_id, diary.consumed_at, diary.created_at
          from diary_entries diary
          join food_revisions used_revision on used_revision.id = diary.source_revision_id
          join lateral (
            select latest.id
              from food_revisions latest
             where latest.food_id = used_revision.food_id
             order by latest.revision desc
             limit 1
          ) current_revision on true
         where diary.entry_type = 'FOOD' and $predicate
        union all
        select diary.entry_type, used_revision.recipe_id as entity_id,
               current_revision.id as current_revision_id, diary.consumed_at, diary.created_at
          from diary_entries diary
          join recipe_revisions used_revision on used_revision.id = diary.source_revision_id
          join lateral (
            select latest.id
              from recipe_revisions latest
             where latest.recipe_id = used_revision.recipe_id
             order by latest.revision desc
             limit 1
          ) current_revision on true
         where diary.entry_type = 'RECIPE' and $predicate
        """.trimIndent()

    private fun toTrackableUse(record: org.jooq.Record) =
        TrackableUse(
            DiaryEntryType.valueOf(record.get("entry_type", String::class.java)!!),
            record.get("entity_id", UUID::class.java)!!,
            record.get("current_revision_id", UUID::class.java)!!,
            record.get("consumed_at", OffsetDateTime::class.java)!!,
            record.get("created_at", OffsetDateTime::class.java)!!,
        )

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
