package com.macrosaurus.identity

import java.math.BigDecimal
import java.time.LocalDate

enum class FormulaSex { MALE, FEMALE }

enum class UnitSystem { METRIC, IMPERIAL }

data class ProfileSnapshot(
    val userId: String,
    val displayName: String,
    val locale: String,
    val timezone: String,
    val unitSystem: UnitSystem,
    val birthDate: LocalDate?,
    val heightCm: BigDecimal?,
    val formulaSex: FormulaSex?,
    val activityMultiplier: BigDecimal,
)

fun interface ProfileReader {
    fun get(userId: String): ProfileSnapshot?
}
