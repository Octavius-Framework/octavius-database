package io.github.octaviusframework.db.api.exception

/**
 * Exception thrown when there are infrastructure or connectivity issues.
 *
 * This includes failures to obtain a connection from the pool, network timeouts,
 * or the database server being unreachable.
 */
class ConnectionException(
    message: String,
    queryContext: QueryContext? = null,
    cause: Throwable?
) : DatabaseException(message, cause, queryContext)