package com.macrosaurus.identity.application

import com.macrosaurus.catalog.NutrientCatalog
import com.macrosaurus.identity.persistence.JooqTargetRepository
import com.macrosaurus.identity.persistence.TargetAmounts
import com.macrosaurus.shared.InvalidOperationException
import org.springframework.stereotype.Service
import java.math.BigDecimal

internal data class NutrientTarget(
    val nutrientCode: String,
    val displayName: String,
    val unit: String,
    val targetAmount: BigDecimal?,
    val minimumAmount: BigDecimal?,
    val maximumAmount: BigDecimal?,
)

internal data class SetNutrientTargetCommand(
    val targetAmount: BigDecimal?,
    val minimumAmount: BigDecimal?,
    val maximumAmount: BigDecimal?,
)

@Service
internal class TargetService(
    private val nutrients: NutrientCatalog,
    private val repository: JooqTargetRepository,
) {
    fun list(userId: String): List<NutrientTarget> {
        val targets = repository.findAll(userId)
        return nutrients.nutrients().map { definition ->
            val target = targets[definition.code]
            NutrientTarget(
                definition.code,
                definition.displayName,
                definition.unit,
                target?.target,
                target?.minimum,
                target?.maximum,
            )
        }
    }

    fun set(
        userId: String,
        nutrientCode: String,
        command: SetNutrientTargetCommand,
    ): NutrientTarget {
        if (command.targetAmount == null && command.minimumAmount == null && command.maximumAmount == null) {
            throw InvalidOperationException("At least one target value is required")
        }
        if (command.minimumAmount != null && command.maximumAmount != null && command.minimumAmount > command.maximumAmount) {
            throw InvalidOperationException("Minimum target cannot exceed maximum target")
        }
        if (nutrients.nutrients().none { it.code == nutrientCode }) {
            throw InvalidOperationException("Unknown nutrient code")
        }
        repository.save(userId, nutrientCode, TargetAmounts(command.targetAmount, command.minimumAmount, command.maximumAmount))
        return list(userId).first { it.nutrientCode == nutrientCode }
    }

    fun clear(
        userId: String,
        nutrientCode: String,
    ) {
        repository.delete(userId, nutrientCode)
    }
}
