package io.github.octaviusframework.db.core.builder

import io.github.octaviusframework.db.api.builder.UpdateQueryBuilder
import io.github.octaviusframework.db.api.exception.checkStatement
import io.github.octaviusframework.db.core.jdbc.RowMappers
import io.github.octaviusframework.db.core.type.registry.TypeRegistry

/** Internal implementation of [UpdateQueryBuilder] for building SQL UPDATE statements. */
internal class DatabaseUpdateQueryBuilder(
    queryExecutor: QueryExecutor,
    rowMappers: RowMappers,
    typeRegistry: TypeRegistry,
    table: String
) : AbstractQueryBuilder<UpdateQueryBuilder>(queryExecutor, rowMappers, typeRegistry, table), UpdateQueryBuilder {

    override val canReturnResultsByDefault = false
    private val setClauses = mutableMapOf<String, String>()
    private var fromClause: String? = null
    private var whereClause: String? = null

    override fun setExpression(column: String, value: String): UpdateQueryBuilder = apply {
        // Similar to below, but for a single column
        setClauses[column] = value
    }

    override fun setExpressions(values: Map<String, String>): UpdateQueryBuilder = apply {
        values.forEach { (key, value) ->
            setClauses[key] = value
        }
    }

    override fun setValues(values: Map<String, Any?>): UpdateQueryBuilder {
        // Generate placeholder map for the existing setExpressions method
        val placeholders = values.keys.associateWith { key -> "@$key" }
        return this.setExpressions(placeholders)
    }

    override fun setValues(values: List<String>): UpdateQueryBuilder {
        // Generate placeholder map for the existing setExpressions method
        val placeholders = values.associateWith { key -> "@$key" }
        return this.setExpressions(placeholders)
    }

    override fun setValue(column: String): UpdateQueryBuilder {
        return this.setExpression(column, "@$column")
    }

    override fun from(tables: String): UpdateQueryBuilder = apply {
        this.fromClause = tables
    }

    override fun where(condition: String): UpdateQueryBuilder = apply {
        this.whereClause = condition
    }

    override fun buildSql(): String {
        checkStatement(setClauses.isNotEmpty()) { "Cannot build an UPDATE statement without a SET clause." }
        checkStatement(!whereClause.isNullOrBlank()) { "Cannot build an UPDATE statement without a WHERE clause for safety." }

        val sql = StringBuilder(buildWithClause())
        sql.append("UPDATE $table")

        sql.append("\nSET\n  ")
        sql.append(setClauses.entries.joinToString(",\n  ") { "${it.key} = ${it.value}" })

        fromClause?.let { sql.append("\nFROM $it") }

        sql.append("\nWHERE $whereClause")
        sql.append(buildReturningClause())

        return sql.toString()
    }

    override fun copy(): DatabaseUpdateQueryBuilder {
        // 1. Create a new, "clean" instance using the main constructor
        val newBuilder = DatabaseUpdateQueryBuilder(
            this.queryExecutor,
            this.rowMappers,
            this.typeRegistry,
            this.table!! // Non-null because table is nullable in AbstractQueryBuilder
        )

        // 2. Copy state from base class using helper method
        newBuilder.copyBaseStateFrom(this)

        // 3. Copy state specific to THIS class
        newBuilder.fromClause = this.fromClause
        newBuilder.whereClause = this.whereClause
        newBuilder.setClauses.putAll(setClauses)

        // 4. Return fully configured copy
        return newBuilder
    }
}
