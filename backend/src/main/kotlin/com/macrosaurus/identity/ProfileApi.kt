package com.macrosaurus.identity

import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import org.jooq.DSLContext
import org.jooq.impl.DSL.currentOffsetDateTime
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.springframework.stereotype.Service
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
    val unitSystem: String,
    val birthDate: LocalDate?,
    val heightCm: BigDecimal?,
    val formulaSex: String?,
    val activityMultiplier: BigDecimal,
)

data class UpdateProfileRequest(
    @field:NotBlank val displayName: String,
    @field:NotBlank val locale: String = "en",
    @field:NotBlank val timezone: String = "UTC",
    val unitSystem: String = "METRIC",
    val birthDate: LocalDate? = null,
    @field:DecimalMin("30") @field:DecimalMax("300") val heightCm: BigDecimal? = null,
    val formulaSex: String? = null,
    @field:DecimalMin("1.0") @field:DecimalMax("2.5") val activityMultiplier: BigDecimal = BigDecimal("1.2"),
)

@Service
class ProfileService(
    private val db: DSLContext,
) {
    fun get(userId: String): ProfileView? =
        db
            .select(
                field("user_id", String::class.java),
                field("display_name", String::class.java),
                field("locale", String::class.java),
                field("timezone", String::class.java),
                field("unit_system", String::class.java),
                field("birth_date", LocalDate::class.java),
                field("height_cm", BigDecimal::class.java),
                field("formula_sex", String::class.java),
                field("activity_multiplier", BigDecimal::class.java),
            ).from(table("user_profiles"))
            .where(field("user_id").eq(userId))
            .fetchOne()
            ?.let {
                ProfileView(it.value1(), it.value2(), it.value3(), it.value4(), it.value5(), it.value6(), it.value7(), it.value8(), it.value9())
            }

    fun upsert(
        userId: String,
        request: UpdateProfileRequest,
    ): ProfileView {
        db
            .insertInto(table("user_profiles"))
            .columns(
                field("user_id"),
                field("display_name"),
                field("locale"),
                field("timezone"),
                field("unit_system"),
                field("birth_date"),
                field("height_cm"),
                field("formula_sex"),
                field("activity_multiplier"),
            ).values(
                userId,
                request.displayName,
                request.locale,
                request.timezone,
                request.unitSystem,
                request.birthDate,
                request.heightCm,
                request.formulaSex,
                request.activityMultiplier,
            ).onConflict(field("user_id"))
            .doUpdate()
            .set(field("display_name"), request.displayName)
            .set(field("locale"), request.locale)
            .set(field("timezone"), request.timezone)
            .set(field("unit_system"), request.unitSystem)
            .set(field("birth_date"), request.birthDate)
            .set(field("height_cm"), request.heightCm)
            .set(field("formula_sex"), request.formulaSex)
            .set(field("activity_multiplier"), request.activityMultiplier)
            .set(field("updated_at"), currentOffsetDateTime())
            .execute()
        return requireNotNull(get(userId))
    }
}

@RestController
@RequestMapping("/api/v1/me/profile")
class ProfileController(
    private val users: UserContext,
    private val profiles: ProfileService,
) {
    @GetMapping
    fun get(): ProfileView =
        profiles.get(users.userId())
            ?: ProfileView(users.userId(), "Macrosaurus user", "en", "UTC", "METRIC", null, null, null, BigDecimal("1.2"))

    @PutMapping
    fun update(
        @Valid @RequestBody request: UpdateProfileRequest,
    ): ProfileView = profiles.upsert(users.userId(), request)
}
