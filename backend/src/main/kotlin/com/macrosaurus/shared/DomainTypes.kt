package com.macrosaurus.shared

import java.math.BigDecimal

data class NutrientValues(
    val values: Map<String, BigDecimal>,
) {
    operator fun get(code: String): BigDecimal? = values[code]

    fun scaled(factor: BigDecimal): NutrientValues =
        NutrientValues(
            values.mapValues { (_, amount) -> amount.multiply(factor).stripTrailingZeros() },
        )

    fun plus(other: NutrientValues): NutrientValues {
        val keys = values.keys + other.values.keys
        return NutrientValues(
            keys.associateWith { code ->
                (values[code] ?: BigDecimal.ZERO).add(other.values[code] ?: BigDecimal.ZERO)
            },
        )
    }

    companion object {
        val EMPTY = NutrientValues(emptyMap())
    }
}

object NutrientMath {
    private val FOUR = BigDecimal("4")
    private val NINE = BigDecimal("9")
    private val SEVEN = BigDecimal("7")

    fun calculatedCalories(values: Map<String, BigDecimal>): BigDecimal =
        (values["protein_g"] ?: BigDecimal.ZERO)
            .multiply(FOUR)
            .add((values["carbohydrate_g"] ?: BigDecimal.ZERO).multiply(FOUR))
            .add((values["fat_g"] ?: BigDecimal.ZERO).multiply(NINE))
            .add((values["alcohol_g"] ?: BigDecimal.ZERO).multiply(SEVEN))
            .stripTrailingZeros()
}
