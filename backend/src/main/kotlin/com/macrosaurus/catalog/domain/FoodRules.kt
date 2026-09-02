package com.macrosaurus.catalog.domain

import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.FoodAmount
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodSnapshot
import com.macrosaurus.catalog.PortionDraft
import com.macrosaurus.catalog.ResolvedFoodAmount
import com.macrosaurus.shared.InvalidOperationException
import com.macrosaurus.shared.NutrientValues
import java.math.BigDecimal
import java.math.RoundingMode

internal object FoodDraftValidator {
    fun validate(
        draft: FoodDraft,
        knownNutrients: Set<String>,
    ) {
        if (draft.name.isBlank()) throw InvalidOperationException("Food name is required")
        if (draft.basisAmount <= BigDecimal.ZERO) throw InvalidOperationException("Food basis amount must be positive")
        if (draft.densityGPerMl != null && draft.densityGPerMl <= BigDecimal.ZERO) {
            throw InvalidOperationException("Food density must be positive")
        }
        if (draft.nutrients.values.any { it < BigDecimal.ZERO }) throw InvalidOperationException("Nutrient values cannot be negative")
        val unknownNutrients = draft.nutrients.keys - knownNutrients
        if (unknownNutrients.isNotEmpty()) {
            throw InvalidOperationException("Unknown nutrient codes: ${unknownNutrients.sorted().joinToString()}")
        }
        if (draft.portions.count(PortionDraft::default) > 1) {
            throw InvalidOperationException("Only one named portion can be the default")
        }
        draft.portions.forEach { portion ->
            if (portion.name.isBlank()) throw InvalidOperationException("Named portion name is required")
            if (portion.quantity <= BigDecimal.ZERO) throw InvalidOperationException("Named portion quantity must be positive")
            if (portion.gramWeight == null && portion.milliliterVolume == null) {
                throw InvalidOperationException("Named portions need a gram weight or milliliter volume")
            }
            if (portion.gramWeight?.let { it <= BigDecimal.ZERO } == true ||
                portion.milliliterVolume?.let { it <= BigDecimal.ZERO } == true
            ) {
                throw InvalidOperationException("Named portion measurements must be positive")
            }
        }
        when (draft.basisType) {
            BasisType.PER_100_G -> {
                if (draft.basisUnit.lowercase() !in setOf("g", "gram", "grams")) {
                    throw InvalidOperationException("PER_100_G foods must use grams")
                }
            }

            BasisType.PER_100_ML -> {
                if (draft.basisUnit.lowercase() !in setOf("ml", "milliliter", "milliliters")) {
                    throw InvalidOperationException("PER_100_ML foods must use milliliters")
                }
            }

            BasisType.PER_SERVING -> {
                if (draft.basisAmount.compareTo(BigDecimal.ONE) != 0 || draft.basisUnit.lowercase() !in setOf("serving", "servings")) {
                    throw InvalidOperationException("PER_SERVING foods must use one serving")
                }
            }
        }
    }
}

internal object FoodAmountResolver {
    fun resolve(
        food: FoodSnapshot,
        amount: FoodAmount,
    ): ResolvedFoodAmount {
        val portion =
            amount.portionId?.let { id ->
                food.portions.firstOrNull { it.id == id }
                    ?: throw InvalidOperationException("Portion does not belong to this food revision")
            }
        val (factor, grams) =
            when {
                portion?.gramWeight != null -> {
                    val totalG = portion.gramWeight.multiply(amount.quantity).divide(portion.quantity, 12, RoundingMode.HALF_UP)
                    factorForMass(food, totalG) to totalG
                }

                portion?.milliliterVolume != null -> {
                    val ml = portion.milliliterVolume.multiply(amount.quantity).divide(portion.quantity, 12, RoundingMode.HALF_UP)
                    factorForVolume(food, ml) to food.densityGPerMl?.multiply(ml)
                }

                amount.unit.lowercase() in setOf("g", "gram", "grams") -> {
                    factorForMass(food, amount.quantity) to amount.quantity
                }

                amount.unit.lowercase() in setOf("ml", "milliliter", "milliliters") -> {
                    factorForVolume(food, amount.quantity) to food.densityGPerMl?.multiply(amount.quantity)
                }

                amount.unit.lowercase() in setOf("serving", "servings") && food.basisType == BasisType.PER_SERVING -> {
                    amount.quantity.divide(food.basisAmount, 12, RoundingMode.HALF_UP) to null
                }

                else -> {
                    throw InvalidOperationException("The selected unit cannot be converted for this food")
                }
            }
        return ResolvedFoodAmount(
            food.revisionId,
            food.name,
            amount.quantity,
            amount.unit,
            grams,
            NutrientValues(food.nutrients).scaled(factor),
        )
    }

    private fun factorForMass(
        food: FoodSnapshot,
        grams: BigDecimal,
    ): BigDecimal =
        when (food.basisType) {
            BasisType.PER_100_G -> {
                grams.divide(food.basisAmount, 12, RoundingMode.HALF_UP)
            }

            BasisType.PER_100_ML -> {
                food.densityGPerMl?.let { density ->
                    grams.divide(density, 12, RoundingMode.HALF_UP).divide(food.basisAmount, 12, RoundingMode.HALF_UP)
                } ?: throw InvalidOperationException("Density is required to convert this volume-based food to grams")
            }

            BasisType.PER_SERVING -> {
                throw InvalidOperationException("This serving-only food has no gram conversion")
            }
        }

    private fun factorForVolume(
        food: FoodSnapshot,
        milliliters: BigDecimal,
    ): BigDecimal =
        when (food.basisType) {
            BasisType.PER_100_ML -> {
                milliliters.divide(food.basisAmount, 12, RoundingMode.HALF_UP)
            }

            BasisType.PER_100_G -> {
                food.densityGPerMl?.multiply(milliliters)?.divide(food.basisAmount, 12, RoundingMode.HALF_UP)
                    ?: throw InvalidOperationException("Density is required to convert this mass-based food to milliliters")
            }

            BasisType.PER_SERVING -> {
                throw InvalidOperationException("This serving-only food has no volume conversion")
            }
        }
}
