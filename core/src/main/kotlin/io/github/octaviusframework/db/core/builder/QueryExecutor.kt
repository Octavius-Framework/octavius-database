package io.github.octaviusframework.db.core.builder

import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.exception.QueryContext
import io.github.octaviusframework.db.core.exception.ExceptionTranslator
import io.github.octaviusframework.db.core.jdbc.JdbcTemplate
import io.github.octaviusframework.db.core.jdbc.RowMapper
import io.github.octaviusframework.db.core.type.InternalQueryOptions
import io.github.octaviusframework.db.core.type.KotlinToPostgresConverter
import io.github.octaviusframework.db.core.type.PositionalQuery
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Internal component responsible for executing SQL queries.
 * 
 * Centralizes logic for:
 * - Converting SQL with named parameters to positional parameters.
 * - Logging executed queries.
 * - Exception translation.
 * - Interaction with [JdbcTemplate].
 */
internal class QueryExecutor(
    private val jdbcTemplate: JdbcTemplate,
    private val kotlinToPostgresConverter: KotlinToPostgresConverter
) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    /**
     * Executes a query that returns a list of results.
     */
    fun <M> executeQuery(
        sql: String,
        params: Map<String, Any?>,
        rowMapper: RowMapper<M>,
        options: InternalQueryOptions
    ): DataResult<List<M>> {
        return execute(sql, params, options) { positionalQuery ->
            val results: List<M> = jdbcTemplate.query(positionalQuery, rowMapper)
            DataResult.Success(results)
        }
    }

    /**
     * Executes a modifying query and returns the number of affected rows.
     */
    fun executeUpdate(
        sql: String,
        params: Map<String, Any?>,
        options: InternalQueryOptions
    ): DataResult<Int> {
        return execute(sql, params, options) { positionalQuery ->
            val affectedRows = jdbcTemplate.update(positionalQuery)
            DataResult.Success(affectedRows)
        }
    }

    /**
     * Executes a query and streams the results row by row.
     */
    fun <M> executeStream(
        sql: String,
        params: Map<String, Any?>,
        fetchSize: Int,
        rowMapper: RowMapper<M>,
        options: InternalQueryOptions,
        action: (item: M) -> Unit
    ): DataResult<Unit> {
        return execute(sql, params, options) { positionalQuery ->
            jdbcTemplate.query(positionalQuery, fetchSize) { rs ->
                while (rs.next()) {
                    val mappedItem = rowMapper.mapRow(rs)
                    action(mappedItem)
                }
            }
            DataResult.Success(Unit)
        }
    }

    /**
     * Generic helper for executing any database action with standard logging and error handling.
     */
    private fun <R> execute(
        sql: String,
        params: Map<String, Any?>,
        options: InternalQueryOptions,
        action: (positionalQuery: PositionalQuery) -> DataResult<R>
    ): DataResult<R> {
        var positionalQuery: PositionalQuery? = null
        return try {
            positionalQuery = kotlinToPostgresConverter.toPositionalQuery(sql, params, options)
            logger.debug {
                """
                Executing query (original): $sql with params: $params
                  -> (database): ${positionalQuery.sql} with positional params: ${positionalQuery.params}
                """.trimIndent()
            }
            action(positionalQuery)
        } catch (e: Exception) {
            val queryContext = QueryContext(
                sql = sql,
                parameters = params,
                dbSql = positionalQuery?.sql,
                dbParameters = positionalQuery?.params
            )

            val translatedException = ExceptionTranslator.translate(e, queryContext)
            logger.error(translatedException) { "Database error occurred" }

            return DataResult.Failure(translatedException)
        }
    }
}
