package io.github.octaviusframework.db.api.exception

enum class TransactionExceptionMessage {
    TIMEOUT,
    DEADLOCK,
    SERIALIZATION_FAILURE,
    UNKNOWN_ROLLBACK
}

/**
 * Concurrency and transaction-related issues (e.g., deadlocks, timeouts).
 */
class TransactionException(
    val messageEnum: TransactionExceptionMessage,
    queryContext: QueryContext?,
    cause: Throwable?
): DatabaseException(messageEnum.name, cause, queryContext) {
    override val includeCauseInToString = false
}
