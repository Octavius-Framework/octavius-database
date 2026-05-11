package io.github.octaviusframework.db.api.exception

/**
 * Exception thrown when an error occurs in the database that is not recognized
 * or categorized into a more specific [DatabaseException] subtype.
 */
class UnknownDatabaseException(
    message: String,
    queryContext: QueryContext? = null,
    cause: Throwable?,
) : DatabaseException(message, cause, queryContext = queryContext)