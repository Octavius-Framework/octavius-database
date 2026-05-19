package io.github.octaviusframework.db.core.builder

import io.github.octaviusframework.db.api.DataResult
import io.github.octaviusframework.db.api.mapper.DataMapper
import io.github.octaviusframework.db.api.builder.StreamingTerminalMethods
import io.github.octaviusframework.db.core.jdbc.RowMapper
import io.github.octaviusframework.db.core.type.InternalQueryOptions
import kotlin.reflect.KClass

internal class StreamingQueryBuilder(
    private val builder: AbstractQueryBuilder<*>,
    private val fetchSize: Int
) : StreamingTerminalMethods {

    private fun <T> executeStream(
        params: Map<String, Any?>,
        rowMapper: RowMapper<T>,
        options: InternalQueryOptions,
        action: (item: T) -> Unit
    ): DataResult<Unit> {
        val sql = builder.buildSql() // Can throw FatalDatabaseException (BadStatementException)
        return builder.queryExecutor.executeStream(sql, params, fetchSize, rowMapper, options, action)
    }

    // --- Public terminal methods that use the helper method ---

    override fun forEachRow(params: Map<String, Any?>, action: (row: Map<String, Any?>) -> Unit): DataResult<Unit> {
        val options = builder.internalOptions()
        return executeStream(params, builder.rowMappers.ColumnNameMapper(options), options, action)
    }

    override fun <T : Any> forEachRowOf(
        mapper: DataMapper<T>,
        params: Map<String, Any?>,
        action: (obj: T) -> Unit
    ): DataResult<Unit> {
        val options = builder.internalOptions()
        return executeStream(params, builder.rowMappers.CustomObjectMapper(mapper, options), options, action)
    }

    override fun <T : Any> forEachRowOf(
        kClass: KClass<T>,
        params: Map<String, Any?>,
        action: (obj: T) -> Unit
    ): DataResult<Unit> {
        val options = builder.internalOptions()
        return executeStream(params, builder.rowMappers.DataObjectMapper(kClass, options), options, action)
    }
}
