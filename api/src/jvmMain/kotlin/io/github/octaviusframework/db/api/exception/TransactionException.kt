package io.github.octaviusframework.db.api.exception

/**
 * Messages for [TransactionException] covering concurrency and transaction lifecycle errors.
 */
enum class TransactionExceptionMessage {
    /** The operation timed out (PostgreSQL 57014 or lock timeout). */
    TIMEOUT,
    /** A deadlock was detected and this transaction was rolled back (PostgreSQL 40P01). */
    DEADLOCK,
    /** Concurrent updates prevented transaction serialization (PostgreSQL 40001). */
    SERIALIZATION_FAILURE,
    /** The transaction was rolled back by the database for internal reasons (PostgreSQL 40000). */
    TRANSACTION_ROLLBACK
}

/**
 * Exception thrown for concurrency-related issues and transaction lifecycle failures.
 *
 * These errors are typically transient and may succeed if retried (especially [DEADLOCK][TransactionExceptionMessage.DEADLOCK]
 * and [SERIALIZATION_FAILURE][TransactionExceptionMessage.SERIALIZATION_FAILURE]).
 */
class TransactionException(
    val messageEnum: TransactionExceptionMessage,
    queryContext: QueryContext?,
    cause: Throwable?
): DatabaseException(messageEnum.name, cause, queryContext) {

    override fun getDetailedMessage(): String {
        return buildString {
            append("\n")
            appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        }
    }
}

private fun generateDeveloperMessage(
    messageEnum: TransactionExceptionMessage
): String {
    return when (messageEnum) {
        TransactionExceptionMessage.TIMEOUT -> "Transaction or statement timeout exceeded."
        TransactionExceptionMessage.DEADLOCK -> "Deadlock detected. The transaction was chosen as a victim to break the cycle."
        TransactionExceptionMessage.SERIALIZATION_FAILURE -> "Serialization failure. The transaction could not be completed due to concurrent updates."
        TransactionExceptionMessage.TRANSACTION_ROLLBACK -> "The transaction was rolled back by the database."
    }
}
