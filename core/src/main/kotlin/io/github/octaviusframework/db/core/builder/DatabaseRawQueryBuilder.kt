package io.github.octaviusframework.db.core.builder

import io.github.octaviusframework.db.api.builder.RawQueryBuilder
import io.github.octaviusframework.db.core.jdbc.JdbcTemplate
import io.github.octaviusframework.db.core.jdbc.RowMappers
import io.github.octaviusframework.db.core.type.KotlinToPostgresConverter

/**
 * Executes a raw SQL query that returns results.
 * Allows passing arbitrary SQL for execution with convenient terminal methods.
 */
internal class DatabaseRawQueryBuilder(
    jdbcTemplate: JdbcTemplate,
    kotlinToPostgresConverter: KotlinToPostgresConverter,
    rowMappers: RowMappers,
    private val sql: String
) : AbstractQueryBuilder<RawQueryBuilder>(jdbcTemplate, kotlinToPostgresConverter, rowMappers, null), RawQueryBuilder {
    override val canReturnResultsByDefault = true
    override fun buildSql(): String = sql

    // NOTE: This function has no practical use
    // due to the fact that this builder is immutable
    override fun copy(): DatabaseRawQueryBuilder {
        return this
    }
}
