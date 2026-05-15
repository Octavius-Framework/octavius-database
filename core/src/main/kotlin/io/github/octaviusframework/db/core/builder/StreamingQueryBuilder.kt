package io.github.octaviusframework.db.core.builder

import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.builder.StreamingTerminalMethods
import io.github.octaviusframework.db.api.exception.DatabaseException
import io.github.octaviusframework.db.api.exception.QueryContext
import io.github.octaviusframework.db.core.exception.ExceptionTranslator
import io.github.octaviusframework.db.core.jdbc.RowMapper
import io.github.octaviusframework.db.core.type.PositionalQuery
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.reflect.KClass

internal class StreamingQueryBuilder(
    private val builder: AbstractQueryBuilder<*>,
    private val fetchSize: Int
) : StreamingTerminalMethods {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private fun <T> executeStream(
        params: Map<String, Any?>,
        rowMapper: RowMapper<T>,
        action: (item: T) -> Unit
    ): DataResult<Unit> {
        val sql = builder.buildSql() // Can throw FatalDatabaseException (BadStatementException)
        return builder.queryExecutor.executeStream(sql, params, fetchSize, rowMapper, builder.queryOptions, action)
    }

    // --- Public terminal methods that use the helper method ---

    override fun forEachRow(params: Map<String, Any?>, action: (row: Map<String, Any?>) -> Unit): DataResult<Unit> {
        return executeStream(params, builder.rowMappers.ColumnNameMapper(builder.queryOptions), action)
    }

    override fun <T : Any> forEachRowOf(
        kClass: KClass<T>,
        params: Map<String, Any?>,
        action: (obj: T) -> Unit
    ): DataResult<Unit> {
        return executeStream(params, builder.rowMappers.DataObjectMapper(kClass, builder.queryOptions), action)
    }
}
