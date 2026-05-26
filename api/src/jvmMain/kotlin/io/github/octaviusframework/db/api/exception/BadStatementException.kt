package io.github.octaviusframework.db.api.exception

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Messages for [BadStatementException] categorized by their root cause.
 *
 * Categories primarily cover PostgreSQL Class 42 (Syntax Error or Access Rule Violation)
 * and framework-level validation errors during query building.
 */
enum class BadStatementExceptionMessage {
    /** Mandatory clause (like WHERE in DELETE) is missing. */
    MISSING_CLAUSE,
    /** Named parameters defined in SQL are missing in the builder. */
    MISSING_PARAMETERS,
    /** Same parameter name used multiple times with conflicting definitions. */
    DUPLICATE_PARAMETERS,
    /** Operation attempted in a state not allowed by the transaction (e.g., write in read-only transaction). */
    INVALID_TRANSACTION_STATE,
    /** Query builder is in an inconsistent state for the requested operation. */
    INVALID_STATEMENT_STATE,
    /** Iterative query with fetchSize > 0 requires an active transaction. */
    ITERATIVE_REQUIRES_TRANSACTION,
    /** The requested feature is not supported by the current database provider or configuration. */
    UNSUPPORTED_FEATURE,

    // Class 42 — General Categories
    /** SQL syntax is malformed (PostgreSQL 42601, 42602, etc.). */
    SYNTAX_ERROR,
    /** Referenced table, column, function, or other object does not exist (PostgreSQL 42703, 42P01, etc.). */
    UNDEFINED_OBJECT,
    /** Attempt to create an object that already exists (PostgreSQL 42701, 42P07, etc.). */
    DUPLICATE_OBJECT,
    /** Reference to an object is ambiguous (PostgreSQL 42702, 42725, etc.). */
    AMBIGUOUS_OBJECT,
    /** Data type mismatch or invalid type coercion (PostgreSQL 42804, 42P18, etc.). */
    DATA_TYPE_ERROR,
    /** General error in object definition or schema mismatch. */
    INVALID_DEFINITION,
}

/**
 * Exception thrown when a query is semantically or syntactically invalid.
 *
 * This exception covers:
 * 1. **Framework Validation:** Errors detected before sending the query to the DB (e.g., missing WHERE clause).
 * 2. **Database Syntax/Access Errors:** Errors returned by PostgreSQL (Class 42), such as typos in table names
 *    or incorrect SQL syntax.
 *
 * As a [FatalDatabaseException], it indicates a developer error that cannot be
 * recovered from at runtime without code changes.
 */
class BadStatementException(
    val messageEnum: BadStatementExceptionMessage,
    val errorPosition: Int? = null,
    queryContext: QueryContext? = null, cause: Throwable?
) : FatalDatabaseException(messageEnum.name, queryContext, cause) {

    override fun getDetailedMessage(): String {
        return buildString {
            append("\n")
            appendLine("message: ${generateDeveloperMessage(messageEnum)}")

            val sqlToAnalyze = queryContext?.dbSql ?: queryContext?.sql
            if (errorPosition != null && sqlToAnalyze != null) {
                appendLine()
                appendLine("ERROR LOCATION:")
                appendLine(highlightSqlPosition(sqlToAnalyze, errorPosition))
            }
        }
    }

    private fun highlightSqlPosition(sql: String, position: Int): String {
        if (position > sql.length) return "Position: $position"

        val zeroBasedPos = position - 1

        var lineStart = sql.lastIndexOf('\n', zeroBasedPos)
        lineStart = if (lineStart == -1) 0 else lineStart + 1

        var lineEnd = sql.indexOf('\n', zeroBasedPos)
        if (lineEnd == -1) lineEnd = sql.length

        val line = sql.substring(lineStart, lineEnd).replace("\r", "")

        val colIndex = zeroBasedPos - lineStart

        val cleanLine = line.replace('\t', ' ')
        val pointer = " ".repeat(colIndex.coerceAtLeast(0)) + "^"

        return "$cleanLine\n$pointer"
    }

}

private fun generateDeveloperMessage(
    messageEnum: BadStatementExceptionMessage
): String {
    return when (messageEnum) {
        BadStatementExceptionMessage.SYNTAX_ERROR -> "SQL syntax error. The statement is malformed."
        BadStatementExceptionMessage.UNDEFINED_OBJECT -> "Database object not found (table, column, function, etc.)."
        BadStatementExceptionMessage.DUPLICATE_OBJECT -> "Database object already exists."
        BadStatementExceptionMessage.AMBIGUOUS_OBJECT -> "Ambiguous reference to a database object."
        BadStatementExceptionMessage.DATA_TYPE_ERROR -> "Data type mismatch or invalid coercion."
        BadStatementExceptionMessage.INVALID_DEFINITION -> "Invalid object definition or schema mismatch."
        BadStatementExceptionMessage.INVALID_TRANSACTION_STATE -> "Invalid transaction state."
        BadStatementExceptionMessage.MISSING_CLAUSE -> "Query clause is missing."
        BadStatementExceptionMessage.MISSING_PARAMETERS -> "Missing parameters in query."
        BadStatementExceptionMessage.DUPLICATE_PARAMETERS -> "Duplicate parameters in query."
        BadStatementExceptionMessage.INVALID_STATEMENT_STATE -> "Statement is in invalid state."
        BadStatementExceptionMessage.ITERATIVE_REQUIRES_TRANSACTION -> "Iterative query with fetchSize > 0 requires an active transaction to work correctly in PostgreSQL."
        BadStatementExceptionMessage.UNSUPPORTED_FEATURE -> "The requested feature is not supported by the current database provider or configuration."
    }
}

@OptIn(ExperimentalContracts::class)
fun checkStatement(
    value: Boolean,
    messageEnum: BadStatementExceptionMessage = BadStatementExceptionMessage.MISSING_CLAUSE,
    details: () -> String
) {
    contract {
        returns() implies value
    }
    if (!value) {
        throw BadStatementException(messageEnum, cause = IllegalStateException(details()))
    }
}

@OptIn(ExperimentalContracts::class)
inline fun requireStatement(
    value: Boolean,
    messageEnum: BadStatementExceptionMessage = BadStatementExceptionMessage.MISSING_CLAUSE,
    details: () -> String
) {
    contract {
        returns() implies value
    }
    if (!value) {
        throw BadStatementException(messageEnum, cause = IllegalArgumentException(details()))
    }
}
