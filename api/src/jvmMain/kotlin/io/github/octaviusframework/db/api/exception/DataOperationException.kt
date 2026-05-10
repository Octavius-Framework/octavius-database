package io.github.octaviusframework.db.api.exception

enum class DataOperationExceptionMessage {
    EMPTY_RESULT,
    INVALID_DATA_FORMAT,
    PERMISSION_DENIED
}


class DataOperationException(
    val messageEnum: DataOperationExceptionMessage, queryContext: QueryContext?,
    cause: Throwable?
) : DatabaseException(messageEnum.name, cause, queryContext)