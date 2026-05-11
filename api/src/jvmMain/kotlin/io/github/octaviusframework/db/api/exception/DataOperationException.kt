package io.github.octaviusframework.db.api.exception

/**
 * Messages for [DataOperationException] related to data retrieval and permissions.
 */
enum class DataOperationExceptionMessage {
    /** Query returned no rows when at least one was expected (e.g., in toSingle() or toField()). */
    EMPTY_RESULT,
    /** Access denied due to database-level permissions (PostgreSQL Class 42). */
    PERMISSION_DENIED,
    /** General data-related error (PostgreSQL Class 22), such as invalid input syntax for a type. */
    INVALID_DATA_FORMAT
}

/**
 * Exception thrown during data operations that are syntactically correct but fail
 * due to data content, result expectations, or permission issues.
 *
 * This exception typically represents errors that occur during the execution phase,
 * such as when a single result is expected but none is found.
 */
class DataOperationException(
    val messageEnum: DataOperationExceptionMessage,
    queryContext: QueryContext? = null,
    cause: Throwable? = null
) : DatabaseException(messageEnum.name, cause, queryContext) {

    override fun getDetailedMessage(): String {
        return buildString {
            append("\n")
            appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        }
    }
}

private fun generateDeveloperMessage(
    messageEnum: DataOperationExceptionMessage
): String {
    return when (messageEnum) {
        DataOperationExceptionMessage.EMPTY_RESULT -> "The query returned no rows, but at least one was required by the terminal method (e.g., toSingle())."
        DataOperationExceptionMessage.PERMISSION_DENIED -> "Access denied. The database user does not have sufficient permissions for this operation."
        DataOperationExceptionMessage.INVALID_DATA_FORMAT -> "Data format error. The provided value is incompatible with the expected database type."
    }
}
