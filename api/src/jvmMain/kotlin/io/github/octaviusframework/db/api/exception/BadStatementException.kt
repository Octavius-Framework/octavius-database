package io.github.octaviusframework.db.api.exception

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

enum class BadStatementExceptionMessage {
    MISSING_CLAUSE,
    MISSING_PARAMETERS,
    DUPLICATE_PARAMETERS,
    INVALID_TRANSACTION_STATE,
    INVALID_STATEMENT_STATE,

    // Class 42 — General Categories
    SYNTAX_ERROR,
    UNDEFINED_OBJECT,
    DUPLICATE_OBJECT,
    AMBIGUOUS_OBJECT,
    DATA_TYPE_ERROR,
    INVALID_DEFINITION
}

/**
 * Exception thrown when a query cannot be built correctly due to invalid state
 * or missing mandatory clauses (e.g., DELETE without WHERE).
 *
 */
class BadStatementException(
    val messageEnum: BadStatementExceptionMessage,
    queryContext: QueryContext? = null, cause: Throwable?
) : FatalDatabaseException(messageEnum.name, queryContext, cause) {
    //TODO some kind of context for MISSING_CLAUSE and INVALID_STATEMENT_STATE
    override fun getDetailedMessage(): String {
        return buildString {
            append("\n")
            appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        }
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
