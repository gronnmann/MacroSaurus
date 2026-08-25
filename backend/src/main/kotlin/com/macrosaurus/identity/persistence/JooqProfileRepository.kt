package com.macrosaurus.identity.persistence

import com.macrosaurus.identity.FormulaSex
import com.macrosaurus.identity.ProfileSnapshot
import com.macrosaurus.identity.UnitSystem
import org.jooq.DSLContext
import org.jooq.impl.DSL.currentOffsetDateTime
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDate

@Repository
internal class JooqProfileRepository(
    private val db: DSLContext,
) {
    fun get(userId: String): ProfileSnapshot? =
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
                ProfileSnapshot(
                    it.value1(),
                    it.value2(),
                    it.value3(),
                    it.value4(),
                    UnitSystem.valueOf(it.value5()),
                    it.value6(),
                    it.value7(),
                    it.value8()?.let(FormulaSex::valueOf),
                    it.value9(),
                )
            }

    fun upsert(profile: ProfileSnapshot) {
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
                profile.userId,
                profile.displayName,
                profile.locale,
                profile.timezone,
                profile.unitSystem.name,
                profile.birthDate,
                profile.heightCm,
                profile.formulaSex?.name,
                profile.activityMultiplier,
            ).onConflict(field("user_id"))
            .doUpdate()
            .set(field("display_name"), profile.displayName)
            .set(field("locale"), profile.locale)
            .set(field("timezone"), profile.timezone)
            .set(field("unit_system"), profile.unitSystem.name)
            .set(field("birth_date"), profile.birthDate)
            .set(field("height_cm"), profile.heightCm)
            .set(field("formula_sex"), profile.formulaSex?.name)
            .set(field("activity_multiplier"), profile.activityMultiplier)
            .set(field("updated_at"), currentOffsetDateTime())
            .execute()
    }
}
