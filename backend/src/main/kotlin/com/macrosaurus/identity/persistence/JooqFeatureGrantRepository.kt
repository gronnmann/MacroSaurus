package com.macrosaurus.identity.persistence

import com.macrosaurus.identity.ProfileSnapshot
import com.macrosaurus.identity.UnitSystem
import com.macrosaurus.identity.UserFeature
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal

internal data class AdminUserRecord(
    val profile: ProfileSnapshot,
    val aiLabelScanEnabled: Boolean,
)

@Repository
internal class JooqFeatureGrantRepository(
    private val db: DSLContext,
) {
    fun enabled(
        userId: String,
        feature: UserFeature,
    ): Boolean =
        db
            .fetchOne(
                "select coalesce((select enabled from user_feature_grants where user_id = ? and feature_code = ?), false)",
                userId,
                feature.code,
            )?.get(0, Boolean::class.java) ?: false

    fun set(
        userId: String,
        feature: UserFeature,
        enabled: Boolean,
        grantedBy: String,
    ): Int =
        db.execute(
            """
            insert into user_feature_grants(user_id, feature_code, enabled, granted_by)
            values (?, ?, ?, ?)
            on conflict (user_id, feature_code) do update
               set enabled = excluded.enabled, granted_by = excluded.granted_by,
                   updated_at = current_timestamp
            """.trimIndent(),
            userId,
            feature.code,
            enabled,
            grantedBy,
        )

    fun users(query: String): List<AdminUserRecord> =
        db
            .fetch(
                """
                select p.user_id, p.display_name, p.locale, p.timezone, p.unit_system,
                       p.birth_date, p.height_cm, p.formula_sex, p.activity_multiplier,
                       coalesce(g.enabled, false) as ai_label_scan_enabled
                  from user_profiles p
                  left join user_feature_grants g
                    on g.user_id = p.user_id and g.feature_code = ?
                 where ? = '' or p.display_name ilike '%' || ? || '%' or p.user_id ilike '%' || ? || '%'
                 order by lower(p.display_name), p.user_id
                 limit 100
                """.trimIndent(),
                UserFeature.AI_LABEL_SCAN.code,
                query.trim(),
                query.trim(),
                query.trim(),
            ).map { record ->
                AdminUserRecord(
                    ProfileSnapshot(
                        record.get("user_id", String::class.java)!!,
                        record.get("display_name", String::class.java)!!,
                        record.get("locale", String::class.java)!!,
                        record.get("timezone", String::class.java)!!,
                        UnitSystem.valueOf(record.get("unit_system", String::class.java)!!),
                        record.get("birth_date", java.time.LocalDate::class.java),
                        record.get("height_cm", BigDecimal::class.java),
                        record.get("formula_sex", String::class.java)?.let(com.macrosaurus.identity.FormulaSex::valueOf),
                        record.get("activity_multiplier", BigDecimal::class.java)!!,
                    ),
                    record.get("ai_label_scan_enabled", Boolean::class.java) == true,
                )
            }
}
