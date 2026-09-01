package com.macrosaurus.identity.application

import com.macrosaurus.identity.FormulaSex
import com.macrosaurus.identity.ProfileReader
import com.macrosaurus.identity.ProfileSnapshot
import com.macrosaurus.identity.ProfileUpdate
import com.macrosaurus.identity.ProfileWriter
import com.macrosaurus.identity.UnitSystem
import com.macrosaurus.identity.persistence.JooqProfileRepository
import com.macrosaurus.shared.InvalidOperationException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal data class UpdateProfileCommand(
    val displayName: String,
    val locale: String,
    val timezone: String,
    val unitSystem: UnitSystem,
    val birthDate: LocalDate?,
    val heightCm: BigDecimal?,
    val formulaSex: FormulaSex?,
    val activityMultiplier: BigDecimal,
)

@Service
internal class ProfileService(
    private val repository: JooqProfileRepository,
    private val clock: Clock,
) : ProfileReader,
    ProfileWriter {
    override fun get(userId: String): ProfileSnapshot? = repository.get(userId)

    fun upsert(
        userId: String,
        command: UpdateProfileCommand,
    ): ProfileSnapshot {
        validate(command)
        repository.upsert(
            ProfileSnapshot(
                userId,
                command.displayName,
                command.locale,
                command.timezone,
                command.unitSystem,
                command.birthDate,
                command.heightCm,
                command.formulaSex,
                command.activityMultiplier,
            ),
        )
        return requireNotNull(get(userId))
    }

    override fun save(
        userId: String,
        update: ProfileUpdate,
    ): ProfileSnapshot =
        upsert(
            userId,
            UpdateProfileCommand(
                update.displayName,
                update.locale,
                update.timezone,
                update.unitSystem,
                update.birthDate,
                update.heightCm,
                update.formulaSex,
                update.activityMultiplier,
            ),
        )

    private fun validate(command: UpdateProfileCommand) {
        runCatching { ZoneId.of(command.timezone) }
            .getOrElse { throw InvalidOperationException("Unknown profile timezone") }
        if (Locale.forLanguageTag(command.locale).language.isBlank()) {
            throw InvalidOperationException("Unknown profile locale")
        }
        if (command.birthDate?.isAfter(LocalDate.now(clock)) == true) {
            throw InvalidOperationException("Birth date cannot be in the future")
        }
    }
}
