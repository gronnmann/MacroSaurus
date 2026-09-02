package com.macrosaurus.catalog

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.postgresql.PostgreSQLContainer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.sql.DataSource

class CatalogImportApplicationIT {
    @Test
    fun `database-only context imports a release without web or security beans`() {
        val postgres = PostgreSQLContainer("postgres:17-alpine")
        postgres.start()
        try {
            val context =
                SpringApplicationBuilder(CatalogImportApplication::class.java)
                    .web(WebApplicationType.NONE)
                    .properties(
                        "spring.main.banner-mode=off",
                        "logging.level.root=ERROR",
                    ).run(
                        "--macrosaurus.catalog-import.enabled=true",
                        "--spring.datasource.url=${postgres.jdbcUrl}",
                        "--spring.datasource.username=${postgres.username}",
                        "--spring.datasource.password=${postgres.password}",
                    )
            try {
                assertThat(context.containsBean("acquisitionController")).isFalse()
                assertThat(context.containsBean("securityFilterChain")).isFalse()

                val foods =
                    (1..2_121).joinToString(",") { index ->
                        """
                        {
                          "externalId": "test-food-$index",
                          "name": "Database-only food $index",
                          "aliases": {"nb": "Databasefri mat $index"},
                          "nutrients": {"energy_kcal": 71, "protein_g": 2.5},
                          "portions": [{"name": "1 bowl", "gramWeight": 250, "default": true}]
                        }
                        """.trimIndent()
                    }
                val input =
                    """
                    {
                      "source": "MATVARETABELLEN",
                      "releaseKey": "test-2026",
                      "checksum": "sha256:database-only-test",
                      "foods": [$foods]
                    }
                    """.trimIndent()
                val output = ByteArrayOutputStream()

                context
                    .getBean(CatalogImportCommand::class.java)
                    .execute(ByteArrayInputStream(input.toByteArray()), output)

                assertThat(output.toString()).contains("\"importedCount\":2121")
                val jdbc = JdbcTemplate(context.getBean(DataSource::class.java))
                assertThat(
                    jdbc.queryForObject(
                        "select count(*) from foods where source_kind = 'MATVARETABELLEN'",
                        Int::class.java,
                    ),
                ).isEqualTo(2_121)
                assertThat(
                    jdbc.queryForObject(
                        """
                        select count(*) from food_nutrients n
                        join food_revisions r on r.id = n.food_revision_id
                        join foods f on f.id = r.food_id
                        where f.source_kind = 'MATVARETABELLEN'
                        """.trimIndent(),
                        Int::class.java,
                    ),
                ).isEqualTo(4_242)
                assertThat(
                    jdbc.queryForObject(
                        """
                        select count(*) from food_aliases a
                        join foods f on f.id = a.food_id
                        where f.source_kind = 'MATVARETABELLEN'
                        """.trimIndent(),
                        Int::class.java,
                    ),
                ).isEqualTo(2_121)
                assertThat(
                    jdbc.queryForObject(
                        """
                        select count(*) from portions p
                        join food_revisions r on r.id = p.food_revision_id
                        join foods f on f.id = r.food_id
                        where f.source_kind = 'MATVARETABELLEN'
                        """.trimIndent(),
                        Int::class.java,
                    ),
                ).isEqualTo(2_121)
            } finally {
                context.close()
            }
        } finally {
            postgres.stop()
        }
    }
}
