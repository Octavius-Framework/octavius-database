package io.github.octaviusframework.db.api.exception

enum class TransactionExceptionMessage {
    TIMEOUT,
    DEADLOCK,
    SERIALIZATION_FAILURE,
    TRANSACTION_ROLLBACK
}

/**
 * Concurrency and transaction-related issues (e.g., deadlocks, timeouts).
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
