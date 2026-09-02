package com.macrosaurus

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.DriverManager

class MigrationUpgradeIT {
    @Test
    fun `production V7 upgrades without repair and cleans temporary legacy state`() {
        val postgres = PostgreSQLContainer("postgres:17-alpine")
        postgres.start()
        try {
            migrate(postgres, "6")
            postgres.execute(
                """
                insert into user_goal_settings(user_id, energy_mode, energy_value, macro_mode)
                values ('legacy-user', 'FIXED', 2000, 'CUSTOM_GRAMS')
                """.trimIndent(),
            )

            migrate(postgres, "7")
            postgres.execute(
                """
                insert into nutrition_day_reviews(user_id, local_date, status, estimated_total_kcal)
                values ('legacy-user', date '2026-09-01', 'ESTIMATED_TOTAL', 0)
                """.trimIndent(),
            )

            assertThat(postgres.queryInt("select checksum from flyway_schema_history where version = '7'"))
                .isEqualTo(893938796)
            assertThat(postgres.queryInt("select count(*) from nutrition_program_revisions where source = 'LEGACY'"))
                .isEqualTo(1)

            migrate(postgres, "9")

            assertThat(postgres.queryInt("select count(*) from nutrition_program_revisions where source = 'LEGACY'"))
                .isZero()
            assertThat(
                postgres.queryInt(
                    """
                    select count(*) from information_schema.columns
                    where table_schema = 'public'
                      and table_name = 'nutrition_program_revisions'
                      and column_name = 'legacy_settings'
                    """.trimIndent(),
                ),
            ).isZero()
            assertThat(
                postgres.queryString(
                    """
                    select status from nutrition_day_reviews
                    where user_id = 'legacy-user' and local_date = date '2026-09-01'
                    """.trimIndent(),
                ),
            ).isEqualTo("FASTING")
        } finally {
            postgres.stop()
        }
    }

    private fun migrate(
        postgres: PostgreSQLContainer,
        target: String,
    ) {
        Flyway
            .configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .target(target)
            .load()
            .migrate()
    }

    private fun PostgreSQLContainer.execute(sql: String) {
        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.createStatement().use { statement -> statement.execute(sql) }
        }
    }

    private fun PostgreSQLContainer.queryInt(sql: String): Int =
        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }

    private fun PostgreSQLContainer.queryString(sql: String): String =
        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    result.next()
                    result.getString(1)
                }
            }
        }
}
