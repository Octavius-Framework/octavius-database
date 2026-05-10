package io.github.octaviusframework.db.api.exception

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

enum class BadStatementExceptionMessage {
    MISSING_CLAUSE,
    MISSING_PARAMETERS,
    DUPLICATE_PARAMETERS,
    SYNTAX_ERROR,
    OBJECT_NOT_FOUND,
    INVALID_TRANSACTION_STATE,
    INVALID_STATEMENT_STATE
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
        BadStatementExceptionMessage.SYNTAX_ERROR ->
            "SQL syntax error. The statement is malformed or contains invalid syntax."

        BadStatementExceptionMessage.OBJECT_NOT_FOUND ->
            "Database object not found. Ensure that the table, column, or function exists and is correctly spelled."

        BadStatementExceptionMessage.INVALID_TRANSACTION_STATE ->
            "Invalid transaction state. The operation cannot be performed in the current state of the transaction (e.g., trying to write in a read-only transaction or after a previous error)."

        BadStatementExceptionMessage.MISSING_CLAUSE -> "Query clause is missing."

        BadStatementExceptionMessage.MISSING_PARAMETERS -> "Missing parameters in query"

        BadStatementExceptionMessage.DUPLICATE_PARAMETERS -> "Duplicate parameters in query"
        BadStatementExceptionMessage.INVALID_STATEMENT_STATE -> "Statement is in invalid state"
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
