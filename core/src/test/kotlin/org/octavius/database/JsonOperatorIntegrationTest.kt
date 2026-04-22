package org.octavius.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.octavius.data.DataAccess
import org.octavius.data.builder.toFieldStrict
import org.octavius.data.getOrThrow
import org.octavius.database.config.DatabaseConfig
import org.octavius.database.jdbc.DefaultJdbcTransactionProvider
import org.octavius.database.jdbc.JdbcTemplate
import java.nio.file.Files
import java.nio.file.Paths

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JsonOperatorIntegrationTest: AbstractIntegrationTest() {

    override val sqlToExecuteOnSetup: String = """
        CREATE TABLE IF NOT EXISTS json_test (
            id SERIAL PRIMARY KEY,
            data JSONB
        );

        INSERT INTO json_test (data) VALUES ('{"a": 1, "b": 2}');
        INSERT INTO json_test (data) VALUES ('{"b": 2, "c": 3}');
        INSERT INTO json_test (data) VALUES ('{"d": 4}');
    """.trimIndent()

    @Test
    fun `should handle jsonb exist operator '?'`() {
        // This query contains '?' which JDBC might mistake for a parameter
        val result = dataAccess.rawQuery("SELECT count(*) as count FROM json_test WHERE data ? 'a'")
            .toFieldStrict<Long>()
            .getOrThrow()

        assertThat(result).isEqualTo(1L)
    }

    @Test
    fun `should handle jsonb exist any operator '?|'`() {
        val result = dataAccess.rawQuery("SELECT count(*) as count FROM json_test WHERE data ?| array['a', 'c']")
            .toFieldStrict<Long>()
            .getOrThrow()

        // Rows: {"a": 1, "b": 2} (has a), {"b": 2, "c": 3} (has c)
        assertThat(result).isEqualTo(2L)
    }

    @Test
    fun `should handle jsonb exist all operator '?&'`() {
        val result = dataAccess.rawQuery("SELECT count(*) as count FROM json_test WHERE data ?& array['a', 'b']")
            .toFieldStrict<Long>()
            .getOrThrow()

        // Rows: {"a": 1, "b": 2} (has a and b)
        assertThat(result).isEqualTo(1L)
    }

    @Test
    fun `should handle mixing jsonb operators with named parameters`() {
        val result = dataAccess.rawQuery("SELECT count(*) as count FROM json_test WHERE data ? @key AND id = @id")
            .toFieldStrict<Long>("key" to "a", "id" to 1)
            .getOrThrow()

        assertThat(result).isEqualTo(1L)
    }
}
