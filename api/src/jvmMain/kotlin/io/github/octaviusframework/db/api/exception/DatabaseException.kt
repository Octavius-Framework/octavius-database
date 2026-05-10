package io.github.octaviusframework.db.api.exception

/**
 * Base sealed exception for most Octavius Database errors.
 * Only InitializationException and BuilderException are excluded as they are thrown and can't be inside DataResult
 */
sealed class DatabaseException(
    message: String,
    cause: Throwable? = null,
    queryContext: QueryContext? = null
): OctaviusException(message, cause, queryContext)
