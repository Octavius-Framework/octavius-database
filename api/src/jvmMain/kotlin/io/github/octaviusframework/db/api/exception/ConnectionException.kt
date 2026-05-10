package io.github.octaviusframework.db.api.exception

/**
 * Infrastructure and connectivity issues.
 */
class ConnectionException(
    message: String,
    queryContext: QueryContext? = null,
    cause: Throwable?
) : DatabaseException(message, cause, queryContext)