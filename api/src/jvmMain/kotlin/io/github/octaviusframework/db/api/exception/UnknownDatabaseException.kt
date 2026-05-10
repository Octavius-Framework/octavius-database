package io.github.octaviusframework.db.api.exception

class UnknownDatabaseException(
    message: String,
    queryContext: QueryContext? = null,
    cause: Throwable?,
) : DatabaseException(message, cause, queryContext = queryContext)