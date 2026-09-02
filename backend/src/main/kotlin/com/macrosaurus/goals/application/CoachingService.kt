package com.macrosaurus.goals.application

import com.macrosaurus.expenditure.ExpenditureEstimator
import com.macrosaurus.goals.GoalStatus
import com.macrosaurus.goals.NutritionProgramSnapshot
import com.macrosaurus.goals.ProgramSource
import com.macrosaurus.goals.ProgramStyle
import com.macrosaurus.goals.WeightGoalSnapshot
import com.macrosaurus.goals.WeightGoalType
import com.macrosaurus.goals.domain.ProgramCalculator
import com.macrosaurus.goals.domain.ProgramInputs
import com.macrosaurus.goals.persistence.JooqStrategyRepository
import com.macrosaurus.identity.ProfileReader
import com.macrosaurus.identity.ProfileSnapshot
import com.macrosaurus.identity.ProfileUpdate
import com.macrosaurus.identity.ProfileWriter
import com.macrosaurus.identity.UnitSystem
import com.macrosaurus.measurements.NewWeightMeasurement
import com.macrosaurus.measurements.WeightHistory
import com.macrosaurus.measurements.WeightRecorder
import com.macrosaurus.shared.InvalidOperationException
import com.macrosaurus.shared.NotFoundException
import com.macrosaurus.tracking.NutritionDayReviewer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

@Service
internal class CoachingService(
    private val profiles: ProfileReader,
    private val profileWriter: ProfileWriter,
    private val weights: WeightHistory,
    private val weightRecorder: WeightRecorder,
    private val expenditure: ExpenditureEstimator,
    private val nutritionReviews: NutritionDayReviewer,
    private val strategy: JooqStrategyRepository,
    private val clock: Clock,
) {
    fun status(userId: String): CoachingStatus {
        val profile = profiles.get(userId)
        val program = strategy.activeProgram(userId)
        val complete = profile != null && program != null
        if (!complete) return CoachingStatus(false, strategy.activeGoal(userId), program, null, false)
        val today = today(profile.timezone)
        val next = nextCheckIn(program, strategy.latestResolvedCheckIn(userId))
        return CoachingStatus(true, strategy.activeGoal(userId), program, next, next != null && !today.isBefore(next))
    }

    fun draft(userId: String): CoachingSetupDraft = strategy.draft(userId) ?: defaultDraft(userId)

    fun saveDraft(
        userId: String,
        draft: CoachingSetupDraft,
    ): CoachingSetupDraft {
        if (draft.currentStep !in 1..5) throw InvalidOperationException("Unknown setup step")
        strategy.saveDraft(userId, draft)
        return draft
    }

    fun preview(
        userId: String,
        draft: CoachingSetupDraft = this.draft(userId),
    ): SetupPreview {
        val profile = profile(draft, userId)
        val weight = draft.weightKg ?: throw InvalidOperationException("Current weight is required")
        val date = today(profile.timezone)
        val estimate = expenditure.preview(profile, weight, date)
        val calculation =
            ProgramCalculator.calculate(
                date,
                inputs(draft, weight),
                estimate,
                profile.activityMultiplier,
            )
        return SetupPreview(
            estimate,
            calculation.energyKcal,
            calculation.proteinG,
            calculation.carbohydrateG,
            calculation.fatG,
            calculation.estimatedCompletionDate,
            calculation.warnings,
        )
    }

    @Transactional
    fun complete(
        userId: String,
        requested: CoachingSetupDraft? = null,
    ): CoachingStatus {
        val draft = requested ?: draft(userId)
        val wasComplete = status(userId).setupComplete
        val profile = profile(draft, userId)
        val weight = draft.weightKg ?: throw InvalidOperationException("Current weight is required")
        val preview = preview(userId, draft)
        val date = today(profile.timezone)
        profileWriter.save(
            userId,
            ProfileUpdate(
                profile.displayName,
                profile.locale,
                profile.timezone,
                profile.unitSystem,
                profile.birthDate!!,
                profile.heightCm!!,
                profile.formulaSex!!,
                profile.activityMultiplier,
            ),
        )
        val zone = ZoneId.of(profile.timezone)
        val duplicate = weights.list(userId, 200).any { it.measuredAt.toLocalDate() == date && it.weightKg.subtract(weight).abs() < BigDecimal("0.001") }
        if (!duplicate) {
            weightRecorder.record(userId, NewWeightMeasurement(weight, date.atTime(LocalTime.NOON).atZone(zone).toOffsetDateTime(), "Goal setup"))
        }
        val goal =
            WeightGoalSnapshot(
                UUID.randomUUID(),
                draft.goalType ?: throw InvalidOperationException("Choose a goal"),
                weight,
                draft.targetWeightKg,
                draft.weeklyRatePercent ?: BigDecimal.ZERO,
                GoalStatus.ACTIVE,
                date,
                null,
            )
        strategy.replaceGoal(userId, goal)
        strategy.replaceProgram(
            userId,
            NutritionProgramSnapshot(
                UUID.randomUUID(),
                goal.id,
                draft.programStyle ?: throw InvalidOperationException("Choose a program style"),
                date,
                null,
                preview.energyKcal,
                preview.proteinG,
                preview.carbohydrateG,
                preview.fatG,
                draft.proteinGPerKg,
                draft.fatEnergyPercent,
                preview.expenditure.suggestedKcal,
                preview.expenditure.lowerKcal,
                preview.expenditure.upperKcal,
                preview.expenditure.algorithmVersion,
                if (wasComplete) ProgramSource.PROFILE_RERUN else ProgramSource.ONBOARDING,
            ),
        )
        strategy.deleteDraft(userId)
        return status(userId)
    }

    fun currentCheckIn(userId: String): CheckInView {
        val status = status(userId)
        if (!status.setupComplete || !status.checkInDue) return CheckInView(false, null, null, null, null, null, emptyList(), false, null)
        val profile = requireNotNull(profiles.get(userId))
        val today = today(profile.timezone)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val periodTo = weekStart.minusDays(1)
        val periodFrom = periodTo.minusDays(6)
        val checkIn = strategy.checkIn(userId, weekStart) ?: strategy.createCheckIn(userId, weekStart)
        val candidates = nutritionReviews.candidates(userId, periodTo.minusDays(20), periodTo)
        val needsWeight = weights.list(userId, 200).none { !it.measuredAt.toLocalDate().isBefore(periodTo.minusDays(6)) && !it.measuredAt.toLocalDate().isAfter(periodTo) }
        return CheckInView(true, checkIn.id, weekStart, periodFrom, periodTo, checkIn.status, candidates, needsWeight, checkIn.proposal)
    }

    @Transactional
    fun refresh(
        userId: String,
        id: UUID,
    ): CheckInView {
        val view = currentCheckIn(userId)
        if (view.id != id) throw NotFoundException("Check-in was not found")
        if (view.status != CheckInStatus.DRAFT) return view
        val unresolved = view.candidates.filter { it.review == null }
        if (unresolved.isNotEmpty()) throw InvalidOperationException("Review every missing or possibly partial day before updating targets")
        val profile = requireNotNull(profiles.get(userId))
        val program = strategy.activeProgram(userId) ?: throw InvalidOperationException("An active program is required")
        val goal = strategy.activeGoal(userId)
        val periodTo = requireNotNull(view.periodTo)
        val estimate = expenditure.estimate(userId, periodTo)
        val currentWeight =
            estimate.trendWeightKg ?: weights.list(userId, 200).firstOrNull()?.weightKg
                ?: throw InvalidOperationException("A weigh-in is required")
        val proposal =
            if (program.style == ProgramStyle.MANUAL || goal == null || estimate.modelState == "HOLDING" || estimate.suggestedKcal == null) {
                CheckInProposal(
                    estimate,
                    program.energyKcal,
                    program.energyKcal,
                    program.proteinG,
                    program.carbohydrateG,
                    program.fatG,
                    false,
                    if (program.style == ProgramStyle.MANUAL) listOf("Manual targets stay fixed; this check-in updates insights only.") else listOf("Targets are holding until the model has enough recent data."),
                )
            } else {
                val calculated =
                    ProgramCalculator.calculate(
                        periodTo,
                        ProgramInputs(
                            ProgramStyle.COACHED,
                            goal.type,
                            currentWeight,
                            goal.targetWeightKg,
                            goal.weeklyRatePercent,
                            program.proteinGPerKg ?: BigDecimal("1.6"),
                            program.fatEnergyPercent ?: BigDecimal("25"),
                            null,
                            null,
                            null,
                            null,
                        ),
                        estimate,
                        profile.activityMultiplier,
                        program.energyKcal,
                        recurringCheckIn = true,
                    )
                CheckInProposal(
                    estimate,
                    program.energyKcal,
                    calculated.energyKcal,
                    calculated.proteinG,
                    calculated.carbohydrateG,
                    calculated.fatG,
                    true,
                    calculated.warnings,
                )
            }
        strategy.saveProposal(userId, id, proposal)
        return currentCheckIn(userId)
    }

    @Transactional
    fun accept(
        userId: String,
        id: UUID,
    ): CheckInView {
        val stored = strategy.checkIn(userId, id) ?: throw NotFoundException("Check-in was not found")
        if (stored.status == CheckInStatus.ACCEPTED) return currentCheckIn(userId)
        if (stored.status != CheckInStatus.DRAFT) throw InvalidOperationException("This check-in is already closed")
        val proposal = stored.proposal ?: throw InvalidOperationException("Refresh the check-in proposal before accepting")
        val active = strategy.activeProgram(userId) ?: throw InvalidOperationException("An active program is required")
        val profile = requireNotNull(profiles.get(userId))
        if (proposal.targetUpdateAvailable) {
            val date = today(profile.timezone)
            strategy.replaceProgram(
                userId,
                NutritionProgramSnapshot(
                    UUID.randomUUID(),
                    active.goalId,
                    active.style,
                    date,
                    null,
                    proposal.proposedEnergyKcal,
                    proposal.proposedProteinG,
                    proposal.proposedCarbohydrateG,
                    proposal.proposedFatG,
                    active.proteinGPerKg,
                    active.fatEnergyPercent,
                    proposal.estimate.suggestedKcal,
                    proposal.estimate.lowerKcal,
                    proposal.estimate.upperKcal,
                    proposal.estimate.algorithmVersion,
                    ProgramSource.CHECK_IN,
                ),
            )
        }
        strategy.resolveCheckIn(userId, id, CheckInStatus.ACCEPTED)
        return currentCheckIn(userId)
    }

    @Transactional
    fun skip(
        userId: String,
        id: UUID,
    ): CheckInView {
        val stored = strategy.checkIn(userId, id) ?: throw NotFoundException("Check-in was not found")
        if (stored.status == CheckInStatus.DRAFT) strategy.resolveCheckIn(userId, id, CheckInStatus.SKIPPED)
        return currentCheckIn(userId)
    }

    private fun defaultDraft(userId: String): CoachingSetupDraft {
        val profile = profiles.get(userId)
        val goal = strategy.activeGoal(userId)
        val program = strategy.activeProgram(userId)
        return CoachingSetupDraft(
            displayName = profile?.displayName,
            locale = profile?.locale ?: "en",
            timezone = profile?.timezone ?: ZoneId.systemDefault().id,
            birthDate = profile?.birthDate,
            heightCm = profile?.heightCm,
            formulaSex = profile?.formulaSex,
            activityMultiplier = profile?.activityMultiplier ?: BigDecimal("1.2"),
            weightKg = weights.list(userId, 1).firstOrNull()?.weightKg,
            goalType = goal?.type ?: WeightGoalType.MAINTAIN,
            targetWeightKg = goal?.targetWeightKg,
            weeklyRatePercent = goal?.weeklyRatePercent ?: BigDecimal.ZERO,
            programStyle = program?.style ?: ProgramStyle.COACHED,
            proteinGPerKg = program?.proteinGPerKg ?: BigDecimal("1.6"),
            fatEnergyPercent = program?.fatEnergyPercent ?: BigDecimal("25"),
            manualEnergyKcal = program?.energyKcal,
            manualProteinG = program?.proteinG,
            manualCarbohydrateG = program?.carbohydrateG,
            manualFatG = program?.fatG,
        )
    }

    private fun profile(
        draft: CoachingSetupDraft,
        userId: String,
    ): ProfileSnapshot {
        runCatching { ZoneId.of(draft.timezone) }
            .getOrElse { throw InvalidOperationException("Unknown profile timezone") }
        val date = today(draft.timezone)
        val birthDate = draft.birthDate ?: throw InvalidOperationException("Birth date is required")
        if (Period.between(birthDate, date).years < 18) throw InvalidOperationException("Macrosaurus coaching is available to adults aged 18 and over")
        val displayName = draft.displayName?.trim()?.takeIf { it.isNotBlank() } ?: throw InvalidOperationException("Display name is required")
        val height = draft.heightCm ?: throw InvalidOperationException("Height is required")
        if (height !in BigDecimal("30")..BigDecimal("300")) throw InvalidOperationException("Height must be between 30 and 300 cm")
        if (draft.activityMultiplier !in BigDecimal("1.0")..BigDecimal("3.0")) throw InvalidOperationException("Activity multiplier must be between 1.0 and 3.0")
        return ProfileSnapshot(
            userId,
            displayName,
            draft.locale,
            draft.timezone,
            UnitSystem.METRIC,
            birthDate,
            height,
            draft.formulaSex ?: throw InvalidOperationException("Formula sex is required for the starting estimate"),
            draft.activityMultiplier,
        )
    }

    private fun inputs(
        draft: CoachingSetupDraft,
        weight: BigDecimal,
    ) = ProgramInputs(
        draft.programStyle ?: throw InvalidOperationException("Choose a program style"),
        draft.goalType ?: throw InvalidOperationException("Choose a goal"),
        weight,
        draft.targetWeightKg,
        draft.weeklyRatePercent ?: BigDecimal.ZERO,
        draft.proteinGPerKg,
        draft.fatEnergyPercent,
        draft.manualEnergyKcal,
        draft.manualProteinG,
        draft.manualCarbohydrateG,
        draft.manualFatG,
    )

    private fun nextCheckIn(
        program: NutritionProgramSnapshot?,
        last: StoredCheckIn?,
    ): LocalDate? {
        if (program == null) return null
        return last?.weekStart?.plusWeeks(1)
            ?: program.effectiveFrom.plusDays(7).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    }

    private fun today(timezone: String?): LocalDate {
        val zone = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.of("UTC")
        return LocalDate.now(clock.withZone(zone))
    }
}
