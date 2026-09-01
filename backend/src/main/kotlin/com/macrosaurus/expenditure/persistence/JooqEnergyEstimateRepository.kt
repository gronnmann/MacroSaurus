package com.macrosaurus.expenditure.persistence

import com.macrosaurus.expenditure.EnergyEstimate
import com.macrosaurus.shared.JsonCodec
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal class JooqEnergyEstimateRepository(
    private val db: DSLContext,
    private val json: JsonCodec,
) {
    fun insert(
        userId: String,
        estimate: EnergyEstimate,
    ) {
        db.execute(
            """
            insert into energy_estimates(id, user_id, estimate_date, baseline_kcal, adaptive_kcal,
                                         suggested_kcal, confidence, algorithm_version, explanation,
                                         lower_kcal, upper_kcal, trend_weight_kg, trend_weight_lower_kg,
                                         trend_weight_upper_kg, model_state, requirements)
            values (?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?, ?, ?, cast(? as jsonb))
            """.trimIndent(),
            UUID.randomUUID(),
            userId,
            estimate.date,
            estimate.baselineKcal,
            estimate.adaptiveKcal,
            estimate.suggestedKcal,
            estimate.confidence,
            estimate.algorithmVersion,
            json.write(estimate.explanation),
            estimate.lowerKcal,
            estimate.upperKcal,
            estimate.trendWeightKg,
            estimate.trendWeightLowerKg,
            estimate.trendWeightUpperKg,
            estimate.modelState,
            json.write(estimate.requirements),
        )
    }
}
