package io.github.octaviusframework.db.core.builder

import io.github.octaviusframework.db.api.builder.DeleteQueryBuilder
import io.github.octaviusframework.db.api.exception.checkStatement
import io.github.octaviusframework.db.core.jdbc.RowMappers

/** Internal implementation of [DeleteQueryBuilder] for building SQL DELETE statements. */
internal class DatabaseDeleteQueryBuilder(
    queryExecutor: QueryExecutor,
    rowMappers: RowMappers,
    table: String
) : AbstractQueryBuilder<DeleteQueryBuilder>(queryExecutor, rowMappers, table),
    DeleteQueryBuilder {
    override val canReturnResultsByDefault = false
    private var whereClause: String? = null
    private var usingClause: String? = null

    override fun using(tables: String): DeleteQueryBuilder = apply {
        this.usingClause = tables
    }

    override fun where(condition: String): DeleteQueryBuilder = apply {
        this.whereClause = condition
    }

    override fun buildSql(): String {
        checkStatement(!whereClause.isNullOrBlank()) { "Cannot build a DELETE statement without a WHERE clause for safety." }

        val sql = StringBuilder(buildWithClause())
        sql.append("DELETE FROM $table")
        usingClause?.let { sql.append("\nUSING $it") }
        sql.append("\nWHERE $whereClause")
        sql.append(buildReturningClause())

        return sql.toString()
    }

    override fun copy(): DatabaseDeleteQueryBuilder {
        // 1. Create a new, "clean" instance using the main constructor
        val newBuilder = DatabaseDeleteQueryBuilder(
            this.queryExecutor,
            this.rowMappers,
            this.table!! // Non-null because table is nullable in AbstractQueryBuilder
        )

        // 2. Copy state from base class using helper method
        newBuilder.copyBaseStateFrom(this)

        // 3. Copy state specific to THIS class
        newBuilder.whereClause = this.whereClause
        newBuilder.usingClause = this.usingClause

        // 4. Return fully configured copy
        return newBuilder
    }
}
