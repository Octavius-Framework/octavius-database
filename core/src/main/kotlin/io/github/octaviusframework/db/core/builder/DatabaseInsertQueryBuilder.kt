package io.github.octaviusframework.db.core.builder

import io.github.octaviusframework.db.api.builder.InsertQueryBuilder
import io.github.octaviusframework.db.api.builder.OnConflictClauseBuilder
import io.github.octaviusframework.db.api.exception.BadStatementExceptionMessage
import io.github.octaviusframework.db.api.exception.checkStatement
import io.github.octaviusframework.db.api.exception.requireStatement
import io.github.octaviusframework.db.core.jdbc.RowMappers
import io.github.octaviusframework.db.core.type.registry.TypeRegistry

/** Internal implementation of [InsertQueryBuilder] for building SQL INSERT statements. */
internal class DatabaseInsertQueryBuilder(
    queryExecutor: QueryExecutor,
    rowMappers: RowMappers,
    typeRegistry: TypeRegistry,
    table: String
) : AbstractQueryBuilder<InsertQueryBuilder>(queryExecutor, rowMappers, typeRegistry, table), InsertQueryBuilder {

    override val canReturnResultsByDefault = false
    private var explicitColumns: List<String>? = null
    private val valuePlaceholders = mutableMapOf<String, String>()
    private var selectSource: String? = null
    private var onConflictBuilder: DatabaseOnConflictClauseBuilder? = null

    override fun columns(vararg columns: String): InsertQueryBuilder = apply {
        this.explicitColumns = columns.toList()
    }

    override fun valuesExpressions(expressions: Map<String, String>): InsertQueryBuilder = apply {
        checkStatement(
            selectSource == null,
            BadStatementExceptionMessage.INVALID_STATEMENT_STATE
        ) { "Cannot use valuesExpressions() when fromSelect() has already been called." }
        expressions.forEach { (key, value) ->
            valuePlaceholders[key] = value
        }
    }

    override fun valueExpression(column: String, expression: String): InsertQueryBuilder = apply {
        checkStatement(
            selectSource == null,
            BadStatementExceptionMessage.INVALID_STATEMENT_STATE
        ) { "Cannot use valueExpression() when fromSelect() has already been called." }
        valuePlaceholders[column] = expression
    }

    override fun values(data: Map<String, Any?>): InsertQueryBuilder {
        checkStatement(
            selectSource == null,
            BadStatementExceptionMessage.INVALID_STATEMENT_STATE
        ) { "Cannot use values() when fromSelect() has already been called." }
        val placeholders = data.keys.associateWith { key -> "@$key" }
        // Delegate to low-level method
        return this.valuesExpressions(placeholders)
    }

    override fun values(values: List<String>): InsertQueryBuilder {
        checkStatement(
            selectSource == null,
            BadStatementExceptionMessage.INVALID_STATEMENT_STATE
        ) { "Cannot use values() when fromSelect() has already been called." }
        val placeholders = values.associateWith { key -> "@$key" }
        // Delegate to low-level method
        return this.valuesExpressions(placeholders)
    }

    override fun value(column: String): InsertQueryBuilder {
        checkStatement(
            selectSource == null,
            BadStatementExceptionMessage.INVALID_STATEMENT_STATE
        ) { "Cannot use value() when fromSelect() has already been called." }
        // Delegate to low-level method
        return this.valueExpression(column, "@$column")
    }

    override fun fromSelect(query: String): InsertQueryBuilder = apply {
        checkStatement(
            valuePlaceholders.isEmpty(),
            BadStatementExceptionMessage.INVALID_STATEMENT_STATE
        ) { "Cannot use fromSelect() when values() has already been called." }

        this.selectSource = query
    }

    /**
     * Configures the ON CONFLICT clause in a fluent and safe manner.
     *
     * ```kotlin
     * // Re-enlist a legionnaire under the same legion — update their rank if the record already exists
     * dataAccess.insertInto("legionnaires")
     *     .values(data)
     *     .onConflict {
     *         onColumns("name", "legion_id")
     *         doUpdate(
     *             "rank" to "EXCLUDED.rank",
     *             "re_enlisted_at" to "NOW()"
     *         )
     *     }
     *     .execute(data)
     * ```
     */
    override fun onConflict(config: OnConflictClauseBuilder.() -> Unit): InsertQueryBuilder = apply {
        if (onConflictBuilder == null) {
            onConflictBuilder = DatabaseOnConflictClauseBuilder()
        }
        onConflictBuilder!!.apply(config)
    }

    override fun buildSql(): String {
        val hasValues = valuePlaceholders.isNotEmpty()
        val hasSelect = selectSource != null

        checkStatement(hasValues || hasSelect) { "Cannot build an INSERT statement without values or a SELECT source." }

        val sql = StringBuilder(buildWithClause())

        // Determine columns: explicit > from values > none (for fromSelect)
        val targetColumns = explicitColumns
            ?: valuePlaceholders.keys.toList().ifEmpty { null }

        if (targetColumns != null) {
            val columnsSql = targetColumns.joinToString(", ")
            sql.append("INSERT INTO $table ($columnsSql)")
        } else {
            // No columns specified - valid for fromSelect()
            sql.append("INSERT INTO $table")
        }

        if (hasValues) {
            checkStatement(targetColumns != null) { "Cannot build VALUES clause without columns" }
            val placeholders = targetColumns.joinToString(", ") { key ->
                val placeholder = valuePlaceholders[key]
                checkStatement(
                    placeholder != null,
                    BadStatementExceptionMessage.INVALID_STATEMENT_STATE
                ) { "No value or expression provided for column '$key'" }
                placeholder
            }
            sql.append("\nVALUES ($placeholders)")
        } else {
            sql.append("\n").append(selectSource!!)
        }

        onConflictBuilder?.let { builder ->
            checkStatement(builder.target != null) { "ON CONFLICT target (columns or constraint) must be specified." }
            checkStatement(builder.action != null) { "ON CONFLICT action (doNothing or doUpdate) must be specified." }

            val target = builder.target!!
            val action = builder.action!!

            // onConstraint already contains "ON CONSTRAINT", so we don't add parentheses
            val targetSql = if (target.startsWith("ON CONSTRAINT")) target else "($target)"

            sql.append("\nON CONFLICT $targetSql $action")
        }

        sql.append(buildReturningClause())

        return sql.toString()
    }

    //------------------------------------------------------------------------------------------------------------------
    //                                          COPY
    //------------------------------------------------------------------------------------------------------------------

    /**
     * Creates and returns a deep copy of this builder.
     * Enables safe creation of query variants without modifying the original.
     */
    override fun copy(): DatabaseInsertQueryBuilder {
        // 1. Create a new, "clean" instance using the main constructor
        val newBuilder = DatabaseInsertQueryBuilder(
            this.queryExecutor,
            this.rowMappers,
            this.typeRegistry,
            this.table!! // We know table is not null for INSERT
        )

        newBuilder.copyBaseStateFrom(this)

        newBuilder.explicitColumns = this.explicitColumns // List is immutable, safe to share reference
        newBuilder.valuePlaceholders.putAll(this.valuePlaceholders) // Copy map contents, not reference!
        newBuilder.selectSource = this.selectSource
        // Also copy onConflictBuilder if it exists! Use its own copy() method.
        newBuilder.onConflictBuilder = this.onConflictBuilder?.copy()

        // 4. Return fully configured copy
        return newBuilder
    }

}

internal class DatabaseOnConflictClauseBuilder : OnConflictClauseBuilder {
    /** Defines the conflict target as a list of columns. */
    internal var target: String? = null

    /** Defines the conflict target as an existing constraint name. */
    internal var action: String? = null

    /** Defines the conflict target (columns). */
    override fun onColumns(vararg columns: String) {
        target = columns.joinToString(", ")
    }

    /** Defines the conflict target (constraint name). */
    override fun onConstraint(constraintName: String) {
        target = "ON CONSTRAINT $constraintName"
    }

    /** In case of conflict, do nothing (DO NOTHING). */
    override fun doNothing() {
        action = "DO NOTHING"
    }

    /**
     * Defines the DO UPDATE action using a raw SET expression.
     * Use `EXCLUDED` to reference the values that were attempted to insert.
     *
     * ```kotlin
     * doUpdate("tribute_amount = EXCLUDED.tribute_amount")
     * ```
     */
    override fun doUpdate(setExpression: String, whereCondition: String?) {
        requireStatement(setExpression.isNotBlank()) { "doUpdate cannot be blank." }
        val updateAction = StringBuilder("DO UPDATE SET $setExpression")
        whereCondition?.let {
            updateAction.append(" WHERE $it")
        }
        action = updateAction.toString()
    }

    /**
     * Defines the DO UPDATE action using column-value pairs.
     *
     * ```kotlin
     * doUpdate(
     *     "tribute_amount" to "EXCLUDED.tribute_amount",
     *     "last_collected_at" to "NOW()"
     * )
     * ```
     */
    override fun doUpdate(vararg setPairs: Pair<String, String>, whereCondition: String?) {
        val setExpression = setPairs.joinToString(",\n") { (column, expression) ->
            "$column = $expression"
        }

        // Call the original method to avoid duplicating clause building logic
        doUpdate(setExpression, whereCondition)
    }

    /**
     * Defines the DO UPDATE action using a map.
     * Useful when the update columns are determined at runtime,
     * e.g., when only non-null fields of a patch request should be updated.
     */
    override fun doUpdate(setMap: Map<String, String>, whereCondition: String?) {
        val setExpression = setMap.map { (column, expression) ->
            "$column = $expression"
        }.joinToString(",\n")

        doUpdate(setExpression, whereCondition)
    }

    /**
     * Creates a copy of this ON CONFLICT clause builder.
     */
    fun copy(): DatabaseOnConflictClauseBuilder {
        val newBuilder = DatabaseOnConflictClauseBuilder()
        newBuilder.target = this.target
        newBuilder.action = this.action
        return newBuilder
    }
}
