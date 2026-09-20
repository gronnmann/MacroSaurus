package com.macrosaurus

import com.macrosaurus.catalog.FoodCatalog
import com.macrosaurus.catalog.FoodDraft
import com.macrosaurus.catalog.FoodSnapshot
import com.macrosaurus.catalog.SourceKind
import com.macrosaurus.catalog.application.CatalogService
import com.macrosaurus.recipes.application.RecipeIngredientCommand
import com.macrosaurus.recipes.application.RecipeService
import com.macrosaurus.recipes.application.SaveRecipeCommand
import com.macrosaurus.shared.SearchStage
import com.macrosaurus.tracking.application.AddFoodEntryCommand
import com.macrosaurus.tracking.application.TrackableType
import com.macrosaurus.tracking.application.TrackingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:tc:postgresql:17-alpine:///macrosaurus",
        "spring.datasource.username=macrosaurus",
        "spring.datasource.password=macrosaurus",
    ],
)
@Transactional
class CatalogSearchIT {
    @Autowired private lateinit var catalog: FoodCatalog

    @Autowired private lateinit var foods: CatalogService

    @Autowired private lateinit var recipes: RecipeService

    @Autowired private lateinit var tracking: TrackingService

    @Autowired private lateinit var db: JdbcTemplate

    private val user = "search-${UUID.randomUUID()}"

    @Test
    fun `butter ranks the primary food before compounds and excludes partial words`() {
        val peanut = food("Peanut butter with omega-3, creamy")
        food("Biscuits, plain or buttermilk, frozen, baked")
        food("Biscuits, plain or buttermilk, prepared from recipe")
        val oil = food("Butter oil, anhydrous")
        val butter = food("Butter, without salt")

        assertThat(catalog.search(user, "Butter").map { it.id }).containsExactly(butter.id, oil.id, peanut.id)
        assertThat(tracking.trackables(user, "Butter", TrackableType.ALL, 30).map { it.id })
            .containsExactly(butter.id, oil.id, peanut.id)
        log(peanut)
        assertThat(tracking.trackables(user, "Butter", TrackableType.ALL, 30).map { it.id })
            .containsExactly(peanut.id, butter.id, oil.id)
    }

    @Test
    fun `all words match names or Norwegian aliases together with a brand regardless of order`() {
        val butter = food("Butter, without salt", "TINE")
        alias(butter, "Smør, usaltet")
        alias(butter, "Smør uten salt")
        food("Butter, salted", "TINE")
        food("Butter, without salt", "Other")

        assertThat(catalog.search(user, "  TINE, SALT---without butter ").map { it.id }).containsExactly(butter.id)
        assertThat(catalog.search(user, "usaltet TINE smør").map { it.id }).containsExactly(butter.id)
        assertThat(catalog.search(user, "smør").map { it.id }).containsExactly(butter.id)
        assertThat(catalog.search(user, "tine smør xyzq")).isEmpty()
        assertThat(catalog.search(user, "%_\\'")).isEmpty()
        assertThat(catalog.search(user, "tine%usaltet").map { it.id }).containsExactly(butter.id)
    }

    @Test
    fun `prefix and typo matching only run when the preceding stage is empty`() {
        val butter = food("Butter, without salt")
        val biscuit = food("Biscuits, buttermilk")
        assertThat(catalog.search(user, "butter").map { it.id }).containsExactly(butter.id)
        assertThat(catalog.search(user, "butt").map { it.id }).containsExactly(butter.id, biscuit.id)
        assertThat(catalog.search(user, "salt butt").map { it.id }).containsExactly(butter.id)
        assertThat(catalog.search(user, "buttr").map { it.id }).containsExactly(butter.id)
        assertThat(catalog.search(user, "salt buttr").map { it.id }).containsExactly(butter.id)
        assertThat(catalog.search(user, "b")).isEmpty()
        assertThat(catalog.search(user, "btr")).isEmpty()

        val exact = food("Buttr spread")
        assertThat(catalog.search(user, "buttr").map { it.id }).containsExactly(exact.id)
        val prefix = food("Buttrx spread")
        foods.revise(user, exact.id, draft("Unrelated food"))
        assertThat(catalog.search(user, "buttr").map { it.id }).containsExactly(prefix.id)
    }

    @Test
    fun `fallback is chosen across foods and recipes and respects type filters`() {
        val butter = food("Butter, without salt")
        val exactRecipe = recipe("Butt", butter)
        val broadRecipe = recipe("Buttermilk biscuits", butter)
        assertThat(tracking.trackables(user, "butt", TrackableType.ALL, 30).map { it.id }).containsExactly(exactRecipe.id)
        assertThat(tracking.trackables(user, "butt", TrackableType.FOOD, 30).map { it.id }).containsExactly(butter.id)
        assertThat(tracking.trackables(user, "butter", TrackableType.ALL, 30).map { it.id }).containsExactly(butter.id)
        assertThat(tracking.trackables(user, "butter", TrackableType.RECIPE, 30).map { it.id }).containsExactly(broadRecipe.id)
        assertThat(tracking.trackables(user, "biscuits buttrmilk", TrackableType.RECIPE, 30).map { it.id }).containsExactly(broadRecipe.id)
    }

    @Test
    fun `search uses only current active visible revisions including recent foods`() {
        val renamed = food("Searchobsolete butter")
        log(renamed)
        val current = foods.revise(user, renamed.id, draft("Searchcurrent pear"))
        assertThat(catalog.search(user, "searchobsolete")).isEmpty()
        assertThat(catalog.search(user, "searchcurrent").single().revisionId).isEqualTo(current.revisionId)
        val inactive = food("Butter inactive")
        log(inactive)
        db.update("update foods set active = false where id = ?", inactive.id)
        foods.create("another-user", draft("Butter private"), SourceKind.USER, null)
        assertThat(catalog.search(user, "butter")).isEmpty()
        assertThat(tracking.trackables(user, "butter", TrackableType.ALL, 30)).isEmpty()
        val recipe = recipe("Obsoleterecipe", current)
        recipes.revise(user, recipe.id, recipeDraft("Currentrecipe", current))
        assertThat(tracking.trackables(user, "obsoleterecipe", TrackableType.RECIPE, 30)).isEmpty()
    }

    @Test
    fun `ranking precedes limits and matching recent aliases survive candidate limits`() {
        repeat(40) { food("Peanut butter variation $it") }
        val butter = food("Butter, without salt")
        val recent = food("Zzz spread")
        alias(recent, "Zzz butter spread")
        alias(recent, "Peanut butter spread")
        assertThat(catalog.search(user, "butter", 1).map { it.id }).containsExactly(butter.id)
        log(recent)
        assertThat(tracking.trackables(user, "butter", TrackableType.ALL, 1).map { it.id }).containsExactly(recent.id)
        assertThat(tracking.trackables(user, "butter", TrackableType.ALL, 2).map { it.id }).containsExactly(recent.id, butter.id)
        assertThat(catalog.search(user, "butter", 100).map { it.id }).doesNotHaveDuplicates()
    }

    @Test
    fun `barcode matches are exact and brand matches rank below food names`() {
        val named = food("Acme butter")
        val branded = food("A spread", "Acme")
        val barcode = foods.create(user, draft("Barcode product").copy(barcode = "5901234123457"), SourceKind.USER, null)
        assertThat(catalog.search(user, "acme").map { it.id }).containsExactly(named.id, branded.id)
        assertThat(catalog.search(user, "5901234123457").map { it.id }).containsExactly(barcode.id)
        assertThat(catalog.search(user, "5901234123458")).isEmpty()
        assertThat(catalog.search(user, "590123412345")).isEmpty()
    }

    @Test
    fun `ranking ties are stable and typo thresholds do not leak into other queries`() {
        val first = food("Butter")
        val second = food("Butter")
        val expected = listOf(first.id, second.id).sortedBy { it.toString() }
        assertThat(catalog.search(user, "butter").map { it.id }).isEqualTo(expected)
        assertThat(tracking.trackables(user, "butter", TrackableType.FOOD, 1).map { it.id }).containsExactly(expected.first())
        val threshold = db.queryForObject("select current_setting('pg_trgm.strict_word_similarity_threshold')", String::class.java)
        catalog.searchHits(user, "buttr", SearchStage.TYPO, 10)
        assertThat(db.queryForObject("select current_setting('pg_trgm.strict_word_similarity_threshold')", String::class.java)).isEqualTo(threshold)
    }

    @Test
    fun `ranked queries work against a catalog sized fixture`() {
        db.update(
            """
            insert into foods(id, owner_user_id, source_kind)
            select md5(? || i::text)::uuid, ?, 'USER' from generate_series(1, 15000) i
            """.trimIndent(),
            user,
            user,
        )
        db.update(
            """
            insert into food_revisions(id, food_id, revision, name, basis_type, basis_amount, basis_unit)
            select md5(f.id::text || 'revision')::uuid, f.id, 1,
                   case when row_number() over (order by f.id) % 100 = 0 then 'Peanut butter, ' else 'Catalog food, ' end || f.id::text,
                   'PER_100_G', 100, 'g'
            from foods f where f.owner_user_id = ?
            """.trimIndent(),
            user,
        )
        db.update(
            """
            insert into food_aliases(food_id, locale, name)
            select f.id, 'nb', 'Norsk mat ' || f.id::text from foods f where f.owner_user_id = ?
            """.trimIndent(),
            user,
        )
        val butter = food("Butter, without salt")
        alias(butter, "Smør, uten salt")
        for (index in listOf("food_revisions_search_idx", "food_revisions_brand_search_idx", "food_aliases_search_idx")) {
            db.queryForObject("select gin_clean_pending_list(?::regclass)", Long::class.java, index)
        }
        db.execute("analyze foods")
        db.execute("analyze food_revisions")
        db.execute("analyze food_aliases")
        for (query in listOf("butter", "butt", "buttr", "smør", "smørr")) {
            val started = System.nanoTime()
            val hits = catalog.search(user, query, 1)
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            assertThat(hits.map { it.id }).containsExactly(butter.id)
            println("Search benchmark: '$query', 15001 foods and aliases, ${elapsedMs}ms")
        }
        val plan =
            db.queryForList(
                """
                explain (analyze, buffers)
                select id from food_revisions
                where search_normalize(name || ' ' || coalesce(brand, '')) ~ '(^| )butter( |$)'
                """.trimIndent(),
                String::class.java,
            )
        println("Search name prefilter plan:\n" + plan.joinToString("\n"))
    }

    private fun draft(
        name: String,
        brand: String? = null,
    ) = FoodDraft(
        name = name,
        brand = brand,
        nutrients = mapOf("energy_kcal" to BigDecimal("100")),
    )

    private fun food(
        name: String,
        brand: String? = null,
    ) = foods.create(user, draft(name, brand), SourceKind.USER, null)

    private fun alias(
        food: FoodSnapshot,
        name: String,
    ) {
        db.update("insert into food_aliases(food_id, locale, name) values (?, 'nb', ?)", food.id, name)
    }

    private fun log(food: FoodSnapshot) {
        tracking.addFood(user, AddFoodEntryCommand(food.revisionId, BigDecimal("10"), "g", localDate = LocalDate.now()))
    }

    private fun recipeDraft(
        name: String,
        ingredient: FoodSnapshot,
    ) = SaveRecipeCommand(
        name,
        BigDecimal.ONE,
        null,
        listOf(RecipeIngredientCommand(ingredient.revisionId, BigDecimal("10"), "g", null)),
    )

    private fun recipe(
        name: String,
        ingredient: FoodSnapshot,
    ) = recipes.create(user, recipeDraft(name, ingredient))
}
