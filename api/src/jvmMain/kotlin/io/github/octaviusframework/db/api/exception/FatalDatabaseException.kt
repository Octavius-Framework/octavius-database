package io.github.octaviusframework.db.api.exception

abstract class FatalDatabaseException(
    message: String, queryContext: QueryContext?,
    cause: Throwable?
) : OctaviusException(message, cause, queryContext)
