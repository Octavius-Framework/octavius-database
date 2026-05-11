package io.github.octaviusframework.db.api.exception

/**
 * Base exception for recoverable database errors that are returned within [io.github.octaviusframework.db.api.DataResult].
 *
 * These exceptions represent errors that occur during query execution (e.g., constraint violations,
 * serialization failures) and can often be handled by the application logic.
 */
sealed class DatabaseException(
    message: String,
    cause: Throwable? = null,
    queryContext: QueryContext? = null
): OctaviusException(message, cause, queryContext)
