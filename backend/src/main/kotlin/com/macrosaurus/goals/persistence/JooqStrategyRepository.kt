package com.macrosaurus.goals.persistence

import com.macrosaurus.goals.GoalStatus
import com.macrosaurus.goals.NutritionProgramSnapshot
import com.macrosaurus.goals.ProgramSource
import com.macrosaurus.goals.ProgramStyle
import com.macrosaurus.goals.WeightGoalSnapshot
import com.macrosaurus.goals.WeightGoalType
import com.macrosaurus.goals.application.CheckInProposal
import com.macrosaurus.goals.application.CheckInStatus
import com.macrosaurus.goals.application.CoachingSetupDraft
import com.macrosaurus.goals.application.StoredCheckIn
import com.macrosaurus.shared.JsonCodec
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Repository
internal class JooqStrategyRepository(
    private val db: DSLContext,
    private val json: JsonCodec,
) {
    fun activeGoal(userId: String): WeightGoalSnapshot? =
        db
            .fetchOne(
                "select * from weight_goals where user_id = ? and status = 'ACTIVE' order by started_on desc limit 1",
                userId,
            )?.let(::goal)

    fun activeProgram(userId: String): NutritionProgramSnapshot? =
        db
            .fetchOne(
                "select * from nutrition_program_revisions where user_id = ? and effective_to is null order by created_at desc limit 1",
                userId,
            )?.let(::program)

    fun programForDate(
        userId: String,
        date: LocalDate,
    ): NutritionProgramSnapshot? =
        db
            .fetchOne(
                """
                select * from nutrition_program_revisions
                 where user_id = ? and effective_from <= ? and (effective_to is null or effective_to >= ?)
                 order by effective_from desc, created_at desc limit 1
                """.trimIndent(),
                userId,
                date,
                date,
            )?.let(::program)

    fun replaceGoal(
        userId: String,
        goal: WeightGoalSnapshot,
    ) {
        db.execute(
            "update weight_goals set status = 'ARCHIVED', ended_on = ? where user_id = ? and status = 'ACTIVE'",
            goal.startedOn.minusDays(1),
            userId,
        )
        db.execute(
            """
            insert into weight_goals(id, user_id, goal_type, starting_weight_kg, target_weight_kg,
                                     weekly_rate_percent, status, started_on, ended_on)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            goal.id,
            userId,
            goal.type.name,
            goal.startingWeightKg,
            goal.targetWeightKg,
            goal.weeklyRatePercent,
            goal.status.name,
            goal.startedOn,
            goal.endedOn,
        )
    }

    fun replaceProgram(
        userId: String,
        program: NutritionProgramSnapshot,
    ) {
        db.execute(
            """
            update nutrition_program_revisions
               set effective_to = greatest(effective_from, cast(? as date) - 1)
             where user_id = ? and effective_to is null
            """.trimIndent(),
            program.effectiveFrom,
            userId,
        )
        db.execute(
            """
            insert into nutrition_program_revisions(
                id, user_id, goal_id, style, effective_from, effective_to, energy_kcal, protein_g,
                carbohydrate_g, fat_g, protein_g_per_kg, fat_energy_percent, expenditure_kcal,
                expenditure_lower_kcal, expenditure_upper_kcal, algorithm_version, source
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            program.id,
            userId,
            program.goalId,
            program.style.name,
            program.effectiveFrom,
            program.effectiveTo,
            program.energyKcal,
            program.proteinG,
            program.carbohydrateG,
            program.fatG,
            program.proteinGPerKg,
            program.fatEnergyPercent,
            program.expenditureKcal,
            program.expenditureLowerKcal,
            program.expenditureUpperKcal,
            program.algorithmVersion,
            program.source.name,
        )
    }

    fun draft(userId: String): CoachingSetupDraft? =
        db
            .fetchOne("select payload::text as payload from coaching_setup_drafts where user_id = ?", userId)
            ?.get("payload", String::class.java)
            ?.let { json.read(it, CoachingSetupDraft::class.java) }

    fun saveDraft(
        userId: String,
        draft: CoachingSetupDraft,
    ) {
        db.execute(
            """
            insert into coaching_setup_drafts(user_id, current_step, payload)
            values (?, ?, cast(? as jsonb))
            on conflict(user_id) do update set current_step = excluded.current_step,
                payload = excluded.payload, updated_at = current_timestamp
            """.trimIndent(),
            userId,
            draft.currentStep,
            json.write(draft),
        )
    }

    fun deleteDraft(userId: String) {
        db.execute("delete from coaching_setup_drafts where user_id = ?", userId)
    }

    fun latestResolvedCheckIn(userId: String): StoredCheckIn? =
        db
            .fetchOne(
                "select *, proposal::text as proposal_json from weekly_check_ins where user_id = ? and status <> 'DRAFT' order by week_start desc limit 1",
                userId,
            )?.let(::checkIn)

    fun checkIn(
        userId: String,
        weekStart: LocalDate,
    ): StoredCheckIn? =
        db
            .fetchOne(
                "select *, proposal::text as proposal_json from weekly_check_ins where user_id = ? and week_start = ?",
                userId,
                weekStart,
            )?.let(::checkIn)

    fun checkIn(
        userId: String,
        id: UUID,
    ): StoredCheckIn? =
        db
            .fetchOne(
                "select *, proposal::text as proposal_json from weekly_check_ins where user_id = ? and id = ?",
                userId,
                id,
            )?.let(::checkIn)

    fun createCheckIn(
        userId: String,
        weekStart: LocalDate,
    ): StoredCheckIn {
        val id = UUID.randomUUID()
        db.execute(
            "insert into weekly_check_ins(id, user_id, week_start, status) values (?, ?, ?, 'DRAFT') on conflict do nothing",
            id,
            userId,
            weekStart,
        )
        return requireNotNull(checkIn(userId, weekStart))
    }

    fun saveProposal(
        userId: String,
        id: UUID,
        proposal: CheckInProposal,
    ) {
        db.execute(
            "update weekly_check_ins set proposal = cast(? as jsonb) where id = ? and user_id = ? and status = 'DRAFT'",
            json.write(proposal),
            id,
            userId,
        )
    }

    fun resolveCheckIn(
        userId: String,
        id: UUID,
        status: CheckInStatus,
    ) {
        db.execute(
            "update weekly_check_ins set status = ?, resolved_at = current_timestamp where id = ? and user_id = ? and status = 'DRAFT'",
            status.name,
            id,
            userId,
        )
    }

    private fun goal(record: org.jooq.Record) =
        WeightGoalSnapshot(
            record.get("id", UUID::class.java)!!,
            WeightGoalType.valueOf(record.get("goal_type", String::class.java)!!),
            record.get("starting_weight_kg", BigDecimal::class.java)!!,
            record.get("target_weight_kg", BigDecimal::class.java),
            record.get("weekly_rate_percent", BigDecimal::class.java)!!,
            GoalStatus.valueOf(record.get("status", String::class.java)!!),
            record.get("started_on", LocalDate::class.java)!!,
            record.get("ended_on", LocalDate::class.java),
        )

    private fun program(record: org.jooq.Record) =
        NutritionProgramSnapshot(
            record.get("id", UUID::class.java)!!,
            record.get("goal_id", UUID::class.java),
            ProgramStyle.valueOf(record.get("style", String::class.java)!!),
            record.get("effective_from", LocalDate::class.java)!!,
            record.get("effective_to", LocalDate::class.java),
            record.get("energy_kcal", BigDecimal::class.java),
            record.get("protein_g", BigDecimal::class.java),
            record.get("carbohydrate_g", BigDecimal::class.java),
            record.get("fat_g", BigDecimal::class.java),
            record.get("protein_g_per_kg", BigDecimal::class.java),
            record.get("fat_energy_percent", BigDecimal::class.java),
            record.get("expenditure_kcal", BigDecimal::class.java),
            record.get("expenditure_lower_kcal", BigDecimal::class.java),
            record.get("expenditure_upper_kcal", BigDecimal::class.java),
            record.get("algorithm_version", String::class.java),
            ProgramSource.valueOf(record.get("source", String::class.java)!!),
        )

    private fun checkIn(record: org.jooq.Record): StoredCheckIn =
        StoredCheckIn(
            record.get("id", UUID::class.java)!!,
            record.get("week_start", LocalDate::class.java)!!,
            CheckInStatus.valueOf(record.get("status", String::class.java)!!),
            record.get("proposal_json", String::class.java)?.let { json.read(it, CheckInProposal::class.java) },
        )
}
