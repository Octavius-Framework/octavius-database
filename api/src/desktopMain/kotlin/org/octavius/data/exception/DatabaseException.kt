package org.octavius.data.exception

/**
 * Base sealed exception for all Octavius Database errors.
 */
sealed class DatabaseException(
    message: String,
    cause: Throwable? = null,
    queryContext: QueryContext? = null,
    private val includeCauseInToString: Boolean = true
): RuntimeException(message, cause) {

    private var _queryContext: QueryContext? = queryContext
    open val queryContext: QueryContext? get() = _queryContext

    /**
     * Enriches the exception with the transaction step index.
     */
    fun withStepIndex(index: Int): DatabaseException {
        _queryContext = _queryContext?.withTransactionStep(index) 
            ?: QueryContext(sql = "", parameters = emptyMap(), transactionStepIndex = index)
        return this
    }

    /**
     * Enriches the exception with a full query context.
     */
    fun withContext(context: QueryContext): DatabaseException {
        _queryContext = context
        return this
    }

    /**
     * Subclasses can provide additional technical details here.
     */
    open fun getDetailedMessage(): String? = null

    override fun toString(): String {
        val contextStr = queryContext?.toString() ?: ""
        val detailedMsg = getDetailedMessage()?.let { "| DETAILS: $it\n" } ?: ""
        
        val causeSection = if (includeCauseInToString) {
            val nestedError = cause?.toString()?.prependIndent("|   ") ?: "|   No cause available"
            """
| CAUSE:
------------------------------------------------------------
$nestedError
------------------------------------------------------------
"""
        } else ""

        return """
$contextStr

------------------------------------------------------------
| ERROR: ${this::class.simpleName}
| MESSAGE: $message
${detailedMsg}------------------------------------------------------------
$causeSection
"""
    }
}

/**
 * Errors in the application code or framework logic (e.g., mapping failures, dependency errors).
 * Errors that probably can't be fixed without database or code changes
 */
sealed class CodeExecutionException(
    val details: String,
    message: String,
    queryContext: QueryContext?,
    cause: Throwable?
) : DatabaseException(message, cause, queryContext, includeCauseInToString = true) {
    override fun getDetailedMessage(): String? = details
}

/**
 * Thrown when the SQL query is syntactically incorrect or references non-existent objects.
 */
class GrammarException(
    message: String,
    queryContext: QueryContext?,
    cause: Throwable?
) : DatabaseException(message, cause, queryContext, includeCauseInToString = true)

/**
 * Thrown when the database user lacks sufficient privileges to perform an operation.
 */
class PermissionException(
    message: String,
    queryContext: QueryContext?,
    cause: Throwable?
) : DatabaseException(message, cause, queryContext, includeCauseInToString = true)

/**
 * Infrastructure and connectivity issues.
 */
class ConnectionException(
    message: String,
    cause: Throwable?
) : DatabaseException(message, cause, null, includeCauseInToString = true)

/**
 * Concurrency and transaction-related issues (e.g., deadlocks, timeouts).
 */
class ConcurrencyException(
    val errorType: ConcurrencyErrorType,
    queryContext: QueryContext?,
    cause: Throwable?
) : DatabaseException("Concurrency error: $errorType", cause, queryContext, includeCauseInToString = false)

enum class ConcurrencyErrorType {
    TIMEOUT,
    DEADLOCK
}

class UnknownDatabaseException(
    message: String,
    cause: Throwable?,
): DatabaseException(message, cause, includeCauseInToString = true)