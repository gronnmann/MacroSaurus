package com.macrosaurus.goals.persistence

import com.macrosaurus.goals.application.EnergyGoalMode
import com.macrosaurus.goals.application.GoalSettings
import com.macrosaurus.goals.application.GoalWeightBasis
import com.macrosaurus.goals.application.MacroGoalMode
import com.macrosaurus.goals.application.SaveGoalSettingsCommand
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
internal class JooqGoalRepository(
    private val db: DSLContext,
) {
    fun get(userId: String): GoalSettings? {
        val record = db.fetchOne("select * from user_goal_settings where user_id = ?", userId) ?: return null
        return GoalSettings(
            true,
            record.get("energy_mode", String::class.java)?.let(EnergyGoalMode::valueOf),
            record.get("energy_value", BigDecimal::class.java),
            record.get("macro_mode", String::class.java)?.let(MacroGoalMode::valueOf),
            record.get("protein_g_per_kg", BigDecimal::class.java),
            record.get("fat_energy_percent", BigDecimal::class.java),
            record.get("weight_basis", String::class.java)?.let(GoalWeightBasis::valueOf),
            record.get("manual_weight_kg", BigDecimal::class.java),
            record.get("protein_target_g", BigDecimal::class.java),
            record.get("carbohydrate_target_g", BigDecimal::class.java),
            record.get("fat_target_g", BigDecimal::class.java),
            record.get("protein_energy_percent", BigDecimal::class.java),
            record.get("carbohydrate_energy_percent", BigDecimal::class.java),
        )
    }

    fun save(
        userId: String,
        settings: SaveGoalSettingsCommand,
    ) {
        db.execute(
            """
            insert into user_goal_settings(
                user_id, energy_mode, energy_value, macro_mode, protein_g_per_kg, fat_energy_percent,
                weight_basis, manual_weight_kg, protein_target_g, carbohydrate_target_g, fat_target_g,
                protein_energy_percent, carbohydrate_energy_percent
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict(user_id) do update set
                energy_mode = excluded.energy_mode, energy_value = excluded.energy_value,
                macro_mode = excluded.macro_mode, protein_g_per_kg = excluded.protein_g_per_kg,
                fat_energy_percent = excluded.fat_energy_percent, weight_basis = excluded.weight_basis,
                manual_weight_kg = excluded.manual_weight_kg, protein_target_g = excluded.protein_target_g,
                carbohydrate_target_g = excluded.carbohydrate_target_g, fat_target_g = excluded.fat_target_g,
                protein_energy_percent = excluded.protein_energy_percent,
                carbohydrate_energy_percent = excluded.carbohydrate_energy_percent,
                updated_at = current_timestamp
            """.trimIndent(),
            userId,
            settings.energyMode.name,
            settings.energyValue,
            settings.macroMode.name,
            settings.proteinGPerKg,
            settings.fatEnergyPercent,
            settings.weightBasis?.name,
            settings.manualWeightKg,
            settings.proteinTargetG,
            settings.carbohydrateTargetG,
            settings.fatTargetG,
            settings.proteinEnergyPercent,
            settings.carbohydrateEnergyPercent,
        )
    }
}
