package com.macrosaurus.catalog.persistence

import com.macrosaurus.catalog.BasisType
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodSnapshot
import com.macrosaurus.catalog.NutrientDefinition
import com.macrosaurus.catalog.PortionSnapshot
import com.macrosaurus.catalog.SourceKind
import com.macrosaurus.catalog.domain.FoodDraftValidator
import com.macrosaurus.shared.ForbiddenException
import com.macrosaurus.shared.JsonCodec
import com.macrosaurus.shared.NotFoundException
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@Repository
internal class JooqCatalogRepository(
    private val db: DSLContext,
    private val json: JsonCodec,
) {
    fun nutrients(): List<NutrientDefinition> =
        db
            .fetch(
                "select code, display_name, category, unit, sort_order from nutrient_definitions order by sort_order, code",
            ).map {
                NutrientDefinition(
                    it.get("code", String::class.java)!!,
                    it.get("display_name", String::class.java)!!,
                    it.get("category", String::class.java)!!,
                    it.get("unit", String::class.java)!!,
                    it.get("sort_order", Int::class.java)!!,
                )
            }

    fun search(
        userId: String,
        query: String,
        limit: Int,
    ): List<FoodSnapshot> {
        val term = "%${query.trim()}%"
        return db
            .fetch(
                """
                select distinct on (f.id) f.id, fr.id as revision_id, fr.revision, fr.name, fr.brand,
                       f.barcode, f.source_kind, f.external_id, sr.release_key as source_release,
                       fr.basis_type, fr.basis_amount, fr.basis_unit, fr.density_g_per_ml, fr.created_at
                  from foods f join food_revisions fr on fr.food_id = f.id
                  left join food_source_releases sr on sr.id = fr.source_release_id
                 where (f.source_kind <> 'USER' or f.owner_user_id = ?)
                   and f.active
                   and (fr.name ilike ? or coalesce(fr.brand, '') ilike ? or coalesce(f.barcode, '') = ?
                        or exists (select 1 from food_aliases a where a.food_id = f.id and a.name ilike ?))
                 order by f.id, fr.revision desc
                 limit ?
                """.trimIndent(),
                userId,
                term,
                term,
                query.trim(),
                term,
                limit.coerceIn(1, 100),
            ).let(::foodsFromRecords)
    }

    fun get(
        userId: String,
        foodId: UUID,
    ): FoodSnapshot {
        val record =
            db.fetchOne(
                """
                select f.id, fr.id as revision_id, fr.revision, fr.name, fr.brand, f.barcode,
                       f.source_kind, f.external_id, sr.release_key as source_release,
                       fr.basis_type, fr.basis_amount, fr.basis_unit, fr.density_g_per_ml, fr.created_at
                  from foods f join food_revisions fr on fr.food_id = f.id
                  left join food_source_releases sr on sr.id = fr.source_release_id
                 where f.id = ? and (f.source_kind <> 'USER' or f.owner_user_id = ?)
                 order by fr.revision desc limit 1
                """.trimIndent(),
                foodId,
                userId,
            ) ?: throw NotFoundException("Food was not found")
        return foodsFromRecords(listOf(record)).single()
    }

    fun byRevision(
        userId: String,
        revisionId: UUID,
    ): FoodSnapshot {
        val record =
            db.fetchOne(
                """
                select f.id, fr.id as revision_id, fr.revision, fr.name, fr.brand, f.barcode,
                       f.source_kind, f.external_id, sr.release_key as source_release,
                       fr.basis_type, fr.basis_amount, fr.basis_unit, fr.density_g_per_ml, fr.created_at
                  from foods f join food_revisions fr on fr.food_id = f.id
                  left join food_source_releases sr on sr.id = fr.source_release_id
                 where fr.id = ? and (f.source_kind <> 'USER' or f.owner_user_id = ?)
                """.trimIndent(),
                revisionId,
                userId,
            ) ?: throw NotFoundException("Food revision was not found")
        return foodsFromRecords(listOf(record)).single()
    }

    fun byRevisions(
        userId: String,
        revisionIds: Collection<UUID>,
    ): Map<UUID, FoodSnapshot> {
        if (revisionIds.isEmpty()) return emptyMap()
        val records =
            db
                .select(
                    field("f.id").`as`("id"),
                    field("fr.id").`as`("revision_id"),
                    field("fr.revision").`as`("revision"),
                    field("fr.name").`as`("name"),
                    field("fr.brand").`as`("brand"),
                    field("f.barcode").`as`("barcode"),
                    field("f.source_kind").`as`("source_kind"),
                    field("f.external_id").`as`("external_id"),
                    field("sr.release_key").`as`("source_release"),
                    field("fr.basis_type").`as`("basis_type"),
                    field("fr.basis_amount").`as`("basis_amount"),
                    field("fr.basis_unit").`as`("basis_unit"),
                    field("fr.density_g_per_ml").`as`("density_g_per_ml"),
                    field("fr.created_at").`as`("created_at"),
                ).from(table("foods f"))
                .join(table("food_revisions fr"))
                .on(field("fr.food_id").eq(field("f.id")))
                .leftJoin(table("food_source_releases sr"))
                .on(field("sr.id").eq(field("fr.source_release_id")))
                .where(field("fr.id", UUID::class.java).`in`(revisionIds))
                .and(field("f.source_kind").ne("USER").or(field("f.owner_user_id", String::class.java).eq(userId)))
                .fetch()
        val foods = foodsFromRecords(records)
        if (foods.size != revisionIds.toSet().size) throw NotFoundException("One or more food revisions were not found")
        return foods.associateBy(FoodSnapshot::revisionId)
    }

    fun byBarcode(
        userId: String,
        barcode: String,
    ): List<FoodSnapshot> =
        db
            .fetch(
                """
                select matched.*
                  from (
                    select distinct on (f.id) f.id, fr.id as revision_id, fr.revision, fr.name, fr.brand,
                           f.barcode, f.source_kind, f.external_id, sr.release_key as source_release,
                           fr.basis_type, fr.basis_amount, fr.basis_unit, fr.density_g_per_ml, fr.created_at
                      from foods f join food_revisions fr on fr.food_id = f.id
                      left join food_source_releases sr on sr.id = fr.source_release_id
                     where f.barcode = ?
                       and f.active
                       and (f.source_kind <> 'USER' or f.owner_user_id = ?)
                     order by f.id, fr.revision desc
                  ) matched
                 order by case when matched.source_kind = 'USER' then 0 else 1 end,
                          matched.created_at desc
                """.trimIndent(),
                normalizeBarcode(barcode),
                userId,
            ).let(::foodsFromRecords)

    fun create(
        userId: String,
        draft: FoodDraft,
        source: SourceKind,
        externalId: String?,
    ): FoodSnapshot {
        validateDraft(draft)
        val foodId = UUID.randomUUID()
        val revisionId = UUID.randomUUID()
        db.execute(
            "insert into foods(id, owner_user_id, source_kind, external_id, barcode) values (?, ?, ?, ?, ?)",
            foodId,
            if (source == SourceKind.USER) userId else null,
            source.name,
            externalId,
            normalizeBarcode(draft.barcode),
        )
        insertRevision(foodId, revisionId, 1, draft)
        return get(userId, foodId)
    }

    fun revise(
        userId: String,
        foodId: UUID,
        draft: FoodDraft,
    ): FoodSnapshot {
        val owned =
            db
                .selectOne()
                .from(table("foods"))
                .where(field("id", UUID::class.java).eq(foodId))
                .and(field("owner_user_id", String::class.java).eq(userId))
                .forUpdate()
                .fetchOne()
        if (owned == null) throw NotFoundException("Food was not found")
        val existing = get(userId, foodId)
        if (existing.source != SourceKind.USER) throw ForbiddenException("External source foods cannot be edited")
        validateDraft(draft)
        val revisionId = UUID.randomUUID()
        insertRevision(foodId, revisionId, existing.revision + 1, draft)
        db.execute("update foods set barcode = ? where id = ?", normalizeBarcode(draft.barcode), foodId)
        return get(userId, foodId)
    }

    private fun insertRevision(
        foodId: UUID,
        revisionId: UUID,
        revision: Int,
        request: FoodDraft,
        sourceReleaseId: UUID? = null,
        locale: String? = null,
    ) {
        db.execute(
            """
            insert into food_revisions(id, food_id, revision, name, brand, basis_type, basis_amount, basis_unit,
                                       density_g_per_ml, source_release_id, locale)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            revisionId,
            foodId,
            revision,
            request.name.trim(),
            request.brand?.trim(),
            request.basisType.name,
            request.basisAmount,
            request.basisUnit,
            request.densityGPerMl,
            sourceReleaseId,
            locale,
        )
        request.nutrients.forEach { (code, amount) ->
            db.execute(
                "insert into food_nutrients(food_revision_id, nutrient_code, amount, value_kind) values (?, ?, ?, 'REPORTED')",
                revisionId,
                code,
                amount,
            )
        }
        request.portions.forEach { portion ->
            db.execute(
                """
                insert into portions(
                    id, food_revision_id, name, quantity, gram_weight, milliliter_volume, is_default
                ) values (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                UUID.randomUUID(),
                revisionId,
                portion.name.trim(),
                portion.quantity,
                portion.gramWeight,
                portion.milliliterVolume,
                portion.default,
            )
        }
    }

    private fun validateDraft(request: FoodDraft) {
        val knownNutrients =
            if (request.nutrients.isEmpty()) {
                emptySet()
            } else {
                db
                    .select(field("code", String::class.java))
                    .from(table("nutrient_definitions"))
                    .where(field("code", String::class.java).`in`(request.nutrients.keys))
                    .fetchSet(field("code", String::class.java))
            }
        FoodDraftValidator.validate(request, knownNutrients)
    }

    private fun foodsFromRecords(records: List<org.jooq.Record>): List<FoodSnapshot> {
        if (records.isEmpty()) return emptyList()
        val revisionIds = records.map { it.get("revision_id", UUID::class.java)!! }
        val nutrients =
            db
                .select(
                    field("food_revision_id", UUID::class.java),
                    field("nutrient_code", String::class.java),
                    field("amount", BigDecimal::class.java),
                ).from(table("food_nutrients"))
                .where(field("food_revision_id", UUID::class.java).`in`(revisionIds))
                .fetch()
                .groupBy { it.value1() }
                .mapValues { (_, values) -> values.associate { it.value2() to it.value3() } }
        val portions =
            db
                .select(
                    field("food_revision_id", UUID::class.java),
                    field("id", UUID::class.java),
                    field("name", String::class.java),
                    field("quantity", BigDecimal::class.java),
                    field("gram_weight", BigDecimal::class.java),
                    field("milliliter_volume", BigDecimal::class.java),
                    field("is_default", Boolean::class.java),
                ).from(table("portions"))
                .where(field("food_revision_id", UUID::class.java).`in`(revisionIds))
                .orderBy(field("is_default").desc(), field("name"))
                .fetch()
                .groupBy { it.value1() }
                .mapValues { (_, values) ->
                    values.map {
                        PortionSnapshot(it.value2(), it.value3(), it.value4(), it.value5(), it.value6(), it.value7() ?: false)
                    }
                }
        return records.map { record ->
            val revisionId = record.get("revision_id", UUID::class.java)!!
            FoodSnapshot(
                record.get("id", UUID::class.java)!!,
                revisionId,
                record.get("revision", Int::class.java)!!,
                record.get("name", String::class.java)!!,
                record.get("brand", String::class.java),
                record.get("barcode", String::class.java),
                SourceKind.valueOf(record.get("source_kind", String::class.java)!!),
                BasisType.valueOf(record.get("basis_type", String::class.java)!!),
                record.get("basis_amount", BigDecimal::class.java)!!,
                record.get("basis_unit", String::class.java)!!,
                record.get("density_g_per_ml", BigDecimal::class.java),
                nutrients[revisionId].orEmpty(),
                portions[revisionId].orEmpty(),
                record.get("created_at", OffsetDateTime::class.java)!!,
                record.get("external_id", String::class.java),
                record.get("source_release", String::class.java),
            )
        }
    }

    private fun normalizeBarcode(barcode: String?): String? = barcode?.filter(Char::isDigit)?.takeIf { it.isNotBlank() }
}
