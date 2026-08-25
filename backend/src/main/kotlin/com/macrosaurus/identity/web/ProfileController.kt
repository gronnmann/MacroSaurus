package com.macrosaurus.identity.web

import com.macrosaurus.identity.FormulaSex
import com.macrosaurus.identity.ProfileSnapshot
import com.macrosaurus.identity.UnitSystem
import com.macrosaurus.identity.application.ProfileService
import com.macrosaurus.identity.application.UpdateProfileCommand
import com.macrosaurus.shared.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate

data class ProfileView(
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

data class UpdateProfileRequest(
    @field:NotBlank val displayName: String,
    @field:NotBlank val locale: String = "en",
    @field:NotBlank val timezone: String = "UTC",
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val birthDate: LocalDate? = null,
    @field:DecimalMin("30") @field:DecimalMax("300") val heightCm: BigDecimal? = null,
    val formulaSex: FormulaSex? = null,
    @field:DecimalMin("1.0") @field:DecimalMax("2.5") val activityMultiplier: BigDecimal = BigDecimal("1.2"),
)

private fun ProfileSnapshot.toView() = ProfileView(userId, displayName, locale, timezone, unitSystem, birthDate, heightCm, formulaSex, activityMultiplier)

@RestController
@RequestMapping("/api/v1/me/profile")
internal class ProfileController(
    private val users: CurrentUser,
    private val profiles: ProfileService,
) {
    @GetMapping
    fun get(): ProfileView =
        profiles.get(users.userId())?.toView()
            ?: ProfileView(users.userId(), "Macrosaurus user", "en", "UTC", UnitSystem.METRIC, null, null, null, BigDecimal("1.2"))

    @PutMapping
    fun update(
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ProfileView =
        profiles
            .upsert(
                users.userId(),
                UpdateProfileCommand(
                    request.displayName,
                    request.locale,
                    request.timezone,
                    request.unitSystem,
                    request.birthDate,
                    request.heightCm,
                    request.formulaSex,
                    request.activityMultiplier,
                ),
            ).toView()
}
