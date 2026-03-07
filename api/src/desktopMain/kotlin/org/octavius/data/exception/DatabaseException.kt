package org.octavius.data.exception

/**
 * Base sealed exception for all data layer errors.
 * 
 * NOTE: This class is being superseded by OctaviusDatabaseException.
 * Existing code should migrate to catching OctaviusDatabaseException.
 */
sealed class DatabaseException(
    message: String,
    cause: Throwable? = null,
    val queryContext: QueryContext? = null
) : RuntimeException(message, cause)

/**
 * Errors during SQL query execution.
 * 
 * @deprecated Use OctaviusDatabaseException.DatabaseExecutionException instead.
 */
@Deprecated("Use OctaviusDatabaseException.DatabaseExecutionException instead", 
    replaceWith = ReplaceWith("OctaviusDatabaseException.DatabaseExecutionException"))
class QueryExecutionException(
    val sql: String,
    val params: Map<String, Any?>,
    val expandedSql: String? = null,
    val expandedParams: List<Any?>? = null,
    message: String? = null,
    cause: Throwable? = null,
    queryContext: QueryContext? = null
) : DatabaseException(
    message ?: "Error during query execution",
    cause,
    queryContext ?: QueryContext(sql, params, expandedSql, expandedParams)
) {

    override fun toString(): String {
        val nestedError = cause?.toString()?.prependIndent("|   ") ?: "|   No cause available"

        return """
        
${this@QueryExecutionException.queryContext}

------------------------------------------------------------
| ERROR CAUSE:
------------------------------------------------------------
$nestedError
------------------------------------------------------------
        """.trimIndent()
    }
}
