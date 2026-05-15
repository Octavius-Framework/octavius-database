package io.github.octaviusframework.db.core.builder

import io.github.octaviusframework.db.api.builder.RawQueryBuilder
import io.github.octaviusframework.db.core.jdbc.RowMappers
import io.github.octaviusframework.db.core.type.registry.TypeRegistry

/**
 * Executes a raw SQL query that returns results.
 * Allows passing arbitrary SQL for execution with convenient terminal methods.
 */
internal class DatabaseRawQueryBuilder(
    queryExecutor: QueryExecutor,
    rowMappers: RowMappers,
    typeRegistry: TypeRegistry,
    private val sql: String
) : AbstractQueryBuilder<RawQueryBuilder>(queryExecutor, rowMappers, typeRegistry), RawQueryBuilder {

    override val canReturnResultsByDefault = true
    override fun buildSql(): String = sql

    override fun copy(): DatabaseRawQueryBuilder {
        val newBuilder = DatabaseRawQueryBuilder(queryExecutor, rowMappers, typeRegistry, sql)
        newBuilder.copyBaseStateFrom(this)
        return newBuilder
    }
}
